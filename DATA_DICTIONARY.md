# DATA DICTIONARY & EXACT-MATCH RULES

**System:** AI Tool Gems — Supplier Price Intelligence Agent  
**Database:** SQLite (`supplier_intelligence.db`)  
**Persistent Export:** `master_catalog.xlsx`

---

## 1. Core Entities & Schema Definitions

### 1.1 `suppliers`
| Field | Type | Description |
|---|---|---|
| `id` | Integer (PK) | Unique sequential ID |
| `canonical_name` | String(100) | Standardized business/store name (e.g., "DigiCart Tools") |
| `primary_handle` | String(100) | Primary Telegram handle or username (e.g., `@getdigitaltools_bot`) |
| `all_handles` | Text | Comma-separated list of associated bots, channels, and support admins |
| `entity_type` | String(50) | `BOT`, `CHANNEL`, `ADMIN`, `SUPPORT`, or `COMMUNITY` |
| `public_url` | String(255) | Public channel or preview link (`https://t.me/s/...` or `https://t.me/...`) |
| `country_region` | String(100) | Evidenced operational region (e.g., Pakistan, Bangladesh, Vietnam, Global) |
| `supported_currencies` | String(100) | Accepted currencies (e.g., `USD,USDT,PKR,UPI`) |
| `payment_methods` | Text | Evidenced payment channels (e.g., `EasyPaisa,JazzCash,Binance Pay,Crypto`) |
| `access_status` | String(50) | `PUBLIC`, `INTERACTIVE_MENU_REQUIRED`, `PRIVATE_INVITE` |
| `reliability_score` | Float | 0 to 100 historical score based on verified delivery and warranty honor |
| `risk_score` | Float | 0 to 100 risk penalty score |
| `risk_flags` | Text | Active warning tags |
| `created_at` / `updated_at` | DateTime | Timestamps with UTC timezone |

---

### 1.2 `products`
| Field | Type | Description |
|---|---|---|
| `id` | Integer (PK) | Primary key |
| `name` | String(100) | Canonical tool name (e.g., "ChatGPT", "Google Gemini") |
| `category` | String(100) | Functional grouping (e.g., `AI Assistant`, `Video Generation`, `SEO & Data`) |
| `publisher` | String(100) | Underlying creator/company (e.g., OpenAI, Google, JetBrains) |
| `licensing_status` | String(50) | `COMMERCIAL`, `EDUCATIONAL`, `RETAIL` |
| `is_active` | Boolean | Catalog inclusion flag |

---

### 1.3 `product_plans`
| Field | Type | Description |
|---|---|---|
| `id` | Integer (PK) | Primary key |
| `product_id` | Integer (FK) | Reference to `products.id` |
| `plan_name` | String(100) | Specific tier/edition (e.g., "Plus Private", "Pro 18M Invitation") |
| `duration` | String(50) | Standardized time window (e.g., `1 Month`, `12 Months`, `Lifetime`) |
| `units` | String(50) | Quantity unit (e.g., `Account`, `Profile`, `Credits`) |
| `credits` | String(50) | Credit allowance if applicable (e.g., `1000 credits`, `8.5k credits`) |
| `seats` | Integer | Allowed concurrent users/devices |
| `access_type` | Enum | `PRIVATE`, `SHARED`, `TEAM`, `EDU`, `KEY`, `INVITE`, `PROMO_CODE`, `API` |
| `region_lock` | String(50) | `Global`, `US Only`, `India Only`, etc. |
| `matching_key` | String(200) | **Immutable unique composite key** (see Section 3) |

---

### 1.4 `price_observations`
| Field | Type | Description |
|---|---|---|
| `id` | Integer (PK) | Primary key |
| `supplier_id` | Integer (FK) | Reference to `suppliers.id` |
| `plan_id` | Integer (FK) | Reference to matched `product_plans.id` |
| `amount` | Float | Quoted price amount in source currency |
| `currency` | String(10) | Source currency code (`USD`, `USDT`, `PKR`, `EUR`) |
| `original_raw_text` | Text | Exact unedited snippet or post quote text |
| `source_url` | String(255) | Direct verifiable post or channel link |
| `observed_at` | DateTime (UTC) | When the quote was recorded |
| `quote_expiry` | DateTime (UTC) | Stated expiration time from post if specified |
| `warranty` | String(100) | Replacement guarantee (e.g., `Full Warranty`, `30 Days`, `No Warranty`) |
| `delivery_estimate` | String(50) | Stated delivery timeline (e.g., `Instant`, `15-60 min`) |
| `fx_rate_used` | Float | Exact FX rate applied at calculation time |
| `amount_pkr` | Float | Converted landed cost in PKR |
| `verification_status` | Enum | See Section 2 below |
| `confidence_score` | Float | 0 to 100 confidence score |
| `risk_level` | Enum | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `content_hash` | String(64) | SHA-256 hash preventing duplicate identical posts |

---

## 2. Verification Status Definitions

1. **`VERIFIED`**:
   - The quote has been directly observed via an inspectable public link or a verified transaction screenshot.
   - Price, plan tier, duration, warranty, and stock status are confirmed current within the configured freshness window (default: 48 hours).
2. **`USER_SUPPLIED_UNVERIFIED`**:
   - Historical claim from earlier conversation or notes.
   - Preserved with full provenance but disqualified from winning "Cheapest Verified" until live proof is recorded.
3. **`UNVERIFIED`**:
   - Detected candidate or manual draft awaiting confirmation.
4. **`STALE`**:
   - Previously verified observation whose age exceeds the freshness threshold (>48 hours) without re-verification.
5. **`INCOMPLETE`**:
   - Missing critical plan parameters (e.g. unknown duration or unspecified warranty).
6. **`REJECTED`**:
   - Proven fraudulent, inactive, or terms-violating offer.

---

## 3. Exact-Match Matching Key Rules

Unlike offers must never be merged into a single comparison bucket. To guarantee genuine comparability, every exact plan generates an immutable key:

$$\text{matching\_key} = \text{product\_slug} \ \Vert\ \text{plan\_slug} \ \Vert\ \text{duration\_slug} \ \Vert\ \text{access\_type}$$

### Key Components:
- **Product Slug:** Standardized lowercase alphanumeric product name (`chatgpt`, `google_gemini`).
- **Plan Slug:** Specific plan tier (`chatgpt_plus`, `all_apps_3_years`).
- **Duration Slug:** Standardized time interval (`1_month`, `12_months`, `lifetime`).
- **Access Type:** Strict access model (`private`, `shared`, `edu`, `key`, `invite`).

### Prohibited Merges:
- ❌ ChatGPT Plus 1 Month Private $\neq$ ChatGPT Plus Edu 2 Years
- ❌ Netflix 1-Month Private $\neq$ Netflix 3-Month Shared Slot
- ❌ Leonardo AI 8.5k credits $\neq$ Leonardo AI Unlimited
- ❌ Proton VPN 1 Year $\neq$ Proton VPN Lifetime

---

## 4. Lowest Verified Winner Algorithm

An offer qualifies as **Lowest Verified Comparable Winner** if and only if ALL of the following criteria are satisfied:
1. Exact matching key matches the queried product plan.
2. `verification_status == VERIFIED`.
3. Quote age $\le$ freshness threshold (default 48h).
4. `risk_level` does NOT exceed the allowed threshold (`LOW` or `MEDIUM` only; `HIGH` and `CRITICAL` offers are excluded).
5. Warranty terms are explicitly stated (offers with `No Warranty` or unstated warranty are disqualified).
6. Stock status is `Available` or `In Stock`.

If no quote satisfies all six criteria, the engine returns **`No verified winner yet`** with reasons, displaying the closest unverified candidates as pending leads.
