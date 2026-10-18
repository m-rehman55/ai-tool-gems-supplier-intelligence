from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from src.models import Base, Supplier, ScanRunStatus
from src.research import classify_navigation_label, checkpoint, complete_checkpoint, finish_run, start_run


def test_purchase_boundary_is_never_a_safe_navigation_action():
    assert classify_navigation_label("Products")[0] is True
    assert classify_navigation_label("Next page")[0] is True
    assert classify_navigation_label("Buy now")[0] is False
    assert classify_navigation_label("Wallet top up")[0] is False


def test_runs_and_checkpoints_are_idempotent_and_resumable():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    db = sessionmaker(bind=engine)()
    try:
        supplier = Supplier(canonical_name="Configured Supplier", primary_handle="@configured")
        db.add(supplier)
        db.flush()
        run = start_run(db, "MT_PROTO", supplier.id)
        first = checkpoint(db, run, supplier.id, "MENU", "products", "Visible Products button")
        same = checkpoint(db, run, supplier.id, "MENU", "products", "Ignored duplicate")
        assert first.id == same.id
        complete_checkpoint(first, "Menu inspected")
        finish_run(run, ScanRunStatus.PARTIAL, "Session expired after one safe menu.", "SESSION_EXPIRED")
        db.commit()
        assert run.status == ScanRunStatus.PARTIAL
        assert first.status == "COMPLETED"
        assert run.error_category == "SESSION_EXPIRED"
    finally:
        db.close()
