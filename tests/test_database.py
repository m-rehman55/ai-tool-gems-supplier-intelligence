"""
Unit tests for SQLite database schema, persistence, conflicting quote preservation, and audit trail.
"""

from datetime import datetime, timezone
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from src.models import (
    Base, Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    AccessType, VerificationStatus
)

@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def test_preserve_conflicting_observations(db_session):
    """
    Ensures that conflicting observations for the same supplier and plan
    are both preserved without overwriting history.
    """
    supplier = Supplier(
        canonical_name="AI Toolify",
        primary_handle="@aitoolify_bot",
        entity_type="BOT"
    )
    product = Product(name="Gemini Pro", category="AI Assistant")
    db_session.add_all([supplier, product])
    db_session.commit()

    plan = ProductPlan(
        product_id=product.id,
        plan_name="18 Months",
        duration="18 Months",
        access_type=AccessType.INVITE,
        matching_key="gemini_pro__18_months__18_months__invite"
    )
    db_session.add(plan)
    db_session.commit()

    # Claim 1: $0.42
    obs1 = PriceObservation(
        supplier_id=supplier.id,
        plan_id=plan.id,
        amount=0.42,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=round(0.42 * 280.0, 2),
        original_raw_text="$0.42 Gemini Pro 18M",
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        observed_at=datetime.now(timezone.utc)
    )
    # Claim 2: $0.45
    obs2 = PriceObservation(
        supplier_id=supplier.id,
        plan_id=plan.id,
        amount=0.45,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=round(0.45 * 280.0, 2),
        original_raw_text="$0.45 Gemini Pro 18M",
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        observed_at=datetime.now(timezone.utc)
    )

    db_session.add_all([obs1, obs2])
    db_session.commit()

    # Query all observations for this plan
    observations = db_session.query(PriceObservation).filter_by(plan_id=plan.id).all()
    assert len(observations) == 2
    amounts = {o.amount for o in observations}
    assert 0.42 in amounts
    assert 0.45 in amounts

def test_distinct_plan_keys(db_session):
    """Ensures 1-Month Private and 2-Year Edu are distinct plan rows."""
    product = Product(name="ChatGPT", category="AI Chatbot")
    db_session.add(product)
    db_session.commit()

    plan1 = ProductPlan(
        product_id=product.id,
        plan_name="Plus Private",
        duration="1 Month",
        access_type=AccessType.PRIVATE,
        matching_key="chatgpt__plus_private__1_month__private"
    )
    plan2 = ProductPlan(
        product_id=product.id,
        plan_name="Plus Edu",
        duration="2 Years",
        access_type=AccessType.EDU,
        matching_key="chatgpt__plus_edu__2_years__edu"
    )
    db_session.add_all([plan1, plan2])
    db_session.commit()

    plans = db_session.query(ProductPlan).filter_by(product_id=product.id).all()
    assert len(plans) == 2
    keys = {p.matching_key for p in plans}
    assert "chatgpt__plus_private__1_month__private" in keys
    assert "chatgpt__plus_edu__2_years__edu" in keys
