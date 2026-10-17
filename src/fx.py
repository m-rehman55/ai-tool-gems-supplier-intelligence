"""
Foreign exchange (FX) management engine.
Handles live and manual conversion of USD, USDT, EUR to PKR with provenance and staleness checks.
"""

from datetime import datetime, timezone, timedelta
from typing import Tuple, Dict, Any, Optional
from src.config import settings
from src.models import FxRate

class FxEngine:
    def __init__(self, db_session=None):
        self.db = db_session
        self.cached_rates = {
            "USD": settings.default_usd_to_pkr,
            "USDT": settings.default_usdt_to_pkr,
            "EUR": settings.default_eur_to_pkr,
            "PKR": 1.0
        }
        self.staleness_hours = 24

    def get_rate(self, base_currency: str, quote_currency: str = "PKR") -> Tuple[float, str, datetime, bool]:
        """
        Retrieves exchange rate for pair.
        Returns (rate, source_provider, retrieved_at, is_stale).
        """
        base = base_currency.upper().strip()
        quote = quote_currency.upper().strip()
        
        if base == quote:
            return 1.0, "IDENTICAL_CURRENCY", datetime.now(timezone.utc), False
            
        if self.db:
            fx_row = self.db.query(FxRate).filter_by(base_currency=base, quote_currency=quote).first()
            if fx_row:
                is_stale = False
                now = datetime.now(timezone.utc)
                if fx_row.retrieved_at:
                    age = now - fx_row.retrieved_at.replace(tzinfo=timezone.utc if fx_row.retrieved_at.tzinfo is None else fx_row.retrieved_at.tzinfo)
                    if age > timedelta(hours=self.staleness_hours):
                        is_stale = True
                return fx_row.rate, fx_row.source_provider, fx_row.retrieved_at, is_stale

        # Fallback to configured defaults
        default_rate = self.cached_rates.get(base, 280.0)
        return default_rate, "DEFAULT_CONFIG_FALLBACK", datetime.now(timezone.utc), True

    def convert_to_pkr(self, amount: float, currency: str) -> Tuple[float, float, str, bool]:
        """
        Converts given amount in currency to PKR.
        Returns (amount_pkr, rate_used, provider, is_stale).
        """
        rate, provider, _, is_stale = self.get_rate(currency, "PKR")
        amount_pkr = round(amount * rate, 2)
        return amount_pkr, rate, provider, is_stale

    def update_rate(self, base_currency: str, rate: float, provider: str = "MANUAL_UPDATE", is_override: bool = True) -> None:
        """Saves a new or overridden exchange rate."""
        base = base_currency.upper().strip()
        self.cached_rates[base] = rate
        if self.db:
            fx_row = self.db.query(FxRate).filter_by(base_currency=base, quote_currency="PKR").first()
            now = datetime.now(timezone.utc)
            if fx_row:
                fx_row.rate = rate
                fx_row.source_provider = provider
                fx_row.retrieved_at = now
                fx_row.is_manual_override = is_override
                fx_row.status = "ACTIVE"
            else:
                self.db.add(FxRate(
                    base_currency=base,
                    quote_currency="PKR",
                    rate=rate,
                    source_provider=provider,
                    retrieved_at=now,
                    is_manual_override=is_override,
                    status="ACTIVE"
                ))
            self.db.commit()
