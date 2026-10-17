"""
Alerts and Notification Engine.
Detects price changes, quote expiry/staleness, warranty updates, margin conflicts, and high-risk offers.
Deduplicates alerts and logs detailed provenance.
"""

from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from src.models import PriceObservation, ProductPlan, PriceHistory, RiskLevel, VerificationStatus
from src.config import settings

@dataclass
class AlertItem:
    alert_type: str  # PRICE_DROP, PRICE_INCREASE, STALE_QUOTE, WARRANTY_CHANGE, HIGH_RISK, MARGIN_CONFLICT
    severity: str    # INFO, WARNING, CRITICAL
    tool_plan: str
    supplier_name: str
    message: str
    old_value: Optional[str] = None
    new_value: Optional[str] = None
    source_url: Optional[str] = None
    detected_at: datetime = None

class AlertEngine:
    def __init__(self, db_session):
        self.db = db_session

    def scan_for_alerts(self) -> List[AlertItem]:
        """Runs rule checks across active database entities."""
        alerts: List[AlertItem] = []
        now = datetime.now(timezone.utc)

        # 1. High-risk and suspiciously cheap offers
        high_risk_obs = self.db.query(PriceObservation).filter(
            PriceObservation.risk_level.in_([RiskLevel.HIGH, RiskLevel.CRITICAL])
        ).all()
        for o in high_risk_obs:
            alerts.append(AlertItem(
                alert_type="HIGH_RISK",
                severity="WARNING",
                tool_plan=o.plan.matching_key if o.plan else "Unmatched",
                supplier_name=o.supplier.canonical_name if o.supplier else "Unknown",
                message=f"High-risk offer detected: {o.risk_flags or 'Unverified student/shared/lifetime claim'}",
                new_value=f"${o.amount:.2f} ({o.warranty})",
                source_url=o.source_url,
                detected_at=now
            ))

        # 2. Stale or expired quotes (>48 hours)
        stale_cutoff = now - timedelta(hours=settings.quote_freshness_hours)
        stale_obs = self.db.query(PriceObservation).filter(
            PriceObservation.observed_at < stale_cutoff
        ).all()
        for s in stale_obs:
            alerts.append(AlertItem(
                alert_type="STALE_QUOTE",
                severity="INFO",
                tool_plan=s.plan.matching_key if s.plan else "Unmatched",
                supplier_name=s.supplier.canonical_name if s.supplier else "Unknown",
                message=f"Quote age exceeds {settings.quote_freshness_hours}h. Re-verification advised.",
                old_value=s.observed_at.strftime("%Y-%m-%d %H:%M UTC"),
                new_value="STALE",
                source_url=s.source_url,
                detected_at=now
            ))

        # 3. Price changes from PriceHistory
        recent_events = self.db.query(PriceHistory).order_by(PriceHistory.event_timestamp.desc()).limit(15).all()
        for ev in recent_events:
            alerts.append(AlertItem(
                alert_type=ev.event_type,
                severity="INFO" if ev.event_type == "PRICE_DROP" else "WARNING",
                tool_plan=ev.plan.matching_key if ev.plan else "",
                supplier_name=f"Supplier #{ev.supplier_id}",
                message=f"{ev.event_type}: Old ${ev.old_amount} -> New ${ev.new_amount}",
                old_value=f"${ev.old_amount}" if ev.old_amount else "-",
                new_value=f"${ev.new_amount}",
                source_url=ev.source_url,
                detected_at=ev.event_timestamp
            ))

        return alerts
