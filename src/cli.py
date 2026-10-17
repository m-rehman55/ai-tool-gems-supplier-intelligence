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

        for pl in matched_plans:
            print("\n" + "=" * 65)
            print(f"📦 TOOL: {pl.product.name} — {pl.plan_name}")
            print(f"⏱ Duration: {pl.duration} | Access: {pl.access_type.value}")
            print(f"🔑 Matching Key: {pl.matching_key}")
            print("-" * 65)
            
            # Find active price observations
            obs_list = sorted(pl.observations, key=lambda x: x.amount_pkr)
            if not obs_list:
                print("⚠️  No supplier quotes recorded yet. Status: PENDING_QUOTES")
                continue
                
            winner = obs_list[0]
            sup = winner.supplier
            
            # Pricing math
            pricing = calc.calculate(winner.amount, winner.fx_rate_used)
            
            print(f"🏆 BEST SUPPLIER: {sup.canonical_name} ({sup.primary_handle})")
            print(f"💵 Wholesale Cost: ${winner.amount:.2f} {winner.currency} (≈ Rs. {winner.amount_pkr:,.2f} PKR)")
            print(f"🛡 Warranty: {winner.warranty}")
            print(f"📦 Status / Evidence: {winner.verification_status.value} ({winner.capture_method})")
            print(f"🔗 Supplier Link: {sup.public_url or ('https://t.me/' + sup.primary_handle.lstrip('@'))}")
            print(f"🏷 Suggested Web Retail: Rs. {pricing.suggested_retail_pkr:,.0f} PKR")
            print(f"💰 Estimated Net Profit: +Rs. {pricing.estimated_net_profit_pkr:,.0f} PKR ({pricing.net_margin_pct}% margin)")
            if pricing.has_margin_conflict:
                print(f"⚠️  {pricing.conflict_notes}")

            if len(obs_list) > 1:
                print(f"\n📊 Other Suppliers ({len(obs_list)-1} alternatives):")
                for alt in obs_list[1:]:
                    print(f"   • {alt.supplier.canonical_name}: ${alt.amount:.2f} {alt.currency} (~Rs. {alt.amount_pkr:,.0f}) | {alt.warranty} | {alt.verification_status.value}")

def cmd_export(output: str = None) -> None:
    print("[EXPORT] Generating master_catalog.xlsx with 13 worksheets...")
    init_db()
    with get_db() as db:
        path = export_master_catalog(db, Path(output) if output else None)
    print(f"[OK] Master catalog written to: {path}")

def cmd_research() -> None:
    print("[RESEARCH] Executing public Telegram channels research cycle...")
    init_db()
    adapter = TelegramPublicChannelAdapter()
    with get_db() as db:
        suppliers = db.query(Supplier).filter(Supplier.public_url.like("%t.me/s/%")).all()
        print(f"Found {len(suppliers)} public supplier channels to inspect.")
        
        for s in suppliers:
            handle = s.public_url.replace("https://t.me/s/", "").strip()
            print(f"--> Checking @{handle}...")
            msgs = adapter.fetch_channel_messages(handle)
            print(f"    Retrieved {len(msgs)} public announcements.")

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
    
    subparsers.add_parser("research", help="Run rate-limited public Telegram channel research")
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
        cmd_research()
    elif args.command == "alerts":
        cmd_alerts()
    elif args.command == "backup":
        cmd_backup()

if __name__ == "__main__":
    main()
