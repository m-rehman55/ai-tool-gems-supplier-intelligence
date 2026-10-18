package tech.aitoolgems.supplierpricefinder.engine

import tech.aitoolgems.supplierpricefinder.data.model.NavigationSafety
import java.util.Locale

object SafetyBoundary {

    /**
     * Regex patterns for actions that cross purchase, payment, or irreversible transaction boundaries.
     */
    /**
     * Regex patterns for actions that cross purchase, payment, or irreversible transaction boundaries.
     */
    private val FORBIDDEN_PURCHASE_PATTERNS = listOf(
        "\\bbuy\\b",
        "\\bpurchase\\b",
        "\\bcheckout\\b",
        "\\border\\b",
        "\\bpay\\b",
        "\\bpayment\\b",
        "\\bconfirm\\s+order\\b",
        "\\bplace\\s+order\\b",
        "\\bwallet\\b",
        "\\btop\\s*up\\b",
        "\\bdeposit\\b",
        "\\binvoice\\b",
        "\\btransfer\\b",
        "\\bpay\\s*now\\b",
        "\\bcart\\b",
        "\\bsubscribe\\b",
        "\\bactivate\\b",
        "\\brenew\\b",
        "\\bconfirm\\b"
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    private val FORBIDDEN_CALLBACK_PATTERNS = listOf(
        "buy_", "order_", "checkout_", "pay_", "deposit_", "invoice_", "topup_", "confirm_", "activate_", "renew_"
    )

    private val SAFE_CATALOG_KEYWORDS = listOf(
        "product", "shop", "store", "catalog", "menu", "service", "tools",
        "back", "next", "page", "previous", "detail", "view", "open", "category", "list", "info", "help"
    )

    /**
     * Classifies whether an inline button or menu option is safe to navigate.
     * FAILS CLOSED: Returns SAFE only if it matches explicit catalog navigation terms.
     * Returns BLOCKED_PURCHASE_RISK for purchase/payment actions.
     * Returns MANUAL_REVIEW_REQUIRED for unknown/ambiguous labels.
     */
    fun checkButtonSafety(buttonText: String, callbackData: String = ""): NavigationSafety {
        val cleanText = buttonText.trim().lowercase(Locale.ROOT)
        val cleanData = callbackData.trim().lowercase(Locale.ROOT)

        for (pattern in FORBIDDEN_PURCHASE_PATTERNS) {
            if (pattern.containsMatchIn(cleanText)) {
                return NavigationSafety.BLOCKED_PURCHASE_RISK
            }
        }

        for (dataPrefix in FORBIDDEN_CALLBACK_PATTERNS) {
            if (cleanData.contains(dataPrefix)) {
                return NavigationSafety.BLOCKED_PURCHASE_RISK
            }
        }

        // Must match safe catalog keyword to be deemed SAFE (Fail-Closed)
        val isExplicitlySafeText = SAFE_CATALOG_KEYWORDS.any { cleanText.contains(it) }
        val isExplicitlySafeData = cleanData.isEmpty() || SAFE_CATALOG_KEYWORDS.any { cleanData.contains(it) }

        if (isExplicitlySafeText && isExplicitlySafeData) {
            return NavigationSafety.SAFE
        }

        return NavigationSafety.MANUAL_REVIEW_REQUIRED
    }

    /**
     * Identifies whether a button is a safe catalog or product navigation target.
     */
    fun isCatalogNavigationButton(buttonText: String): Boolean {
        return checkButtonSafety(buttonText).isSafe
    }
}
