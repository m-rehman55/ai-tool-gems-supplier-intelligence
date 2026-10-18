package tech.aitoolgems.supplierpricefinder.engine

import java.util.Locale

object PriceExtractor {

    private val REFERRAL_PATTERNS = listOf(
        "referral", "refer & earn", "invite friends", "in your wallet",
        "earn free balance", "cashback", "giveaway", "deposit bonus"
    )

    fun isReferralOrBonusMessage(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return REFERRAL_PATTERNS.any { t.contains(it) }
    }

    fun extractPrice(text: String): Pair<Double?, String> {
        val clean = text.trim()
        if (isReferralOrBonusMessage(clean)) return null to "USD"

        // Check flash sale arrows: $10 -> $5
        val arrowMatch = "\\$\\s*(\\d+(?:\\.\\d{1,2})?)\\s*→\\s*\\$\\s*(\\d+(?:\\.\\d{1,2})?)".toRegex().find(clean)
        if (arrowMatch != null) {
            val p = arrowMatch.groupValues[2].toDoubleOrNull()
            return p to "USDT"
        }

        // Direct price regex: $4.80 or 4.80 USDT or Price: $5
        val priceMatch = "(?:Price|Price\\s*:|\\$|💵|💰|💲)\\s*(?:[:\\s]*)(?:\\$|USDT)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(USDT|USD|PKR|INR|EUR)?".toRegex(RegexOption.IGNORE_CASE).find(clean)
        if (priceMatch != null) {
            val amount = priceMatch.groupValues[1].toDoubleOrNull()
            val currGroup = priceMatch.groupValues[2]
            val curr = when {
                currGroup.isNotEmpty() -> currGroup.uppercase(Locale.ROOT)
                clean.contains("USDT", ignoreCase = true) -> "USDT"
                clean.contains("PKR", ignoreCase = true) || clean.contains("Rs", ignoreCase = true) -> "PKR"
                else -> "USD"
            }
            return amount to curr
        }

        return null to "USD"
    }

    fun extractWarranty(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        return when {
            t.contains("(fw)") || t.contains("full warranty") -> "Full Warranty (FW)"
            t.contains("(nw)") || t.contains("no warranty") -> "No Warranty (NW)"
            t.contains("1 day warranty") || t.contains("24h warranty") -> "1 Day Warranty"
            t.contains("5h warranty") -> "5h Warranty"
            t.contains("3 days warranty") -> "3 Days Warranty"
            t.contains("25 days warranty") -> "25 Days Warranty"
            t.contains("30 days warranty") || t.contains("1 month warranty") -> "1 Month Warranty"
            t.contains("1 year warranty") -> "1 Year Warranty"
            else -> "Not Stated"
        }
    }

    fun extractStock(text: String): String {
        val stockMatch = "(?:Available|Stock:?|Current stock:?)\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE).find(text)
        return if (stockMatch != null) {
            "${stockMatch.groupValues[1]} in stock"
        } else if (text.contains("out of stock", ignoreCase = true) || text.contains("sold out", ignoreCase = true)) {
            "Out of stock"
        } else {
            "Available"
        }
    }
}
