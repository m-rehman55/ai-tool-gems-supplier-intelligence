"""
Cost, margin, and retail pricing calculation engine.
Calculates landed cost, transaction fees, warranty reserve, break-even, and suggested retail prices.
Detects MARGIN_CONFLICT when competitor prices undercut safe margins.
"""

from typing import Dict, Any, Optional
from dataclasses import dataclass
from src.config import settings

@dataclass
class PricingResult:
    supplier_cost_usd: float
    supplier_cost_pkr: float
    fx_rate_used: float
    payment_fee_pkr: float
    warranty_reserve_pkr: float
    total_landed_cost_pkr: float
    break_even_pkr: float
    target_profit_pkr: float
    suggested_retail_pkr: float
    estimated_gross_profit_pkr: float
    estimated_net_profit_pkr: float
    net_margin_pct: float
    competitor_retail_pkr: Optional[float] = None
    has_margin_conflict: bool = False
    conflict_notes: str = ""

class PricingCalculator:
    def __init__(
        self,
        min_profit_pkr: float = settings.target_profit_min_pkr,
        max_profit_pkr: float = settings.target_profit_max_pkr,
        payment_fee_pct: float = settings.payment_network_fee_pct,
        warranty_reserve_pct: float = settings.warranty_reserve_pct
    ):
        self.min_profit_pkr = min_profit_pkr
        self.max_profit_pkr = max_profit_pkr
        self.payment_fee_pct = payment_fee_pct
        self.warranty_reserve_pct = warranty_reserve_pct

    def calculate(
        self,
        cost_amount: float,
        fx_rate: float,
        competitor_price_pkr: Optional[float] = None,
        desired_profit_pkr: Optional[float] = None
    ) -> PricingResult:
        """
        Executes full landed cost and profit calculation.
        """
        cost_pkr = round(cost_amount * fx_rate, 2)
        
        # Landed cost components
        fee_pkr = round(cost_pkr * self.payment_fee_pct, 2)
        reserve_pkr = round(cost_pkr * self.warranty_reserve_pct, 2)
        total_landed_cost = round(cost_pkr + fee_pkr + reserve_pkr, 2)
        break_even = total_landed_cost

        # Determine target profit
        if desired_profit_pkr is not None:
            target_profit = desired_profit_pkr
        else:
            # Scale target profit based on cost magnitude within band
            if cost_pkr < 500:
                target_profit = self.min_profit_pkr  # Rs. 400
            elif cost_pkr < 2000:
                target_profit = (self.min_profit_pkr + self.max_profit_pkr) / 2  # Rs. 600
            elif cost_pkr < 5000:
                target_profit = self.max_profit_pkr  # Rs. 800
            else:
                target_profit = max(self.max_profit_pkr, round(cost_pkr * 0.25))

        # Initial suggested retail price before competitor benchmarking
        raw_retail = total_landed_cost + target_profit
        
        # Rounding rules (to nearest 50 for < 2000, nearest 100 for >= 2000)
        if raw_retail < 2000:
            suggested_retail = round(raw_retail / 50) * 50
        else:
            suggested_retail = round(raw_retail / 100) * 100

        # Competitor benchmarking & conflict detection
        has_conflict = False
        conflict_notes = []

        if competitor_price_pkr is not None and competitor_price_pkr > 0:
            if suggested_retail >= competitor_price_pkr:
                # Competitor is equal or cheaper
                potential_competitive_price = competitor_price_pkr - 50.0  # Be slightly cheaper
                if potential_competitive_price > break_even + 200.0:
                    # Feasible to adjust without selling at loss
                    suggested_retail = potential_competitive_price
                    conflict_notes.append("Adjusted to beat competitor price by Rs. 50 while preserving positive margin.")
                else:
                    has_conflict = True
                    conflict_notes.append(
                        f"MARGIN_CONFLICT: Competitor price (Rs. {competitor_price_pkr:,}) is below safe break-even "
                        f"(Rs. {break_even:,.2f}) + minimum margin. Recommend owner review rather than matching loss-making price."
                    )

        gross_profit = round(suggested_retail - cost_pkr, 2)
        net_profit = round(suggested_retail - total_landed_cost, 2)
        net_margin_pct = round((net_profit / suggested_retail * 100) if suggested_retail > 0 else 0, 2)

        return PricingResult(
            supplier_cost_usd=cost_amount,
            supplier_cost_pkr=cost_pkr,
            fx_rate_used=fx_rate,
            payment_fee_pkr=fee_pkr,
            warranty_reserve_pkr=reserve_pkr,
            total_landed_cost_pkr=total_landed_cost,
            break_even_pkr=break_even,
            target_profit_pkr=target_profit,
            suggested_retail_pkr=suggested_retail,
            estimated_gross_profit_pkr=gross_profit,
            estimated_net_profit_pkr=net_profit,
            net_margin_pct=net_margin_pct,
            competitor_retail_pkr=competitor_price_pkr,
            has_margin_conflict=has_conflict,
            conflict_notes=" ".join(conflict_notes)
        )
