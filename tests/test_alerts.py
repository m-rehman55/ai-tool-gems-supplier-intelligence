"""
Unit tests for alert engine: high-risk detection, stale quotes, and price history events.
"""

from datetime import datetime, timezone, timedelta
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from src.models import (
    Base, Supplier, Product, ProductPlan, PriceObservation, PriceHistory,
    RiskLevel, VerificationStatus, AccessType
)
from src.alerts import AlertEngine

@pytest.fixture
def alert_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def test_detect_high_risk_alerts(alert_db):
    supplier = Supplier(canonical_name="Suspicious Seller", primary_handle="@suspicious_bot")
    product = Product(name="Autodesk", category="Design")
    alert_db.add_all([supplier, product])
    alert_db.commit()

    plan = ProductPlan(
        product_id=product.id,
        plan_name="All Apps 3 Years",
        duration="3 Years",
        access_type=AccessType.KEY,
        matching_key="autodesk__all_apps_3_years__3_years__key"
    )
    alert_db.add(plan)
    alert_db.commit()

    # Add high-risk observation
    obs = PriceObservation(
        supplier_id=supplier.id,
        plan_id=plan.id,
        amount=1.30,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=round(1.30 * 280.0, 2),
        original_raw_text="$1.30 Autodesk 3Y",
        risk_level=RiskLevel.HIGH,
        risk_flags="UNUSUALLY_CHEAP_EDUCATIONAL_CLAIM",
        warranty="No Warranty",
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        observed_at=datetime.now(timezone.utc)
    )
    alert_db.add(obs)
    alert_db.commit()

    engine = AlertEngine(alert_db)
    alerts = engine.scan_for_alerts()
    
    high_risk_alerts = [a for a in alerts if a.alert_type == "HIGH_RISK"]
    assert len(high_risk_alerts) == 1
    assert high_risk_alerts[0].severity == "WARNING"
    assert "Suspicious Seller" in high_risk_alerts[0].supplier_name
    assert "UNUSUALLY_CHEAP" in high_risk_alerts[0].message

def test_detect_stale_quote_alerts(alert_db):
    supplier = Supplier(canonical_name="Old Bot", primary_handle="@old_bot")
    product = Product(name="Coursera", category="Education")
    alert_db.add_all([supplier, product])
    alert_db.commit()

    plan = ProductPlan(
        product_id=product.id,
        plan_name="Plus 12M",
        duration="12 Months",
        access_type=AccessType.INVITE,
        matching_key="coursera__plus_12m__12_months__invite"
    )
    alert_db.add(plan)
    alert_db.commit()

    # Observation from 5 days ago (120 hours > 48 hours)
    old_date = datetime.now(timezone.utc) - timedelta(days=5)
    obs = PriceObservation(
        supplier_id=supplier.id,
        plan_id=plan.id,
        amount=3.50,
        currency="USD",
        fx_rate_used=280.0,
        amount_pkr=round(3.50 * 280.0, 2),
        original_raw_text="$3.50 Coursera Plus 12M",
        risk_level=RiskLevel.LOW,
        verification_status=VerificationStatus.USER_SUPPLIED_UNVERIFIED,
        observed_at=old_date
    )
    alert_db.add(obs)
    alert_db.commit()

    engine = AlertEngine(alert_db)
    alerts = engine.scan_for_alerts()
    
    stale_alerts = [a for a in alerts if a.alert_type == "STALE_QUOTE"]
    assert len(stale_alerts) == 1
    assert stale_alerts[0].severity == "INFO"
    assert "Old Bot" in stale_alerts[0].supplier_name
