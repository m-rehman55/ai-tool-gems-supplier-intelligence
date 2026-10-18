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

    /**
     * Parses duration/validity from raw text.
     * FAILS TRUTHFULLY: Returns "UNKNOWN" if no explicit duration is stated.
     */
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
            return "$v Day" + if (v > 1) "s" else ""
        }

        return "UNKNOWN"
    }

    /**
     * Parses account access model from text.
     * FAILS TRUTHFULLY: Returns UNKNOWN if unstated.
     */
    fun parseAccessType(rawText: String): AccessType {
        val t = rawText.lowercase(Locale.ROOT)
        return when {
            t.contains("slot") || t.contains("shared") || t.contains("profile") -> AccessType.SHARED
            t.contains("edu") || t.contains("student") || t.contains("k12") -> AccessType.EDU
            t.contains("team") || t.contains("panel") || t.contains("admin") -> AccessType.TEAM
            t.contains("key") || t.contains("license") || t.contains("retail") -> AccessType.KEY
            t.contains("api") || t.contains("token") -> AccessType.API
            t.contains("private") || t.contains("personal") || t.contains("mail") || t.contains("email") -> AccessType.PRIVATE
            else -> AccessType.UNKNOWN
        }
    }

    /**
     * Extracts known canonical tool name from line.
     */
    fun extractProductName(rawText: String): String {
        val t = rawText.lowercase(Locale.ROOT)
        return when {
            t.contains("chatgpt") || t.contains("openai") || t.contains("gpt-4") -> "ChatGPT Plus"
            t.contains("canva") -> "Canva Pro"
            t.contains("gemini") -> "Google Gemini"
            t.contains("claude") -> "Claude Pro"
            t.contains("netflix") -> "Netflix Premium"
            t.contains("midjourney") -> "Midjourney"
            t.contains("capcut") -> "CapCut Pro"
            t.contains("grammarly") -> "Grammarly Premium"
            t.contains("prime video") || t.contains("amazon prime") -> "Amazon Prime"
            t.contains("jetbrains") -> "JetBrains All Products"
            t.contains("cursor") -> "Cursor AI"
            t.contains("github copilot") || t.contains("copilot") -> "GitHub Copilot"
            t.contains("freepik") -> "Freepik Premium"
            t.contains("envato") -> "Envato Elements"
            t.contains("runway") -> "Runway Gen-2"
            t.contains("elevenlabs") -> "ElevenLabs"
            else -> rawText.lines().firstOrNull()?.take(30)?.trim() ?: "Digital Tool"
        }
    }

    fun generateMatchingKey(productName: String, planName: String, duration: String, accessType: AccessType): String {
        val pSlug = normalizeString(productName).replace(" ", "_")
        val plSlug = normalizeString(planName).replace(" ", "_").take(40)
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
