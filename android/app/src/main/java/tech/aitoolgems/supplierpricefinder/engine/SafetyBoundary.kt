package tech.aitoolgems.supplierpricefinder.engine

import tech.aitoolgems.supplierpricefinder.data.model.CrawlerButton
import tech.aitoolgems.supplierpricefinder.data.model.CrawlerButtonType
import tech.aitoolgems.supplierpricefinder.data.model.NavigationSafety
import tech.aitoolgems.supplierpricefinder.data.model.SupplierPlaybook
import java.util.Locale

object SafetyBoundary {

    /**
     * Regex patterns for irreversible purchase, checkout, deposit, or payment actions.
     * These are STRICTLY FORBIDDEN and CANNOT be overridden by playbooks or AI.
     */
    private val FORBIDDEN_TRANSACTION_PATTERNS = listOf(
        "\\bbuy\\s*now\\b",
        "\\bcheckout\\b",
        "\\bpay\\s*now\\b",
        "\\bpay\\b",
        "\\bpayment\\b",
        "\\bplace\\s+order\\b",
        "\\bconfirm\\s+order\\b",
        "\\border\\s+now\\b",
        "\\bwallet\\b",
        "\\btop\\s*up\\b",
        "\\bdeposit\\b",
        "\\binvoice\\b",
        "\\btransfer\\b",
        "\\bcart\\b",
        "\\badd\\s+to\\s+cart\\b",
        "\\bsubscribe\\b",
        "\\bconfirm\\b",
        "\\bmake\\s+payment\\b",
        "\\bcrypto\\b",
        "\\btrx\\b",
        "\\busdt\\s+trc20\\b",
        "\\bbinance\\s*pay\\b"
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    private val FORBIDDEN_CALLBACK_PATTERNS = listOf(
        "buynow_", "checkout_", "pay_", "deposit_", "invoice_", "topup_", "confirm_", "transfer_", "cart_"
    )

    /**
     * Keywords that clearly indicate safe catalog exploration, menus, details, or navigation.
     */
    private val SAFE_CATALOG_KEYWORDS = listOf(
        "product", "shop", "store", "catalog", "menu", "service", "tools", "all tools",
        "category", "categories", "list", "view", "open", "detail", "details", "info",
        "back", "return", "previous", "next", "more", "page", "home", "main menu", "🔙", "⬅️", "➡️", ">>", "<<"
    )

    /**
     * Recognised safe language selection labels.
     */
    private val SAFE_LANGUAGE_KEYWORDS = listOf(
        "english", "eng", "🇬🇧", "🇺🇸", "en", "language"
    )

    /**
     * Recognised safe variant selection labels (durations, access models, tiers).
     */
    private val SAFE_VARIANT_PATTERNS = listOf(
        "\\b\\d+\\s*(day|days|d|week|weeks|w|month|months|m|year|years|y)\\b",
        "\\b(private|shared|personal|team|admin|edu|student|panel|slot|profile|key|license)\\b",
        "\\b(tier|option|plan)\\s*\\d*\\b"
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    /**
     * Recognised membership gate verification buttons.
     */
    private val SAFE_GATE_VERIFICATION_KEYWORDS = listOf(
        "joined", "check membership", "verify", "done", "i have joined", "check", "try again"
    )

    /**
     * Ambiguous catalog button patterns (e.g. bare "Buy", "Buy Tools").
     * For a new supplier, these require 1-time user confirmation.
     */
    private val AMBIGUOUS_CATALOG_PATTERNS = listOf(
        "^\\s*buy\\s*$",
        "^\\s*buy\\s+tools\\s*$",
        "^\\s*buy\\s+products\\s*$",
        "^\\s*buy\\s+accounts\\s*$"
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    /**
     * Evaluates the safety of a button using layered deterministic defense.
     * 1. Hard Transaction Blocker: purchase, checkout, deposit, wallet are NEVER executed.
     * 2. Button Type Check: Web Apps and Contact Requests are blocked.
     * 3. Known Supplier Playbook: if user previously confirmed this exact menu button opens catalog.
     * 4. Ambiguous Bare Buy: flags for manual review if no playbook exists.
     * 5. Catalog / Variant / Language / Gate Rule Matching.
     * 6. Fail-Closed: returns MANUAL_REVIEW_REQUIRED for anything unrecognized.
     */
    fun checkButtonSafety(
        buttonText: String,
        callbackData: String = "",
        buttonType: CrawlerButtonType = CrawlerButtonType.INLINE_CALLBACK,
        menuFingerprint: String = "",
        playbook: SupplierPlaybook? = null
    ): NavigationSafety {
        val cleanText = buttonText.trim().lowercase(Locale.ROOT)
        val cleanData = callbackData.trim().lowercase(Locale.ROOT)

        // 1. HARD SAFETY LAYER: Never purchase, checkout, pay, or modify account
        if (buttonType == CrawlerButtonType.BUY_TRANSACTION) {
            return NavigationSafety.BLOCKED_PURCHASE_RISK
        }
        if (buttonType == CrawlerButtonType.REQUEST_CONTACT_OR_LOCATION) {
            return NavigationSafety.BLOCKED_CREDENTIAL_SHARING
        }
        if (buttonType == CrawlerButtonType.WEB_APP) {
            return NavigationSafety.MANUAL_REVIEW_REQUIRED
        }

        for (pattern in FORBIDDEN_TRANSACTION_PATTERNS) {
            if (pattern.containsMatchIn(cleanText)) {
                return NavigationSafety.BLOCKED_PURCHASE_RISK
            }
        }
        for (prefix in FORBIDDEN_CALLBACK_PATTERNS) {
            if (cleanData.contains(prefix)) {
                return NavigationSafety.BLOCKED_PURCHASE_RISK
            }
        }

        // 2. PLAYBOOK LAYER: If operator previously validated this exact menu action as safe catalog
        if (playbook != null && playbook.isUserApproved) {
            if (playbook.menuFingerprint == menuFingerprint &&
                playbook.actionLabel.equals(buttonText.trim(), ignoreCase = true)) {
                return NavigationSafety.SAFE
            }
        }

        // 3. AMBIGUOUS BARE BUY LAYER: Bare "Buy" or "Buy Tools" opens catalog on some bots, but checkouts on others.
        for (pattern in AMBIGUOUS_CATALOG_PATTERNS) {
            if (pattern.containsMatchIn(cleanText)) {
                return NavigationSafety.MANUAL_REVIEW_REQUIRED
            }
        }

        // 4. LANGUAGE SELECTOR MATCH
        if (SAFE_LANGUAGE_KEYWORDS.any { cleanText == it || cleanText.contains(it) }) {
            return NavigationSafety.SAFE
        }

        // 5. MEMBERSHIP GATE CONFIRMATION MATCH
        if (SAFE_GATE_VERIFICATION_KEYWORDS.any { cleanText.contains(it) }) {
            return NavigationSafety.SAFE
        }

        // 6. VARIANT DURATION & ACCESS TYPE MATCH (1 Month, 4 Months, Private, etc.)
        for (pattern in SAFE_VARIANT_PATTERNS) {
            if (pattern.containsMatchIn(cleanText)) {
                return NavigationSafety.SAFE
            }
        }

        // 7. SAFE CATALOG & MENU NAVIGATION MATCH
        val isExplicitlySafeText = SAFE_CATALOG_KEYWORDS.any { cleanText.contains(it) }
        val isExplicitlySafeData = cleanData.isEmpty() || SAFE_CATALOG_KEYWORDS.any { cleanData.contains(it) }

        if (isExplicitlySafeText && isExplicitlySafeData) {
            return NavigationSafety.SAFE
        }

        // FAIL CLOSED: Unrecognized labels go to manual review
        return NavigationSafety.MANUAL_REVIEW_REQUIRED
    }

    /**
     * Quick helper for checking button text safety.
     */
    fun isCatalogNavigationButton(buttonText: String): Boolean {
        return checkButtonSafety(buttonText).isSafe
    }
}
