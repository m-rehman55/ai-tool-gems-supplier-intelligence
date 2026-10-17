"""
Configuration module for Supplier Price Intelligence Agent.
Handles environment variables, default FX rates, cost/margin parameters, and paths.
"""

import os
from pathlib import Path
from dataclasses import dataclass, field

BASE_DIR = Path(__file__).resolve().parent.parent

@dataclass
class Settings:
    app_env: str = os.getenv("APP_ENV", "development")
    app_port: int = int(os.getenv("APP_PORT", "8501"))
    
    # Storage Paths
    database_path: Path = field(default_factory=lambda: BASE_DIR / os.getenv("DATABASE_PATH", "data/supplier_intelligence.db"))
    excel_path: Path = field(default_factory=lambda: BASE_DIR / os.getenv("EXCEL_PATH", "master_catalog.xlsx"))
    backup_dir: Path = field(default_factory=lambda: BASE_DIR / "data" / "backups")
    seed_dir: Path = field(default_factory=lambda: BASE_DIR / "data" / "seed")
    
    # FX Rate Defaults (PKR Benchmarks)
    default_usd_to_pkr: float = float(os.getenv("DEFAULT_USD_TO_PKR", "280.0"))
    default_usdt_to_pkr: float = float(os.getenv("DEFAULT_USDT_TO_PKR", "282.5"))
    default_eur_to_pkr: float = float(os.getenv("DEFAULT_EUR_TO_PKR", "305.0"))
    
    # Landed Cost & Profit Margin Policy
    target_profit_min_pkr: float = float(os.getenv("TARGET_PROFIT_MIN_PKR", "400.0"))
    target_profit_max_pkr: float = float(os.getenv("TARGET_PROFIT_MAX_PKR", "800.0"))
    payment_network_fee_pct: float = float(os.getenv("PAYMENT_NETWORK_FEE_PCT", "0.015"))  # 1.5%
    warranty_reserve_pct: float = float(os.getenv("WARRANTY_REPLACEMENT_RESERVE_PCT", "0.03"))  # 3%
    
    # Freshness & Staleness Policy (Hours)
    quote_freshness_hours: int = int(os.getenv("QUOTE_FRESHNESS_HOURS", "48"))
    stale_threshold_hours: int = int(os.getenv("STALE_THRESHOLD_HOURS", "96"))
    
    # Public Crawler Policy
    max_requests_per_minute: int = int(os.getenv("MAX_REQUESTS_PER_MINUTE", "20"))
    user_agent: str = os.getenv(
        "USER_AGENT",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    def ensure_directories(self) -> None:
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        self.backup_dir.mkdir(parents=True, exist_ok=True)
        self.seed_dir.mkdir(parents=True, exist_ok=True)

settings = Settings()
settings.ensure_directories()
