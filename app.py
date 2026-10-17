"""
AIToolGems — Private Supplier Price Intelligence Dashboard
Streamlit application with 10 comprehensive operational sections.
Strictly confidential: For owner procurement only.
"""

import streamlit as st
import pandas as pd
from datetime import datetime, timezone
from pathlib import Path

from src.database import init_db, get_db, backup_database
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    RetailBenchmark, FxRate, ResearchQueue, AuditLog, AppSetting,
    VerificationStatus, RiskLevel, AccessType
)
from src.matching import generate_matching_key, parse_duration, parse_access_type
from src.pricing import PricingCalculator
from src.fx import FxEngine
from src.alerts import AlertEngine
from src.excel import export_master_catalog

# Page configuration
st.set_page_config(
    page_title="Supplier Intelligence | AIToolGems",
    page_icon="💎",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Ensure database is initialized
init_db()

st.sidebar.title("💎 Supplier Radar")
st.sidebar.caption("Private Procurement & Price Intelligence")

menu = st.sidebar.radio(
    "Navigation",
    [
        "1. Overview & Radar",
        "2. Supplier Directory",
        "3. Products & Plans",
        "4. Price Comparison",
        "5. Quote History",
        "6. Research Queue",
        "7. Manual Quote Capture",
        "8. Retail / Profit Calculator",
        "9. Import & Export",
        "10. Settings & Audit Log"
    ]
)

# -------------------------------------------------------------
# 1. OVERVIEW & RADAR
# -------------------------------------------------------------
if menu == "1. Overview & Radar":
    st.header("📊 Procurement Overview & Live Radar")
    st.info("🔒 **Strictly Confidential:** This workspace contains internal supplier cost rates. Do not publish to public storefront.")

    with get_db() as db:
        total_suppliers = db.query(Supplier).count()
        total_plans = db.query(ProductPlan).count()
        total_quotes = db.query(PriceObservation).count()
        verified_quotes = db.query(PriceObservation).filter_by(verification_status=VerificationStatus.VERIFIED).count()
        unverified_quotes = db.query(PriceObservation).filter(PriceObservation.verification_status != VerificationStatus.VERIFIED).count()
        high_risk_quotes = db.query(PriceObservation).filter(PriceObservation.risk_level.in_([RiskLevel.HIGH, RiskLevel.CRITICAL])).count()
        pending_research = db.query(ResearchQueue).count()

        col1, col2, col3, col4 = st.columns(4)
        col1.metric("Total Suppliers / Bots", total_suppliers)
        col2.metric("Exact Product Plans", total_plans)
        col3.metric("Total Quotes Tracked", total_quotes)
        col4.metric("Pending Research Leads", pending_research)

        col5, col6, col7, col8 = st.columns(4)
        col5.metric("Verified Quotes", verified_quotes)
        col6.metric("Unverified / Seed Quotes", unverified_quotes)
        col7.metric("High-Risk Offers", high_risk_quotes)
        col8.metric("Base FX (USD/PKR)", f"Rs. {FxEngine(db).get_rate('USD')[0]:,.1f}")

        st.subheader("⚠️ Live Alerts & Changes Feed")
        alert_engine = AlertEngine(db)
        alerts = alert_engine.scan_for_alerts()
        if alerts:
            for a in alerts[:6]:
                if a.severity == "WARNING":
                    st.warning(f"**[{a.alert_type}] {a.tool_plan}** ({a.supplier_name}): {a.message}")
                else:
                    st.info(f"**[{a.alert_type}] {a.tool_plan}** ({a.supplier_name}): {a.message}")
        else:
            st.success("All supplier quotes and price benchmarks are within normal risk parameters.")

        st.subheader("🏆 Quick Best-Rate Summary (Top Tools)")
        quick_rows = []
        for pl in db.query(ProductPlan).limit(10).all():
            obs = sorted(pl.observations, key=lambda x: x.amount_pkr)
            if obs:
                best = obs[0]
                quick_rows.append({
                    "Product": pl.product.name,
                    "Plan": pl.plan_name,
                    "Duration": pl.duration,
                    "Best Supplier": best.supplier.canonical_name,
                    "Cost USD": f"${best.amount:.2f}",
                    "Cost PKR": f"Rs. {best.amount_pkr:,.0f}",
                    "Warranty": best.warranty,
                    "Status": best.verification_status.value
                })
        if quick_rows:
            st.dataframe(pd.DataFrame(quick_rows), use_container_width=True)

# -------------------------------------------------------------
# 2. SUPPLIER DIRECTORY
# -------------------------------------------------------------
elif menu == "2. Supplier Directory":
    st.header("👥 Supplier & Bot Directory")
    with get_db() as db:
        search_term = st.text_input("🔍 Search supplier name, handle, or payment method:", "")
        query = db.query(Supplier)
        if search_term:
            query = query.filter(
                (Supplier.canonical_name.ilike(f"%{search_term}%")) |
                (Supplier.all_handles.ilike(f"%{search_term}%")) |
                (Supplier.payment_methods.ilike(f"%{search_term}%"))
            )
        suppliers = query.all()
        
        sup_data = []
        for s in suppliers:
            sup_data.append({
                "ID": s.id,
                "Name": s.canonical_name,
                "Primary Handle": s.primary_handle,
                "All Handles": s.all_handles,
                "Access Status": s.access_status,
                "Currencies": s.supported_currencies,
                "Payment Methods": s.payment_methods,
                "Reliability Score": f"{s.reliability_score:.0f}/100",
                "Risk Score": f"{s.risk_score:.0f}/100",
                "Public Link": s.public_url or "Interactive Bot Only"
            })
        st.dataframe(pd.DataFrame(sup_data), use_container_width=True)

# -------------------------------------------------------------
# 3. PRODUCTS & PLANS
# -------------------------------------------------------------
elif menu == "3. Products & Plans":
    st.header("📦 Canonical Products & Exact Plans")
    with get_db() as db:
        categories = ["All"] + sorted(list(set(p.category for p in db.query(Product).all())))
        sel_cat = st.selectbox("Filter Category:", categories)
        
        query = db.query(ProductPlan)
        if sel_cat != "All":
            query = query.join(Product).filter(Product.category == sel_cat)
            
        plans = query.all()
        plan_data = []
        for pl in plans:
            plan_data.append({
                "Tool": pl.product.name,
                "Category": pl.product.category,
                "Plan Name": pl.plan_name,
                "Duration": pl.duration,
                "Access Type": pl.access_type.value,
                "Units / Credits": pl.units or pl.credits,
                "Matching Key": pl.matching_key,
                "Total Quotes": len(pl.observations)
            })
        st.dataframe(pd.DataFrame(plan_data), use_container_width=True)

# -------------------------------------------------------------
# 4. PRICE COMPARISON
# -------------------------------------------------------------
elif menu == "4. Price Comparison":
    st.header("⚖️ Exact-Plan Price Comparison")
    st.write("Compare all supplier quotes for a specific plan side-by-side. Genuinely comparable offers only.")

    with get_db() as db:
        plans = db.query(ProductPlan).all()
        plan_options = {f"{p.product.name} — {p.plan_name} ({p.duration}, {p.access_type.value})": p for p in plans}
        sel_label = st.selectbox("Select Exact Product Plan:", list(plan_options.keys()))
        
        if sel_label:
            selected_plan = plan_options[sel_label]
            st.markdown(f"**Canonical Matching Key:** `{selected_plan.matching_key}`")

            obs_list = sorted(selected_plan.observations, key=lambda x: x.amount_pkr)
            if not obs_list:
                st.warning("No price observations recorded yet for this plan.")
            else:
                winner = obs_list[0]
                calc = PricingCalculator()
                res = calc.calculate(winner.amount, winner.fx_rate_used)

                col_w1, col_w2, col_w3 = st.columns(3)
                col_w1.metric("🏆 Lowest Sourcing Cost", f"${winner.amount:.2f} {winner.currency}", f"Rs. {winner.amount_pkr:,.0f} PKR")
                col_w2.metric("🏷 Suggested Web Retail", f"Rs. {res.suggested_retail_pkr:,.0f} PKR", f"{res.net_margin_pct}% margin")
                col_w3.metric("💰 Estimated Net Profit", f"+Rs. {res.estimated_net_profit_pkr:,.0f} PKR")

                st.subheader("All Supplier Offers for This Exact Plan:")
                comp_rows = []
                for idx, o in enumerate(obs_list, 1):
                    comp_rows.append({
                        "Rank": f"#{idx}" if idx > 1 else "🏆 Winner",
                        "Supplier": o.supplier.canonical_name,
                        "Handle": o.supplier.primary_handle,
                        "Price": f"${o.amount:.2f} {o.currency}",
                        "Price (PKR)": f"Rs. {o.amount_pkr:,.0f}",
                        "Warranty": o.warranty,
                        "Verification": o.verification_status.value,
                        "Risk Level": o.risk_level.value,
                        "Source Link": o.source_url or "Private Bot"
                    })
                st.dataframe(pd.DataFrame(comp_rows), use_container_width=True)

# -------------------------------------------------------------
# 5. QUOTE HISTORY
# -------------------------------------------------------------
elif menu == "5. Quote History":
    st.header("📈 Price Change & Event History")
    with get_db() as db:
        events = db.query(PriceHistory).order_by(PriceHistory.event_timestamp.desc()).all()
        if not events:
            st.info("No price change events logged yet. History is automatically recorded when quotes change.")
        else:
            h_data = []
            for ev in events:
                h_data.append({
                    "Timestamp UTC": ev.event_timestamp.strftime("%Y-%m-%d %H:%M"),
                    "Event Type": ev.event_type,
                    "Plan Key": ev.plan.matching_key if ev.plan else "",
                    "Old Amount": f"${ev.old_amount}" if ev.old_amount else "-",
                    "New Amount": f"${ev.new_amount} {ev.currency}",
                    "Details": ev.details
                })
            st.dataframe(pd.DataFrame(h_data), use_container_width=True)

# -------------------------------------------------------------
# 6. RESEARCH QUEUE
# -------------------------------------------------------------
elif menu == "6. Research Queue":
    st.header("🕵️‍♂️ Supplier Research & Inspection Queue")
    with get_db() as db:
        tasks = db.query(ResearchQueue).all()
        q_data = []
        for t in tasks:
            q_data.append({
                "ID": t.id,
                "Target URL / Handle": t.target_url,
                "Status": t.status.value,
                "Blocker / Notes": t.blocker_reason or "Public inspection pending",
                "Attempts": t.attempt_count,
                "Next Action": t.next_action
            })
        st.dataframe(pd.DataFrame(q_data), use_container_width=True)

# -------------------------------------------------------------
# 7. MANUAL QUOTE CAPTURE
# -------------------------------------------------------------
elif menu == "7. Manual Quote Capture":
    st.header("📝 Manual Quote Entry & Evidence Capture")
    st.write("Use this form to record a new price quote seen in private Telegram bot menus or screenshots.")

    with get_db() as db:
        suppliers = db.query(Supplier).all()
        plans = db.query(ProductPlan).all()

        with st.form("manual_quote_form"):
            col_f1, col_f2 = st.columns(2)
            sup_name = col_f1.selectbox("Supplier:", [s.canonical_name for s in suppliers])
            plan_key = col_f2.selectbox("Product Plan:", [p.matching_key for p in plans])

            col_p1, col_p2, col_p3 = st.columns(3)
            amount = col_p1.number_input("Price Amount:", min_value=0.1, value=5.0, step=0.5)
            currency = col_p2.selectbox("Currency:", ["USD", "USDT", "EUR", "PKR", "INR"])
            warranty = col_f2.text_input("Warranty Terms:", "Full Warranty (100% Replacement Guarantee)")

            raw_text = st.text_area("Copied Message Text / Quote Snippet:")
            source_link = st.text_input("Source URL / Channel Post Link:", "")
            reviewer_notes = st.text_input("Reviewer Notes:", "Manually observed quote")

            submitted = st.form_submit_button("Record Price Quote")
            if submitted:
                selected_sup = db.query(Supplier).filter_by(canonical_name=sup_name).first()
                selected_pl = db.query(ProductPlan).filter_by(matching_key=plan_key).first()
                
                fx = FxEngine(db)
                amt_pkr, rate_used, prov, _ = fx.convert_to_pkr(amount, currency)

                new_obs = PriceObservation(
                    supplier_id=selected_sup.id,
                    plan_id=selected_pl.id,
                    amount=amount,
                    currency=currency,
                    original_raw_text=raw_text or f"Manual quote of {amount} {currency}",
                    source_url=source_link,
                    warranty=warranty,
                    fx_rate_used=rate_used,
                    fx_provider=prov,
                    amount_pkr=amt_pkr,
                    verification_status=VerificationStatus.UNVERIFIED,
                    capture_method="MANUAL_INPUT",
                    content_hash=f"manual_{datetime.now(timezone.utc).timestamp()}",
                    reviewer_notes=reviewer_notes
                )
                db.add(new_obs)
                db.commit()
                st.success(f"Successfully recorded quote of ${amount:.2f} {currency} (Rs. {amt_pkr:,.0f} PKR) for {plan_key}!")

# -------------------------------------------------------------
# 8. RETAIL / PROFIT CALCULATOR
# -------------------------------------------------------------
elif menu == "8. Retail / Profit Calculator":
    st.header("🧮 Landed Cost & Profit Margin Calculator")
    col_c1, col_c2 = st.columns(2)
    
    with col_c1:
        st.subheader("Cost Inputs")
        calc_cost = st.number_input("Wholesale Sourcing Cost (USD):", min_value=0.1, value=5.0, step=0.5)
        calc_fx = st.number_input("USD to PKR Exchange Rate:", min_value=100.0, value=280.0, step=1.0)
        calc_comp = st.number_input("Competitor Retail Price in Pakistan (PKR, optional):", min_value=0.0, value=2500.0, step=50.0)
        calc_profit = st.number_input("Target Profit (PKR):", min_value=200.0, value=650.0, step=50.0)

    calc = PricingCalculator()
    res = calc.calculate(calc_cost, calc_fx, calc_comp if calc_comp > 0 else None, calc_profit)

    with col_c2:
        st.subheader("Breakdown & Suggested Selling Price")
        st.metric("Wholesale Base Cost", f"Rs. {res.supplier_cost_pkr:,.2f} PKR")
        st.write(f"• Payment & Gateway Fee (1.5%): Rs. {res.payment_fee_pkr:,.2f}")
        st.write(f"• Warranty & Replacement Reserve (3%): Rs. {res.warranty_reserve_pkr:,.2f}")
        st.metric("Total Landed Cost (Break-even)", f"Rs. {res.total_landed_cost_pkr:,.2f} PKR")
        st.markdown(f"### 🏷 Suggested Selling Price: **Rs. {res.suggested_retail_pkr:,.0f} PKR**")
        st.markdown(f"### 💰 Estimated Net Profit: **+Rs. {res.estimated_net_profit_pkr:,.0f} PKR** ({res.net_margin_pct}%)")

        if res.has_margin_conflict:
            st.error(res.conflict_notes)
        else:
            st.success("Target margin maintained without conflict.")

# -------------------------------------------------------------
# 9. IMPORT & EXPORT
# -------------------------------------------------------------
elif menu == "9. Import & Export":
    st.header("💾 Excel & Data Backup Hub")
    st.write("Generate or download the single persistent `master_catalog.xlsx` workbook (13 worksheets).")

    with get_db() as db:
        if st.button("Generate / Refresh master_catalog.xlsx"):
            export_path = export_master_catalog(db)
            st.success(f"Workbook successfully generated at: `{export_path}`")

        if Path("master_catalog.xlsx").exists():
            with open("master_catalog.xlsx", "rb") as f:
                st.download_button(
                    label="📥 Download master_catalog.xlsx",
                    data=f.read(),
                    file_name="master_catalog.xlsx",
                    mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )

        st.subheader("Database Snapshot Backup")
        if st.button("Create Instant Database Backup (.db)"):
            b_path = backup_database()
            st.success(f"Snapshot created: `{b_path}`")

# -------------------------------------------------------------
# 10. SETTINGS & AUDIT LOG
# -------------------------------------------------------------
elif menu == "10. Settings & Audit Log":
    st.header("⚙️ Configuration, Risk Policy & Audit Trail")
    with get_db() as db:
        st.subheader("Current FX Rates")
        fx_rates = db.query(FxRate).all()
        for f in fx_rates:
            st.write(f"• **{f.base_currency}/{f.quote_currency}**: {f.rate:.2f} (Source: {f.source_provider})")

        st.subheader("Audit Trail")
        logs = db.query(AuditLog).order_by(AuditLog.created_at.desc()).limit(20).all()
        if logs:
            l_data = [{"Date UTC": l.created_at.strftime("%Y-%m-%d %H:%M"), "Action": l.action_type, "Entity": l.entity_name, "Details": l.details} for l in logs]
            st.dataframe(pd.DataFrame(l_data), use_container_width=True)
        else:
            st.info("System is healthy. All transactions logged.")
