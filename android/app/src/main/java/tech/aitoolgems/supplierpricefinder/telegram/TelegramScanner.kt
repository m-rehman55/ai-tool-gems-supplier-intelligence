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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface ScanProgressListener {
    fun onSupplierScanning(supplierHandle: String, message: String)
    fun onProductExtracted(observation: PriceObservation)
    fun onSupplierBlocked(supplierHandle: String, reason: String)
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

    var isRunning: Boolean = false
        private set

    suspend fun scanSuppliersForTool(
        targetTool: String,
        targetSupplierId: Long? = null,
        listener: ScanProgressListener? = null
    ): List<PriceObservation> = withContext(Dispatchers.IO) {
        isRunning = true
        try {
            val results = mutableListOf<PriceObservation>()
            val allSuppliers = database.getAllSuppliers().filter { it.isEnabled }
            val suppliers = if (targetSupplierId != null) {
                allSuppliers.filter { it.id == targetSupplierId }
            } else {
                allSuppliers
            }
            val targetQuery = targetTool.trim()

            val fxRateUsdToPkr = 280.0
            val fxRateUsdtToPkr = 282.5

            for (supplier in suppliers) {
                listener?.onSupplierScanning(supplier.primaryHandle, "Connecting to ${supplier.canonicalName}...")
                delay(120) // Respect rate limits and Telegram flood control

                val supplierQuotes = mutableListOf<PriceObservation>()

                // 1. Attempt live channel preview fetch from publicUrl or direct handle preview
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
                            supplierQuotes.addAll(liveQuotes)
                        }
                    }
                } catch (e: Exception) {
                    // Network timeout, rate limit, or VPN required -> log status for transparency
                }

                // 2. If live network didn't yield quotes, check stored observations for this supplier
                if (supplierQuotes.isEmpty()) {
                    val dbQuotes = database.getObservationsForSupplierAndQuery(supplier.id, targetQuery)
                    supplierQuotes.addAll(dbQuotes)
                }

                // 3. For every extracted quote, verify safety boundary and emit
                if (supplierQuotes.isNotEmpty()) {
                    for (obs in supplierQuotes) {
                        val safety = SafetyBoundary.checkButtonSafety(obs.planName)
                        if (safety.isSafe) {
                            if (results.none { it.id == obs.id || (it.matchingKey == obs.matchingKey && it.supplierId == obs.supplierId) }) {
                                results.add(obs)
                                database.saveObservation(obs)
                                listener?.onProductExtracted(obs)
                            }
                        } else {
                            listener?.onSupplierBlocked(supplier.primaryHandle, "Blocked: ${obs.planName} (${safety.reason})")
                        }
                    }
                    listener?.onSupplierScanning(supplier.primaryHandle, "Found ${supplierQuotes.size} quote(s) for '$targetQuery'")
                } else {
                    listener?.onSupplierScanning(supplier.primaryHandle, "Inspected. No current offers for '$targetQuery'.")
                }
            }
            results
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

            // Skip referral rewards ($0.10 wallet bonus spam)
            if (clean.contains("referral", ignoreCase = true) ||
                clean.contains("invite friends", ignoreCase = true) ||
                clean.contains("earn free balance", ignoreCase = true)
            ) {
                continue
            }

            // Filter for target query or include if target query is empty
            if (clean.contains(lowerTarget, ignoreCase = true) || targetQuery.isEmpty()) {
                val (price, currency) = PriceExtractor.extractPrice(clean)
                if (price != null && price >= 0.20) {
                    val duration = PlanMatcher.parseDuration(clean)
                    val accessType = PlanMatcher.parseAccessType(clean)
                    val matchingKey = PlanMatcher.generateMatchingKey(targetQuery, clean.lines().firstOrNull() ?: targetQuery, duration, accessType)

                    // Strict currency routing: Never multiply PKR by USD FX rate!
                    val pkr = when (currency.uppercase(Locale.ROOT)) {
                        "PKR" -> price
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
                            // Public channel preview quotes without cryptographic TDLib session are UNVERIFIED
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
