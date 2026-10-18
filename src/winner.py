"""
Winner Selection Engine for Supplier Price Intelligence.
Implements Rule 9 of the Master Execution Prompt:
- Shows all supplier prices side-by-side.
- Evaluates lowest advertised, lowest comparable, lowest verified, and recommended supplier.
- Enforces strict verification conditions (VERIFIED status, freshness, low/medium risk, in stock, valid terms).
- If no quote qualifies as verified, displays "No verified winner yet" with clear candidate disqualification reasons.
"""

from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass, field

from src.models import (
    ProductPlan, PriceObservation, VerificationStatus, RiskLevel, AccessType
)
from src.config import settings


@dataclass
class WinnerEvaluation:
    plan_id: int
    plan_key: str
    product_name: str
    plan_name: str
    duration: str
    access_type: str
    
    # Tiered winner distinctions
    lowest_advertised: Optional[PriceObservation] = None
    lowest_comparable: Optional[PriceObservation] = None
    lowest_verified: Optional[PriceObservation] = None
    recommended: Optional[PriceObservation] = None
    
    has_verified_winner: bool = False
    status_summary: str = "NO_VERIFIED_WINNER_YET"
    
    # Audit trail
    total_quotes_count: int = 0
    verified_quotes_count: int = 0
    pending_candidates: List[PriceObservation] = field(default_factory=list)
    disqualification_reasons: Dict[int, List[str]] = field(default_factory=dict)


def is_suspiciously_cheap(product_name: str, plan_name: str, amount_usd: float) -> Tuple[bool, str]:
    """Detects unrealistically cheap offers that represent referral rewards, cracked accounts, or parsing errors."""
    pn = product_name.lower()
    pl = plan_name.lower()
    
    # Premium AI tools retailing at $20+/mo (ChatGPT Plus, Claude Pro, Perplexity Pro)
    if any(k in pn for k in ["chatgpt", "claude", "perplexity"]):
        if "plus" in pl or "pro" in pl:
            if amount_usd < 1.0:
                return True, f"Suspiciously cheap (${amount_usd:.2f} < $1.00 for {product_name} {plan_name}). Likely referral reward or high risk."
                
    # Gemini Pro
    if "gemini" in pn:
        if amount_usd < 0.25:
            return True, f"Suspiciously cheap (${amount_usd:.2f} < $0.25 for Google Gemini)."
            
    # Generic minimum price floor across any digital tool
    if amount_usd < 0.20:
        return True, f"Suspiciously cheap wholesale price (${amount_usd:.2f} < $0.20)."
        
    return False, ""


def check_quote_eligibility_for_verified_winner(
    obs: PriceObservation,
    freshness_hours: int = settings.quote_freshness_hours
) -> Tuple[bool, List[str]]:
    """
    Evaluates whether an observation meets all mandatory criteria to win lowest verified price:
    1. Verification status is explicitly VERIFIED
    2. Quote is not stale or expired
    3. Licensing / security risk is below HIGH
    4. Not suspiciously cheap
    5. In stock / available
    """
    reasons = []
    
    # 1. Verification status
    if obs.verification_status != VerificationStatus.VERIFIED:
        reasons.append(f"Verification status is {obs.verification_status.value} (requires VERIFIED)")
        
    # 2. Risk level
    if obs.risk_level in [RiskLevel.HIGH, RiskLevel.CRITICAL]:
        reasons.append(f"Risk level is {obs.risk_level.value} (exceeds allowed risk threshold)")
        
    # 3. Stock availability
    st = (obs.stock_status or "").lower()
    if any(k in st for k in ["out of stock", "sold out", "unavailable"]):
        reasons.append(f"Stock status is '{obs.stock_status}'")
        
    # 4. Freshness & Expiry
    now = datetime.now(timezone.utc)
    if obs.quote_expiry:
        exp = obs.quote_expiry if obs.quote_expiry.tzinfo else obs.quote_expiry.replace(tzinfo=timezone.utc)
        if now > exp:
            reasons.append(f"Quote expired on {exp.strftime('%Y-%m-%d %H:%M')}")
            
    if obs.observed_at:
        obs_time = obs.observed_at if obs.observed_at.tzinfo else obs.observed_at.replace(tzinfo=timezone.utc)
        age_hours = (now - obs_time).total_seconds() / 3600.0
        if age_hours > freshness_hours:
            reasons.append(f"Quote is stale ({age_hours:.1f} hours old, max allowed {freshness_hours}h)")
            
    # 5. Suspicious pricing check
    if obs.plan and obs.plan.product:
        p_name = obs.plan.product.name
        pl_name = obs.plan.plan_name
        is_suspicious, susp_reason = is_suspiciously_cheap(p_name, pl_name, obs.amount)
        if is_suspicious:
            reasons.append(susp_reason)
            
    return (len(reasons) == 0), reasons


class WinnerEngine:
    """Evaluates supplier quotes for any ProductPlan and awards verified winner strictly according to rules."""
    
    def __init__(self, freshness_hours: int = settings.quote_freshness_hours):
        self.freshness_hours = freshness_hours

    def evaluate_plan(self, plan: ProductPlan) -> WinnerEvaluation:
        observations = list(plan.observations or [])
        total_count = len(observations)
        
        evaluation = WinnerEvaluation(
            plan_id=plan.id,
            plan_key=plan.matching_key,
            product_name=plan.product.name if plan.product else "Unknown",
            plan_name=plan.plan_name,
            duration=plan.duration,
            access_type=plan.access_type.value if plan.access_type else "UNKNOWN",
            total_quotes_count=total_count
        )
        
        if not observations:
            evaluation.status_summary = "NO_QUOTES_RECORDED"
            return evaluation
            
        # Sort all observations by amount_pkr ascending
        sorted_all = sorted(observations, key=lambda x: x.amount_pkr)
        
        # 1. Lowest Advertised: absolute lowest price recorded
        evaluation.lowest_advertised = sorted_all[0]
        
        # 2. Lowest Comparable: lowest price that matches exact plan duration and access type
        # (Already partitioned by plan in this relational model)
        evaluation.lowest_comparable = sorted_all[0]
        
        # 3. Lowest Verified: satisfies all verification & safety criteria
        verified_candidates = []
        pending_candidates = []
        
        for obs in sorted_all:
            eligible, reasons = check_quote_eligibility_for_verified_winner(obs, self.freshness_hours)
            if eligible:
                verified_candidates.append(obs)
            else:
                evaluation.disqualification_reasons[obs.id] = reasons
                pending_candidates.append(obs)
                
        evaluation.verified_quotes_count = len(verified_candidates)
        evaluation.pending_candidates = pending_candidates
        
        if verified_candidates:
            evaluation.lowest_verified = verified_candidates[0]
            evaluation.has_verified_winner = True
            evaluation.status_summary = "VERIFIED_WINNER_FOUND"
            
            # 4. Recommended Supplier: best verified quote with active warranty & high reliability
            best_recommended = None
            for cand in verified_candidates:
                w_str = (cand.warranty or "").lower()
                has_warranty = not ("no warranty" in w_str or "not stated" in w_str or "0" in w_str)
                rel_score = cand.supplier.reliability_score if cand.supplier else 50.0
                if has_warranty and rel_score >= 60.0:
                    best_recommended = cand
                    break
            evaluation.recommended = best_recommended or verified_candidates[0]
        else:
            evaluation.has_verified_winner = False
            evaluation.status_summary = "NO_VERIFIED_WINNER_YET"
            evaluation.lowest_verified = None
            evaluation.recommended = None
            
        return evaluation
