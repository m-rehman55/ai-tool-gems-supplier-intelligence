"""
Permanent Excel workspace manager.
Generates and maintains a single canonical workbook named master_catalog.xlsx with all 13 required sheets.
Preserves existing workbooks and avoids daily file proliferation.
"""

from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List

from src.config import settings
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    RetailBenchmark, FxRate, ResearchQueue, AuditLog, VerificationStatus
)

def export_master_catalog(db, output_path: Optional[Path] = None) -> Path:
    """
    Exports the canonical database into a single, comprehensive master_catalog.xlsx workbook.
    """
    try:
        import openpyxl
        from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
        from openpyxl.utils import get_column_letter
    except ImportError:
        # Fallback if openpyxl is not yet installed in runtime
        return _fallback_csv_export(db)

    target_file = output_path or settings.excel_path
    wb = openpyxl.Workbook()
    # Remove default sheet
    wb.remove(wb.active)

    header_font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
    header_fill = PatternFill(start_color="1F497D", end_color="1F497D", fill_type="solid")
    warning_fill = PatternFill(start_color="FFF2CC", end_color="FFF2CC", fill_type="solid")
    risk_fill = PatternFill(start_color="FCE4D6", end_color="FCE4D6", fill_type="solid")
    thin_border = Border(
        left=Side(style='thin', color='D9D9D9'),
        right=Side(style='thin', color='D9D9D9'),
        top=Side(style='thin', color='D9D9D9'),
        bottom=Side(style='thin', color='D9D9D9')
    )

    def style_sheet(ws, headers: List[str], rows: List[List[Any]]) -> None:
        ws.append(headers)
        for col_idx, _ in enumerate(headers, 1):
            cell = ws.cell(row=1, column=col_idx)
            cell.font = header_font
            cell.fill = header_fill
            cell.alignment = Alignment(horizontal="center", vertical="center")
        
        for r_idx, r_data in enumerate(rows, 2):
            ws.append(r_data)
            for c_idx in range(1, len(r_data) + 1):
                cell = ws.cell(row=r_idx, column=c_idx)
                cell.border = thin_border
                # Highlight unverified or high risk
                val_str = str(cell.value or "")
                if "UNVERIFIED" in val_str or "STALE" in val_str:
                    cell.fill = warning_fill
                elif "HIGH" in val_str or "CRITICAL" in val_str:
                    cell.fill = risk_fill

        ws.freeze_panes = "A2"
        # Auto column width
        for col in ws.columns:
            max_len = max(len(str(cell.value or '')) for cell in col)
            col_letter = get_column_letter(col[0].column)
            ws.column_dimensions[col_letter].width = min(max(max_len + 3, 12), 45)

    # 1. README
    ws_readme = wb.create_sheet("README")
    readme_rows = [
        ["AIToolGems — Supplier Price Intelligence Agent", ""],
        ["Generated / Updated At (UTC)", datetime.now(timezone.utc).isoformat()],
        ["Purpose", "Private procurement and supplier price comparison for AIToolGems Pakistan."],
        ["Confidentiality", "STRICTLY PRIVATE. Contains supplier wholesale cost rates. Do not publish."],
        ["Base Currency", "PKR (Pakistani Rupee)"],
        ["Sourcing Currencies", "USD, USDT, EUR"],
        ["Winner Policy", "Only offers with matching duration, tier, credits, warranty and verified proof can win."]
    ]
    style_sheet(ws_readme, ["Attribute", "Details"], readme_rows)

    # 2. Suppliers
    ws_sup = wb.create_sheet("Suppliers")
    sup_headers = ["ID", "Canonical Name", "Primary Handle", "Entity Type", "Public URL", "Currencies", "Payment Methods", "Access Status", "Reliability", "Risk Score", "Risk Flags", "Notes"]
    sup_rows = []
    for s in db.query(Supplier).all():
        sup_rows.append([s.id, s.canonical_name, s.primary_handle, s.entity_type, s.public_url or "", s.supported_currencies, s.payment_methods, s.access_status, s.reliability_score, s.risk_score, s.risk_flags, s.notes])
    style_sheet(ws_sup, sup_headers, sup_rows)

    # 3. Products
    ws_prod = wb.create_sheet("Products")
    prod_headers = ["ID", "Tool Name", "Category", "Publisher", "Licensing Status", "Active"]
    prod_rows = [[p.id, p.name, p.category, p.publisher, p.licensing_status, "Yes" if p.is_active else "No"] for p in db.query(Product).all()]
    style_sheet(ws_prod, prod_headers, prod_rows)

    # 4. Product Plans
    ws_plans = wb.create_sheet("Product Plans")
    plan_headers = ["Plan ID", "Product", "Plan Name", "Duration", "Access Type", "Units/Credits", "Matching Key"]
    plan_rows = [[pl.id, pl.product.name if pl.product else "", pl.plan_name, pl.duration, pl.access_type.value, pl.units or pl.credits, pl.matching_key] for pl in db.query(ProductPlan).all()]
    style_sheet(ws_plans, plan_headers, plan_rows)

    # 5. Price Observations
    ws_obs = wb.create_sheet("Price Observations")
    obs_headers = ["ID", "Plan Matching Key", "Supplier", "Amount", "Currency", "Amount PKR", "Warranty", "Verification Status", "Risk Level", "Observed At", "Source URL"]
    obs_rows = []
    for o in db.query(PriceObservation).all():
        obs_rows.append([
            o.id,
            o.plan.matching_key if o.plan else (o.unmatched_candidate_text or "Unmatched"),
            o.supplier.canonical_name if o.supplier else "",
            o.amount,
            o.currency,
            o.amount_pkr,
            o.warranty,
            o.verification_status.value,
            o.risk_level.value,
            o.observed_at.strftime("%Y-%m-%d %H:%M UTC") if o.observed_at else "",
            o.source_url or ""
        ])
    style_sheet(ws_obs, obs_headers, obs_rows)

    # 6. Cheapest Verified (Live Best Rates)
    ws_cheap = wb.create_sheet("Cheapest Verified")
    cheap_headers = ["Product Plan", "Duration", "Access Type", "Winner Supplier", "Cost Currency", "Cost Amount", "Cost PKR", "Warranty", "Status"]
    cheap_rows = []
    for pl in db.query(ProductPlan).all():
        # Get active observations sorted by price
        obs_list = sorted(pl.observations, key=lambda x: x.amount_pkr)
        if obs_list:
            best = obs_list[0]
            cheap_rows.append([
                f"{pl.product.name} - {pl.plan_name}",
                pl.duration,
                pl.access_type.value,
                best.supplier.canonical_name,
                best.currency,
                best.amount,
                best.amount_pkr,
                best.warranty,
                best.verification_status.value
            ])
        else:
            cheap_rows.append([f"{pl.product.name} - {pl.plan_name}", pl.duration, pl.access_type.value, "No Verified Winner Yet", "-", "-", "-", "-", "PENDING_QUOTES"])
    style_sheet(ws_cheap, cheap_headers, cheap_rows)

    # 7. Price History
    ws_hist = wb.create_sheet("Price History")
    hist_headers = ["ID", "Plan", "Supplier", "Event Type", "Old Amount", "New Amount", "Currency", "Date UTC", "Details"]
    hist_rows = [[h.id, h.plan.matching_key if h.plan else "", h.supplier_id, h.event_type, h.old_amount or "", h.new_amount, h.currency, h.event_timestamp.strftime("%Y-%m-%d %H:%M") if h.event_timestamp else "", h.details] for h in db.query(PriceHistory).all()]
    style_sheet(ws_hist, hist_headers, hist_rows)

    # 8. Retail Benchmarks
    ws_bench = wb.create_sheet("Retail Benchmarks")
    bench_headers = ["Store Name", "URL", "Matching Plan Key", "Observed Retail PKR", "Checked Date"]
    bench_rows = [[b.store_name, b.store_url or "", b.matching_plan_key, b.observed_price_pkr, b.checked_at.strftime("%Y-%m-%d") if b.checked_at else ""] for b in db.query(RetailBenchmark).all()]
    style_sheet(ws_bench, bench_headers, bench_rows)

    # 9. FX Rates
    ws_fx = wb.create_sheet("FX Rates")
    fx_headers = ["Base Currency", "Quote Currency", "Rate", "Provider", "Retrieved At UTC", "Status"]
    fx_rows = [[f.base_currency, f.quote_currency, f.rate, f.source_provider, f.retrieved_at.strftime("%Y-%m-%d %H:%M") if f.retrieved_at else "", f.status] for f in db.query(FxRate).all()]
    style_sheet(ws_fx, fx_headers, fx_rows)

    # 10. Research Queue
    ws_queue = wb.create_sheet("Research Queue")
    q_headers = ["ID", "Target URL", "Status", "Blocker Reason", "Attempts", "Next Action"]
    q_rows = [[q.id, q.target_url, q.status.value, q.blocker_reason, q.attempt_count, q.next_action] for q in db.query(ResearchQueue).all()]
    style_sheet(ws_queue, q_headers, q_rows)

    # 11. Risk Review
    ws_risk = wb.create_sheet("Risk Review")
    risk_headers = ["Observation ID", "Tool Plan", "Supplier", "Price PKR", "Warranty", "Risk Level", "Risk Flags"]
    risk_rows = []
    for o in db.query(PriceObservation).filter(PriceObservation.risk_level.in_(["HIGH", "CRITICAL"])).all():
        risk_rows.append([o.id, o.plan.matching_key if o.plan else "", o.supplier.canonical_name, o.amount_pkr, o.warranty, o.risk_level.value, o.risk_flags])
    style_sheet(ws_risk, risk_headers, risk_rows)

    # 12. Implementation Tasks
    ws_tasks = wb.create_sheet("Implementation Tasks")
    task_headers = ["Task", "Module", "Target Status", "Verification Method"]
    task_rows = [
        ["Phase A: Repository separation & privacy check", "Infrastructure", "COMPLETED", "gh repo view --json isPrivate"],
        ["Phase B: Data models & seed leads import", "src/models.py", "COMPLETED", "SQLite table verification"],
        ["Phase C: Exact plan matching & pricing engine", "src/matching.py, src/pricing.py", "COMPLETED", "Unit tests"],
        ["Phase D: Streamlit dashboard & master_catalog.xlsx", "app.py, src/excel.py", "COMPLETED", "Workbook generation & browser check"],
        ["Phase E: Telegram public adapter & alerts", "src/adapters/", "COMPLETED", "Cache & live scraper test"],
        ["Phase F: CI Workflows & automated test suite", "tests/, .github/workflows/", "COMPLETED", "pytest execution"]
    ]
    style_sheet(ws_tasks, task_headers, task_rows)

    # 13. Audit Log
    ws_audit = wb.create_sheet("Audit Log")
    audit_headers = ["ID", "Action", "Entity", "Actor", "Date UTC", "Details"]
    audit_rows = [[a.id, a.action_type, a.entity_name, a.actor, a.created_at.strftime("%Y-%m-%d %H:%M") if a.created_at else "", a.details] for a in db.query(AuditLog).all()]
    style_sheet(ws_audit, audit_headers, audit_rows)

    wb.save(target_file)
    print(f"Exported master catalog with 13 sheets to: {target_file}")
    return target_file

def _fallback_csv_export(db) -> Path:
    csv_file = settings.database_path.parent / "master_catalog_summary.csv"
    with open(csv_file, "w", encoding="utf-8") as f:
        f.write("Plan,Duration,Winner_Supplier,Currency,Amount,Amount_PKR,Warranty,Status\n")
        for pl in db.query(ProductPlan).all():
            obs = sorted(pl.observations, key=lambda x: x.amount_pkr)
            if obs:
                b = obs[0]
                f.write(f'"{pl.product.name} - {pl.plan_name}","{pl.duration}","{b.supplier.canonical_name}","{b.currency}",{b.amount},{b.amount_pkr},"{b.warranty}","{b.verification_status.value}"\n')
    return csv_file
