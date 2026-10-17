"""
Seed data module.
Populates canonical suppliers, discovery queue leads, and historical unverified price observations.
Preserves conflicting claims without overwriting.
"""

import hashlib
from datetime import datetime, timezone
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, FxRate,
    RetailBenchmark, ResearchQueue, AccessType, VerificationStatus, RiskLevel, ResearchStatus
)
from src.matching import generate_matching_key
from src.config import settings

def compute_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def seed_all_data(db) -> None:
    """Imports all seed leads, products, plans, and historical claims."""
    
    # -------------------------------------------------------------
    # 1. CANONICAL SUPPLIERS & CONTACTS
    # -------------------------------------------------------------
    suppliers_def = [
        {
            "canonical_name": "DigiCart Tools",
            "primary_handle": "@getdigitaltools_bot",
            "all_handles": "@getdigitaltools_bot, @digicarttools, @getdigicart, @digicartlogs",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/digicarttools",
            "supported_currencies": "USD,USDT,INR",
            "payment_methods": "UPI, Crypto, USDT",
            "access_status": "PUBLIC",
            "reliability_score": 85.0,
            "risk_score": 15.0,
            "risk_flags": "",
            "notes": "Active automated shop bot. Fast restock announcements."
        },
        {
            "canonical_name": "PiggyAI Premium",
            "primary_handle": "@PiggyAi799_Bot",
            "all_handles": "@PiggyAi799_Bot, @PiggyAIPremium, @khaidayne, @piggyai79, @piggyai99_bot",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/PiggyAIPremium",
            "supported_currencies": "USD,USDT,VND",
            "payment_methods": "USDT, Binance Pay, Banking",
            "access_status": "PUBLIC",
            "reliability_score": 80.0,
            "risk_score": 20.0,
            "risk_flags": "Sells Edu / Student accounts with limited warranties.",
            "notes": "Global USDT bot + Vietnam automated bot."
        },
        {
            "canonical_name": "Sellnity Store",
            "primary_handle": "@sellnitystorebot",
            "all_handles": "@sellnitystorebot, @sellnity, @sellnitystoreupdates",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/sellnitystoreupdates",
            "supported_currencies": "USD,USDT",
            "payment_methods": "Crypto, USDT",
            "access_status": "PUBLIC",
            "reliability_score": 88.0,
            "risk_score": 15.0,
            "risk_flags": "Flash sales with 6-12 hour timers.",
            "notes": "High variety of AI video, creative software licenses, and productivity tools."
        },
        {
            "canonical_name": "NovaShop Premium",
            "primary_handle": "@Novasubglo_bot",
            "all_handles": "@Novasubglo_bot, @novasubpre, @chupiuoi1610",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/novasubpre",
            "supported_currencies": "USD,USDT",
            "payment_methods": "Binance Pay, USDT",
            "access_status": "PUBLIC",
            "reliability_score": 75.0,
            "risk_score": 25.0,
            "risk_flags": "Frequent stock fluctuations, customer service delays noted in channel.",
            "notes": "Sells Leonardo AI, Grok, ApplePay/Cash ChatGPT Plus."
        },
        {
            "canonical_name": "AI Toolify",
            "primary_handle": "@aitoolify_bot",
            "all_handles": "@aitoolify_bot, @hacksxtips, @aitoolsxsupport2",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/hacksxtips",
            "supported_currencies": "PKR,USD,USDT",
            "payment_methods": "Easypaisa, Binance Pay, Bybit, USDT, CryptoBot",
            "access_status": "PUBLIC",
            "reliability_score": 85.0,
            "risk_score": 15.0,
            "risk_flags": "",
            "notes": "Accepts direct Pakistan EasyPaisa in PKR. Sells Gemini 18M links in bulk."
        },
        {
            "canonical_name": "Verified Streams",
            "primary_handle": "@Verified_Streams_bot",
            "all_handles": "@Verified_Streams_bot, @verifiedstreams, @sharibraza",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/verifiedstreams",
            "supported_currencies": "USDT",
            "payment_methods": "USDT, Crypto",
            "access_status": "PUBLIC",
            "reliability_score": 70.0,
            "risk_score": 30.0,
            "risk_flags": "Warned against flash USDT scams in channel; server reboots reported.",
            "notes": "Sells fresh Gemini claim URLs and GPT Business Panel seats."
        },
        {
            "canonical_name": "AIToolPre Bot",
            "primary_handle": "@aitoolpre_bi_bot",
            "all_handles": "@aitoolpre_bi_bot, @Airdrop_Crypto_x100, @nvtoanr",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/Airdrop_Crypto_x100",
            "supported_currencies": "USD,USDT",
            "payment_methods": "Crypto, USDT",
            "access_status": "PUBLIC",
            "reliability_score": 78.0,
            "risk_score": 20.0,
            "risk_flags": "",
            "notes": "Sells AWS Promo Codes, ChatGPT Pro 5x, and Lovable passes."
        },
        {
            "canonical_name": "PremiumTools Bot",
            "primary_handle": "@premiumtools_bot",
            "all_handles": "@premiumtools_bot, @linkedinpremiumtop, https://t.me/premiumtoolschat",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/premiumtoolschat",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 35.0,
            "risk_flags": "Public channel has 0 posts; requires /start in private bot.",
            "notes": "Catalog requires interactive bot interaction."
        },
        {
            "canonical_name": "PremiKey Bot",
            "primary_handle": "@PremiKeyBot",
            "all_handles": "@PremiKeyBot, @vahnix, @HitmeowSupport",
            "entity_type": "BOT",
            "public_url": "https://t.me/PremiKeyBot",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 30.0,
            "risk_flags": "Interactive bot only.",
            "notes": "Requires private /start command."
        },
        {
            "canonical_name": "SP AI Vault",
            "primary_handle": "@spaivault_bot",
            "all_handles": "@spaivault_bot, @skillpreneures_support",
            "entity_type": "BOT",
            "public_url": "https://t.me/spaivault_bot",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 30.0,
            "risk_flags": "Interactive bot only.",
            "notes": "SaaS and AI digital accounts."
        },
        {
            "canonical_name": "PT Store BD",
            "primary_handle": "@ptstorebd_bot",
            "all_handles": "@ptstorebd_bot, https://t.me/ptsbd26",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/ptsbd26",
            "supported_currencies": "BDT,USD",
            "payment_methods": "Bkash, Nagad, Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 35.0,
            "risk_flags": "Public preview empty.",
            "notes": "Regional Bangladesh bot."
        },
        {
            "canonical_name": "MediaHub Marketplace",
            "primary_handle": "@MediaHubMarketplace_bot",
            "all_handles": "@MediaHubMarketplace_bot, @MediaHubDigitalMarketplace",
            "entity_type": "BOT",
            "public_url": "https://t.me/MediaHubMarketplace_bot",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 30.0,
            "risk_flags": "Auto order 24/7 bot.",
            "notes": "Interactive menu required."
        },
        {
            "canonical_name": "Store Premium MMO",
            "primary_handle": "@storepremium_bot",
            "all_handles": "@storepremium_bot, @mmoglobalhub, @mangiainhat",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/mmoglobalhub",
            "supported_currencies": "USD,USDT",
            "payment_methods": "Crypto, USDT",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 35.0,
            "risk_flags": "Community hub lead.",
            "notes": "MMO account reseller."
        },
        {
            "canonical_name": "PRM Asset Store",
            "primary_handle": "@prmastbot",
            "all_handles": "@prmastbot, @premiumassetstore",
            "entity_type": "BOT",
            "public_url": "https://t.me/prmastbot",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 50.0,
            "risk_score": 30.0,
            "risk_flags": "Interactive bot.",
            "notes": "Asset and subscription reseller."
        },
        {
            "canonical_name": "Toolorax",
            "primary_handle": "@toolorax",
            "all_handles": "https://t.me/toolorax",
            "entity_type": "CHANNEL",
            "public_url": "https://t.me/s/toolorax",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 45.0,
            "risk_score": 40.0,
            "risk_flags": "Source type unresolved until inspected.",
            "notes": "Lead in discovery queue."
        },
        {
            "canonical_name": "AI Corn Bot",
            "primary_handle": "@AI_corn_bot",
            "all_handles": "https://t.me/AI_corn_bot, @HM_LANDER, @sealsareana",
            "entity_type": "BOT",
            "public_url": "https://t.me/s/sealsareana",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 45.0,
            "risk_score": 40.0,
            "risk_flags": "Interactive bot.",
            "notes": "Lead in discovery queue."
        },
        {
            "canonical_name": "FatBunny Hub",
            "primary_handle": "@FatBunny_Hub_bot",
            "all_handles": "@FatBunny_Hub_bot",
            "entity_type": "BOT",
            "public_url": "https://t.me/FatBunny_Hub_bot",
            "supported_currencies": "USD",
            "payment_methods": "Crypto",
            "access_status": "INTERACTIVE_MENU_REQUIRED",
            "reliability_score": 45.0,
            "risk_score": 40.0,
            "risk_flags": "Interactive bot.",
            "notes": "Lead in discovery queue."
        }
    ]

    supplier_objs = {}
    for s in suppliers_def:
        existing = db.query(Supplier).filter_by(canonical_name=s["canonical_name"]).first()
        if not existing:
            sup = Supplier(**s)
            db.add(sup)
            db.flush()
            supplier_objs[s["canonical_name"]] = sup
        else:
            supplier_objs[s["canonical_name"]] = existing

    # -------------------------------------------------------------
    # 2. DISCOVERY QUEUE LEADS (UNRESOLVED)
    # -------------------------------------------------------------
    discovery_queue_urls = [
        ("https://t.me/Siddharthstorebot", "Interactive Bot lead"),
        ("https://t.me/s/digiplacedigitalstore", "Public channel lead"),
        ("https://t.me/tongmmobot", "Interactive Bot lead"),
        ("https://t.me/s/taphoammotb", "Public channel lead"),
        ("https://t.me/s/egyfalcons", "Public channel lead"),
        ("https://t.me/s/brandUpp", "Public channel lead"),
        ("https://t.me/s/groupbuyseotools2022", "Public group-buy channel"),
        ("https://t.me/Chat_GpTpluss", "Telegram chat/channel lead")
    ]
    for url, note in discovery_queue_urls:
        if not db.query(ResearchQueue).filter_by(target_url=url).first():
            db.add(ResearchQueue(
                target_url=url,
                status=ResearchStatus.PENDING,
                blocker_reason="Awaiting public inspection cycle",
                next_action="INSPECT_PUBLIC_URL"
            ))

    # -------------------------------------------------------------
    # 3. PRODUCTS & EXACT PLANS
    # -------------------------------------------------------------
    products_def = [
        ("ChatGPT", "AI Assistants", "OpenAI", [
            ("ChatGPT Plus", "1 Month", AccessType.PRIVATE, "1", "chatgpt__chatgpt_plus__1_month__private"),
            ("ChatGPT Plus Edu / K12", "2 Years", AccessType.EDU, "1", "chatgpt__chatgpt_plus_edu__2_years__edu"),
            ("ChatGPT Plus Student", "4 Months", AccessType.EDU, "1", "chatgpt__chatgpt_plus_student__4_months__edu"),
            ("ChatGPT Go", "1 Month", AccessType.PRIVATE, "1", "chatgpt__chatgpt_go__1_month__private"),
            ("ChatGPT Business Team", "1 Month", AccessType.TEAM, "5", "chatgpt__chatgpt_business_team__1_month__team")
        ]),
        ("Google Gemini", "AI Assistants", "Google", [
            ("Gemini Pro Invitation", "18 Months", AccessType.INVITE, "1", "google_gemini__gemini_pro_invitation__18_months__invite")
        ]),
        ("Claude", "AI Assistants", "Anthropic", [
            ("Claude Pro (3.5 Sonnet)", "1 Month", AccessType.PRIVATE, "1", "claude__claude_pro_3_5_sonnet__1_month__private")
        ]),
        ("Grok", "AI Assistants", "xAI", [
            ("SuperGrok 2", "10 Days", AccessType.PRIVATE, "1", "grok__supergrok_2__10_days__private")
        ]),
        ("Perplexity", "AI Assistants", "Perplexity AI", [
            ("Perplexity Pro", "12 Months", AccessType.PRIVATE, "1", "perplexity__perplexity_pro__12_months__private")
        ]),
        ("CapCut", "AI Video", "ByteDance", [
            ("CapCut Pro Personal", "1 Month", AccessType.PRIVATE, "2 devices", "capcut__capcut_pro_personal__1_month__private"),
            ("CapCut Pro Team", "6 Months", AccessType.TEAM, "Multiple", "capcut__capcut_pro_team__6_months__team")
        ]),
        ("Leonardo AI", "AI Design", "Leonardo", [
            ("Leonardo AI Essential", "1 Month", AccessType.PRIVATE, "8500 credits", "leonardo_ai__leonardo_ai_essential__1_month__private")
        ]),
        ("Veo", "AI Video", "Google DeepMind", [
            ("Veo 3 Ultra Admin", "1 Month", AccessType.TEAM, "1 seat", "veo__veo_3_ultra_admin__1_month__team")
        ]),
        ("HeyGen", "AI Video", "HeyGen", [
            ("HeyGen AI Avatar Creator", "1 Month", AccessType.PRIVATE, "1", "heygen__heygen_ai_avatar_creator__1_month__private")
        ]),
        ("Kling AI", "AI Video", "Kuaishou", [
            ("Kling AI Video Standard", "1 Month", AccessType.PRIVATE, "1000 credits", "kling_ai__kling_ai_video_standard__1_month__private")
        ]),
        ("Krea AI", "AI Design", "Krea", [
            ("Krea AI Basic", "1 Month", AccessType.PRIVATE, "5100 credits", "krea_ai__krea_ai_basic__1_month__private")
        ]),
        ("Duolingo", "Education", "Duolingo", [
            ("Duolingo Super Promo", "12 Months", AccessType.PROMO_CODE, "1", "duolingo__duolingo_super_promo__12_months__promo_code")
        ]),
        ("Coursera", "Education", "Coursera", [
            ("Coursera Plus", "12 Months", AccessType.PRIVATE, "1", "coursera__coursera_plus__12_months__private")
        ]),
        ("JetBrains", "Development", "JetBrains", [
            ("JetBrains All Products Pack", "12 Months", AccessType.EDU, "1", "jetbrains__jetbrains_all_products_pack__12_months__edu")
        ]),
        ("Autodesk", "Architecture", "Autodesk", [
            ("Autodesk All Apps AEC", "3 Years", AccessType.EDU, "1", "autodesk__autodesk_all_apps_aec__3_years__edu")
        ]),
        ("iLovePDF", "Productivity", "iLovePDF", [
            ("iLovePDF Premium", "12 Months", AccessType.PRIVATE, "2000 credits", "ilovepdf__ilovepdf_premium__12_months__private")
        ]),
        ("Proton VPN", "VPN & Privacy", "Proton AG", [
            ("Proton VPN Plus 1Y", "12 Months", AccessType.PRIVATE, "1", "proton_vpn__proton_vpn_plus_1y__12_months__private"),
            ("Proton VPN Plus Lifetime", "Lifetime", AccessType.PRIVATE, "1", "proton_vpn__proton_vpn_plus_lifetime__lifetime__private")
        ]),
        ("Zoom", "Business", "Zoom", [
            ("Zoom Pro 28D", "1 Month", AccessType.PRIVATE, "1", "zoom__zoom_pro_28d__1_month__private")
        ]),
        ("Netflix", "Entertainment", "Netflix", [
            ("Netflix 4K UHD Profile", "1 Month", AccessType.SHARED, "1 slot", "netflix__netflix_4k_uhd_profile__1_month__shared"),
            ("Netflix 4K UHD Profile 3M", "3 Months", AccessType.SHARED, "1 slot", "netflix__netflix_4k_uhd_profile_3m__3_months__shared")
        ])
    ]

    plan_objs = {}
    for p_name, cat, pub, plans in products_def:
        prod = db.query(Product).filter_by(name=p_name).first()
        if not prod:
            prod = Product(name=p_name, category=cat, publisher=pub)
            db.add(prod)
            db.flush()
        
        for pl_name, dur, acc, units, m_key in plans:
            pl = db.query(ProductPlan).filter_by(matching_key=m_key).first()
            if not pl:
                pl = ProductPlan(
                    product_id=prod.id,
                    plan_name=pl_name,
                    duration=dur,
                    access_type=acc,
                    units=units,
                    matching_key=m_key
                )
                db.add(pl)
                db.flush()
            plan_objs[m_key] = pl

    # -------------------------------------------------------------
    # 4. HISTORICAL UNVERIFIED PRICE OBSERVATIONS (CONVERSATION SEED)
    # Preserving conflicting historical claims
    # -------------------------------------------------------------
    hist_claims = [
        # Gemini 18M conflicts
        ("AI Toolify", "google_gemini__gemini_pro_invitation__18_months__invite", 0.42, "USD", "Claimed at $0.42 in prompt seed", "Working Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("AI Toolify", "google_gemini__gemini_pro_invitation__18_months__invite", 0.45, "USD", "Claimed at $0.45 (≈ PKR 125, EasyPaisa) in prompt seed", "Working Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("Verified Streams", "google_gemini__gemini_pro_invitation__18_months__invite", 0.60, "USDT", "Claimed at $0.60 USDT in prompt seed", "Working Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.MEDIUM),
        
        # ChatGPT Plus 1M Private conflicts
        ("DigiCart Tools", "chatgpt__chatgpt_plus__1_month__private", 5.00, "USD", "Claimed at $5.00 in prompt seed", "Full Warranty (FW)", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "chatgpt__chatgpt_plus__1_month__private", 5.20, "USD", "Claimed at $5.20 UPI in prompt seed", "No Warranty (NW)", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("PiggyAI Premium", "chatgpt__chatgpt_plus__1_month__private", 7.62, "USD", "Claimed at $7.62 in prompt seed", "1 Day Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("NovaShop Premium", "chatgpt__chatgpt_plus__1_month__private", 10.48, "USD", "Claimed at $10.48 in prompt seed", "Full Warranty (FW)", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),

        # ChatGPT Student / Edu
        ("PiggyAI Premium", "chatgpt__chatgpt_plus_edu__2_years__edu", 4.63, "USD", "Claimed at $4.63 Edu 2 years in prompt seed", "5h Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.HIGH),
        ("PiggyAI Premium", "chatgpt__chatgpt_plus_student__4_months__edu", 7.70, "USD", "Claimed at $7.70 student pay link in prompt seed", "No Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.HIGH),

        # Other Historical Leads
        ("DigiCart Tools", "chatgpt__chatgpt_go__1_month__private", 4.80, "USD", "ChatGPT Go claimed at $4.80", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "chatgpt__chatgpt_business_team__1_month__team", 20.00, "USD", "ChatGPT Business slot claimed at $20.00", "25 Days Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("NovaShop Premium", "grok__supergrok_2__10_days__private", 2.00, "USD", "SuperGrok claimed at $2.00", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "perplexity__perplexity_pro__12_months__private", 46.50, "USD", "Perplexity Pro 12M claimed at $46.50", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("PiggyAI Premium", "capcut__capcut_pro_personal__1_month__private", 1.27, "USD", "CapCut Pro claimed at $1.27", "Working", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("NovaShop Premium", "leonardo_ai__leonardo_ai_essential__1_month__private", 1.00, "USD", "Leonardo AI claimed at $1.00", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("NovaShop Premium", "veo__veo_3_ultra_admin__1_month__team", 4.50, "USD", "Veo 3 Ultra claimed at $4.50", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("Sellnity Store", "heygen__heygen_ai_avatar_creator__1_month__private", 12.57, "USD", "HeyGen AI Avatar claimed at $12.57", "1 Day Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.MEDIUM),
        ("PiggyAI Premium", "netflix__netflix_4k_uhd_profile_3m__3_months__shared", 3.24, "USD", "Netflix 4K 3M slot claimed at $3.24", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.HIGH),

        # Education & Software Tools
        ("DigiCart Tools", "duolingo__duolingo_super_promo__12_months__promo_code", 0.80, "USD", "Duolingo 12M promo claimed at $0.80", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "jetbrains__jetbrains_all_products_pack__12_months__edu", 3.70, "USD", "JetBrains 1Y Edu claimed at $3.70", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.MEDIUM),
        ("DigiCart Tools", "coursera__coursera_plus__12_months__private", 3.50, "USD", "Coursera Plus 12M claimed at $3.50", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "autodesk__autodesk_all_apps_aec__3_years__edu", 1.30, "USD", "Autodesk 3Y claimed at $1.30", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.HIGH),
        ("Sellnity Store", "ilovepdf__ilovepdf_premium__12_months__private", 1.02, "USD", "iLovePDF 1Y claimed at $1.02", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("Sellnity Store", "kling_ai__kling_ai_video_standard__1_month__private", 5.02, "USD", "Kling AI claimed at $5.02", "Standard", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("Sellnity Store", "krea_ai__krea_ai_basic__1_month__private", 5.98, "USD", "Krea AI claimed at $5.98", "1 Day Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("Sellnity Store", "zoom__zoom_pro_28d__1_month__private", 2.92, "USD", "Zoom Pro claimed at $2.92", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "proton_vpn__proton_vpn_plus_1y__12_months__private", 4.50, "USD", "Proton VPN 1Y claimed at $4.50", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.LOW),
        ("DigiCart Tools", "proton_vpn__proton_vpn_plus_lifetime__lifetime__private", 8.00, "USD", "Proton VPN Lifetime claimed at $8.00", "Full Warranty", VerificationStatus.USER_SUPPLIED_UNVERIFIED, RiskLevel.HIGH)
    ]

    for sup_name, m_key, amt, curr, raw_text, warr, v_stat, r_lvl in hist_claims:
        sup = supplier_objs.get(sup_name)
        pl = plan_objs.get(m_key)
        if sup and pl:
            c_hash = compute_hash(f"{sup.canonical_name}:{m_key}:{amt}:{curr}:{raw_text}")
            if not db.query(PriceObservation).filter_by(content_hash=c_hash).first():
                rate = settings.default_usdt_to_pkr if curr == "USDT" else settings.default_usd_to_pkr
                db.add(PriceObservation(
                    supplier_id=sup.id,
                    plan_id=pl.id,
                    amount=amt,
                    currency=curr,
                    original_raw_text=raw_text,
                    source_url=sup.public_url,
                    source_snippet=raw_text,
                    warranty=warr,
                    fx_rate_used=rate,
                    fx_provider="SEED_HISTORICAL_DEFAULT",
                    amount_pkr=round(amt * rate, 2),
                    verification_status=v_stat,
                    confidence_score=40.0,
                    risk_level=r_lvl,
                    risk_flags="Unverified historical conversation claim." if r_lvl != RiskLevel.LOW else "",
                    capture_method="CONVERSATION_SEED_UNVERIFIED",
                    content_hash=c_hash,
                    reviewer_notes="Imported from historical chat context. Requires live proof to win verified status."
                ))

    # -------------------------------------------------------------
    # 5. RETAIL BENCHMARKS (aitoolgems.tech historical selling prices)
    # -------------------------------------------------------------
    benchmarks = [
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "chatgpt__chatgpt_plus__1_month__private", 2300.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "google_gemini__gemini_pro_invitation__18_months__invite", 800.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "capcut__capcut_pro_personal__1_month__private", 900.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "leonardo_ai__leonardo_ai_essential__1_month__private", 1900.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "veo__veo_3_ultra_admin__1_month__team", 2100.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "heygen__heygen_ai_avatar_creator__1_month__private", 4200.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "grok__supergrok_2__10_days__private", 1800.0),
        ("AIToolGems (Current Storefront)", "https://aitoolgems.tech/", "netflix__netflix_4k_uhd_profile__1_month__shared", 400.0),
        ("aitoolspak.tech (Public Competitor)", "https://aitoolspak.tech/", "chatgpt__chatgpt_plus__1_month__private", 2500.0),
        ("aitoolspak.tech (Public Competitor)", "https://aitoolspak.tech/", "google_gemini__gemini_pro_invitation__18_months__invite", 1000.0),
        ("aitoolspak.tech (Public Competitor)", "https://aitoolspak.tech/", "capcut__capcut_pro_personal__1_month__private", 999.0)
    ]
    for store, s_url, m_key, price in benchmarks:
        if not db.query(RetailBenchmark).filter_by(store_name=store, matching_plan_key=m_key).first():
            db.add(RetailBenchmark(
                store_name=store,
                store_url=s_url,
                matching_plan_key=m_key,
                observed_price_pkr=price,
                evidence_snippet="Historical benchmark record for price comparison"
            ))

    db.commit()
    print("Successfully seeded all suppliers, plans, research queue leads, and historical claims.")
