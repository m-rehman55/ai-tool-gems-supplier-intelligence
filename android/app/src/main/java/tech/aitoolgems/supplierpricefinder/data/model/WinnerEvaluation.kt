package tech.aitoolgems.supplierpricefinder.data.model

data class WinnerEvaluation(
    val matchingKey: String,
    val productName: String,
    val planName: String,
    val duration: String,
    val accessType: AccessType,
    val lowestAdvertised: PriceObservation? = null,
    val lowestComparable: PriceObservation? = null,
    val lowestVerified: PriceObservation? = null,
    val recommended: PriceObservation? = null,
    val hasVerifiedWinner: Boolean = false,
    val statusSummary: String = "NO_VERIFIED_WINNER_YET",
    val disqualificationReasons: Map<Long, List<String>> = emptyMap(),
    val allObservations: List<PriceObservation> = emptyList()
)
