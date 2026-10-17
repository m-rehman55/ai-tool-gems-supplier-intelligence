"""
Ingests verified live prices directly from the user's screen recording
(Screen Recording 2026-10-17 182823.mp4):
- PiggyAi_Bot
- HitMeow Shop (@PremiKeyBot / @vahnix)
"""

import sys
from datetime import datetime, timezone

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from src.database import get_db
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    AccessType, VerificationStatus, RiskLevel
)
from src.matching import generate_matching_key
from src.excel import export_master_catalog

def main():
    print("[VIDEO INGEST] Extracting verified bot-shop quotes from Screen Recording...")
    with get_db() as db:
        # Suppliers
        sup_piggy = db.query(Supplier).filter(Supplier.primary_handle.like('%PiggyAi%')).first()
        sup_hitmeow = db.query(Supplier).filter(Supplier.primary_handle.like('%PremiKey%')).first()
        if not sup_hitmeow:
            sup_hitmeow = Supplier(
                canonical_name="HitMeow Shop",
                primary_handle="@PremiKeyBot",
                all_handles="@PremiKeyBot, @vahnix, @HitmeowSupport",
                entity_type="BOT",
                public_url="https://t.me/PremiKeyBot",
                supported_currencies="USD,USDT",
                payment_methods="Binance Pay, USDT, Crypto",
                access_status="PUBLIC",
                reliability_score=88.0
            )
            db.add(sup_hitmeow)
            db.flush()

        def get_or_create_prod(name, cat):
            p = db.query(Product).filter_by(name=name).first()
            if not p:
                p = Product(name=name, category=cat)
                db.add(p)
                db.flush()
            return p

        p_capcut = get_or_create_prod("CapCut", "Video & Creative")
        p_nord = get_or_create_prod("NordVPN", "VPN & Security")
        p_cursor = get_or_create_prod("Cursor Pro", "Coding AI & IDE")
        p_eleven = get_or_create_prod("ElevenLabs", "Audio & Voice AI")
        p_kling = get_or_create_prod("Kling AI", "Video & Creative")
        p_gemini = get_or_create_prod("Google Gemini", "AI Assistant")

        # Verified Quotes directly from video recording
        items = [
            # CapCut: HitMeow vs PiggyAI battle!
            (sup_hitmeow, p_capcut, "CapCut Pro Personal", "1 Month", AccessType.PRIVATE, "Account", "", 1.25, "Full Warranty (FW)", "2 in stock", "CapCut Pro Personal 1 month - full warranty | $1.25 | 📦 2"),
            (sup_piggy, p_capcut, "CapCut Pro Personal", "1 Month", AccessType.PRIVATE, "Account", "", 1.27, "Full Warranty (FW)", "5 in stock", "CapCut Pro Personal 30D full warranty | $1.27 | 📦 5"),
            (sup_piggy, p_capcut, "CapCut Pro Team 3 Months", "3 Months", AccessType.TEAM, "Account", "", 6.16, "Full Warranty (FW)", "3 in stock", "CapCut Pro Team 3 Months - full warranty | $6.16 | 📦 3"),
            (sup_hitmeow, p_capcut, "CapCut Pro Team 3 Months", "3 Months", AccessType.TEAM, "Account", "", 7.28, "Full Warranty (FW)", "4 in stock", "CapCut Pro Team 3 Months - full warranty | $7.28 | 📦 4"),
            (sup_hitmeow, p_capcut, "CapCut Pro Personal 3 Months", "3 Months", AccessType.PRIVATE, "Account", "", 3.74, "Full Warranty (FW)", "4 in stock", "CapCut Pro Personal 3 months - full warranty | $3.74 | 📦 4"),
            (sup_hitmeow, p_capcut, "CapCut Pro Team 12 Months", "12 Months", AccessType.TEAM, "Account", "", 22.82, "Full Warranty (FW)", "1 in stock", "CapCut Pro Team 12 Months - full warranty | $22.82 | 📦 1"),
            
            # Other tools from video
            (sup_hitmeow, p_nord, "NordVPN 10 Devices 3M", "3 Months", AccessType.SHARED, "Slot", "", 2.08, "Full Warranty (FW)", "Available", "NordVPN code for 10 devices, 3 months, full warranty | $2.08"),
            (sup_hitmeow, p_nord, "NordVPN Slot 6M", "6 Months", AccessType.SHARED, "Slot", "", 4.16, "Full Warranty (FW)", "Available", "NordVPN Android/PC/iOS 6 months slot full warranty | $4.16"),
            (sup_hitmeow, p_cursor, "Cursor Pro 2026 Credits 1M", "1 Month", AccessType.API, "Account", "2026 credits", 8.42, "Full Warranty (FW)", "Available", "API Cursor Pro 2026 Credits 1 Month full warranty | $8.42"),
            (sup_hitmeow, p_eleven, "ElevenLabs Redeem 100k Credits", "1 Month", AccessType.KEY, "Code", "100k credits", 1.04, "Full Warranty (FW)", "Available", "ElevenLabs Redeem 100k Credits | $1.04"),
            (sup_hitmeow, p_kling, "Kling AI Video Standard", "1 Month", AccessType.PRIVATE, "Account", "700-1000 credits", 5.20, "Full Warranty (FW)", "Available", "Kling 700-1000 Credits 1 month full warranty | $5.20"),
            (sup_piggy, p_gemini, "Gemini Pro Invitation", "18 Months", AccessType.INVITE, "Link", "", 0.45, "24H Warranty", "Available", "GEMINI AI PRO LINK 18 months Warranty 24H | $0.45")
        ]

        rate = 282.5  # USDT to PKR
        for sup, prod, pl_name, dur, acc, units, creds, price, warr, stock, raw in items:
            m_key = generate_matching_key(prod.name, pl_name, dur, acc)
            pl = db.query(ProductPlan).filter_by(matching_key=m_key).first()
            if not pl:
                pl = ProductPlan(
                    product_id=prod.id,
                    plan_name=pl_name,
                    duration=dur,
                    access_type=acc,
                    units=units,
                    credits=creds,
                    matching_key=m_key
                )
                db.add(pl)
                db.flush()

            clean_handle = sup.primary_handle.lstrip("@")
            c_hash = f"video_{sup.id}_{m_key}_{price}"
            obs = db.query(PriceObservation).filter_by(content_hash=c_hash).first()
            if not obs:
                db.add(PriceObservation(
                    supplier_id=sup.id,
                    plan_id=pl.id,
                    amount=price,
                    currency="USDT",
                    original_raw_text=raw,
                    source_url=f"https://t.me/{clean_handle}",
                    source_snippet=raw,
                    warranty=warr,
                    stock_status=stock,
                    fx_rate_used=rate,
                    fx_provider="FX_ENGINE_USDT",
                    amount_pkr=round(price * rate, 2),
                    verification_status=VerificationStatus.VERIFIED,
                    confidence_score=100.0,
                    risk_level=RiskLevel.LOW,
                    capture_method="USER_SCREEN_RECORDING_VERIFIED",
                    content_hash=c_hash,
                    reviewer_notes="Extracted directly from owner Screen Recording (Screen Recording 2026-10-17 182823.mp4)"
                ))
                print(f"  [+] Ingested: {sup.canonical_name} -> {prod.name} ({pl_name}): ${price} USDT")

        db.commit()
        print("[SUCCESS] All video-recorded quotes committed to database.")
        print("[EXPORT] Updating master_catalog.xlsx...")
        export_master_catalog(db)
        print("[OK] master_catalog.xlsx refreshed.")

if __name__ == "__main__":
    main()
