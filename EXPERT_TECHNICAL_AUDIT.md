# Expert Technical Audit — AI Tool Gems Supplier Intelligence

**Audit date:** 18 October 2026  
**Repository:** `m-rehman55/ai-tool-gems-supplier-intelligence`  
**Branch inspected:** `main`  
**Repository visibility at audit time:** **Public**  
**Scope:** Actual default-branch files for the Android app, Python crawler, price extraction/matching, persistence, secrets handling, tests, documentation, and the latest visible Android CI run.

## Executive verdict

The repository has a real Android project, a local catalog database, price-extraction and comparison logic, a Python/Telethon research script, and an Android CI workflow. The latest visible Android workflow for commit `c03bcd6b4b8367f6f9c1e0b52ddc5899e7623119` passed its test/build/artifact steps and uploaded a debug APK.

However, **the current APK is not a fully working authenticated Telegram bot-shop crawler**. The most critical blocker is that the Android login dialog simulates authentication. The Android scanner itself makes HTTP requests to public Telegram channel-preview pages; it does not connect through the user’s Telegram account, click inline bot menus, or open every product detail. Seed/cache fallback and verification labels then make the user-facing result more confident than the evidence supports.

A green Gradle build is useful, but it does not prove the real Telegram login flow, supplier access, menu navigation, catalog completeness, or current price accuracy.

## Findings, evidence, and proper fixes

### P0 — Telegram login is simulated, not real authentication

**Files:** `android/app/src/main/java/tech/aitoolgems/supplierpricefinder/MainActivity.kt`, method `showTelegramConfigDialog()`.

**Observed behavior:** The “Request Telegram Login Code” click handler does not call Telegram or TDLib. It immediately displays “code requested.” “Verify & Connect” accepts any non-empty text and stores a generated string beginning `AUTHORIZED_MTPROTO_SESSION_...`; it then reports the account as connected. No Telegram authentication code is verified and no account session is created.

**Why it matters:** This is a release-blocking correctness and trust defect. It can falsely indicate the user’s account is connected while the scanner is still just using public HTTP previews.

**Proper fix:** Implement the official Telegram TDLib authorization-state flow. Handle TDLib initialization, phone-number request, login-code verification, two-step password when requested, logout, cancellation, reconnect, and recoverable errors. Show “Connected” only after TDLib reports `authorizationStateReady`; never fabricate a session token or equate saved API credentials with authenticated access. Add unit tests for state transitions and at least one owner-run end-to-end device test with a real authorized account.

Official references: [TDLib Getting Started — user authorization](https://core.telegram.org/tdlib/getting-started), [authorizationStateReady](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1authorization_state_ready.html).

### P0 — Android scanner does not inspect authenticated bot menus

**File:** `android/app/src/main/java/tech/aitoolgems/supplierpricefinder/telegram/TelegramScanner.kt`.

**Observed behavior:** It requests `https://t.me/s/<handle>` using OkHttp, parses page HTML, and never uses the saved credentials or an authenticated Telegram client. It does not send `/start`, receive real bot updates, click inline callbacks, page through menus, or inspect each product detail. Most interactive bot shops are not represented by public channel-preview HTML.

**Proper fix:** Implement a real TDLib-backed scanner as a separate adapter; retain public preview parsing as a distinct source type. Enumerate the owner’s Telegram dialogs locally, but keep scanning limited to explicitly approved supplier bots/channels and transparent settings. For each approved bot, run a resumable state machine: inspect visible safe menus, open Products/Shop/Catalog, paginate, open each safe product detail, extract structured fields, save source evidence, and checkpoint progress. Unsupported, inaccessible, rate-limited, or ambiguous bots must become `MANUAL_REVIEW_REQUIRED`; never claim they were scanned.

Only inspect the authorized account’s accessible chats. Do not try to bypass invite, membership, access-control, or bot restrictions. Never click buy/order/checkout/pay/wallet/deposit/confirmation actions.

### P0 — Seeded and cached prices can be presented as verified/live

**Files:** `android/app/src/main/java/tech/aitoolgems/supplierpricefinder/data/local/SeedObservations.kt`, `telegram/TelegramScanner.kt`, and `MainActivity.kt`.

**Observed behavior:** The bundled `CANONICAL_SEED_OBSERVATIONS` contain `VERIFIED` labels and old fixed timestamps. If a live public-page fetch fails or returns no parsed offer, the scanner falls back to stored observations. The UI can then say “Scan complete. Found … verified quote(s)” even though the current supplier was not successfully inspected. Newly parsed public-preview data is also marked `VERIFIED` merely because API ID/hash values are configured; that is not valid verification evidence.

**Proper fix:** All seeds/imports start as `UNVERIFIED` or `STALE` according to evidence age. Credentials configured ≠ authenticated, public post ≠ verified stock, and an inaccessible bot ≠ out of stock. Keep cached results in a separate visual section with original source, observation time, age, and status. Do not use cached records as the live scan outcome. Award `VERIFIED` only under a documented evidence policy and preserve source-message/post link, evidence timestamp, plan attributes, and reviewer decision. Keep price history append-only; an updated quote must not erase its preceding observation.

### P1 — “No results” and scan errors are conflated

**File:** `telegram/TelegramScanner.kt`.

**Observed behavior:** HTTP errors and exceptions are swallowed; after a failure the scanner may use cached data and still show a normal completion/no-stock message. `isRunning` is set to true and reset only at the normal end; an uncaught exception can leave it stuck true. The HTTP response is not closed with a `use` block.

**Proper fix:** Use explicit statuses such as `SCANNED_WITH_RESULTS`, `SCANNED_NO_PRICE`, `MANUAL_REVIEW_REQUIRED`, `AUTH_REQUIRED`, `NETWORK_ERROR`, `HTTP_ERROR`, and `RATE_LIMITED`. Store a sanitized error class/message and retry eligibility. Use `try/finally` for the running state and close every OkHttp `Response`. Add timeout, bounded retry/backoff, cancellation, and a per-supplier result summary. Do not report “out of stock” unless the source explicitly says so.

### P1 — Supplier-level crawl button scans the full directory

**File:** `MainActivity.kt`, `loadSuppliersTab()`.

**Observed behavior:** The supplier-specific “Crawling …” action calls `performLiveScan(supplier.canonicalName)`, while `TelegramScanner.scanSuppliersForTool()` loops over every enabled supplier and treats the argument as a product-name query. This does not actually target just the selected supplier.

**Proper fix:** Add a scanner entry point with an explicit `supplierId`/handle filter; share the same scan implementation between “scan one supplier” and “scan all approved suppliers.” Test that a one-supplier run makes no request to any other source and updates only the selected supplier’s `last_checked_at`.

### P1 — New observations can be dropped by a transient ID collision

**File:** `telegram/TelegramScanner.kt`.

**Observed behavior:** Fresh `PriceObservation` objects default to `id = 0`; the duplicate check compares IDs before comparing a plan key. Thus, once one transient row with ID 0 is in the in-memory results, another new row with ID 0 may be treated as a duplicate even if it belongs to another supplier/product.

**Proper fix:** Deduplicate using a stable key that includes supplier identity, canonical plan key, and source message/post ID (or a content hash when source IDs are unavailable). Persist the observation first and then use the database-generated ID. Add tests for multiple suppliers, same price, same plan, updated price, and duplicate replays.

### P1 — Currency conversion can produce incorrect comparisons

**File:** `telegram/TelegramScanner.kt`; `engine/PriceExtractor.kt`.

**Observed behavior:** The scanner uses the USDT rate only for USDT and otherwise applies USD/PKR to every currency. A source price already in PKR will be multiplied by the USD rate; EUR/INR have extraction labels but no validated conversion path. Fixed rates are embedded in scanner code and are not timestamped as FX quotes.

**Proper fix:** Route by explicit currency: PKR multiplier 1; USD and USDT with separate configured rates; other currencies only with a fresh known rate, otherwise mark the PKR estimate unavailable. Store original amount/currency, FX rate, rate timestamp/provider, computed amount, and whether it is an estimate. Never compare a converted estimate as an official exact cost without showing its rate timestamp. Move rate configuration out of hard-coded scanner constants.

### P1 — Price extraction is too permissive and can read the wrong number

**File:** `engine/PriceExtractor.kt`.

**Observed behavior:** The single loose regex can accidentally select a quantity, duration, discount percentage, old price, or another unrelated number in a multi-product/multi-price message. In the flash-sale arrow special case, it labels the detected sale amount as USDT even when the source might be USD. If the text mentions `USDT` anywhere, the generic extraction path may assign USDT to a different price.

**Proper fix:** Parse structured product-detail text where available; otherwise extract a price candidate together with its local currency marker and surrounding line/context. For ambiguous or multi-price messages, return `INCOMPLETE`/`MANUAL_REVIEW_REQUIRED`, not a guessed value. Distinguish old price, sale price, discounted price, units/credits, and fee. Add adversarial test fixtures for PKR, Rs, USD, USDT, EUR, INR, price ranges, discount percentages, quantities, and multiple products per message.

### P1 — The comparator does not guarantee like-for-like equivalence

**Files:** `MainActivity.kt`, `engine/PlanMatcher.kt`, and `engine/WinnerEngine.kt`.

**Observed behavior:** The search result can contain multiple matching plans, then `WinnerEngine.evaluatePlan()` sorts all received observations by PKR without enforcing a single canonical plan group. A 1-month shared plan can compete with a 12-month private plan if the search matches both. Unknown duration defaults to `1 Month` and unknown access type defaults to `PRIVATE`, creating false equivalence.

**Proper fix:** Return grouped results by canonical plan identity. Compare only identical product/edition, duration, access model, seat/slot count, region, renewal type, and relevant warranty/eligibility terms. Use `UNKNOWN` for missing duration/access and exclude uncertain offers from winner selection until reviewed. Show the cheapest verified offer per exact plan group, not one winner across unrelated plans.

### P1 — Android price changes overwrite the current row instead of preserving history

**File:** `data/local/LocalDatabase.kt`, table schema and `saveObservation()`.

**Observed behavior:** The Android database currently defines only supplier and price-observation tables. When a source matches the same `supplier_id + matching_key`, `saveObservation()` updates that row in place, replacing amount, source text, status, and timestamp. This loses the prior quote unless it survives elsewhere. Unlike the Python database, the Android app has no equivalent append-only price history / scan-run / scan-checkpoint tables.

**Proper fix:** Add immutable observation snapshots plus a separate current-offer projection (or a price-history table with explicit event rows). Every change in price, currency, availability, warranty, or plan terms must preserve before/after values and source evidence. Add scan-run and supplier-checkpoint records for resumability. Export both current state and the historical timeline.

### P1 — Adding a supplier can replace a row and orphan associated observations

**File:** `data/local/LocalDatabase.kt`, `addSupplier()`.

**Observed behavior:** It writes a supplier using `CONFLICT_REPLACE` against the unique canonical name, but does not first de-duplicate/resolve the normalized Telegram handle. SQLite REPLACE can delete and recreate an existing supplier row with a new ID, while observation rows have no enforced foreign-key relationship or migration to the new ID. A renamed/re-added supplier can create duplicates or break the link between supplier and saved offers.

**Proper fix:** Normalize handles (strip `@`, lowercase), enforce a unique normalized handle, use explicit insert-or-update semantics without deleting the row, and validate the source URL/handle. Add tests for the same handle under different casing, same canonical name renamed, and preservation of associated observations.

### P1 — “Every bot on Telegram” cannot mean a complete global crawl

**Technical boundary:** TDLib provides access to chats known/accessibile to the authorized account and lets an app search chats by query; it does not provide a universal exhaustive list of every bot that exists on Telegram. Telegram public-chat search is query-based, not a guaranteed complete worldwide bot directory.

**Proper fix:** Define the coverage promise as: every bot already in the user’s chat list / approved supplier directory, plus additional public bots found through query-based discovery. Display `discovered`, `selected`, `scanned`, `blocked`, and `manual-review` counts so the app never claims global completeness. Allow importing a list of handles/links and finding/searching public suppliers, but don’t imply the public search API enumerates the whole platform.

Official references: [TDLib searchChats](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1search_chats.html), [TDLib searchPublicChats](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1search_public_chats.html).

### P1 — Android database upgrades are destructive

**File:** `data/local/LocalDatabase.kt`, `onUpgrade()`.

**Observed behavior:** It drops both `price_observations` and `suppliers` and recreates them whenever the schema version changes. This deletes local supplier additions and observation/history data on upgrades.

**Proper fix:** Add version-by-version, non-destructive SQLite migrations. Back up the local DB before migration; add fields/tables without dropping existing rows. Add a migration regression test that creates data under the old schema, upgrades, and proves the supplier/observation rows survive.

### P1 — Encryption silently falls back to plaintext preferences

**File:** `data/local/SecureStorage.kt`.

**Observed behavior:** If `EncryptedSharedPreferences` setup throws, the code falls back to ordinary `MODE_PRIVATE` preferences while UI copy still claims credentials are encrypted.

**Proper fix:** Fail closed: show a clear secure-storage error and do not save API hash/session material in plaintext. Test encryption on supported Android versions/devices. Keep session DB in app-private storage, encrypt it using supported TDLib parameters/key handling and Android Keystore, and ensure no session material enters screenshots, logs, backups, or artifacts.

### P1 — Purchase safety fails open for unknown action labels

**File:** `engine/SafetyBoundary.kt`.

**Observed behavior:** It returns `SAFE` unless a known purchase/payment keyword is present. Catalog navigation matching is substring-based. A supplier-specific action such as “Activate”, “Subscribe”, “Renew”, or an opaque callback can be marked safe if it does not match the current blocked list. Callback detection is only a few string prefixes.

**Proper fix:** Fail closed: allow only an explicit, narrow set of catalog navigation actions when the button text and callback payload are both understood. Unknown or ambiguous actions require manual review. Inspect callback data before sending any callback. Add an allowlist of catalog controls and adversarial tests for localized buttons, emoji labels, obfuscated callbacks, activate/renew/select-plan/confirm/continue buttons, and multilingual labels.

### P1 — Python Telethon crawler only inspects a small, shallow subset

**File:** `scripts/telethon_bot_crawler.py`.

**Observed behavior:** Its fixed target list has eight bots; it tries `/open` and `/start`, clicks at most one Products/Shop-like button, returns labels from the immediate screen, and does not recursively visit product details or upsert the resulting inventory into the normalized price database. It prints errors to the console but doesn't create a full durable scan report. It is not the Android scanner.

**Proper fix:** Reuse the same scan-job/catalog data contract and implement bounded, resumable checkpoints per supplier, chat/message IDs, safe-navigation decisions, error category, and source evidence. Upsert extracted product/plan/price facts idempotently and preserve every change as history. Add pagination/detail navigation with safety stops and a manual review queue. Do not claim a supplier was fully crawled if only its first menu was seen.

### P1 — README and status documentation contradict observed implementation

**Files:** `README.md`, `IMPLEMENTATION_STATUS.md`, `IMPLEMENTATION_AUDIT.md`.

**Observed behavior:** README and status claim the repository is private, the Android app offers real-time bot inspection, credentials/session are verified, and earlier live checks succeeded. Yet repository metadata shows Public; the latest `IMPLEMENTATION_AUDIT.md` says Telegram connection verification is false and that no supplier menu/product was opened. These statements cannot all be true at the same time.

**Proper fix:** Make `IMPLEMENTATION_STATUS.md` the evidence-backed source of truth. Label each feature `Implemented and tested`, `Implemented but not integration-tested`, `Partial`, or `Not implemented`. Include exact test run/commit and the limits tested. Correct README claims after end-to-end evidence exists. Never describe a debug APK build as a verified production app.

### P2 — Repository is public and its catalog/data artifacts are visible

**Observed behavior:** The repo metadata says visibility `public`. Tracked seed files include supplier handles, scraped message text, product/price catalogs, and research dumps. Those files and prior Git history are public to anyone; deleting them from the latest tree does not erase their Git history.

**Proper fix:** Keep the source repository public if that is the owner's decision, but separate public app source from confidential/live sourcing data. Replace real catalog examples with synthetic fixtures; avoid committing live session files, API hashes, private supplier conversations, and current confidential wholesale quotes. If any actual secret was ever committed, rotate it; deleting a file later is not sufficient. Keep `.env`, Telegram sessions, SQLite runtime DBs, logs, and backups excluded.

### P2 — Release workflow can publish a debug APK as a “Release”

**File:** `.github/workflows/android-build.yml`.

**Observed behavior:** The optional workflow_dispatch release step uploads `android/app/build/outputs/apk/debug/app-debug.apk` to a GitHub Release with a fixed `v1.0.0-apk` tag. A debug, debuggable artifact is not a production release and the fixed tag is not a safe versioning strategy.

**Proper fix:** Do not call the debug APK a production release. Build a `release` variant, use a version-derived unique tag, configure signing using protected repository/environment secrets, and gate publishing behind explicit approval. If signing is not configured, publish a clearly named debug/testing artifact only.

### P2 — Build workflow permissions and artifact naming need a release boundary

**File:** `.github/workflows/android-build.yml`.

**Observed behavior:** Build/test job has `contents: write` although routine test/build/artifact upload can run with read-only contents. Release publication is mixed into the same workflow and uses a fixed tag.

**Proper fix:** Use `contents: read` for routine builds. Put release publication in a separate manually approved job/environment with explicit write permission, unique version/tag checks, and signing secrets. Keep debug artifacts clearly labeled and do not call them production releases. Do not put Telegram credentials, sessions, or private runtime DB files in CI.

## Recommended architecture

1. **Android UI:** branded search, status chips, per-supplier scan progress, result groups, scan history, filters, catalog export, and explicit Manual Review indicators.
2. **Telegram adapter:** TDLib-managed owner session and real authorization states. Public-page preview stays a separately labeled source.
3. **Scan orchestrator:** bounded concurrency, supplier allowlist, checkpoints, timeouts/backoff, cancellation, rate-limit detection, and resumability.
4. **Navigation safety:** explicit safe-catalog allowlist, inspect callback payload, manual review for unknown actions, hard stop before transaction/credential/account-modification controls.
5. **Normalization:** canonical product, edition, duration, access type, seats/credits, region, renewal, warranty, currency and verification evidence.
6. **Local persistence:** non-destructive migrations, append-only observations/history, idempotent source keys, encrypted session storage, and backups.
7. **Winner engine:** exact-group comparison only, fresh verified evidence only, correct FX conversion, clear no-winner state when any condition is unknown.
8. **Quality gates:** Kotlin unit tests, Python tests, deterministic mocked Telegram-update tests, database migration tests, security boundary tests, and owner-run real-device smoke tests.

## Acceptance criteria before claiming “fully working”

- [ ] Invalid code/password never reports “connected”; only TDLib `authorizationStateReady` does.
- [ ] Real login, reconnect, logout, and two-step password are tested on device.
- [ ] The app actually opens one selected bot's catalog and at least one product-detail page via authenticated Telegram data.
- [ ] Each extracted offer stores exact source evidence, source observation time, original text, currency, plan terms, stock/warranty as stated, extraction status, and scan-run ID.
- [ ] A failed/unavailable scan never becomes “no stock” and never falls back to cached rows while claiming a live result.
- [ ] Seeded observations are never presented as current verified offers.
- [ ] Different product durations/access types/seat models cannot compete in one cheapest-equivalent group.
- [ ] PKR is not multiplied by an FX rate; unsupported/old FX values are labeled estimates or excluded.
- [ ] Adding a bot works; one-bot scan touches only that bot; all-approved scan is resumable; inaccessible bots go to Manual Review.
- [ ] Upgrade migrations preserve existing local data.
- [ ] Unknown/transactional buttons and callbacks stop safely before any purchase action.
- [ ] Latest CI is green, and real Telegram integration is separately smoke-tested on the user's phone with VPN enabled.

## Audit limitation

This audit is source-level plus inspection of the latest GitHub Actions metadata. I did not have the user's phone/session, did not sign into Telegram, did not click a real supplier bot, did not see real live prices, and did not run the APK on a physical device. Therefore no real Telegram connectivity or supplier-price accuracy is certified by this report.
