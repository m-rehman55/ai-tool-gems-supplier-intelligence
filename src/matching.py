"""
Exact-plan matching and normalization module.
Ensures like-for-like comparison. Distinguishes duration, tier, credits, and access type.
"""

import re
from typing import Tuple, Optional, Dict, Any
from src.models import AccessType

def normalize_string(text: str) -> str:
    """Standardizes string by stripping emojis, lowercase, and consolidating spaces."""
    if not text:
        return ""
    # Remove emojis and non-ascii
    cleaned = re.sub(r'[^\x00-\x7F]+', ' ', text)
    # Remove symbols
    cleaned = re.sub(r'[^\w\s\-\.\+]', ' ', cleaned)
    # Normalize spaces
    cleaned = re.sub(r'\s+', ' ', cleaned).strip().lower()
    return cleaned

def parse_duration(raw_text: str) -> str:
    """Extracts standardized duration label from text."""
    t = raw_text.lower()
    if 'lifetime' in t:
        return 'Lifetime'
    
    # Years
    y_m = re.search(r'\b(\d+)\s*(?:years?|y)\b', t)
    if y_m:
        val = int(y_m.group(1))
        return f"{val} Year{'s' if val > 1 else ''}"
        
    # Months
    m_m = re.search(r'\b(\d+)\s*(?:months?|m)\b', t)
    if m_m:
        val = int(m_m.group(1))
        return f"{val} Month{'s' if val > 1 else ''}"
        
    # Days
    d_m = re.search(r'\b(\d+)\s*(?:days?|d)\b', t)
    if d_m:
        val = int(d_m.group(1))
        return f"{val} Days"
        
    # Hours
    h_m = re.search(r'\b(\d+)\s*(?:hours?|h)\b', t)
    if h_m:
        val = int(h_m.group(1))
        return f"{val} Hours"
        
    return "1 Month"

def parse_access_type(raw_text: str) -> AccessType:
    """Infers access and account model from raw description."""
    t = raw_text.lower()
    if 'slot' in t or 'shared' in t or 'profile' in t:
        return AccessType.SHARED
    elif 'edu' in t or 'student' in t or 'k12' in t:
        return AccessType.EDU
    elif 'team' in t or 'panel' in t or 'admin' in t:
        return AccessType.TEAM
    elif 'api' in t or re.search(r'\btoken\b', t):
        return AccessType.API
    elif 'key' in t or 'license' in t or 'retail' in t:
        return AccessType.KEY
    elif 'invite' in t or 'family' in t or 'link' in t:
        return AccessType.INVITE
    elif 'promo' in t or re.search(r'\bcode\b', t) or 'voucher' in t:
        return AccessType.PROMO_CODE
    elif 'private' in t or 'personal' in t or 'email|password' in t or '2fa' in t:
        return AccessType.PRIVATE
    return AccessType.UNKNOWN

def generate_matching_key(product_name: str, plan_name: str, duration: str, access_type: AccessType) -> str:
    """
    Creates an immutable, canonical matching key.
    Ensures different durations, tiers, and access models produce distinct keys.
    """
    p_slug = normalize_string(product_name).replace(' ', '_')
    plan_slug = normalize_string(plan_name).replace(' ', '_')
    dur_slug = normalize_string(duration).replace(' ', '_')
    acc_slug = access_type.value.lower()
    return f"{p_slug}__{plan_slug}__{dur_slug}__{acc_slug}"

def are_plans_comparable(plan_a: Dict[str, Any], plan_b: Dict[str, Any]) -> Tuple[bool, str]:
    """
    Validates whether two offers are strictly comparable.
    Returns (True, 'MATCH') or (False, reason).
    """
    if plan_a.get('product_name') != plan_b.get('product_name'):
        return False, "Different product tool"
        
    if plan_a.get('duration') != plan_b.get('duration'):
        return False, f"Different durations: {plan_a.get('duration')} vs {plan_b.get('duration')}"
        
    if plan_a.get('access_type') != plan_b.get('access_type'):
        return False, f"Different access types: {plan_a.get('access_type')} vs {plan_b.get('access_type')}"
        
    if plan_a.get('credits') != plan_b.get('credits'):
        return False, f"Different credits/allowance: {plan_a.get('credits')} vs {plan_b.get('credits')}"
        
    return True, "GENUINELY_COMPARABLE"
