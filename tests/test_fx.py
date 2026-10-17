"""
Unit tests for Foreign Exchange (FX) engine, USD vs USDT disaggregation, and staleness.
"""

from datetime import datetime, timezone, timedelta
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from src.models import Base, FxRate
from src.fx import FxEngine

@pytest.fixture
def in_memory_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def test_fx_defaults_and_usdt_disaggregation():
    engine = FxEngine(db_session=None)
    usd_rate, _, _, _ = engine.get_rate("USD", "PKR")
    usdt_rate, _, _, _ = engine.get_rate("USDT", "PKR")
    
    # Must never be treated as strictly identical without distinction
    assert usd_rate == 280.0
    assert usdt_rate == 282.5
    assert usd_rate != usdt_rate

def test_identical_currency():
    engine = FxEngine(db_session=None)
    rate, provider, _, is_stale = engine.get_rate("PKR", "PKR")
    assert rate == 1.0
    assert provider == "IDENTICAL_CURRENCY"
    assert is_stale is False

def test_fx_staleness_detection(in_memory_db):
    old_time = datetime.now(timezone.utc) - timedelta(hours=30)
    in_memory_db.add(FxRate(
        base_currency="EUR",
        quote_currency="PKR",
        rate=305.0,
        source_provider="TEST_SOURCE",
        retrieved_at=old_time,
        status="ACTIVE"
    ))
    in_memory_db.commit()

    engine = FxEngine(db_session=in_memory_db)
    rate, provider, _, is_stale = engine.get_rate("EUR", "PKR")
    assert rate == 305.0
    assert provider == "TEST_SOURCE"
    assert is_stale is True  # Older than 24h

def test_fx_manual_override(in_memory_db):
    engine = FxEngine(db_session=in_memory_db)
    engine.update_rate("USD", 285.0, provider="USER_MANUAL_OVERRIDE", is_override=True)
    
    rate, provider, _, is_stale = engine.get_rate("USD", "PKR")
    assert rate == 285.0
    assert provider == "USER_MANUAL_OVERRIDE"
    assert is_stale is False
