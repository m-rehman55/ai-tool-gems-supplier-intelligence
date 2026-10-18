package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.WinnerEngine

class WinnerEngineTest {

    @Test
    fun testEmptyObservationsYieldsNoWinner() {
        val eval = WinnerEngine.evaluatePlan("chatgpt__plus__1_month__private", emptyList())
        assertFalse(eval.hasVerifiedWinner)
        assertEquals("NO_QUOTES_RECORDED", eval.statusSummary)
        assertNull(eval.lowestVerified)
    }

    @Test
    fun testUnverifiedQuotesDoNotWinVerifiedWinner() {
        val unverifiedObs = PriceObservation(
            id = 1,
            supplierId = 1,
            supplierName = "DigiCart Tools",
            supplierHandle = "@getdigitaltools_bot",
            productName = "ChatGPT",
            planName = "ChatGPT Plus",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "chatgpt__plus__1_month__private",
            amount = 5.00,
            currency = "USD",
            amountPkr = 1400.0,
            verificationStatus = VerificationStatus.USER_SUPPLIED_UNVERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "ChatGPT Plus $5.00 unverified seed claim"
        )

        val eval = WinnerEngine.evaluatePlan("chatgpt__plus__1_month__private", listOf(unverifiedObs))
        assertFalse("Unverified quotes must not win verified status", eval.hasVerifiedWinner)
        assertNull(eval.lowestVerified)
        assertEquals("NO_VERIFIED_WINNER_YET", eval.statusSummary)
        assertEquals(unverifiedObs, eval.lowestAdvertised)
    }

    @Test
    fun testStaleQuoteDisqualification() {
        // 72 hours old quote (stale under 48h policy)
        val oldTimestamp = System.currentTimeMillis() - (72 * 3600 * 1000L)
        val staleObs = PriceObservation(
            id = 2,
            supplierId = 1,
            supplierName = "PiggyAI",
            supplierHandle = "@PiggyAi799_Bot",
            productName = "CapCut",
            planName = "CapCut Pro",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "capcut__pro__1_month__private",
            amount = 1.27,
            currency = "USD",
            amountPkr = 355.6,
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "CapCut Pro $1.27",
            observedAt = oldTimestamp
        )

        val eval = WinnerEngine.evaluatePlan("capcut__pro__1_month__private", listOf(staleObs))
        assertFalse(eval.hasVerifiedWinner)
        assertNull(eval.lowestVerified)
    }

    @Test
    fun testVerifiedWinnerSelection() {
        val unverifiedCheaper = PriceObservation(
            id = 1,
            supplierId = 2,
            supplierName = "Unknown Bot",
            supplierHandle = "@unknown_bot",
            productName = "ChatGPT",
            planName = "ChatGPT Plus",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "chatgpt__plus__1_month__private",
            amount = 3.00,
            currency = "USD",
            amountPkr = 840.0,
            verificationStatus = VerificationStatus.USER_SUPPLIED_UNVERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "$3.00 claim"
        )

        val verifiedQuote = PriceObservation(
            id = 2,
            supplierId = 1,
            supplierName = "DigiCart Tools",
            supplierHandle = "@getdigitaltools_bot",
            productName = "ChatGPT",
            planName = "ChatGPT Plus",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "chatgpt__plus__1_month__private",
            amount = 4.80,
            currency = "USDT",
            amountPkr = 1356.0,
            warranty = "Full Warranty (FW)",
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "ChatGPT Plus $4.80 USDT Full Warranty"
        )

        val eval = WinnerEngine.evaluatePlan("chatgpt__plus__1_month__private", listOf(unverifiedCheaper, verifiedQuote))
        assertTrue(eval.hasVerifiedWinner)
        assertEquals(unverifiedCheaper, eval.lowestAdvertised) // $3.00 is lowest advertised
        assertEquals(verifiedQuote, eval.lowestVerified)       // $4.80 is verified winner!
        assertEquals("VERIFIED_WINNER_FOUND", eval.statusSummary)
    }
}
