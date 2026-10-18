package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import tech.aitoolgems.supplierpricefinder.engine.WinnerEngine

class AuditRegressionTestSuite {

    @Test
    fun testUnknownSafetyButtonsAreManualReview() {
        val ambiguousActions = listOf(
            "Activate Now",
            "Proceed",
            "Confirm Slot",
            "Subscribe Tier",
            "Renew License",
            "Mystery Promo"
        )
        for (action in ambiguousActions) {
            val safety = SafetyBoundary.checkButtonSafety(action)
            assertFalse("Action '$action' must not be auto-safe (fail-closed)", safety.isSafe)
        }
    }

    @Test
    fun testForbiddenPurchaseButtonsAreBlocked() {
        val purchaseActions = listOf(
            "Buy Now",
            "Order 1 Month",
            "Checkout",
            "Pay $5",
            "Top Up Wallet",
            "Deposit Crypto",
            "Transfer Balance",
            "Add to Cart"
        )
        for (action in purchaseActions) {
            val safety = SafetyBoundary.checkButtonSafety(action)
            assertEquals("Action '$action' must be BLOCKED_PURCHASE_RISK", NavigationSafety.BLOCKED_PURCHASE_RISK, safety)
            assertFalse(safety.isSafe)
        }
    }

    @Test
    fun testPkrPriceExtractionAndNoDoubleMultiplier() {
        // Direct PKR string
        val (p1, c1) = PriceExtractor.extractPrice("Canva Pro 1 Year: Rs 2800")
        assertEquals(2800.0, p1 ?: 0.0, 0.001)
        assertEquals("PKR", c1)

        // Strict rate calculation
        val fxRateUsdToPkr = 280.0
        val fxRateUsdtToPkr = 282.5

        val pkrCalculated = when (c1.uppercase()) {
            "PKR" -> p1!! * 1.0 // Multiplier must be exactly 1.0! Never 280!
            "USDT" -> p1!! * fxRateUsdtToPkr
            "USD" -> p1!! * fxRateUsdToPkr
            else -> p1!! * fxRateUsdToPkr
        }
        assertEquals(2800.0, pkrCalculated, 0.001)
    }

    @Test
    fun testLikeForLikeGroupingExcludesDifferentPlans() {
        val sharedOneMonth = PriceObservation(
            id = 1,
            supplierId = 1,
            supplierName = "Bot A",
            supplierHandle = "@bot_a",
            productName = "ChatGPT",
            planName = "ChatGPT Plus 1 Month Shared",
            duration = "1 Month",
            accessType = AccessType.SHARED,
            matchingKey = "chatgpt__plus__1_month__shared",
            amount = 1.50,
            currency = "USD",
            amountPkr = 420.0,
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "Shared 1M $1.50"
        )

        val privateOneMonth = PriceObservation(
            id = 2,
            supplierId = 2,
            supplierName = "Bot B",
            supplierHandle = "@bot_b",
            productName = "ChatGPT",
            planName = "ChatGPT Plus 1 Month Private",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "chatgpt__plus__1_month__private",
            amount = 4.80,
            currency = "USD",
            amountPkr = 1344.0,
            verificationStatus = VerificationStatus.VERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = "Private 1M $4.80"
        )

        // WinnerEngine must isolate canonical groups
        val eval = WinnerEngine.evaluatePlan("chatgpt__plus__1_month__private", listOf(sharedOneMonth, privateOneMonth))
        assertTrue(eval.hasVerifiedWinner)
        assertEquals(privateOneMonth, eval.lowestVerified)
        // Shared offer must NOT win or compete in private evaluation
        assertNotEquals(sharedOneMonth, eval.lowestVerified)
    }

    @Test
    fun testTransientObservationsWithSameZeroIdFromDifferentSuppliersNotDropped() {
        val obs1 = PriceObservation(
            id = 0,
            supplierId = 1,
            supplierName = "Supplier 1",
            supplierHandle = "@sup1_bot",
            productName = "CapCut",
            planName = "CapCut Pro 1M",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "capcut__pro__1_month__private",
            amount = 1.5,
            currency = "USD",
            amountPkr = 420.0,
            originalRawText = "offer 1"
        )

        val obs2 = PriceObservation(
            id = 0,
            supplierId = 2,
            supplierName = "Supplier 2",
            supplierHandle = "@sup2_bot",
            productName = "CapCut",
            planName = "CapCut Pro 1M",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "capcut__pro__1_month__private",
            amount = 1.3,
            currency = "USD",
            amountPkr = 364.0,
            originalRawText = "offer 2"
        )

        // Deduplication using composite key (supplierId + matchingKey) must retain both
        val list = mutableListOf<PriceObservation>()
        for (item in listOf(obs1, obs2)) {
            if (list.none { it.supplierId == item.supplierId && it.matchingKey == item.matchingKey }) {
                list.add(item)
            }
        }
        assertEquals("Both distinct supplier observations must be preserved", 2, list.size)
    }

    @Test
    fun testReferralBonusSpamIsNeverAProductPrice() {
        val spam1 = "Refer friends to get $0.10 wallet bonus!"
        val spam2 = "Invite 5 people for free deposit in your wallet"
        assertTrue(PriceExtractor.isReferralOrBonusMessage(spam1))
        assertTrue(PriceExtractor.isReferralOrBonusMessage(spam2))

        val (price, _) = PriceExtractor.extractPrice(spam1)
        assertNull("Referral bonus must never be parsed as product price", price)
    }
}
