"""
Database management module for SQLite persistence.
Provides session factories, schema initialization, and transactional safety.
"""

import shutil
from datetime import datetime, timezone
from pathlib import Path
from contextlib import contextmanager
from typing import Generator, Optional
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

from src.config import settings
from src.models import Base, FxRate, AppSetting

engine = create_engine(
    f"sqlite:///{settings.database_path}",
    echo=False,
    connect_args={"check_same_thread": False}
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def init_db() -> None:
    """Creates all tables and seeds default FX rates and settings if empty."""
    settings.ensure_directories()
    Base.metadata.create_all(bind=engine)
    
    with get_db() as db:
        # Seed default FX rates if missing
        if not db.query(FxRate).filter_by(base_currency="USD", quote_currency="PKR").first():
            db.add(FxRate(
                base_currency="USD",
                quote_currency="PKR",
                rate=settings.default_usd_to_pkr,
                source_provider="DEFAULT_SETTINGS",
                retrieved_at=datetime.now(timezone.utc),
                is_manual_override=False,
                status="ACTIVE"
            ))
        if not db.query(FxRate).filter_by(base_currency="USDT", quote_currency="PKR").first():
            db.add(FxRate(
                base_currency="USDT",
                quote_currency="PKR",
                rate=settings.default_usdt_to_pkr,
                source_provider="DEFAULT_SETTINGS",
                retrieved_at=datetime.now(timezone.utc),
                is_manual_override=False,
                status="ACTIVE"
            ))
        if not db.query(FxRate).filter_by(base_currency="EUR", quote_currency="PKR").first():
            db.add(FxRate(
                base_currency="EUR",
                quote_currency="PKR",
                rate=settings.default_eur_to_pkr,
                source_provider="DEFAULT_SETTINGS",
                retrieved_at=datetime.now(timezone.utc),
                is_manual_override=False,
                status="ACTIVE"
            ))
            
        # Seed core settings
        default_settings = {
            "TARGET_PROFIT_MIN_PKR": str(settings.target_profit_min_pkr),
            "TARGET_PROFIT_MAX_PKR": str(settings.target_profit_max_pkr),
            "PAYMENT_NETWORK_FEE_PCT": str(settings.payment_network_fee_pct),
            "WARRANTY_RESERVE_PCT": str(settings.warranty_reserve_pct),
            "QUOTE_FRESHNESS_HOURS": str(settings.quote_freshness_hours),
            "SCHEMA_VERSION": "1.0.0"
        }
        for k, v in default_settings.items():
            if not db.query(AppSetting).filter_by(key=k).first():
                db.add(AppSetting(key=k, value=v, description=f"Default {k} setting"))
                
        db.commit()

@contextmanager
def get_db() -> Generator[Session, None, None]:
    """Context manager for transactional database operations."""
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()

def backup_database(destination: Optional[Path] = None) -> Path:
    """Creates a timestamped snapshot of the SQLite database."""
    settings.backup_dir.mkdir(parents=True, exist_ok=True)
    if destination is None:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        destination = settings.backup_dir / f"supplier_intelligence_backup_{timestamp}.db"
    
    if settings.database_path.exists():
        shutil.copy2(settings.database_path, destination)
    return destination
