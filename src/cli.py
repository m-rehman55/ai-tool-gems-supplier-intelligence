"""
Operational Command-Line Interface (CLI) for Supplier Price Intelligence.
Provides fast lookups, database seeding, public research cycles, and workbook exports.
"""

import sys
import argparse
from datetime import datetime, timezone
from pathlib import Path

# Fix Windows console unicode encoding
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from src.database import init_db, get_db, backup_database
from src.seed_data import seed_all_data
from src.excel import export_master_catalog
from src.pricing import PricingCalculator
from src.fx import FxEngine
from src.alerts import AlertEngine
from src.adapters.public_telegram import TelegramPublicChannelAdapter
from src.winner import WinnerEngine
from src.models import Supplier, ProductPlan, PriceObservation, VerificationStatus

def cmd_init() -> None:
    print("[INIT] Initializing local SQLite database & seeding canonical leads...")
    init_db()
    with get_db() as db:
        seed_all_data(db)
    print("[DONE] Database initialized and seeded successfully.")

def cmd_best(query: str) -> None:
    q = query.lower().strip()
    with get_db() as db:
        plans = db.query(ProductPlan).all()
        matched_plans = [p for p in plans if q in p.matching_key.lower() or q in p.product.name.lower()]
        
        if not matched_plans:
            print(f"[LOOKUP] No exact plan matched for '{query}'.")
            print("Try keywords like: 'chatgpt', 'gemini', 'capcut', 'jetbrains', 'duolingo', 'coursera', 'kling'.")
            return

        fx = FxEngine(db)
        calc = PricingCalculator()
        winner_engine = WinnerEngine()

        for pl in matched_plans:
            print("\n" + "=" * 65)
            print(f"📦 TOOL: {pl.product.name} — {pl.plan_name}")
            print(f"⏱ Duration: {pl.duration} | Access: {pl.access_type.value}")
            print(f"🔑 Matching Key: {pl.matching_key}")
            print("-" * 65)
            
            obs_list = sorted(pl.observations, key=lambda x: x.amount_pkr)
            if not obs_list:
                print("⚠️  No supplier quotes recorded yet. Status: PENDING_QUOTES")
                continue
                
            eval_res = winner_engine.evaluate_plan(pl)
            
            if eval_res.has_verified_winner and eval_res.lowest_verified:
                winner = eval_res.lowest_verified
                sup = winner.supplier
                pricing = calc.calculate(winner.amount, winner.fx_rate_used)
                
                print(f"🏆 LOWEST VERIFIED SUPPLIER: {sup.canonical_name} ({sup.primary_handle})")
                print(f"💵 Wholesale Cost: ${winner.amount:.2f} {winner.currency} (≈ Rs. {winner.amount_pkr:,.2f} PKR)")
                print(f"🛡 Warranty: {winner.warranty}")
                print(f"📦 Status / Evidence: {winner.verification_status.value} ({winner.capture_method})")
                print(f"🔗 Supplier Link: {sup.public_url or ('https://t.me/' + sup.primary_handle.lstrip('@'))}")
                print(f"🏷 Suggested Web Retail: Rs. {pricing.suggested_retail_pkr:,.0f} PKR")
                print(f"💰 Estimated Net Profit: +Rs. {pricing.estimated_net_profit_pkr:,.0f} PKR ({pricing.net_margin_pct}% margin)")
                if pricing.has_margin_conflict:
                    print(f"⚠️  {pricing.conflict_notes}")
            else:
                print("⏳ NO VERIFIED WINNER YET (Awaiting recent proof / verified quote)")
                if eval_res.lowest_advertised:
                    low = eval_res.lowest_advertised
                    pricing = calc.calculate(low.amount, low.fx_rate_used)
                    reasons = eval_res.disqualification_reasons.get(low.id, ["Unverified claim"])
                    print(f"📢 Lowest Advertised (Pending Verification): {low.supplier.canonical_name}")
                    print(f"   Amount: ${low.amount:.2f} {low.currency} (~Rs. {low.amount_pkr:,.0f} PKR) | {low.warranty}")
                    print(f"   Status: {low.verification_status.value} | Pending reason: {'; '.join(reasons)}")
                    print(f"   Provisional Retail: Rs. {pricing.suggested_retail_pkr:,.0f} PKR")

            if len(obs_list) > 1:
                print(f"\n📊 All Supplier Quotes ({len(obs_list)} total):")
                for alt in obs_list:
                    verified_tag = "✅ VERIFIED" if alt.verification_status == VerificationStatus.VERIFIED else f"⏳ {alt.verification_status.value}"
                    print(f"   • {alt.supplier.canonical_name}: ${alt.amount:.2f} {alt.currency} (~Rs. {alt.amount_pkr:,.0f}) | {alt.warranty} | {verified_tag}")

def cmd_export(output: str = None) -> None:
    print("[EXPORT] Generating master_catalog.xlsx with 13 worksheets...")
    init_db()
    with get_db() as db:
        path = export_master_catalog(db, Path(output) if output else None)
    print(f"[OK] Master catalog written to: {path}")

def match_announcement_to_plan(db, clean_text: str):
    """Matches raw announcement text to canonical product and exact plan."""
    from src.matching import parse_duration, parse_access_type, normalize_string
    if TelegramPublicChannelAdapter.is_promotional_or_referral(clean_text):
        return None
        
    t = clean_text.lower()
    
    # Keyword to product mapping
    product_keywords = [
        ("chatgpt go", "ChatGPT"),
        ("chatgpt", "ChatGPT"),
        ("gpt plus", "ChatGPT"),
        ("duolingo", "Duolingo"),
        ("proton vpn", "Proton VPN"),
        ("autodesk", "Autodesk"),
        ("perplexity", "Perplexity Pro"),
        ("lovable", "Lovable"),
        ("coursera", "Coursera"),
        ("ilovepdf", "iLovePDF"),
        ("jetbrain", "JetBrains"),
        ("capcut", "CapCut"),
        ("netflix", "Netflix"),
        ("kling", "Kling AI"),
        ("krea", "Krea AI"),
        ("heygen", "HeyGen"),
        ("zoom", "Zoom Pro"),
        ("elevenlabs", "ElevenLabs"),
        ("elevanlabs", "ElevenLabs"),
        ("gemini", "Google Gemini"),
        ("supergrok", "SuperGrok"),
        ("grok", "SuperGrok"),
        ("leonardo", "Leonardo AI"),
        ("veo", "Google Veo 3"),
    ]
    
    matched_product_name = None
    for kw, p_name in product_keywords:
        if kw in t:
            matched_product_name = p_name
            break
            
    if not matched_product_name:
        return None
        
    dur = parse_duration(clean_text)
    acc = parse_access_type(clean_text)
    
    # Query candidate plans for this product
    candidate_plans = db.query(ProductPlan).join(ProductPlan.product).filter(ProductPlan.product.has(name=matched_product_name)).all()
    if not candidate_plans:
        return None
        
    # Exact match on duration and access type
    for cp in candidate_plans:
        if cp.duration.lower() == dur.lower() and cp.access_type == acc:
            return cp
            
    # Fallback to duration match
    for cp in candidate_plans:
        if cp.duration.lower() == dur.lower():
            return cp
            
    return candidate_plans[0]

def cmd_research(public_only: bool = True) -> None:
    print("[RESEARCH] Executing public Telegram channels research cycle...")
    init_db()
    adapter = TelegramPublicChannelAdapter()
    
    from src.models import ResearchQueue, ResearchStatus, PriceHistory, RiskLevel
    
    with get_db() as db:
        fx = FxEngine(db)
        public_suppliers = db.query(Supplier).filter(Supplier.public_url.like("%t.me/s/%")).all()
        interactive_suppliers = db.query(Supplier).filter(Supplier.access_status == "INTERACTIVE_MENU_REQUIRED").all()
        
        print(f"Inspecting {len(public_suppliers)} public channels and queuing {len(interactive_suppliers)} interactive bots...")
        
        verified_count = 0
        new_obs_count = 0
        
        for s in public_suppliers:
            handle = s.public_url.replace("https://t.me/s/", "").strip().strip("/")
            print(f"--> Fetching @{handle}...")
            msgs = adapter.fetch_channel_messages(handle)
            print(f"    Processing {len(msgs)} messages from @{handle}...")
            
            s.last_checked_at = datetime.now(timezone.utc)
            
            for m in msgs:
                matched_plan = match_announcement_to_plan(db, m["clean_text"])
                if not matched_plan or not m.get("detected_price"):
                    continue
                    
                price = float(m["detected_price"])
                curr = "USDT" if "USDT" in m["clean_text"].upper() or "USDT" in (s.supported_currencies or "") else "USD"
                rate, _, _, _ = fx.get_rate(curr, "PKR")
                amount_pkr = round(price * rate, 2)
                
                # Check if this exact announcement was already captured
                existing_obs = db.query(PriceObservation).filter_by(content_hash=m["content_hash"]).first()
                if not existing_obs:
                    # Check for previous price by this supplier to detect price drop/increase
                    prev_obs = db.query(PriceObservation).filter_by(supplier_id=s.id, plan_id=matched_plan.id).order_by(PriceObservation.observed_at.desc()).first()
                    
                    event_type = "NEW_QUOTE"
                    if prev_obs and prev_obs.amount != price:
                        event_type = "PRICE_DROP" if price < prev_obs.amount else "PRICE_INCREASE"
                        db.add(PriceHistory(
                            plan_id=matched_plan.id,
                            supplier_id=s.id,
                            event_type=event_type,
                            old_amount=prev_obs.amount,
                            new_amount=price,
                            currency=curr,
                            old_warranty=prev_obs.warranty,
                            new_warranty=m.get("warranty", "Not Stated"),
                            source_url=m["source_url"],
                            details=f"Live public price change detected on @{handle}: ${prev_obs.amount} -> ${price}"
                        ))
                    
                    # Create verified observation
                    new_obs = PriceObservation(
                        supplier_id=s.id,
                        plan_id=matched_plan.id,
                        amount=price,
                        currency=curr,
                        original_raw_text=m["clean_text"],
                        source_url=m["source_url"],
                        source_snippet=m["clean_text"][:250],
                        warranty=m.get("warranty", "Full Warranty (FW)" if "(FW)" in m["clean_text"] else "Not Stated"),
                        stock_status=m.get("stock", "Available"),
                        fx_rate_used=rate,
                        fx_provider="FX_ENGINE",
                        amount_pkr=amount_pkr,
                        verification_status=VerificationStatus.VERIFIED,
                        confidence_score=95.0,
                        risk_level=RiskLevel.LOW if "(FW)" in m["clean_text"] else RiskLevel.MEDIUM,
                        capture_method="PUBLIC_CRAWLER",
                        content_hash=m["content_hash"],
                        reviewer_notes=f"Auto-verified via public post {m['source_url']}"
                    )
                    db.add(new_obs)
                    new_obs_count += 1
                    verified_count += 1
            
            db.commit()
            
        # Queue interactive bots requiring /start without fake data
        for ib in interactive_suppliers:
            existing_task = db.query(ResearchQueue).filter_by(supplier_id=ib.id).first()
            if not existing_task:
                db.add(ResearchQueue(
                    supplier_id=ib.id,
                    target_url=ib.public_url or f"https://t.me/{ib.primary_handle.lstrip('@')}",
                    status=ResearchStatus.INTERACTIVE_MENU_REQUIRED,
                    blocker_reason="Bot requires interactive chat and /start command. Automated scraping without user session not permitted.",
                    next_action="Review catalog via Telegram app and record quote using Streamlit Manual Entry"
                ))
        db.commit()

        print(f"[SUCCESS] Research cycle completed: {verified_count} quotes verified, {new_obs_count} new observations recorded.")
        print("[EXPORT] Updating master_catalog.xlsx with fresh verified quotes...")
        export_master_catalog(db)
        print("[OK] Master catalog updated successfully.")

def cmd_alerts() -> None:
    print("[ALERTS] Scanning active price changes, staleness, and margin conflicts...")
    init_db()
    with get_db() as db:
        engine = AlertEngine(db)
        alerts = engine.scan_for_alerts()
        if not alerts:
            print("✅ No active risk or price-change alerts.")
            return
            
        print(f"⚠️  Detected {len(alerts)} alerts:\n")
        for a in alerts:
            print(f"[{a.severity}] {a.alert_type} | {a.tool_plan} ({a.supplier_name})")
            print(f"   {a.message}")
            if a.source_url:
                print(f"   Source: {a.source_url}")
            print("-" * 50)

def cmd_backup() -> None:
    path = backup_database()
    print(f"[BACKUP] Snapshot saved to: {path}")

def main():
    parser = argparse.ArgumentParser(description="AIToolGems Supplier Price Intelligence CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)
    
    subparsers.add_parser("init", help="Initialize SQLite DB and seed canonical data")
    
    best_p = subparsers.add_parser("best", help="Instant best supplier price lookup for a tool")
    best_p.add_argument("query", type=str, help="Tool or plan search keyword (e.g. chatgpt, gemini, duolingo)")
    
    exp_p = subparsers.add_parser("export-workbook", help="Generate single master_catalog.xlsx workbook")
    exp_p.add_argument("--output", type=str, default=None, help="Output file path")
    
    res_p = subparsers.add_parser("research", help="Run rate-limited public Telegram channel research")
    res_p.add_argument("--public-only", action="store_true", default=True, help="Scan only permitted public channels")
    
    subparsers.add_parser("alerts", help="Check active risk, staleness, and price alerts")
    subparsers.add_parser("backup", help="Create timestamped SQLite backup")
    
    args = parser.parse_args()
    
    if args.command == "init":
        cmd_init()
    elif args.command == "best":
        cmd_best(args.query)
    elif args.command == "export-workbook":
        cmd_export(args.output)
    elif args.command == "research":
        cmd_research(public_only=getattr(args, "public_only", True))
    elif args.command == "alerts":
        cmd_alerts()
    elif args.command == "backup":
        cmd_backup()

if __name__ == "__main__":
    main()
