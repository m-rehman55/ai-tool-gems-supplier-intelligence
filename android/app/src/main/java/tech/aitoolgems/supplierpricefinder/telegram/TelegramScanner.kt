package tech.aitoolgems.supplierpricefinder.telegram

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.aitoolgems.supplierpricefinder.data.local.LocalDatabase
import tech.aitoolgems.supplierpricefinder.data.local.SecureStorage
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor
import tech.aitoolgems.supplierpricefinder.engine.SafetyBoundary
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface ScanProgressListener {
    fun onSupplierScanning(supplierHandle: String, message: String)
    fun onProductExtracted(observation: PriceObservation)
    fun onSupplierBlocked(supplierHandle: String, reason: String)
    fun onApprovalRequired(item: ManualReviewItem, onDecision: (Boolean) -> Unit) {}
    fun onStateChanged(supplierHandle: String, state: CrawlerState, message: String) {}
}

class TelegramScanner(
    private val context: Context,
    private val database: LocalDatabase,
    private val secureStorage: SecureStorage
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val tgManager = TelegramClientManager.getInstance(context)
    val adaptiveCrawler = AdaptiveBotCrawler(context, database, tgManager)

    var isRunning: Boolean = false
        private set

    fun cancelScan() {
        adaptiveCrawler.cancel()
        isRunning = false
    }

    fun pauseScan() {
        adaptiveCrawler.pause()
    }

    fun resumeScan() {
        adaptiveCrawler.resume()
    }

    /**
     * Executes an adaptive scan across suppliers for a specific tool or full catalog.
     */
    suspend fun scanSuppliersForTool(
        targetTool: String,
        targetSupplierId: Long? = null,
        searchPolicy: SearchPolicy = SearchPolicy.FAST_LOOKUP,
        listener: ScanProgressListener? = null
    ): List<PriceObservation> = withContext(Dispatchers.IO) {
        isRunning = true
        val runUuid = UUID.randomUUID().toString()
        val allSuppliers = database.getAllSuppliers().filter { it.isEnabled }
        val suppliers = if (targetSupplierId != null) {
            allSuppliers.filter { it.id == targetSupplierId }
        } else {
            allSuppliers
        }

        val trigger = if (targetSupplierId != null) "SINGLE_SUPPLIER_SCAN" else (if (targetTool.isEmpty()) "FULL_CATALOG_SCAN" else "SEARCH_TOOL_SCAN")
        val runId = database.startScanRun(runUuid, trigger, suppliers.size, searchPolicy.name)
        val targetQuery = targetTool.trim()

        try {
            val results = mutableListOf<PriceObservation>()
            val fxRateUsdToPkr = 280.0
            val fxRateUsdtToPkr = 282.5

            var completedSuppliers = 0
            var partialSuppliers = 0
            var blockedSuppliers = 0

            // 1. If Telegram MTProto is authenticated, run genuine adaptive crawler
            if (tgManager.currentAuthState == TelegramAuthState.AUTHENTICATED) {
                listener?.onSupplierScanning("CRAWLER", "Using genuine MTProto TDLib connection (${searchPolicy.label}) for ${suppliers.size} supplier(s)...")
                val crawlResults = adaptiveCrawler.executeCrawl(
                    suppliers = suppliers,
                    targetProductQuery = targetQuery,
                    searchPolicy = searchPolicy,
                    runId = runId,
                    listener = object : AdaptiveCrawlListener {
                        override fun onStateChanged(supplierHandle: String, state: CrawlerState, message: String) {
                            listener?.onStateChanged(supplierHandle, state, message)
                            listener?.onSupplierScanning(supplierHandle, message)
                        }

                        override fun onProgressUpdate(stepText: String, completedSuppliers: Int, totalSuppliers: Int) {
                            listener?.onSupplierScanning("SYSTEM", stepText)
                        }

                        override fun onVariantDiscovered(observation: PriceObservation) {
                            results.add(observation)
                            listener?.onProductExtracted(observation)
                        }

                        override fun onApprovalRequired(item: ManualReviewItem, onDecision: (Boolean) -> Unit) {
                            listener?.onApprovalRequired(item, onDecision)
                        }

                        override fun onTimelineEvent(event: NavigationTimelineEvent) {
                            // Persisted by crawler
                        }
                    }
                )

                if (crawlResults.isNotEmpty()) {
                    for (obs in crawlResults) {
                        if (results.none { it.id == obs.id || (it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId) }) {
                            results.add(obs)
                        }
                    }
                }
            }

            // 2. Fallback / supplementary preview for suppliers that yielded no live bot observations
            for (supplier in suppliers) {
                val hasLiveOffers = results.any { it.supplierId == supplier.id }
                if (!hasLiveOffers) {
                    listener?.onSupplierScanning(supplier.primaryHandle, "Checking public web preview for ${supplier.canonicalName}...")
                    delay(100)

                    val channelHandle = supplier.primaryHandle.trim().removePrefix("@")
                    val targetUrl = when {
                        !supplier.publicUrl.isNullOrEmpty() && supplier.publicUrl.contains("t.me/s/") -> supplier.publicUrl
                        else -> "https://t.me/s/$channelHandle"
                    }

                    try {
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .build()
                        val response = httpClient.newCall(request).execute()
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                val liveQuotes = parseChannelHtml(body, supplier, targetQuery, fxRateUsdToPkr, fxRateUsdtToPkr)
                                for (obs in liveQuotes) {
                                    val safety = SafetyBoundary.checkButtonSafety(obs.planName)
                                    if (safety.isSafe) {
                                        if (results.none { it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId }) {
                                            results.add(obs)
                                            database.saveObservation(obs)
                                            listener?.onProductExtracted(obs)
                                        }
                                    } else {
                                        listener?.onSupplierBlocked(supplier.primaryHandle, "Blocked: ${obs.planName} (${safety.reason})")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Network timeout or VPN requirement
                    }

                    // 3. Truthful local cache fallback: clearly marked as CACHED
                    val stillNoOffers = results.none { it.supplierId == supplier.id }
                    if (stillNoOffers) {
                        val dbQuotes = database.getObservationsForSupplierAndQuery(supplier.id, targetQuery)
                        for (cached in dbQuotes) {
                            val markedCached = cached.copy(
                                verificationStatus = VerificationStatus.CACHED,
                                captureMethod = "LOCAL_DATABASE_CACHE"
                            )
                            if (results.none { it.matchingKey == markedCached.matchingKey && it.supplierId == markedCached.supplierId }) {
                                results.add(markedCached)
                            }
                        }
                    }
                }
            }

            // Update ScanRun status in DB
            val finalRun = database.getLatestScanRun()
            if (finalRun != null && finalRun.id == runId) {
                finalRun.completedAt = System.currentTimeMillis()
                finalRun.status = "COMPLETED"
                finalRun.totalOffersFound = results.size
                finalRun.totalVariantsFound = results.size
                database.updateScanRun(finalRun)
            }

            results
        } catch (e: Exception) {
            val finalRun = database.getLatestScanRun()
            if (finalRun != null && finalRun.id == runId) {
                finalRun.completedAt = System.currentTimeMillis()
                finalRun.status = "FAILED"
                finalRun.errorLog = e.localizedMessage ?: e.javaClass.simpleName
                database.updateScanRun(finalRun)
            }
            throw e
        } finally {
            isRunning = false
        }
    }

    private fun parseChannelHtml(
        html: String,
        supplier: Supplier,
        targetQuery: String,
        usdRate: Double,
        usdtRate: Double
    ): List<PriceObservation> {
        val list = mutableListOf<PriceObservation>()
        val msgRegex = Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL)
        val matcher = msgRegex.matcher(html)
        val lowerTarget = targetQuery.lowercase(Locale.ROOT)

        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            val clean = raw.replace("<br\\s*/?>".toRegex(), "\n")
                .replace("<[^>]+>".toRegex(), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim()

            // Skip referral rewards
            if (clean.contains("referral", ignoreCase = true) ||
                clean.contains("invite friends", ignoreCase = true) ||
                clean.contains("earn free balance", ignoreCase = true)
            ) {
                continue
            }

            if (clean.contains(lowerTarget, ignoreCase = true) || targetQuery.isEmpty()) {
                val (price, currency) = PriceExtractor.extractPrice(clean)
                if (price != null && price >= 0.20) {
                    val duration = PlanMatcher.parseDuration(clean)
                    val accessType = PlanMatcher.parseAccessType(clean)
                    val matchingKey = PlanMatcher.generateMatchingKey(targetQuery, clean.lines().firstOrNull() ?: targetQuery, duration, accessType)

                    val pkr = when (currency.uppercase(Locale.ROOT)) {
                        "PKR" -> price // 1.0 multiplier
                        "USDT" -> price * usdtRate
                        "USD" -> price * usdRate
                        else -> price * usdRate
                    }

                    list.add(
                        PriceObservation(
                            supplierId = supplier.id,
                            supplierName = supplier.canonicalName,
                            supplierHandle = supplier.primaryHandle,
                            productName = targetQuery.ifEmpty { "Digital Tool" },
                            planName = clean.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: targetQuery,
                            duration = duration,
                            accessType = accessType,
                            matchingKey = matchingKey,
                            amount = price,
                            currency = currency,
                            amountPkr = pkr,
                            warranty = PriceExtractor.extractWarranty(clean),
                            stockStatus = PriceExtractor.extractStock(clean),
                            verificationStatus = VerificationStatus.USER_SUPPLIED_UNVERIFIED,
                            riskLevel = if (price < 1.0 && targetQuery.contains("ChatGPT", ignoreCase = true)) RiskLevel.HIGH else RiskLevel.LOW,
                            captureMethod = "LIVE_TELEGRAM_PREVIEW",
                            originalRawText = clean.take(250),
                            sourceUrl = supplier.publicUrl,
                            observedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return list
    }
}
