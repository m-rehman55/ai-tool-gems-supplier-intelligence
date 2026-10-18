package tech.aitoolgems.supplierpricefinder.engine

import tech.aitoolgems.supplierpricefinder.data.model.*
import java.util.concurrent.TimeUnit

object WinnerEngine {

    const val DEFAULT_FRESHNESS_HOURS = 48L

    fun isSuspiciouslyCheap(productName: String, planName: String, amountUsd: Double): Boolean {
        val p = productName.lowercase()
        val pl = planName.lowercase()

        if (p.contains("chatgpt") || p.contains("claude") || p.contains("perplexity")) {
            if (pl.contains("plus") || pl.contains("pro")) {
                if (amountUsd < 1.0) return true
            }
        }
        if (p.contains("gemini") && amountUsd < 0.25) return true
        if (amountUsd < 0.20) return true
        return false
    }

    fun isQuoteEligibleForVerifiedWinner(
        obs: PriceObservation,
        maxFreshnessHours: Long = DEFAULT_FRESHNESS_HOURS
    ): Pair<Boolean, List<String>> {
        val reasons = mutableListOf<String>()

        // 1. Verification status
        if (obs.verificationStatus != VerificationStatus.VERIFIED) {
            reasons.add("Status is ${obs.verificationStatus.name} (requires VERIFIED)")
        }

        // 2. Risk level
        if (obs.riskLevel == RiskLevel.HIGH || obs.riskLevel == RiskLevel.CRITICAL) {
            reasons.add("Risk level is ${obs.riskLevel.name}")
        }

        // 3. Stock
        if (obs.stockStatus.contains("out of stock", ignoreCase = true) || obs.stockStatus.contains("sold out", ignoreCase = true)) {
            reasons.add("Out of stock")
        }

        // 4. Freshness
        val ageHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - obs.observedAt)
        if (ageHours > maxFreshnessHours) {
            reasons.add("Quote is stale (${ageHours}h old > ${maxFreshnessHours}h threshold)")
        }

        // 5. Suspiciously cheap
        if (isSuspiciouslyCheap(obs.productName, obs.planName, obs.amount)) {
            reasons.add("Suspiciously cheap wholesale price ($${obs.amount})")
        }

        return (reasons.isEmpty()) to reasons
    }

    fun evaluatePlan(matchingKey: String, observations: List<PriceObservation>): WinnerEvaluation {
        if (observations.isEmpty()) {
            return WinnerEvaluation(
                matchingKey = matchingKey,
                productName = "Unknown",
                planName = "Unknown",
                duration = "Unknown",
                accessType = AccessType.UNKNOWN,
                statusSummary = "NO_QUOTES_RECORDED"
            )
        }

        val first = observations.first()
        val sortedAll = observations.sortedBy { it.amountPkr }

        val lowestAdvertised = sortedAll.first()
        val lowestComparable = sortedAll.first()

        val verifiedCandidates = mutableListOf<PriceObservation>()
        val disqualifications = mutableMapOf<Long, List<String>>()

        for (obs in sortedAll) {
            val (eligible, reasons) = isQuoteEligibleForVerifiedWinner(obs)
            if (eligible) {
                verifiedCandidates.add(obs)
            } else {
                disqualifications[obs.id] = reasons
            }
        }

        val hasWinner = verifiedCandidates.isNotEmpty()
        val lowestVerified = if (hasWinner) verifiedCandidates.first() else null

        // Recommended: verified quote with active warranty
        val recommended = verifiedCandidates.firstOrNull {
            !it.warranty.contains("no warranty", ignoreCase = true) && !it.warranty.contains("not stated", ignoreCase = true)
        } ?: lowestVerified

        return WinnerEvaluation(
            matchingKey = matchingKey,
            productName = first.productName,
            planName = first.planName,
            duration = first.duration,
            accessType = first.accessType,
            lowestAdvertised = lowestAdvertised,
            lowestComparable = lowestComparable,
            lowestVerified = lowestVerified,
            recommended = recommended,
            hasVerifiedWinner = hasWinner,
            statusSummary = if (hasWinner) "VERIFIED_WINNER_FOUND" else "NO_VERIFIED_WINNER_YET",
            disqualificationReasons = disqualifications,
            allObservations = sortedAll
        )
    }
}
