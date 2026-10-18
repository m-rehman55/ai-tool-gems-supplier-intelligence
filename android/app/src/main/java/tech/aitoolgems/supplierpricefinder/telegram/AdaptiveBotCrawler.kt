package tech.aitoolgems.supplierpricefinder.telegram

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.drinkless.tdlib.TdApi
import tech.aitoolgems.supplierpricefinder.data.local.LocalDatabase
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface AdaptiveCrawlListener {
    fun onStateChanged(supplierHandle: String, state: CrawlerState, message: String)
    fun onProgressUpdate(stepText: String, completedSuppliers: Int, totalSuppliers: Int)
    fun onVariantDiscovered(observation: PriceObservation)
    fun onApprovalRequired(item: ManualReviewItem, onDecision: (Boolean) -> Unit)
    fun onTimelineEvent(event: NavigationTimelineEvent)
}

/**
 * Intelligent, adaptive Telegram Supplier Catalog Crawler.
 * Traverses bot menus, resolves language selection, handles membership gates,
 * navigates nested categories/products, explores all plan variants, and paginates.
 */
class AdaptiveBotCrawler(
    private val context: Context,
    private val database: LocalDatabase,
    private val telegramManager: TelegramClientManager
) {

    companion object {
        private const val TAG = "AdaptiveBotCrawler"
        const val MAX_NAVIGATION_DEPTH = 12
        const val MAX_STATES_PER_BOT = 150
        const val MAX_ACTIONS_PER_BOT = 250
        const val MAX_DURATION_MS = 300_000L // 5 minutes
    }

    private val isCancelled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    /**
     * Executes a full or targeted crawl across the given suppliers.
     */
    suspend fun executeCrawl(
        suppliers: List<Supplier>,
        targetProductQuery: String = "",
        runId: Long = 0,
        listener: AdaptiveCrawlListener? = null
    ): List<PriceObservation> = withContext(Dispatchers.IO) {
        val allExtractedOffers = mutableListOf<PriceObservation>()
        isCancelled.set(false)
        isPaused.set(false)

        val totalSuppliers = suppliers.size
        var completedCount = 0
        var partialCount = 0
        var blockedCount = 0

        for ((index, supplier) in suppliers.withIndex()) {
            if (isCancelled.get()) break
            while (isPaused.get()) {
                delay(500)
                if (isCancelled.get()) break
            }

            listener?.onProgressUpdate("Inspecting ${supplier.canonicalName} (${index + 1}/$totalSuppliers)...", index, totalSuppliers)
            listener?.onStateChanged(supplier.primaryHandle, CrawlerState.OPENING_BOT, "Starting adaptive crawler for ${supplier.canonicalName}...")

            val supplierOffers = crawlSingleSupplier(supplier, targetProductQuery, runId, listener)
            allExtractedOffers.addAll(supplierOffers)

            if (supplierOffers.isNotEmpty()) {
                completedCount++
            } else {
                partialCount++
            }

            delay(200) // Polite delay between suppliers
        }

        allExtractedOffers
    }

    /**
     * Adapts and explores an individual supplier bot's complete interactive menu.
     */
    suspend fun crawlSingleSupplier(
        supplier: Supplier,
        targetProductQuery: String,
        runId: Long,
        listener: AdaptiveCrawlListener?
    ): List<PriceObservation> = withContext(Dispatchers.IO) {
        val offers = mutableListOf<PriceObservation>()
        val cleanHandle = supplier.primaryHandle.trim().removePrefix("@")
        val startTime = System.currentTimeMillis()

        var currentState = CrawlerState.QUEUED
        var actionCount = 0
        val visitedMenuFingerprints = mutableSetOf<String>()
        val breadcrumbStack = ArrayDeque<CrawlerMenu>()

        fun emitTimeline(action: String, type: CrawlerButtonType, reason: String, status: String) {
            val event = NavigationTimelineEvent(
                runId = runId,
                supplierId = supplier.id,
                supplierHandle = supplier.primaryHandle,
                state = currentState,
                actionLabel = action,
                actionType = type,
                decisionReason = reason,
                outcomeStatus = status
            )
            database.addTimelineEvent(event)
            listener?.onTimelineEvent(event)
        }

        // 1. Resolve Bot Chat
        currentState = CrawlerState.OPENING_BOT
        listener?.onStateChanged(supplier.primaryHandle, currentState, "Resolving bot @$cleanHandle via MTProto...")
        emitTimeline("SearchPublicChat(@$cleanHandle)", CrawlerButtonType.UNKNOWN, "Locate supplier chat", "START")

        val chat = telegramManager.resolveBotChat(cleanHandle)
        if (chat == null) {
            currentState = CrawlerState.BOT_NOT_FOUND
            listener?.onStateChanged(supplier.primaryHandle, currentState, "Bot @$cleanHandle could not be resolved.")
            emitTimeline("SearchPublicChat", CrawlerButtonType.UNKNOWN, "Bot handle not found on Telegram", "FAILED")
            return@withContext offers
        }

        val chatId = chat.id

        // 2. Start Bot
        currentState = CrawlerState.START_SENT
        listener?.onStateChanged(supplier.primaryHandle, currentState, "Sending /start command to ${chat.title}...")
        emitTimeline("/start", CrawlerButtonType.REPLY_KEYBOARD_TEXT, "Initialize bot conversation", "SENT")

        val startSent = telegramManager.sendBotTextMessage(chatId, "/start")
        if (startSent == null) {
            currentState = CrawlerState.NETWORK_ERROR
            listener?.onStateChanged(supplier.primaryHandle, currentState, "Failed to send /start to bot.")
            emitTimeline("/start", CrawlerButtonType.REPLY_KEYBOARD_TEXT, "Network dispatch error", "FAILED")
            return@withContext offers
        }

        // Wait for bot response with bounded timeout
        var currentMenu = waitForNextBotMenu(chatId, timeoutMs = 7000)
        if (currentMenu == null) {
            currentState = CrawlerState.RESPONSE_TIMEOUT
            listener?.onStateChanged(supplier.primaryHandle, currentState, "Bot @$cleanHandle did not respond in time.")
            emitTimeline("WaitResponse", CrawlerButtonType.UNKNOWN, "Response timed out after 7s", "TIMEOUT")
            return@withContext offers
        }

        // 3. Adaptive Traversal Loop
        while (!isCancelled.get() && actionCount < MAX_ACTIONS_PER_BOT) {
            if (System.currentTimeMillis() - startTime > MAX_DURATION_MS) {
                currentState = CrawlerState.PARTIAL
                emitTimeline("TimeLimit", CrawlerButtonType.UNKNOWN, "Max scan duration reached (5m)", "STOPPED")
                break
            }

            val fingerprint = currentMenu.computeFingerprint()
            visitedMenuFingerprints.add(fingerprint)

            // Extract any direct price observations visible in the current menu text
            val extractedHere = extractOffersFromMenu(currentMenu, supplier, targetProductQuery)
            for (obs in extractedHere) {
                if (offers.none { it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId }) {
                    offers.add(obs)
                    database.saveObservation(obs)
                    listener?.onVariantDiscovered(obs)
                }
            }

            // A. Check for Language Selection Screen
            val languageButton = findLanguageSelectorButton(currentMenu)
            if (languageButton != null) {
                currentState = CrawlerState.LANGUAGE_SELECTION
                listener?.onStateChanged(supplier.primaryHandle, currentState, "Selecting English language (${languageButton.text})...")
                emitTimeline(languageButton.text, languageButton.type, "Auto-select English language", "EXECUTED")
                actionCount++
                currentMenu = executeButtonAction(chatId, currentMenu, languageButton) ?: break
                continue
            }

            // B. Check for Membership Gate
            val membershipGate = detectMembershipGate(currentMenu)
            if (membershipGate != null) {
                currentState = CrawlerState.MEMBERSHIP_GATE
                listener?.onStateChanged(supplier.primaryHandle, currentState, "Membership gate detected: ${membershipGate.title}")

                var userApproved = false
                val reviewItem = ManualReviewItem(
                    runId = runId,
                    supplierId = supplier.id,
                    supplierHandle = supplier.primaryHandle,
                    itemType = "MEMBERSHIP_GATE",
                    title = "Group/Channel Join Required",
                    promptMessage = "Bot requires joining ${membershipGate.targetHandleOrUrl} to unlock its catalog. Approve join?",
                    targetHandleOrUrl = membershipGate.targetHandleOrUrl,
                    rawEvidence = currentMenu.rawText.take(200)
                )
                database.addManualReviewItem(reviewItem)

                // Request approval from listener
                val deferred = CompletableDeferred<Boolean>()
                listener?.onApprovalRequired(reviewItem) { approved ->
                    deferred.complete(approved)
                }

                // If no listener or timeout, default to false (Hard Account-Side-Effect Policy: never join without consent)
                userApproved = try {
                    withTimeoutOrNull(10000) { deferred.await() } ?: false
                } catch (e: Exception) {
                    false
                }

                if (userApproved && membershipGate.targetHandleOrUrl != null) {
                    emitTimeline("JoinChannel(${membershipGate.targetHandleOrUrl})", CrawlerButtonType.URL_LINK, "User approved required channel join", "JOINED")
                    telegramManager.joinChatChannel(membershipGate.targetHandleOrUrl)

                    // Find and click the verify button (e.g. "Joined", "Verify", "Done")
                    val verifyBtn = currentMenu.buttons.flatten().firstOrNull {
                        val t = it.text.lowercase(Locale.ROOT)
                        t.contains("joined") || t.contains("verify") || t.contains("check") || t.contains("done")
                    }
                    if (verifyBtn != null) {
                        emitTimeline(verifyBtn.text, verifyBtn.type, "Verify membership after join", "EXECUTED")
                        actionCount++
                        currentMenu = executeButtonAction(chatId, currentMenu, verifyBtn) ?: break
                        continue
                    }
                } else {
                    currentState = CrawlerState.JOIN_APPROVAL_REQUIRED
                    emitTimeline("JoinDenied", CrawlerButtonType.UNKNOWN, "User declined channel join request", "BLOCKED")
                    break
                }
            }

            // C. Look for Catalog / Products / Store Entry Button
            val catalogButton = findCatalogEntryButton(currentMenu, supplier.id, fingerprint)
            if (catalogButton != null) {
                val safety = SafetyBoundary.checkButtonSafety(
                    catalogButton.text,
                    catalogButton.payload,
                    catalogButton.type,
                    fingerprint,
                    database.getPlaybook(supplier.id, fingerprint)
                )

                if (safety == NavigationSafety.MANUAL_REVIEW_REQUIRED) {
                    // Bare "Buy" or ambiguous catalog entry -> Ask user once
                    val reviewItem = ManualReviewItem(
                        runId = runId,
                        supplierId = supplier.id,
                        supplierHandle = supplier.primaryHandle,
                        itemType = "AMBIGUOUS_CATALOG_ENTRY",
                        title = "Confirm Catalog Button",
                        promptMessage = "Does button '${catalogButton.text}' on ${supplier.canonicalName} open the catalog menu?",
                        rawEvidence = currentMenu.rawText.take(150)
                    )
                    database.addManualReviewItem(reviewItem)

                    val deferred = CompletableDeferred<Boolean>()
                    listener?.onApprovalRequired(reviewItem) { approved ->
                        deferred.complete(approved)
                    }

                    val approved = withTimeoutOrNull(8000) { deferred.await() } ?: false
                    if (approved) {
                        database.savePlaybook(
                            SupplierPlaybook(
                                supplierId = supplier.id,
                                menuFingerprint = fingerprint,
                                actionLabel = catalogButton.text,
                                actionType = catalogButton.type,
                                payload = catalogButton.payload,
                                targetState = CrawlerState.CATALOG_MENU,
                                isUserApproved = true
                            )
                        )
                    } else {
                        emitTimeline(catalogButton.text, catalogButton.type, "Ambiguous catalog entry rejected", "SKIPPED")
                        break
                    }
                }

                currentState = CrawlerState.CATALOG_MENU
                listener?.onStateChanged(supplier.primaryHandle, currentState, "Opening catalog (${catalogButton.text})...")
                emitTimeline(catalogButton.text, catalogButton.type, "Open catalog entry", "EXECUTED")
                actionCount++
                breadcrumbStack.addLast(currentMenu)
                currentMenu = executeButtonAction(chatId, currentMenu, catalogButton) ?: break
                continue
            }

            // D. Explore Product / Category Submenus and Multi-Variant Buttons
            val navigableButtons = currentMenu.buttons.flatten().filter { btn ->
                val safety = SafetyBoundary.checkButtonSafety(btn.text, btn.payload, btn.type, fingerprint)
                safety.isSafe && !btn.text.contains("back", ignoreCase = true) && !btn.text.contains("main menu", ignoreCase = true)
            }

            if (navigableButtons.isNotEmpty()) {
                var traversedAny = false
                for (btn in navigableButtons) {
                    if (actionCount >= MAX_ACTIONS_PER_BOT || isCancelled.get()) break

                    currentState = CrawlerState.PRODUCT_DETAIL
                    listener?.onStateChanged(supplier.primaryHandle, currentState, "Inspecting item: ${btn.text}...")
                    emitTimeline(btn.text, btn.type, "Explore product/variant option", "EXECUTED")
                    actionCount++

                    breadcrumbStack.addLast(currentMenu)
                    val nextMenu = executeButtonAction(chatId, currentMenu, btn)

                    if (nextMenu != null) {
                        val subOffers = extractOffersFromMenu(nextMenu, supplier, targetProductQuery)
                        for (obs in subOffers) {
                            if (offers.none { it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId }) {
                                offers.add(obs)
                                database.saveObservation(obs)
                                listener?.onVariantDiscovered(obs)
                            }
                        }

                        // Inspect for nested variant options (e.g. 1 Day, 1 Month, 4 Months, 12 Months)
                        val variantButtons = nextMenu.buttons.flatten().filter { vBtn ->
                            val s = SafetyBoundary.checkButtonSafety(vBtn.text, vBtn.payload, vBtn.type)
                            s.isSafe && (vBtn.text.contains("month", ignoreCase = true) ||
                                         vBtn.text.contains("day", ignoreCase = true) ||
                                         vBtn.text.contains("year", ignoreCase = true) ||
                                         vBtn.text.contains("private", ignoreCase = true) ||
                                         vBtn.text.contains("shared", ignoreCase = true) ||
                                         vBtn.text.contains("tier", ignoreCase = true))
                        }

                        for (vBtn in variantButtons) {
                            if (actionCount >= MAX_ACTIONS_PER_BOT) break
                            emitTimeline(vBtn.text, vBtn.type, "Explore variant option", "EXECUTED")
                            actionCount++
                            val variantMenu = executeButtonAction(chatId, nextMenu, vBtn)
                            if (variantMenu != null) {
                                val vOffers = extractOffersFromMenu(variantMenu, supplier, targetProductQuery)
                                for (obs in vOffers) {
                                    if (offers.none { it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId }) {
                                        offers.add(obs)
                                        database.saveObservation(obs)
                                        listener?.onVariantDiscovered(obs)
                                    }
                                }
                            }
                        }

                        // Return back from product detail to parent list
                        val backBtn = findBackButton(nextMenu)
                        if (backBtn != null) {
                            emitTimeline(backBtn.text, backBtn.type, "Return to product list", "BACK")
                            executeButtonAction(chatId, nextMenu, backBtn)
                        }
                        traversedAny = true
                    }
                }

                // Check for Pagination (Next Page / Page 2 / >>)
                val nextPgBtn = findPaginationButton(currentMenu)
                if (nextPgBtn != null) {
                    currentState = CrawlerState.PAGINATION
                    listener?.onStateChanged(supplier.primaryHandle, currentState, "Paginating: ${nextPgBtn.text}...")
                    emitTimeline(nextPgBtn.text, nextPgBtn.type, "Traverse to next catalog page", "PAGINATE")
                    actionCount++
                    currentMenu = executeButtonAction(chatId, currentMenu, nextPgBtn) ?: break
                    continue
                }

                if (traversedAny) {
                    break // Completed this catalog level
                }
            }

            // No further safe options on this screen
            break
        }

        currentState = if (offers.isNotEmpty()) CrawlerState.COMPLETED else CrawlerState.PARTIAL
        listener?.onStateChanged(supplier.primaryHandle, currentState, "Completed crawl for ${supplier.canonicalName}. Found ${offers.size} offer(s).")
        database.clearCheckpoint(supplier.id)

        offers
    }

    // =========================================================================
    // HELPER DETECTION & EXTRACTION METHODS
    // =========================================================================

    private fun findLanguageSelectorButton(menu: CrawlerMenu): CrawlerButton? {
        val allButtons = menu.buttons.flatten()
        return allButtons.firstOrNull {
            val t = it.text.trim().lowercase(Locale.ROOT)
            t == "english" || t == "eng" || t == "🇬🇧 english" || t == "🇬🇧" || t == "🇺🇸"
        }
    }

    private fun detectMembershipGate(menu: CrawlerMenu): ManualReviewItem? {
        val text = menu.rawText.lowercase(Locale.ROOT)
        val isGateText = text.contains("join our channel") ||
                         text.contains("subscribe to") ||
                         text.contains("join the channel") ||
                         text.contains("must join") ||
                         text.contains("join below")

        if (!isGateText) return null

        // Try extracting handle or URL from text or URL buttons
        var target: String? = null
        for (btn in menu.buttons.flatten()) {
            if (btn.type == CrawlerButtonType.URL_LINK && btn.url != null) {
                target = btn.url
                break
            }
        }
        if (target == null) {
            val handleMatch = Regex("@([a-zA-Z0-9_]{4,})").find(menu.rawText)
            if (handleMatch != null) {
                target = handleMatch.value
            }
        }

        return ManualReviewItem(
            runId = 0,
            supplierId = 0,
            supplierHandle = "",
            itemType = "MEMBERSHIP_GATE",
            title = "Channel/Group Gate",
            promptMessage = "Bot requires joining $target",
            targetHandleOrUrl = target,
            rawEvidence = menu.rawText.take(150)
        )
    }

    private fun findCatalogEntryButton(menu: CrawlerMenu, supplierId: Long, fingerprint: String): CrawlerButton? {
        val playbook = database.getPlaybook(supplierId, fingerprint)
        if (playbook != null && playbook.isUserApproved) {
            val matched = menu.buttons.flatten().firstOrNull { it.text.trim().equals(playbook.actionLabel, ignoreCase = true) }
            if (matched != null) return matched
        }

        return menu.buttons.flatten().firstOrNull { btn ->
            val t = btn.text.lowercase(Locale.ROOT)
            t.contains("product") || t.contains("shop") || t.contains("store") ||
            t.contains("catalog") || t.contains("tools") || t.contains("all tools") ||
            t.contains("services") || t.matches(Regex("^\\s*buy(\\s+tools)?\\s*$", RegexOption.IGNORE_CASE))
        }
    }

    private fun findPaginationButton(menu: CrawlerMenu): CrawlerButton? {
        return menu.buttons.flatten().firstOrNull { btn ->
            val t = btn.text.lowercase(Locale.ROOT)
            (t.contains("next") || t.contains("page") || t.contains("more") || t.contains(">>")) &&
            !t.contains("prev") && !t.contains("back")
        }
    }

    private fun findBackButton(menu: CrawlerMenu): CrawlerButton? {
        return menu.buttons.flatten().firstOrNull { btn ->
            val t = btn.text.lowercase(Locale.ROOT)
            t.contains("back") || t.contains("return") || t.contains("main menu") || t.contains("🔙") || t.contains("⬅️")
        }
    }

    /**
     * Executes the appropriate button mechanism (Inline Callback vs Reply Keyboard Text).
     */
    private suspend fun executeButtonAction(
        chatId: Long,
        currentMenu: CrawlerMenu,
        button: CrawlerButton
    ): CrawlerMenu? {
        when (button.type) {
            CrawlerButtonType.INLINE_CALLBACK -> {
                telegramManager.sendBotCallbackQuery(chatId, currentMenu.messageId, button.payload)
            }
            CrawlerButtonType.REPLY_KEYBOARD_TEXT -> {
                telegramManager.sendBotTextMessage(chatId, button.text)
            }
            else -> return null
        }

        // Wait for bot update or next message
        return waitForNextBotMenu(chatId, timeoutMs = 4000)
    }

    /**
     * Awaits incoming bot message or edited message content.
     */
    private suspend fun waitForNextBotMenu(chatId: Long, timeoutMs: Long): CrawlerMenu? {
        val deferred = CompletableDeferred<CrawlerMenu>()
        val listener: (TdApi.Object) -> Unit = { obj ->
            when (obj) {
                is TdApi.UpdateNewMessage -> {
                    if (obj.message.chatId == chatId) {
                        deferred.complete(parseTdMessageToMenu(obj.message))
                    }
                }
                is TdApi.UpdateMessageContent -> {
                    if (obj.chatId == chatId) {
                        // Fetch latest message
                        val msg = telegramManager.fetchMessageSync(chatId, obj.messageId)
                        if (msg != null) {
                            deferred.complete(parseTdMessageToMenu(msg))
                        }
                    }
                }
            }
        }

        telegramManager.registerBotUpdateListener(chatId, listener)
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            telegramManager.unregisterBotUpdateListener(chatId)
        }
    }

    /**
     * Parses a TDLib Message object into a decoupled CrawlerMenu.
     */
    fun parseTdMessageToMenu(message: TdApi.Message): CrawlerMenu {
        val rawText = when (val c = message.content) {
            is TdApi.MessageText -> c.text.text
            is TdApi.MessagePhoto -> c.caption.text
            is TdApi.MessageDocument -> c.caption.text
            else -> ""
        }
        val buttonRows = mutableListOf<List<CrawlerButton>>()
        when (val markup = message.replyMarkup) {
            is TdApi.ReplyMarkupInlineKeyboard -> {
                for (row in markup.rows) {
                    val rowBtns = mutableListOf<CrawlerButton>()
                    for (btn in row) {
                        val (type, payload, url) = when (val btnType = btn.type) {
                            is TdApi.InlineKeyboardButtonTypeCallback -> {
                                val p = String(btnType.data, Charsets.UTF_8)
                                Triple(CrawlerButtonType.INLINE_CALLBACK, p, null)
                            }
                            is TdApi.InlineKeyboardButtonTypeUrl -> {
                                Triple(CrawlerButtonType.URL_LINK, "", btnType.url)
                            }
                            is TdApi.InlineKeyboardButtonTypeWebApp -> {
                                Triple(CrawlerButtonType.WEB_APP, "", btnType.url)
                            }
                            is TdApi.InlineKeyboardButtonTypeBuy -> {
                                Triple(CrawlerButtonType.BUY_TRANSACTION, "", null)
                            }
                            else -> {
                                Triple(CrawlerButtonType.UNKNOWN, "", null)
                            }
                        }
                        rowBtns.add(CrawlerButton(btn.text, type, payload, url))
                    }
                    if (rowBtns.isNotEmpty()) buttonRows.add(rowBtns)
                }
            }
            is TdApi.ReplyMarkupShowKeyboard -> {
                for (row in markup.rows) {
                    val rowBtns = mutableListOf<CrawlerButton>()
                    for (btn in row) {
                        rowBtns.add(CrawlerButton(btn.text, CrawlerButtonType.REPLY_KEYBOARD_TEXT, btn.text))
                    }
                    if (rowBtns.isNotEmpty()) buttonRows.add(rowBtns)
                }
            }
        }
        return CrawlerMenu(
            chatId = message.chatId,
            messageId = message.id,
            rawText = rawText,
            buttons = buttonRows,
            editDate = message.editDate
        )
    }

    /**
     * Extracts all prices and plan variants from bot text and attached button labels.
     */
    fun extractOffersFromMenu(
        menu: CrawlerMenu,
        supplier: Supplier,
        targetProductQuery: String
    ): List<PriceObservation> {
        val list = mutableListOf<PriceObservation>()
        val lines = menu.rawText.split("\n")
        val cleanSupplierHandle = supplier.primaryHandle.trim()

        val fxRateUsdToPkr = 280.0
        val fxRateUsdtToPkr = 282.5

        for (line in lines) {
            val (amount, currency) = PriceExtractor.extractPrice(line)
            if (amount != null && currency != null) {
                val duration = PlanMatcher.parseDuration(line)
                val accessType = PlanMatcher.parseAccessType(line)
                val productName = PlanMatcher.extractProductName(line)

                if (targetProductQuery.isEmpty() ||
                    line.contains(targetProductQuery, ignoreCase = true) ||
                    productName.contains(targetProductQuery, ignoreCase = true)) {

                    val pkrAmount = when (currency.uppercase(Locale.ROOT)) {
                        "PKR" -> amount * 1.0 // Multiplier must be exactly 1.0!
                        "USDT" -> amount * fxRateUsdtToPkr
                        "USD" -> amount * fxRateUsdToPkr
                        else -> amount * fxRateUsdToPkr
                    }

                    val key = PlanMatcher.generateMatchingKey(productName, line, duration, accessType)

                    list.add(
                        PriceObservation(
                            id = 0,
                            supplierId = supplier.id,
                            supplierName = supplier.canonicalName,
                            supplierHandle = cleanSupplierHandle,
                            productName = productName,
                            planName = line.take(60).trim(),
                            duration = duration,
                            accessType = accessType,
                            matchingKey = key,
                            amount = amount,
                            currency = currency,
                            amountPkr = pkrAmount,
                            warranty = if (line.contains("FW", ignoreCase = true) || line.contains("Warranty", ignoreCase = true)) "Full Warranty" else "Standard Bot Catalog",
                            stockStatus = if (line.contains("out of stock", ignoreCase = true)) "Out of Stock" else "Available",
                            verificationStatus = VerificationStatus.LIVE_OBSERVED_UNVERIFIED,
                            riskLevel = RiskLevel.LOW,
                            captureMethod = "AUTHENTICATED_TDLIB_ADAPTIVE_CRAWLER",
                            originalRawText = line.trim(),
                            sourceUrl = "https://t.me/${cleanSupplierHandle.removePrefix("@")}",
                            observedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return list
    }
}
