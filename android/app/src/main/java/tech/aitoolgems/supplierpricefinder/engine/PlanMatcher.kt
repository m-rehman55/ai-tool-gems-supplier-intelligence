package tech.aitoolgems.supplierpricefinder.engine

import tech.aitoolgems.supplierpricefinder.data.model.AccessType
import java.util.Locale

object PlanMatcher {

    fun normalizeString(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun parseDuration(rawText: String): String {
        val t = rawText.lowercase(Locale.ROOT)
        if (t.contains("lifetime")) return "Lifetime"

        val yearMatch = "\\b(\\d+)\\s*(?:years?|y)\\b".toRegex().find(t)
        if (yearMatch != null) {
            val v = yearMatch.groupValues[1].toInt()
            return "$v Year" + if (v > 1) "s" else ""
        }

        val monthMatch = "\\b(\\d+)\\s*(?:months?|m)\\b".toRegex().find(t)
        if (monthMatch != null) {
            val v = monthMatch.groupValues[1].toInt()
            return "$v Month" + if (v > 1) "s" else ""
        }

        val dayMatch = "\\b(\\d+)\\s*(?:days?|d)\\b".toRegex().find(t)
        if (dayMatch != null) {
            val v = dayMatch.groupValues[1].toInt()
            return "$v Days"
        }

        return "1 Month"
    }

    fun parseAccessType(rawText: String): AccessType {
        val t = rawText.lowercase(Locale.ROOT)
        return when {
            t.contains("slot") || t.contains("shared") || t.contains("profile") -> AccessType.SHARED
            t.contains("edu") || t.contains("student") || t.contains("k12") -> AccessType.EDU
            t.contains("team") || t.contains("panel") || t.contains("admin") -> AccessType.TEAM
            t.contains("key") || t.contains("license") || t.contains("retail") -> AccessType.KEY
            t.contains("api") || t.contains("token") -> AccessType.API
            t.contains("private") || t.contains("personal") || t.contains("email") || t.contains("password") -> AccessType.PRIVATE
            else -> AccessType.PRIVATE // Default standard access if private tool
        }
    }

    fun generateMatchingKey(productName: String, planName: String, duration: String, accessType: AccessType): String {
        val pSlug = normalizeString(productName).replace(" ", "_")
        val plSlug = normalizeString(planName).replace(" ", "_")
        val durSlug = normalizeString(duration).replace(" ", "_")
        val accSlug = accessType.name.lowercase(Locale.ROOT)
        return "${pSlug}__${plSlug}__${durSlug}__${accSlug}"
    }

    fun arePlansComparable(
        planADuration: String,
        planAAccess: AccessType,
        planBDuration: String,
        planBAccess: AccessType
    ): Pair<Boolean, String> {
        if (!planADuration.equals(planBDuration, ignoreCase = true)) {
            return false to "Different durations: $planADuration vs $planBDuration"
        }
        if (planAAccess != planBAccess) {
            return false to "Different account types: ${planAAccess.name} vs ${planBAccess.name}"
        }
        return true to "GENUINELY_COMPARABLE"
    }
}
