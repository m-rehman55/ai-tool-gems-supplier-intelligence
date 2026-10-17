"""
Unit tests for pricing calculator, landed cost, margins, and conflict detection.
"""

import pytest
from src.pricing import PricingCalculator, PricingResult

def test_landed_cost_calculation():
    calc = PricingCalculator(payment_fee_pct=0.015, warranty_reserve_pct=0.030)
    # $5.00 at 280 PKR/USD = 1400 PKR
    # Fee (1.5%) = 21.00 PKR
    # Reserve (3.0%) = 42.00 PKR
    # Total landed cost = 1463.00 PKR
    res = calc.calculate(cost_amount=5.0, fx_rate=280.0)
    
    assert res.supplier_cost_usd == 5.0
    assert res.supplier_cost_pkr == 1400.0
    assert res.payment_fee_pkr == 21.0
    assert res.warranty_reserve_pkr == 42.0
    assert res.total_landed_cost_pkr == 1463.0
    assert res.break_even_pkr == 1463.0

def test_suggested_retail_and_profit_scaling():
    calc = PricingCalculator(min_profit_pkr=400, max_profit_pkr=800)
    
    # Low cost item: $1.00 (280 PKR) -> target profit 400 PKR
    res_low = calc.calculate(cost_amount=1.0, fx_rate=280.0)
    assert res_low.target_profit_pkr == 400.0
    # Landed cost: 280 + 4.2 + 8.4 = 292.6
    # Suggested retail: 292.6 + 400 = 692.6 -> rounded to nearest 50 = 700.0
    assert res_low.suggested_retail_pkr == 700.0
    assert res_low.estimated_gross_profit_pkr > 0

    # Medium cost item: $5.00 (1400 PKR) -> target profit (400+800)/2 = 600 PKR
    res_med = calc.calculate(cost_amount=5.0, fx_rate=280.0)
    assert res_med.target_profit_pkr == 600.0
    # Landed cost: 1463.00 + 600 = 2063 -> rounded to nearest 100 = 2100.0
    assert res_med.suggested_retail_pkr == 2100.0

def test_margin_conflict_detection():
    calc = PricingCalculator(payment_fee_pct=0.015, warranty_reserve_pct=0.030)
    
    # Cost = $10.00 (2800 PKR), landed cost = 2926 PKR
    # Normal suggested retail would be ~3700 PKR
    # Competitor is dumping at 2500 PKR (below break-even!)
    res_conflict = calc.calculate(cost_amount=10.0, fx_rate=280.0, competitor_price_pkr=2500.0)
    assert res_conflict.has_margin_conflict is True
    assert "MARGIN_CONFLICT" in res_conflict.conflict_notes
    assert "below safe break-even" in res_conflict.conflict_notes

def test_competitor_price_adjustment_safe():
    calc = PricingCalculator(payment_fee_pct=0.015, warranty_reserve_pct=0.030)
    
    # Cost = $5.00 (1400 PKR), landed cost = 1463 PKR
    # Normal suggested retail is 2100 PKR
    # Competitor sells at 2000 PKR (well above break-even 1463 + 200 = 1663)
    # App should adjust to 1950 PKR (competitor - 50) safely
    res_adj = calc.calculate(cost_amount=5.0, fx_rate=280.0, competitor_price_pkr=2000.0)
    assert res_adj.has_margin_conflict is False
    assert res_adj.suggested_retail_pkr == 1950.0
    assert res_adj.estimated_net_profit_pkr > 0
