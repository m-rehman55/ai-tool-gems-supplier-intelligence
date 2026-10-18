"""Durable, purchase-safe research job primitives.

Telegram client code may call this module, but this module performs no network
actions itself. It makes runs resumable and rejects navigation labels that
could create an order, reveal credentials, or commit payment.
"""

from datetime import datetime, timezone
from typing import Optional, Tuple

from src.models import ResearchCheckpoint, ResearchRun, ScanRunStatus


BLOCKED_ACTION_WORDS = {
    "buy", "order", "checkout", "payment", "pay now", "wallet", "top up",
    "confirm", "purchase", "deposit", "invoice", "transfer",
}


def classify_navigation_label(label: str) -> Tuple[bool, str]:
    """Return whether a visible button may be inspected without purchasing."""
    clean = " ".join((label or "").lower().split())
    if not clean:
        return False, "EMPTY_LABEL"
    if any(word in clean for word in BLOCKED_ACTION_WORDS):
        return False, "PURCHASE_BOUNDARY"
    return True, "SAFE_TO_INSPECT"


def start_run(db, access_method: str, supplier_id: Optional[int] = None) -> ResearchRun:
    run = ResearchRun(
        supplier_id=supplier_id,
        access_method=access_method,
        status=ScanRunStatus.RUNNING,
        suppliers_attempted=1 if supplier_id else 0,
    )
    db.add(run)
    db.flush()
    return run


def checkpoint(db, run: ResearchRun, supplier_id: int, checkpoint_type: str, navigation_token: str, detail: str = "") -> ResearchCheckpoint:
    existing = db.query(ResearchCheckpoint).filter_by(
        run_id=run.id,
        supplier_id=supplier_id,
        checkpoint_type=checkpoint_type,
        navigation_token=navigation_token,
    ).first()
    if existing:
        return existing
    item = ResearchCheckpoint(
        run_id=run.id,
        supplier_id=supplier_id,
        checkpoint_type=checkpoint_type,
        navigation_token=navigation_token,
        detail=detail,
    )
    db.add(item)
    db.flush()
    return item


def complete_checkpoint(item: ResearchCheckpoint, detail: Optional[str] = None) -> None:
    item.status = "COMPLETED"
    item.completed_at = datetime.now(timezone.utc)
    if detail is not None:
        item.detail = detail


def finish_run(run: ResearchRun, status: ScanRunStatus, summary: str = "", error_category: str = "", error_message: str = "") -> None:
    run.status = status
    run.finished_at = datetime.now(timezone.utc)
    run.summary = summary
    run.error_category = error_category
    run.error_message = error_message
