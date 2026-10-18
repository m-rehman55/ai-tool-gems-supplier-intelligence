# SECURITY & COMPLIANCE POLICY — Supplier Price Intelligence

**Target Organization:** AI Tool Gems Pakistan (`aitoolgems.tech`)  
**Repository:** `m-rehman55/ai-tool-gems-supplier-intelligence`  
**Classification:** STRICTLY CONFIDENTIAL & PRIVATE (Wholesale Cost Data)

---

## 1. Absolute Separation from Public Storefront

- **Public Repository (`m-rehman55/ai-tool-gems`):**
  - Contains customer-facing static storefront code only.
  - **NEVER** push wholesale prices, supplier names, Telegram handles, margin calculators, or internal notes to the public storefront repository.
  - No wholesale spreadsheets or procurement code should ever be committed to the public repo.
- **Private Repository (`m-rehman55/ai-tool-gems-supplier-intelligence`):**
  - Dedicated strictly to procurement intelligence, wholesale price history, supplier comparison, and landed-cost calculation.
  - Must remain **100% Private** on GitHub at all times.

---

## 2. Secrets & Credential Protection

- **No Credentials in Git:**
  - API keys, database credentials, bot tokens, and user credentials must never be committed to Git.
  - `.gitignore` explicitly blocks `.env`, `*.db`, `*.session`, logs, and temporary cache directories.
- **`.env.example` as Blueprint:**
  - Contains placeholders only for runtime configuration.
- **Zero Telegram User Session Scraping by Default:**
  - Telethon / MTProto user-session connectors are disabled.
  - No personal account session files (`.session`) are stored or used.
  - If a user session connector is evaluated in the future, it requires explicit approval and local OS secret storage—never committed to GitHub.

---

## 3. Telegram API & Bot Limitations

- **Bot API Tokens Cannot Inspect Other Bots:**
  - A Telegram bot token created via `@BotFather` **cannot** read messages, chats, or menus of third-party seller bots (e.g. `@getdigitaltools_bot`, `@sellnitystorebot`).
  - Attempting to query another bot via standard Bot API returns 404/permission errors.
- **Research Queue for Interactive Bots:**
  - Bots requiring `/start` or interactive menu clicks are safely routed to the **Research Queue** with the status `INTERACTIVE_MENU_REQUIRED`.
  - The system will never fabricate an automated bot scrap or fake quote.

### Local authorized user sessions

- The optional Telethon crawler is disabled until `ALLOW_TELETHON_USER_SESSION=1`
  is set locally by the owner.
- Use only the owner's own Telegram API credentials and session on a trusted
  machine. Session files remain under `data/`, are Git-ignored, and must never
  be copied to CI artifacts, cloud backups, screenshots, or the public repo.
- The crawler must stop before Buy, Order, Checkout, payment, wallet, or
  confirmation controls. A visible product-detail record is sufficient for
  research.
- Run `scripts/telegram_diagnostics.py` before enabling a session. Its output
  reports configuration state only and never prints secret values.

---

## 4. Licensing & Risk Assessment Rules

The procurement engine enforces automated risk flags to protect the business and customers:
1. **Unusually Cheap Offers:**
   - Any pricing below 15% of retail without credible B2B volume proof is flagged `UNUSUALLY_CHEAP_EDUCATIONAL_CLAIM` or `HIGH_RISK`.
2. **Account Sharing & Slot Violations:**
   - Shared accounts, profiles, slots (e.g., Netflix slots, single-profile access) are flagged for terms-of-service compliance risk.
3. **Student / Edu Eligibility Claims:**
   - "Edu" or student licenses (e.g. JetBrains, ChatGPT Edu) are marked `RISK_LEVEL: HIGH` to ensure strict eligibility verification before purchase.
4. **"Lifetime" Recurring SaaS Claims:**
   - Recurring software claimed as "Lifetime" is flagged `LIFETIME_AMBIGUITY` and excluded from verified winner status unless officially certified.
5. **No Warranty / Short Warranty:**
   - Offers with zero warranty or less than 24-hour warranty are excluded from recommended retail recommendations.

---

## 5. Free-First & Zero Paid API Rule

- FX rates are fetched from free open currency endpoints or fallback defaults with manual admin override.
- Scraping uses strictly public web preview endpoints (`https://t.me/s/<channel>`) with rate limits (1 req/sec) and local caching (4 hours).
- No paid proxies, paid scraper APIs, or billed LLM endpoints are enabled.
