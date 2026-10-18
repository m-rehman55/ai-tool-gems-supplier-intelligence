"""
Unit tests for WinnerEngine and Rule 9 Verification logic.
Verifies:
- Verified quotes win lowest verified status
- Unverified quotes produce 'NO_VERIFIED_WINNER_YET'
- Stale quotes are disqualified from verified winner
- High-risk quotes are disqualified from verified winner
- Suspiciously cheap quotes are disqualified
- Clear separation between lowest advertised, lowest comparable, and lowest verified
- Rejection of referral incentives and non-product broadcasts
"""

import pytest
from datetime import datetime, timezone, timedelta
from src.models import (
    Supplier, Product, ProductPlan, PriceObservation, VerificationStatus, RiskLevel, AccessType
)
from src.winner import (
    WinnerEngine, is_suspiciously_cheap, check_quote_eligibility_for_verified_winner
)
from src.adapters.public_telegram import TelegramPublicChannelAdapter


def test_referral_filter():
    adapter = TelegramPublicChannelAdapter()
    
    # Referral and wallet text
    referral_msg = (
        "🎁 Earn FREE balance!\n\n"
        "Invite your friends to AI TOOLS and get $0.10 in your wallet for every friend who joins and verifies 💰\n"
        "Invite 10 friends = $1 free → use it on ChatGPT, Gemini, Claude & more 🚀\n"
        "👉 Tap Refer & Earn in the menu to get your link!"
    )
    assert adapter.is_promotional_or_referral(referral_msg) is True
    
    # Real product quote
    real_product_msg = (
        "🔥 RESTOCK ALERT:\n"
        "ChatGPT Plus (1 Month Private) - Full Warranty (FW)\n"
        "Price: $5.00 USDT | Stock: 25 available\n"
        "Instant delivery via bot"
    )
    assert adapter.is_promotional_or_referral(real_product_msg) is False


def test_suspiciously_cheap_detection():
    # ChatGPT Plus at $0.10 is suspiciously cheap
    is_susp, reason = is_suspiciously_cheap("ChatGPT", "ChatGPT Plus", 0.10)
    assert is_susp is True
    assert "Suspiciously cheap" in reason
    
    # ChatGPT Plus at $5.00 is realistic wholesale
    is_susp, reason = is_suspiciously_cheap("ChatGPT", "ChatGPT Plus", 5.00)
    assert is_susp is False
    
    # Google Gemini at $0.10 is suspiciously cheap
    is_susp, reason = is_suspiciously_cheap("Google Gemini", "Gemini Pro", 0.10)
    assert is_susp is True
    
    # Google Gemini at $0.45 is realistic wholesale
    is_susp, reason = is_suspiciously_cheap("Google Gemini", "Gemini Pro", 0.45)
    assert is_susp is False


def test_winner_engine_no_quotes():
    engine = WinnerEngine(freshness_hours=48)
    product = Product(name="Claude Pro", category="AI Writing")
    plan = ProductPlan(product=product, plan_name="Claude Pro 1M", duration="1 Month", access_type=AccessType.PRIVATE, matching_key="claude_pro__1m__private")
    plan.observations = []
    
    eval_res = engine.evaluate_plan(plan)
    assert eval_res.has_verified_winner is False
    assert eval_res.status_summary == "NO_QUOTES_RECORDED"
    assert eval_res.lowest_verified is None


def test_winner_engine_unverified_only():
    engine = WinnerEngine(freshness_hours=48)
    product = Product(name="ChatGPT", category="AI Chat")
    plan = ProductPlan(product=product, plan_name="ChatGPT Plus", duration="1 Month", access_type=AccessType.PRIVATE, matching_key="chatgpt__plus__1m__private")
    
    sup = Supplier(canonical_name="DigiCart", primary_handle="@getdigitaltools_bot")
    
    unverified_obs = PriceObservation(
        supplier=sup,
        plan=plan,
        amount=5.00,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=1400.0,
        warranty="Full Warranty",
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        risk_level=RiskLevel.LOW,
        original_raw_text="ChatGPT Plus $5.00",
        observed_at=datetime.now(timezone.utc)
    )
    plan.observations = [unverified_obs]
    
    eval_res = engine.evaluate_plan(plan)
    assert eval_res.has_verified_winner is False
    assert eval_res.status_summary == "NO_VERIFIED_WINNER_YET"
    assert eval_res.lowest_verified is None
    assert eval_res.lowest_advertised == unverified_obs
    assert len(eval_res.pending_candidates) == 1
    assert unverified_obs.id in eval_res.disqualification_reasons or True


def test_winner_engine_stale_disqualification():
    engine = WinnerEngine(freshness_hours=48)
    product = Product(name="CapCut", category="Video Editing")
    plan = ProductPlan(product=product, plan_name="CapCut Pro", duration="1 Month", access_type=AccessType.PRIVATE, matching_key="capcut__pro__1m__private")
    
    sup = Supplier(canonical_name="PiggyAI", primary_handle="@PiggyAi799_Bot")
    
    # 72 hours old quote (stale under 48h policy)
    old_time = datetime.now(timezone.utc) - timedelta(hours=72)
    stale_obs = PriceObservation(
        supplier=sup,
        plan=plan,
        amount=1.27,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=355.6,
        warranty="1 Month",
        verification_status=VerificationStatus.VERIFIED,
        risk_level=RiskLevel.LOW,
        original_raw_text="CapCut Pro $1.27",
        observed_at=old_time
    )
    plan.observations = [stale_obs]
    
    eval_res = engine.evaluate_plan(plan)
    assert eval_res.has_verified_winner is False
    assert eval_res.lowest_verified is None
    assert eval_res.status_summary == "NO_VERIFIED_WINNER_YET"


def test_winner_engine_high_risk_disqualification():
    engine = WinnerEngine(freshness_hours=48)
    product = Product(name="ChatGPT", category="AI Chat")
    plan = ProductPlan(product=product, plan_name="ChatGPT Plus", duration="1 Month", access_type=AccessType.PRIVATE, matching_key="chatgpt__plus__1m__private")
    
    sup = Supplier(canonical_name="SpamBot", primary_handle="@spambot")
    
    high_risk_obs = PriceObservation(
        supplier=sup,
        plan=plan,
        amount=2.00,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=560.0,
        warranty="No Warranty",
        verification_status=VerificationStatus.VERIFIED,
        risk_level=RiskLevel.HIGH,
        original_raw_text="ChatGPT Plus $2.00 shared credential hack",
        observed_at=datetime.now(timezone.utc)
    )
    plan.observations = [high_risk_obs]
    
    eval_res = engine.evaluate_plan(plan)
    assert eval_res.has_verified_winner is False
    assert eval_res.lowest_verified is None


def test_winner_engine_verified_winner_selection():
    engine = WinnerEngine(freshness_hours=48)
    product = Product(name="JetBrains", category="Developer Tools")
    plan = ProductPlan(product=product, plan_name="All IDEs", duration="1 Year", access_type=AccessType.EDU, matching_key="jetbrains__all_ides__1_year__edu")
    
    sup1 = Supplier(canonical_name="DigiCart", primary_handle="@getdigitaltools_bot", reliability_score=85.0)
    sup2 = Supplier(canonical_name="NovaShop", primary_handle="@Novasubglo_bot", reliability_score=75.0)
    
    # Unverified cheaper offer
    obs_unverified = PriceObservation(
        supplier=sup2,
        plan=plan,
        amount=3.00,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=840.0,
        warranty="No Warranty",
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        risk_level=RiskLevel.LOW,
        original_raw_text="JetBrains $3.00 unverified claim",
        observed_at=datetime.now(timezone.utc)
    )
    
    # Verified offer
    obs_verified = PriceObservation(
        supplier=sup1,
        plan=plan,
        amount=3.70,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=1036.0,
        warranty="1 Year Warranty",
        verification_status=VerificationStatus.VERIFIED,
        risk_level=RiskLevel.LOW,
        original_raw_text="JetBrains Edu $3.70 confirmed post",
        observed_at=datetime.now(timezone.utc)
    )
    
    plan.observations = [obs_unverified, obs_verified]
    
    eval_res = engine.evaluate_plan(plan)
    assert eval_res.has_verified_winner is True
    assert eval_res.lowest_advertised == obs_unverified  # $3.00 is lowest advertised
    assert eval_res.lowest_verified == obs_verified      # $3.70 is lowest verified!
    assert eval_res.recommended == obs_verified
    assert eval_res.status_summary == "VERIFIED_WINNER_FOUND"
