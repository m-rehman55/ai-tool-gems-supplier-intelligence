package tech.aitoolgems.supplierpricefinder.engine

import tech.aitoolgems.supplierpricefinder.data.model.NavigationSafety
import java.util.Locale

object SafetyBoundary {

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
        "\\bcart\\b"
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    private val FORBIDDEN_CALLBACK_PATTERNS = listOf(
        "buy_", "order_", "checkout_", "pay_", "deposit_", "invoice_", "topup_"
    )

    /**
     * Classifies whether an inline button or menu option is safe to navigate.
     * Returns SAFE only if it does not trigger any financial or purchase commitments.
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

        return NavigationSafety.SAFE
    }

    /**
     * Identifies whether a button is a safe catalog or product navigation target.
     */
    fun isCatalogNavigationButton(buttonText: String): Boolean {
        val t = buttonText.trim().lowercase(Locale.ROOT)
        val catalogKeywords = listOf(
            "product", "shop", "store", "catalog", "menu", "service", "tools",
            "back", "next", "page", "previous", "detail", "view", "open"
        )
        return catalogKeywords.any { t.contains(it) } && checkButtonSafety(buttonText).isSafe
    }
}
