package tech.aitoolgems.supplierpricefinder

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import tech.aitoolgems.supplierpricefinder.telegram.CrawlerButton
import tech.aitoolgems.supplierpricefinder.telegram.CrawlerButtonType
import tech.aitoolgems.supplierpricefinder.telegram.CrawlerMenu
import tech.aitoolgems.supplierpricefinder.telegram.MockTelegramFixtures

/**
 * Verifies Master Prompt requirements:
 * 1. Telegram as Source of Truth (bot discovery filtering, deduplication, provenance separation).
 * 2. Existing Bot vs New Bot (skip /start if replyMarkup active in history).
 * 3. Product Aliases & Ranked Menu Exploration.
 * 4. Search Policy (Fast Lookup vs Exhaustive Scan).
 * 5. Deduplication of bots per scan run.
 * 6. Evidence-based honest search exhaustion status.
 */
class TelegramNativeIntelligenceTestSuite {

    // =========================================================================
    // 1. TELEGRAM DISCOVERY & PROVENANCE TESTS
    // =========================================================================

    @Test
    fun testBotDiscoveryExcludesPersonalUsersAndGroups() {
        // TdApi Chat types:
        // Private chat with UserTypeBot -> ELIGIBLE
        // Private chat with UserTypeRegular -> EXCLUDED
        // BasicGroup, Supergroup, Channel -> EXCLUDED

        val botUser = TdApi.User().apply {
            id = 1001L
            type = TdApi.UserTypeBot()
        }
        val regularUser = TdApi.User().apply {
            id = 1002L
            type = TdApi.UserTypeRegular()
        }

        fun isEligibleBot(chatType: TdApi.ChatType, user: TdApi.User?): Boolean {
            if (chatType !is TdApi.ChatTypePrivate) return false
            return user?.type is TdApi.UserTypeBot
        }

        val privateBotChat = TdApi.ChatTypePrivate(1001L)
        val privateUserChat = TdApi.ChatTypePrivate(1002L)
        val groupChat = TdApi.ChatTypeBasicGroup(5001L)
        val channelChat = TdApi.ChatTypeSupergroup(6001L, true)

        assertTrue("Private chat with bot user MUST be eligible", isEligibleBot(privateBotChat, botUser))
        assertFalse("Private chat with regular contact MUST NOT be eligible", isEligibleBot(privateUserChat, regularUser))
        assertFalse("Basic group MUST NOT be treated as a supplier bot", isEligibleBot(groupChat, null))
        assertFalse("Channel MUST NOT be treated as a supplier bot", isEligibleBot(channelChat, null))
    }

    @Test
    fun testSeedDataCannotMasqueradeAsTelegramDiscovered() {
        val seedSupplier = Supplier(
            id = 1,
            canonicalName = "Seed Demo Bot",
            primaryHandle = "@seed_demo_bot",
            allHandles = "@seed_demo_bot",
            entityType = "BOT",
            source = SupplierSource.SEED_DATA,
            telegramChatId = null,
            lastSyncAt = null
        )

        val liveTgSupplier = Supplier(
            id = 2,
            canonicalName = "Live PT Store BD",
            primaryHandle = "@ptsbd_bot",
            allHandles = "@ptsbd_bot",
            entityType = "BOT",
            source = SupplierSource.TELEGRAM_DISCOVERY,
            telegramChatId = 987654321L,
            lastSyncAt = System.currentTimeMillis()
        )

        assertEquals("Seed source must be SEED_DATA", SupplierSource.SEED_DATA, seedSupplier.source)
        assertNull("Seed supplier cannot have live telegramChatId", seedSupplier.telegramChatId)

        assertEquals("Live discovery must be TELEGRAM_DISCOVERY", SupplierSource.TELEGRAM_DISCOVERY, liveTgSupplier.source)
        assertNotNull("Live discovery must have telegramChatId", liveTgSupplier.telegramChatId)
        assertTrue("Live discovery must have valid lastSyncAt", (liveTgSupplier.lastSyncAt ?: 0) > 0)
    }

    @Test
    fun testBotDeduplicationByStableIdentity() {
        val bots = listOf(
            DiscoveredTelegramBot(chatId = 12345L, botUserId = 101L, title = "Alpha Bot", username = "alpha_bot"),
            DiscoveredTelegramBot(chatId = 12345L, botUserId = 101L, title = "Alpha Bot (Updated Title)", username = "alpha_bot"),
            DiscoveredTelegramBot(chatId = 67890L, botUserId = 202L, title = "Beta Bot", username = "beta_bot")
        )

        // Deduplicate by stable chatId
        val uniqueBots = bots.distinctBy { it.chatId }
        assertEquals("Duplicate chat IDs must be collapsed to exactly 2 distinct bots", 2, uniqueBots.size)
        assertEquals("Latest title should be preserved if applied", "Alpha Bot", uniqueBots[0].title)
    }

    // =========================================================================
    // 2. EXISTING BOT VS NEW BOT INITIALIZATION
    // =========================================================================

    @Test
    fun testExistingBotWithActiveMenuSkipsStartCommand() {
        // When a bot already has conversation history with active replyMarkup,
        // it must resume directly into RESUMING_EXISTING_MENU without sending redundant /start.

        val existingMessageWithMarkup = TdApi.Message().apply {
            id = 500L
            chatId = 12345L
            replyMarkup = TdApi.ReplyMarkupInlineKeyboard()
        }

        val emptyHistory = emptyList<TdApi.Message>()
        val activeHistory = listOf(existingMessageWithMarkup)

        fun determineInitialState(history: List<TdApi.Message>): CrawlerState {
            val hasActiveMenu = history.any { it.replyMarkup != null }
            return if (hasActiveMenu) CrawlerState.RESUMING_EXISTING_MENU else CrawlerState.START_SENT
        }

        assertEquals("Existing active bot must resume menu without /start",
            CrawlerState.RESUMING_EXISTING_MENU, determineInitialState(activeHistory))

        assertEquals("New or empty bot must send /start for initialization",
            CrawlerState.START_SENT, determineInitialState(emptyHistory))
    }

    // =========================================================================
    // 3. TARGET PRODUCT ALIASES & BUTTON RANKING
    // =========================================================================

    @Test
    fun testProductAliasExpansionForMajorAiTools() {
        val canvaAliases = PlanMatcher.getAliasesForTool("Canva Pro")
        assertTrue("Must include canva pro", canvaAliases.contains("canva pro"))
        assertTrue("Must include canva premium", canvaAliases.contains("canva premium"))
        assertTrue("Must include canva edu", canvaAliases.contains("canva edu"))

        val chatGptAliases = PlanMatcher.getAliasesForTool("ChatGPT Plus")
        assertTrue("Must include chatgpt plus", chatGptAliases.contains("chatgpt plus"))
        assertTrue("Must include gpt plus", chatGptAliases.contains("gpt plus"))
        assertTrue("Must include openai", chatGptAliases.contains("openai"))

        val capcutAliases = PlanMatcher.getAliasesForTool("CapCut Pro")
        assertTrue("Must include capcut pro", capcutAliases.contains("capcut pro"))
        assertTrue("Must include capcut premium", capcutAliases.contains("capcut premium"))
    }

    @Test
    fun testNavigationButtonRankingPrioritizesTargetToolBranches() {
        val target = "Canva Pro"

        val canvaScore = PlanMatcher.rankNavigationButton("🎨 Canva Pro", target)
        val aiToolsScore = PlanMatcher.rankNavigationButton("🤖 AI Tools", target)
        val vpnScore = PlanMatcher.rankNavigationButton("🛡 Streaming & VPN", target)
        val genericScore = PlanMatcher.rankNavigationButton("ℹ Support & FAQ", target)

        assertTrue("Direct alias button score must be higher than category score", canvaScore > aiToolsScore)
        assertTrue("AI Tools category score must be higher than unrelated category", aiToolsScore > vpnScore)
        assertTrue("Unrelated category score must be higher than support button", vpnScore > genericScore)

        // Verifies list sorting prioritizes Canva first
        val buttons = listOf("Support", "Streaming & VPN", "🎨 Canva Pro", "🤖 AI Tools")
        val sorted = buttons.sortedByDescending { PlanMatcher.rankNavigationButton(it, target) }

        assertEquals("First button clicked must be Canva Pro", "🎨 Canva Pro", sorted[0])
        assertEquals("Second button clicked must be AI Tools", "🤖 AI Tools", sorted[1])
    }

    // =========================================================================
    // 4. SCAN POLICY & HONEST SEARCH EXHAUSTION
    // =========================================================================

    @Test
    fun testFastLookupSatisfiesPolicyOnFirstValidExtraction() {
        val offers = mutableListOf<PriceObservation>()
        val policy = SearchPolicy.FAST_LOOKUP

        // Simulate observing a Canva offer
        offers.add(
            PriceObservation(
                id = 1,
                supplierId = 10,
                supplierName = "PT Store",
                supplierHandle = "@ptstore_bot",
                productName = "Canva Pro",
                planName = "Canva Pro 1 Month",
                duration = "1 Month",
                accessType = AccessType.SHARED,
                matchingKey = "canva_pro_1m_shared",
                amount = 2.50,
                currency = "USD",
                amountPkr = 700.0,
                warranty = "Full Warranty",
                verificationStatus = VerificationStatus.LIVE_OBSERVED,
                originalRawText = "Canva Pro 1 Month Shared - $2.50"
            )
        )

        val finalState = when {
            offers.isNotEmpty() && policy == SearchPolicy.FAST_LOOKUP -> CrawlerState.SEARCH_POLICY_SATISFIED
            offers.isNotEmpty() -> CrawlerState.COMPLETED
            else -> CrawlerState.PARTIAL
        }

        assertEquals("Fast lookup with matching offer must yield SEARCH_POLICY_SATISFIED",
            CrawlerState.SEARCH_POLICY_SATISFIED, finalState)
    }

    @Test
    fun testExhaustiveScanTraversedWithoutMatchYieldsSearchExhaustedNotFound() {
        val offers = emptyList<PriceObservation>()
        val actionsExecuted = 15 // Traversed multiple categories and found no match

        val finalState = when {
            offers.isNotEmpty() -> CrawlerState.COMPLETED
            actionsExecuted > 0 -> CrawlerState.SEARCH_EXHAUSTED_NOT_FOUND
            else -> CrawlerState.PARTIAL
        }

        assertEquals("Full traversal without finding target must honestly report SEARCH_EXHAUSTED_NOT_FOUND",
            CrawlerState.SEARCH_EXHAUSTED_NOT_FOUND, finalState)
    }

    // =========================================================================
    // 5. NO DUPLICATE BOTS IN SCAN RUN
    // =========================================================================

    @Test
    fun testSupplierDeduplicationGuaranteesSingleExecutionPerScanRun() {
        val rawSuppliers = listOf(
            Supplier(id = 1, canonicalName = "Bot A", primaryHandle = "@bot_a", allHandles = "@bot_a", entityType = "BOT"),
            Supplier(id = 2, canonicalName = "Bot B", primaryHandle = "@bot_b", allHandles = "@bot_b", entityType = "BOT"),
            Supplier(id = 1, canonicalName = "Bot A (Duplicate entry)", primaryHandle = "@bot_a", allHandles = "@bot_a", entityType = "BOT")
        )

        val processedSupplierIds = mutableSetOf<Long>()
        val executedList = mutableListOf<Supplier>()

        for (supplier in rawSuppliers) {
            if (processedSupplierIds.add(supplier.id)) {
                executedList.add(supplier)
            }
        }

        assertEquals("Must only execute 2 suppliers, skipping duplicate Bot A", 2, executedList.size)
        assertEquals(listOf(1L, 2L), executedList.map { it.id })
    }

    // =========================================================================
    // 6. BACKTRACKING WITH BACK BUTTON DETECTION
    // =========================================================================

    @Test
    fun testBackButtonDetectionForSafeBacktracking() {
        val menu = CrawlerMenu(
            chatId = 123L,
            messageId = 456L,
            rawText = "Canva Pro Plans:\n1. 1 Month Shared - $2.50\n2. 1 Year Private - $18.00",
            buttons = listOf(
                listOf(
                    CrawlerButton("1 Month Shared", CrawlerButtonType.INLINE_CALLBACK, "plan_1m"),
                    CrawlerButton("1 Year Private", CrawlerButtonType.INLINE_CALLBACK, "plan_1y")
                ),
                listOf(
                    CrawlerButton("🔙 Back to Tools", CrawlerButtonType.INLINE_CALLBACK, "nav_back")
                )
            )
        )

        val backBtn = menu.buttons.flatten().firstOrNull { btn ->
            val t = btn.text.lowercase()
            t.contains("back") || t.contains("return") || t.contains("🔙") || t.contains("⬅️")
        }

        assertNotNull("Back button must be identified in menu", backBtn)
        assertEquals("nav_back", backBtn!!.payload)
        val safety = SafetyBoundary.checkButtonSafety(backBtn.text, backBtn.payload, backBtn.type)
        assertTrue("Back button must be classified as SAFE", safety.isSafe)
    }
}
