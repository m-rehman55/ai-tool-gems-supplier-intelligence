"""
Relational Database Models for Supplier Price Intelligence.
Defines 10 core entities with strict schema constraints, data types, and timestamps.
"""

import uuid
import hashlib
from datetime import datetime, timezone
from enum import Enum
from typing import Optional, List, Dict, Any
from sqlalchemy import (
    Column, Integer, String, Float, Boolean, DateTime, Text, ForeignKey,
    UniqueConstraint, Index, Enum as SQLEnum
)
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()

def utc_now() -> datetime:
    return datetime.now(timezone.utc)

class AccessType(str, Enum):
    PRIVATE = "PRIVATE"
    SHARED = "SHARED"
    TEAM = "TEAM"
    EDU = "EDU"
    KEY = "KEY"
    INVITE = "INVITE"
    PROMO_CODE = "PROMO_CODE"
    API = "API"
    UNKNOWN = "UNKNOWN"

class VerificationStatus(str, Enum):
    VERIFIED = "VERIFIED"
    UNVERIFIED = "UNVERIFIED"
    USER_SUPPLIED_UNVERIFIED = "USER_SUPPLIED_UNVERIFIED"
    STALE = "STALE"
    INCOMPLETE = "INCOMPLETE"
    REJECTED = "REJECTED"

class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class ResearchStatus(str, Enum):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    INTERACTIVE_MENU_REQUIRED = "INTERACTIVE_MENU_REQUIRED"
    BLOCKED = "BLOCKED"
    FAILED = "FAILED"

class Supplier(Base):
    __tablename__ = "suppliers"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    canonical_name = Column(String(100), nullable=False, unique=True)
    primary_handle = Column(String(100), nullable=False)
    all_handles = Column(Text, default="")  # comma-separated handles
    entity_type = Column(String(50), default="BOT")  # BOT, CHANNEL, ADMIN, SUPPORT
    public_url = Column(String(255), nullable=True)
    country_region = Column(String(100), default="Global")
    supported_currencies = Column(String(100), default="USD,USDT")
    payment_methods = Column(Text, default="")
    contact_roles = Column(Text, default="")
    access_status = Column(String(50), default="PUBLIC")  # PUBLIC, INTERACTIVE_MENU_REQUIRED, PRIVATE_INVITE
    reliability_score = Column(Float, default=50.0)  # 0 to 100
    risk_score = Column(Float, default=10.0)
    risk_flags = Column(Text, default="")
    evidence_links = Column(Text, default="")
    notes = Column(Text, default="")
    created_at = Column(DateTime(timezone=True), default=utc_now)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)
    last_checked_at = Column(DateTime(timezone=True), nullable=True)

    observations = relationship("PriceObservation", back_populates="supplier", cascade="all, delete-orphan")
    research_tasks = relationship("ResearchQueue", back_populates="supplier")

class Product(Base):
    __tablename__ = "products"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(100), nullable=False, unique=True)
    category = Column(String(100), nullable=False)
    publisher = Column(String(100), default="Unknown")
    product_family = Column(String(100), default="")
    licensing_status = Column(String(50), default="COMMERCIAL")
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), default=utc_now)
    
    plans = relationship("ProductPlan", back_populates="product", cascade="all, delete-orphan")

class ProductPlan(Base):
    __tablename__ = "product_plans"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    plan_name = Column(String(100), nullable=False)
    duration = Column(String(50), nullable=False)  # 1 Month, 12 Months, Lifetime
    units = Column(String(50), default="1")
    credits = Column(String(50), default="")
    seats = Column(Integer, default=1)
    access_type = Column(SQLEnum(AccessType), default=AccessType.PRIVATE)
    region_lock = Column(String(50), default="Global")
    activation_requirements = Column(Text, default="")
    official_restrictions = Column(Text, default="")
    matching_key = Column(String(200), nullable=False, unique=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), default=utc_now)
    
    product = relationship("Product", back_populates="plans")
    observations = relationship("PriceObservation", back_populates="plan", cascade="all, delete-orphan")
    history_events = relationship("PriceHistory", back_populates="plan", cascade="all, delete-orphan")

class PriceObservation(Base):
    __tablename__ = "price_observations"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    supplier_id = Column(Integer, ForeignKey("suppliers.id"), nullable=False)
    plan_id = Column(Integer, ForeignKey("product_plans.id"), nullable=True)
    unmatched_candidate_text = Column(String(255), nullable=True)
    
    amount = Column(Float, nullable=False)
    currency = Column(String(10), default="USD")
    original_raw_text = Column(Text, nullable=False)
    source_url = Column(String(255), nullable=True)
    source_snippet = Column(Text, default="")
    
    observed_at = Column(DateTime(timezone=True), default=utc_now)
    source_published_at = Column(DateTime(timezone=True), nullable=True)
    quote_expiry = Column(DateTime(timezone=True), nullable=True)
    
    warranty = Column(String(100), default="Not Stated")
    replacement_policy = Column(Text, default="")
    refund_policy = Column(Text, default="")
    delivery_estimate = Column(String(50), default="15-60 min")
    stock_status = Column(String(50), default="Available")
    payment_methods = Column(String(100), default="")
    
    # Financial fields
    fx_rate_used = Column(Float, nullable=False)
    fx_provider = Column(String(50), default="DefaultSettings")
    amount_pkr = Column(Float, nullable=False)
    
    verification_status = Column(SQLEnum(VerificationStatus), default=VerificationStatus.UNVERIFIED)
    confidence_score = Column(Float, default=50.0)
    risk_level = Column(SQLEnum(RiskLevel), default=RiskLevel.LOW)
    risk_flags = Column(Text, default="")
    capture_method = Column(String(50), default="MANUAL_IMPORT")  # PUBLIC_CRAWLER, MANUAL_IMPORT, CONVERSATION_SEED
    parser_version = Column(String(20), default="1.0.0")
    content_hash = Column(String(64), nullable=False, default=lambda: hashlib.sha256(uuid.uuid4().bytes).hexdigest())
    reviewer_notes = Column(Text, default="")

    supplier = relationship("Supplier", back_populates="observations")
    plan = relationship("ProductPlan", back_populates="observations")

    __table_args__ = (
        Index("idx_obs_plan_status", "plan_id", "verification_status"),
        Index("idx_obs_hash", "content_hash"),
    )

class PriceHistory(Base):
    __tablename__ = "price_history"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    plan_id = Column(Integer, ForeignKey("product_plans.id"), nullable=False)
    supplier_id = Column(Integer, ForeignKey("suppliers.id"), nullable=False)
    event_type = Column(String(50), nullable=False)  # NEW_QUOTE, PRICE_DROP, PRICE_INCREASE, WARRANTY_CHANGE, EXPIRED
    old_amount = Column(Float, nullable=True)
    new_amount = Column(Float, nullable=False)
    currency = Column(String(10), default="USD")
    old_warranty = Column(String(100), nullable=True)
    new_warranty = Column(String(100), nullable=True)
    source_url = Column(String(255), nullable=True)
    event_timestamp = Column(DateTime(timezone=True), default=utc_now)
    details = Column(Text, default="")

    plan = relationship("ProductPlan", back_populates="history_events")

class FxRate(Base):
    __tablename__ = "fx_rates"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    base_currency = Column(String(10), nullable=False)
    quote_currency = Column(String(10), default="PKR")
    rate = Column(Float, nullable=False)
    source_provider = Column(String(50), default="MANUAL")
    retrieved_at = Column(DateTime(timezone=True), default=utc_now)
    is_manual_override = Column(Boolean, default=False)
    status = Column(String(20), default="ACTIVE")

    __table_args__ = (
        UniqueConstraint("base_currency", "quote_currency", name="uq_fx_pair"),
    )

class RetailBenchmark(Base):
    __tablename__ = "retail_benchmarks"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    store_name = Column(String(100), nullable=False)  # aitoolspak.tech, etc.
    store_url = Column(String(255), nullable=True)
    matching_plan_key = Column(String(200), nullable=False)
    observed_price_pkr = Column(Float, nullable=False)
    evidence_snippet = Column(Text, default="")
    checked_at = Column(DateTime(timezone=True), default=utc_now)

class ResearchQueue(Base):
    __tablename__ = "research_queue"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    supplier_id = Column(Integer, ForeignKey("suppliers.id"), nullable=True)
    target_url = Column(String(255), nullable=False)
    status = Column(SQLEnum(ResearchStatus), default=ResearchStatus.PENDING)
    blocker_reason = Column(Text, default="")
    attempt_count = Column(Integer, default=0)
    last_attempt_at = Column(DateTime(timezone=True), nullable=True)
    retry_after = Column(DateTime(timezone=True), nullable=True)
    error_message = Column(Text, default="")
    next_action = Column(String(100), default="INSPECT_PUBLIC_URL")

    supplier = relationship("Supplier", back_populates="research_tasks")

class AuditLog(Base):
    __tablename__ = "audit_log"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    action_type = Column(String(50), nullable=False)  # IMPORT, VERIFICATION, PLAN_MERGE, CONFIG_UPDATE
    entity_name = Column(String(100), nullable=False)
    entity_id = Column(String(50), nullable=True)
    actor = Column(String(50), default="SYSTEM")
    details = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now)

class AppSetting(Base):
    __tablename__ = "app_settings"
    
    key = Column(String(100), primary_key=True)
    value = Column(Text, nullable=False)
    description = Column(String(255), default="")
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)
