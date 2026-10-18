# SECURITY & COMPLIANCE POLICY — Supplier Price Intelligence

**Target Organization:** AI Tool Gems Pakistan (`aitoolgems.tech`)  
**Repository:** `m-rehman55/ai-tool-gems-supplier-intelligence` (PUBLIC Source Code)  
**Classification:** Confidential Operational Data Protected On-Device

---

## 1. Repository Visibility & Data Boundary

- **Public Source Code Repository:**
  - The source code is maintained publicly on GitHub to allow external tooling and audit review (e.g. ChatGPT, CI integrations).
  - **Zero Secrets Rule:** `.env`, `*.session`, local SQLite databases, customer records, and private wholesale matrices must **NEVER** be committed to Git.
  - `.gitignore` explicitly excludes:
    - `.env` and local credentials
    - `*.session` and MTProto auth databases
    - `data/*.db` runtime SQLite files
    - Temporary cache and debug token exports

---

## 2. On-Device Credentials & Encryption Policy

- **Fail-Closed Keystore Protection:**
  - Android credentials (`apiId`, `apiHash`, `phoneNumber`) are encrypted using `androidx.security.crypto.EncryptedSharedPreferences` backed by the Android Hardware Keystore (`AES256_GCM_SPEC`).
  - If hardware keystore encryption initialization fails on a physical device, the app **fails closed** and refuses to store credentials in plaintext.
- **Phone Number Masking:**
  - In the Android UI, user phone numbers are masked (`+81****5678`) to prevent accidental leaks in screenshots, video recordings, or presentations.
- **Telegram Session & TDLib Architecture:**
  - Standard Bot API tokens cannot query seller bots.
  - Interactive bot crawling requires authorized account interaction.
  - If native TDLib C++ binaries (`libtdjni.so`) are not compiled for the device ABI, the system remains in a documented `UNAUTHENTICATED / INTEGRATION_BLOCKED` state and never fabricates session tokens.

---

## 3. Strict Safety Boundary (Fail-Closed)

The scanner enforces automated safety stops to prevent any unintended financial transactions:
1. **Catalog Allowlist (SAFE):**
   - Only recognized product navigation actions (`Products`, `Shop`, `Catalog`, `Menu`, `Tools`, `Next`, `Previous`, `View Details`) are executed.
2. **Blocked Purchase Boundary (BLOCKED_PURCHASE_RISK):**
   - Keywords such as `Buy`, `Purchase`, `Checkout`, `Order`, `Pay`, `Payment`, `Wallet`, `Deposit`, `Top up`, `Invoice`, `Transfer`, `Cart`, `Subscribe`, `Activate`, `Renew`, and `Confirm` are strictly intercepted and blocked.
3. **Ambiguous Actions (MANUAL_REVIEW_REQUIRED):**
   - Any unknown button label or opaque callback data fails closed to `MANUAL_REVIEW_REQUIRED`.

---

## 4. Telegram API Terms Compliance

- In compliance with Telegram API Terms of Service, data discovered through authorized sessions or public channels is used exclusively for internal wholesale price comparison.
- Scraped message data is **never used to train or fine-tune AI or Machine Learning models**.
