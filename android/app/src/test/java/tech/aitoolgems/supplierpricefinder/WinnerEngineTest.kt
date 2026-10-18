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

    @Test
    fun testLikeForLikePlanEquivalenceEnforcement() {
        val sharedOneMonth = PriceObservation(
            id = 1,
            supplierId = 1,
            supplierName = "Bot A",
            supplierHandle = "@bot_a",
            productName = "Canva Pro",
            planName = "Canva Pro 1 Month Shared",
            duration = "1 Month",
            accessType = AccessType.SHARED,
            matchingKey = "canva__pro__1_month__shared",
            amount = 1.00,
            currency = "USD",
            amountPkr = 280.0,
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "Canva 1 Month Shared $1.00"
        )

        val privateOneYear = PriceObservation(
            id = 2,
            supplierId = 2,
            supplierName = "Bot B",
            supplierHandle = "@bot_b",
            productName = "Canva Pro",
            planName = "Canva Pro 1 Year Private",
            duration = "1 Year",
            accessType = AccessType.PRIVATE,
            matchingKey = "canva__pro__1_year__private",
            amount = 10.00,
            currency = "USD",
            amountPkr = 2800.0,
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "Canva 1 Year Private $10.00"
        )

        // Evaluating specifically for "canva__pro__1_year__private" must NEVER let the shared 1-month plan win or compete
        val eval = WinnerEngine.evaluatePlan("canva__pro__1_year__private", listOf(sharedOneMonth, privateOneYear))
        assertTrue(eval.hasVerifiedWinner)
        assertEquals(privateOneYear, eval.lowestVerified)
        assertEquals(privateOneYear, eval.lowestAdvertised)
        assertFalse("Shared plan must not be in the canonical comparison group", eval.allObservations.contains(sharedOneMonth))
    }
}
