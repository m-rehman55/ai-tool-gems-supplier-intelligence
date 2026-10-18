package tech.aitoolgems.supplierpricefinder.data.model

enum class AccessType(val label: String) {
    PRIVATE("PRIVATE"),
    SHARED("SHARED"),
    TEAM("TEAM"),
    EDU("EDU"),
    KEY("KEY"),
    API("API"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String): AccessType {
            val v = value.uppercase()
            return when {
                v.contains("SHARED") || v.contains("SLOT") || v.contains("PROFILE") -> SHARED
                v.contains("EDU") || v.contains("STUDENT") || v.contains("K12") -> EDU
                v.contains("TEAM") || v.contains("PANEL") || v.contains("ADMIN") -> TEAM
                v.contains("KEY") || v.contains("LICENSE") -> KEY
                v.contains("API") || v.contains("TOKEN") -> API
                v.contains("PRIVATE") || v.contains("PERSONAL") || v.contains("MAIL") -> PRIVATE
                else -> UNKNOWN
            }
        }
    }
}

enum class VerificationStatus(val label: String) {
    VERIFIED("VERIFIED"),
    USER_SUPPLIED_UNVERIFIED("USER_SUPPLIED_UNVERIFIED"),
    UNVERIFIED("UNVERIFIED"),
    STALE("STALE"),
    INCOMPLETE("INCOMPLETE"),
    MANUAL_REVIEW("MANUAL_REVIEW")
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class NavigationSafety(val isSafe: Boolean, val reason: String) {
    SAFE(true, "Safe navigation button"),
    BLOCKED_PURCHASE_RISK(false, "Blocked purchase or payment button"),
    BLOCKED_CREDENTIAL_SHARING(false, "Blocked credential or account modification"),
    MANUAL_REVIEW_REQUIRED(false, "Ambiguous action requires manual inspection")
}
