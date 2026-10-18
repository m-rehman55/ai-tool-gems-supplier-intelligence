package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.*
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import tech.aitoolgems.supplierpricefinder.telegram.MockTelegramFixtures
import java.util.Locale

class AdaptiveBotCrawlerOfflineTestSuite {

    private val testSupplier = Supplier(
        id = 1,
        canonicalName = "DigiCart Tools",
        primaryHandle = "@digicart_bot",
        allHandles = "@digicart_bot",
        entityType = "BOT",
        publicUrl = "https://t.me/digicart_bot",
        supportedCurrencies = "USD,USDT,PKR",
        isEnabled = true
    )

    @Test
    fun testLanguageSelectionDetection() {
        val menu = MockTelegramFixtures.MENU_LANGUAGE_SELECTION
        val englishBtn = menu.buttons.flatten().firstOrNull {
            it.text.contains("English", ignoreCase = true)
        }
        assertNotNull("English language button must be found", englishBtn)
        assertEquals("lang_en", englishBtn!!.payload)

        val safety = SafetyBoundary.checkButtonSafety(englishBtn.text, englishBtn.payload, englishBtn.type)
        assertTrue("English selection button must be safe", safety.isSafe)
    }

    @Test
    fun testMembershipGateDetectionAndAccountProtectionPolicy() {
        val menu = MockTelegramFixtures.MENU_MEMBERSHIP_GATE
        val raw = menu.rawText

        // Gate detection
        assertTrue("Must detect membership gate phrasing", raw.contains("must join our official updates channel"))
        assertTrue("Must detect required channel handle", raw.contains("@digicart_updates"))

        val joinBtn = menu.buttons.flatten().firstOrNull { it.type == CrawlerButtonType.URL_LINK }
        assertNotNull("Join URL button must be identified", joinBtn)
        assertEquals("https://t.me/digicart_updates", joinBtn!!.url)

        val verifyBtn = menu.buttons.flatten().firstOrNull { it.text.contains("Joined", ignoreCase = true) }
        assertNotNull("Membership verify button must be present", verifyBtn)
        val verifySafety = SafetyBoundary.checkButtonSafety(verifyBtn!!.text, verifyBtn.payload, verifyBtn.type)
        assertTrue("Membership verification button must be safe", verifySafety.isSafe)
    }

    @Test
    fun testMultiVariantExtractionStoresAllOptionsSeparately() {
        val fxRateUsdToPkr = 280.0
        val fxRateUsdtToPkr = 282.5

        // Extract variant 1: 1 Day
        val m1 = MockTelegramFixtures.MENU_VARIANT_1_DAY_DETAILS
        val (p1, c1) = PriceExtractor.extractPrice(m1.rawText)
        val d1 = PlanMatcher.parseDuration(m1.rawText)
        val a1 = PlanMatcher.parseAccessType(m1.rawText)
        val k1 = PlanMatcher.generateMatchingKey("ChatGPT Plus", m1.rawText, d1, a1)

        val obs1 = PriceObservation(
            id = 1,
            supplierId = testSupplier.id,
            supplierName = testSupplier.canonicalName,
            supplierHandle = testSupplier.primaryHandle,
            productName = "ChatGPT Plus",
            planName = "ChatGPT Plus 1 Day Shared",
            duration = d1,
            accessType = a1,
            matchingKey = k1,
            amount = p1!!,
            currency = c1!!,
            amountPkr = p1 * fxRateUsdToPkr,
            warranty = "Full Warranty",
            verificationStatus = VerificationStatus.LIVE_OBSERVED_UNVERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = m1.rawText
        )

        // Extract variant 2: 1 Month
        val m2 = MockTelegramFixtures.MENU_VARIANT_1_MONTH_DETAILS
        val (p2, c2) = PriceExtractor.extractPrice(m2.rawText)
        val d2 = PlanMatcher.parseDuration(m2.rawText)
        val a2 = PlanMatcher.parseAccessType(m2.rawText)
        val k2 = PlanMatcher.generateMatchingKey("ChatGPT Plus", m2.rawText, d2, a2)

        val obs2 = PriceObservation(
            id = 2,
            supplierId = testSupplier.id,
            supplierName = testSupplier.canonicalName,
            supplierHandle = testSupplier.primaryHandle,
            productName = "ChatGPT Plus",
            planName = "ChatGPT Plus 1 Month Private",
            duration = d2,
            accessType = a2,
            matchingKey = k2,
            amount = p2!!,
            currency = c2!!,
            amountPkr = p2 * fxRateUsdToPkr,
            warranty = "Full Warranty",
            verificationStatus = VerificationStatus.LIVE_OBSERVED_UNVERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = m2.rawText
        )

        // Extract variant 3: 4 Months
        val m3 = MockTelegramFixtures.MENU_VARIANT_4_MONTH_DETAILS
        val (p3, c3) = PriceExtractor.extractPrice(m3.rawText)
        val d3 = PlanMatcher.parseDuration(m3.rawText)
        val a3 = PlanMatcher.parseAccessType(m3.rawText)
        val k3 = PlanMatcher.generateMatchingKey("ChatGPT Plus", m3.rawText, d3, a3)

        val obs3 = PriceObservation(
            id = 3,
            supplierId = testSupplier.id,
            supplierName = testSupplier.canonicalName,
            supplierHandle = testSupplier.primaryHandle,
            productName = "ChatGPT Plus",
            planName = "ChatGPT Plus 4 Months Private",
            duration = d3,
            accessType = a3,
            matchingKey = k3,
            amount = p3!!,
            currency = c3!!,
            amountPkr = p3 * fxRateUsdToPkr,
            warranty = "Full Warranty",
            verificationStatus = VerificationStatus.LIVE_OBSERVED_UNVERIFIED,
            riskLevel = RiskLevel.LOW,
            originalRawText = m3.rawText
        )

        // Assert all 3 variants are separate and distinct
        assertEquals("1 Day", obs1.duration)
        assertEquals(1.00, obs1.amount, 0.001)
        assertEquals(AccessType.SHARED, obs1.accessType)

        assertEquals("1 Month", obs2.duration)
        assertEquals(5.00, obs2.amount, 0.001)
        assertEquals(AccessType.PRIVATE, obs2.accessType)

        assertEquals("4 Months", obs3.duration)
        assertEquals(18.00, obs3.amount, 0.001)
        assertEquals(AccessType.PRIVATE, obs3.accessType)

        assertNotEquals("Matching keys for 1 Day and 1 Month must not collide", obs1.matchingKey, obs2.matchingKey)
        assertNotEquals("Matching keys for 1 Month and 4 Months must not collide", obs2.matchingKey, obs3.matchingKey)

        // Assert like-for-like comparability
        val (cmp12, r12) = PlanMatcher.arePlansComparable(obs1.duration, obs1.accessType, obs2.duration, obs2.accessType)
        assertFalse("1 Day Shared and 1 Month Private are not comparable", cmp12)

        val (cmp23, r23) = PlanMatcher.arePlansComparable(obs2.duration, obs2.accessType, obs3.duration, obs3.accessType)
        assertFalse("1 Month and 4 Months are not comparable", cmp23)
    }

    @Test
    fun testBareBuyAmbiguityAndPlaybookLearning() {
        val bareBuyMenu = MockTelegramFixtures.MENU_BARE_BUY_AMBIGUOUS
        val bareBuyBtn = bareBuyMenu.buttons.flatten().first()
        val fingerprint = bareBuyMenu.computeFingerprint()

        // 1. Initial check: Unseen bare "Buy" on new supplier MUST require manual review
        val initialSafety = SafetyBoundary.checkButtonSafety(
            buttonText = bareBuyBtn.text,
            callbackData = bareBuyBtn.payload,
            buttonType = bareBuyBtn.type,
            menuFingerprint = fingerprint,
            playbook = null
        )
        assertEquals("Bare 'Buy' must fail closed to MANUAL_REVIEW_REQUIRED", NavigationSafety.MANUAL_REVIEW_REQUIRED, initialSafety)
        assertFalse(initialSafety.isSafe)

        // 2. Simulate operator approval saving a SupplierPlaybook
        val approvedPlaybook = SupplierPlaybook(
            supplierId = 1002,
            menuFingerprint = fingerprint,
            actionLabel = "Buy",
            actionType = CrawlerButtonType.INLINE_CALLBACK,
            payload = "action_buy",
            targetState = CrawlerState.CATALOG_MENU,
            isUserApproved = true
        )

        // 3. Re-evaluating with approved playbook -> now classified as SAFE!
        val learnedSafety = SafetyBoundary.checkButtonSafety(
            buttonText = bareBuyBtn.text,
            callbackData = bareBuyBtn.payload,
            buttonType = bareBuyBtn.type,
            menuFingerprint = fingerprint,
            playbook = approvedPlaybook
        )
        assertEquals("Learned playbook makes confirmed catalog entry SAFE", NavigationSafety.SAFE, learnedSafety)
        assertTrue(learnedSafety.isSafe)

        // 4. Hard safety check: Even with a playbook, "Buy Now" or "Checkout" MUST REMAIN BLOCKED
        val blockedBuyNow = SafetyBoundary.checkButtonSafety(
            buttonText = "Buy Now",
            callbackData = "buynow_checkout",
            buttonType = bareBuyBtn.type,
            menuFingerprint = fingerprint,
            playbook = approvedPlaybook
        )
        assertEquals("Hard transaction blocker cannot be overridden by playbook", NavigationSafety.BLOCKED_PURCHASE_RISK, blockedBuyNow)
        assertFalse(blockedBuyNow.isSafe)
    }

    @Test
    fun testMultiPagePaginationWithoutLooping() {
        val p1 = MockTelegramFixtures.MENU_PAGINATION_PAGE_1
        val p2 = MockTelegramFixtures.MENU_PAGINATION_PAGE_2

        val nextBtnP1 = p1.buttons.flatten().firstOrNull { it.text.contains("Next", ignoreCase = true) }
        assertNotNull("Page 1 must have Next Page button", nextBtnP1)
        val safetyNext = SafetyBoundary.checkButtonSafety(nextBtnP1!!.text, nextBtnP1.payload, nextBtnP1.type)
        assertTrue("Pagination Next button must be safe", safetyNext.isSafe)

        // Page 2 only has Prev, no Next
        val nextBtnP2 = p2.buttons.flatten().firstOrNull { it.text.contains("Next", ignoreCase = true) }
        assertNull("Page 2 must have no Next button to prevent infinite loop", nextBtnP2)

        // Verify fingerprints are distinct
        assertNotEquals("Page 1 and Page 2 must have distinct fingerprints", p1.computeFingerprint(), p2.computeFingerprint())
    }

    @Test
    fun testPkrPriceExactMultiplierWithoutDoubleConversion() {
        val (pkrAmount, currency) = PriceExtractor.extractPrice("Canva Pro (1 Year) Rs. 3500 PKR")
        assertEquals(3500.0, pkrAmount ?: 0.0, 0.001)
        assertEquals("PKR", currency)

        val fxRateUsdToPkr = 280.0
        val pkrCalculated = when (currency!!.uppercase(Locale.ROOT)) {
            "PKR" -> pkrAmount!! * 1.0 // Multiplier must be exactly 1.0!
            "USD" -> pkrAmount!! * fxRateUsdToPkr
            else -> pkrAmount!! * fxRateUsdToPkr
        }
        assertEquals(3500.0, pkrCalculated, 0.001)
    }

    @Test
    fun testFailsTruthfullyOnUnstatedDurationAndAccess() {
        val line = "Generic Tool $10 USD"
        val dur = PlanMatcher.parseDuration(line)
        val acc = PlanMatcher.parseAccessType(line)

        assertEquals("Unstated duration must be UNKNOWN (never fabricated)", "UNKNOWN", dur)
        assertEquals("Unstated access must be UNKNOWN (never fabricated)", AccessType.UNKNOWN, acc)
    }

    @Test
    fun testBlockedTransactionButtonsNeverExecute() {
        val forbiddenButtons = listOf(
            "Pay $10",
            "Checkout",
            "Buy Now",
            "Deposit Crypto",
            "Top Up Wallet",
            "Transfer Funds",
            "Confirm Order"
        )
        for (btn in forbiddenButtons) {
            val safety = SafetyBoundary.checkButtonSafety(btn)
            assertEquals("Button '$btn' must be BLOCKED_PURCHASE_RISK", NavigationSafety.BLOCKED_PURCHASE_RISK, safety)
            assertFalse(safety.isSafe)
        }
    }
}
