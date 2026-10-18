"""
Unit tests for exact-plan matching, normalization, and comparability rules.
"""

import pytest
from src.matching import (
    normalize_string, parse_duration, parse_access_type,
    generate_matching_key, are_plans_comparable
)
from src.models import AccessType

def test_normalize_string():
    assert normalize_string("  ChatGPT  Plus 🚀 ") == "chatgpt plus"
    assert normalize_string("Leonardo.AI! Essential") == "leonardo.ai essential"

def test_parse_duration():
    assert parse_duration("ChatGPT Plus 1 Month Private") == "1 Month"
    assert parse_duration("Gemini Pro 18 Months Invitation") == "18 Months"
    assert parse_duration("Autodesk AEC 3 Years License") == "3 Years"
    assert parse_duration("Proton VPN Lifetime Deal") == "Lifetime"
    assert parse_duration("SuperGrok 10 Days pass") == "10 Days"

def test_parse_access_type():
    assert parse_access_type("Private Account on your email") == AccessType.PRIVATE
    assert parse_access_type("Account delivered by email and password") == AccessType.PRIVATE
    assert parse_access_type("Netflix 1 Profile Slot") == AccessType.SHARED
    assert parse_access_type("ChatGPT Edu K12 student") == AccessType.EDU
    assert parse_access_type("Windows 11 Retail License Key") == AccessType.KEY
    assert parse_access_type("Gemini Pro Family Invite link") == AccessType.INVITE
    assert parse_access_type("Duolingo Super Promo Code") == AccessType.PROMO_CODE
    assert parse_access_type("OpenAI Codex API token $100") == AccessType.API

def test_distinct_keys_for_different_plans():
    # ChatGPT Plus private 1M vs ChatGPT Edu 2Y vs ChatGPT Plus Student 4M
    key_private = generate_matching_key("ChatGPT", "Plus", "1 Month", AccessType.PRIVATE)
    key_edu = generate_matching_key("ChatGPT", "Plus", "2 Years", AccessType.EDU)
    key_student = generate_matching_key("ChatGPT", "Plus", "4 Months", AccessType.EDU)
    
    assert key_private != key_edu
    assert key_edu != key_student
    assert "private" in key_private
    assert "edu" in key_edu

def test_are_plans_comparable():
    plan_a = {"product_name": "ChatGPT", "duration": "1 Month", "access_type": "PRIVATE", "credits": ""}
    plan_b = {"product_name": "ChatGPT", "duration": "1 Month", "access_type": "PRIVATE", "credits": ""}
    plan_diff_dur = {"product_name": "ChatGPT", "duration": "12 Months", "access_type": "PRIVATE", "credits": ""}
    plan_diff_acc = {"product_name": "ChatGPT", "duration": "1 Month", "access_type": "SHARED", "credits": ""}

    ok, msg = are_plans_comparable(plan_a, plan_b)
    assert ok is True
    assert msg == "GENUINELY_COMPARABLE"

    ok_dur, msg_dur = are_plans_comparable(plan_a, plan_diff_dur)
    assert ok_dur is False
    assert "Different durations" in msg_dur

    ok_acc, msg_acc = are_plans_comparable(plan_a, plan_diff_acc)
    assert ok_acc is False
    assert "Different access types" in msg_acc
