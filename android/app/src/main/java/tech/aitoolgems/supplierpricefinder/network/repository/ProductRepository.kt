package tech.aitoolgems.supplierpricefinder.network.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.aitoolgems.supplierpricefinder.data.local.LocalDatabase
import tech.aitoolgems.supplierpricefinder.data.local.SecureStorage
import tech.aitoolgems.supplierpricefinder.data.model.*
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.network.NetworkModule
import tech.aitoolgems.supplierpricefinder.network.api.BunnyApiService
import tech.aitoolgems.supplierpricefinder.network.api.BunnyProduct
import tech.aitoolgems.supplierpricefinder.network.api.CanbosoApiService
import tech.aitoolgems.supplierpricefinder.network.api.CanbosoProduct

data class ApiSyncResult(
    val bunnyCount: Int = 0,
    val canbosoCount: Int = 0,
    val totalObservationsSaved: Int = 0,
    val errors: List<String> = emptyList()
)

object ProductRepository {
    private const val TAG = "ProductRepository"
    private const val USD_TO_PKR_RATE = 278.50

    private val bunnyService: BunnyApiService by lazy {
        NetworkModule.bunnyRetrofit.create(BunnyApiService::class.java)
    }

    private val canbosoService: CanbosoApiService by lazy {
        NetworkModule.canbosoRetrofit.create(CanbosoApiService::class.java)
    }

    suspend fun getBunnyProducts(apiKey: String): List<BunnyProduct> = withContext(Dispatchers.IO) {
        val response = bunnyService.getProducts(apiKey)
        response.products
    }

    suspend fun getCanbosoProducts(apiKey: String): List<CanbosoProduct> = withContext(Dispatchers.IO) {
        val response = canbosoService.getProducts(apiKey)
        response.products
    }

    /**
     * Synchronizes live product catalogs from authorized reseller APIs (Bunny AI & Canboso),
     * registering them as verified suppliers with source = AUTHORIZED_API, transforming
     * each catalog item into a canonical PriceObservation, and persisting them into LocalDatabase.
     */
    suspend fun syncAuthorizedApiProducts(
        database: LocalDatabase,
        secureStorage: SecureStorage
    ): ApiSyncResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var bunnyProducts = emptyList<BunnyProduct>()
        var canbosoProducts = emptyList<CanbosoProduct>()
        var totalSaved = 0

        // 1. Fetch Bunny AI Reseller Products
        try {
            val key = secureStorage.bunnyApiKey
            bunnyProducts = getBunnyProducts(key)
            Log.d(TAG, "Successfully fetched ${bunnyProducts.size} products from Bunny AI API")
        } catch (e: Exception) {
            val err = "Bunny API error: ${e.message ?: "Failed to connect"}"
            Log.e(TAG, err, e)
            errors.add(err)
        }

        // 2. Fetch Canboso Reseller Products
        try {
            val key = secureStorage.canbosoApiKey
            canbosoProducts = getCanbosoProducts(key)
            Log.d(TAG, "Successfully fetched ${canbosoProducts.size} products from Canboso API")
        } catch (e: Exception) {
            val err = "Canboso API error: ${e.message ?: "Failed to connect"}"
            Log.e(TAG, err, e)
            errors.add(err)
        }

        // 3. Register or get Bunny AI Supplier
        val bunnySupplierId = database.addSupplier(
            Supplier(
                canonicalName = "Bunny AI Reseller Hub",
                primaryHandle = "@bunnytools_bot",
                allHandles = "@bunnytools_bot,https://bunnytoolsbot.tech",
                entityType = "API",
                publicUrl = "https://bunnytoolsbot.tech",
                supportedCurrencies = "USD,USDT",
                isEnabled = true,
                reliabilityScore = 92.0f,
                riskScore = 8.0f,
                source = "AUTHORIZED_API",
                notes = "Authorized direct reseller API hub"
            )
        )

        // 4. Register or get Canboso Supplier
        val canbosoSupplierId = database.addSupplier(
            Supplier(
                canonicalName = "Canboso Reseller Hub",
                primaryHandle = "@canboso_bot",
                allHandles = "@canboso_bot,https://canboso.com",
                entityType = "API",
                publicUrl = "https://canboso.com",
                supportedCurrencies = "USD",
                isEnabled = true,
                reliabilityScore = 90.0f,
                riskScore = 10.0f,
                source = "AUTHORIZED_API",
                notes = "Authorized direct wholesale API"
            )
        )

        // 5. Ingest Bunny products into LocalDatabase
        val now = System.currentTimeMillis()
        for (bp in bunnyProducts) {
            try {
                val fullText = "${bp.name} ${bp.description ?: ""}"
                val canonicalProduct = PlanMatcher.extractProductName(fullText)
                val duration = PlanMatcher.parseDuration(fullText)
                val accessType = PlanMatcher.parseAccessType(fullText)
                val cleanPlanName = bp.name.trim()
                val matchingKey = PlanMatcher.generateMatchingKey(canonicalProduct, cleanPlanName, duration, accessType)

                val stock = bp.stockCount ?: 0
                val stockText = if (stock > 0) "$stock in stock" else "Out of Stock"
                val warranty = if (fullText.contains("fw", ignoreCase = true) || fullText.contains("warranty", ignoreCase = true)) {
                    val fwMatch = "(\\d+)\\s*(?:days?|d)?\\s*fw".toRegex(RegexOption.IGNORE_CASE).find(fullText)
                    if (fwMatch != null) "${fwMatch.groupValues[1]} Days FW" else "Warranty Stated"
                } else {
                    "Not Stated"
                }

                val amountUsd = bp.basePrice
                val amountPkr = amountUsd * USD_TO_PKR_RATE

                val obs = PriceObservation(
                    supplierId = bunnySupplierId,
                    supplierName = "Bunny AI Reseller Hub",
                    supplierHandle = "@bunnytools_bot",
                    productName = canonicalProduct,
                    planName = cleanPlanName,
                    duration = duration,
                    accessType = accessType,
                    matchingKey = matchingKey,
                    amount = amountUsd,
                    currency = "USD",
                    amountPkr = amountPkr,
                    warranty = warranty,
                    stockStatus = stockText,
                    verificationStatus = VerificationStatus.LIVE_OBSERVED,
                    riskLevel = RiskLevel.LOW,
                    captureMethod = "BUNNY_AUTHORIZED_API",
                    originalRawText = bp.description ?: bp.name,
                    sourceUrl = "https://bunnytoolsbot.tech/developer",
                    observedAt = now
                )
                database.saveObservation(obs)
                totalSaved++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ingest Bunny product: ${bp.name}", e)
            }
        }

        // 6. Ingest Canboso products into LocalDatabase
        for (cp in canbosoProducts) {
            try {
                val fullText = "${cp.name} ${cp.description ?: ""}"
                val canonicalProduct = PlanMatcher.extractProductName(fullText)
                val duration = PlanMatcher.parseDuration(fullText)
                val accessType = PlanMatcher.parseAccessType(fullText)
                val cleanPlanName = cp.name.trim()
                val matchingKey = PlanMatcher.generateMatchingKey(canonicalProduct, cleanPlanName, duration, accessType)

                val availableStock = cp.availability?.available ?: 0
                val stockText = if (availableStock > 0) "$availableStock in stock" else "Out of Stock"

                val warranty = when {
                    fullText.contains("no warranty", ignoreCase = true) -> "No Warranty"
                    fullText.contains("warranty", ignoreCase = true) -> "Warranty Provided"
                    else -> "Not Stated"
                }

                val amountUsd = cp.price?.amount ?: 0.0
                val amountPkr = amountUsd * USD_TO_PKR_RATE

                val obs = PriceObservation(
                    supplierId = canbosoSupplierId,
                    supplierName = "Canboso Reseller Hub",
                    supplierHandle = "@canboso_bot",
                    productName = canonicalProduct,
                    planName = cleanPlanName,
                    duration = duration,
                    accessType = accessType,
                    matchingKey = matchingKey,
                    amount = amountUsd,
                    currency = "USD",
                    amountPkr = amountPkr,
                    warranty = warranty,
                    stockStatus = stockText,
                    verificationStatus = VerificationStatus.LIVE_OBSERVED,
                    riskLevel = RiskLevel.LOW,
                    captureMethod = "CANBOSO_AUTHORIZED_API",
                    originalRawText = cp.description ?: cp.name,
                    sourceUrl = "https://canboso.com",
                    observedAt = now
                )
                database.saveObservation(obs)
                totalSaved++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ingest Canboso product: ${cp.name}", e)
            }
        }

        ApiSyncResult(
            bunnyCount = bunnyProducts.size,
            canbosoCount = canbosoProducts.size,
            totalObservationsSaved = totalSaved,
            errors = errors
        )
    }
}
