package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.*
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.*

/**
 * Tests for Master Prompt:
 * 1. Complete Per-Bot Product Catalog Profiling & Statistics.
 * 2. Price Fluctuation (Ups & Downs) tracking.
 * 3. Pakistani Resale Margin & Net Profit Calculator.
 * 4. WhatsApp / Commercial Quotation Formatting.
 */
class BotCatalogAndProfitIntelligenceTest {

    @Test
    fun testPakistaniResaleProfitMarginCalculation() {
        // Example: Wholesale cost is $4.80 USDT (~Rs. 1,356 PKR)
        val wholesalePkr = 1356.0
        val wholesaleUsd = 4.80

        // Case 1: 30% margin
        val calc30 = ProfitCalculation(
            wholesalePkr = wholesalePkr,
            wholesaleUsd = wholesaleUsd,
            marginPercentage = 30.0,
            fixedMarkupPkr = 0.0
        )

        // 1356 * 1.30 = 1762.8 -> rounded to nearest Rs. 50 = Rs. 1,800 PKR
        assertEquals(1800.0, calc30.suggestedRetailPkr, 0.001)
        assertEquals(444.0, calc30.netProfitPkr, 0.001)
        assertTrue(calc30.effectiveMarginPercent > 30.0)

        // Case 2: Fixed markup of Rs. 500
        val calcFixed = ProfitCalculation(
            wholesalePkr = wholesalePkr,
            wholesaleUsd = wholesaleUsd,
            marginPercentage = 0.0,
            fixedMarkupPkr = 500.0
        )

        // 1356 + 500 = 1856 -> rounded to nearest Rs. 50 = Rs. 1,900 PKR
        assertEquals(1900.0, calcFixed.suggestedRetailPkr, 0.001)
        assertEquals(544.0, calcFixed.netProfitPkr, 0.001)
    }

    @Test
    fun testPriceFluctuationDropAndIncreaseDetection() {
        val dropEntry = PriceHistoryEntry(
            id = 1,
            observationId = 10,
            supplierId = 1,
            productName = "Canva Pro",
            planName = "1 Year Team",
            oldAmount = 3.50,
            newAmount = 2.80,
            currency = "USD",
            amountPkr = 784.0,
            changedAt = System.currentTimeMillis()
        )

        assertTrue("Must be identified as price drop", dropEntry.isPriceDrop)
        assertFalse("Must not be identified as price increase", dropEntry.isPriceIncrease)
        assertEquals(-20.0, dropEntry.percentageChange ?: 0.0, 0.01)

        val hikeEntry = PriceHistoryEntry(
            id = 2,
            observationId = 11,
            supplierId = 1,
            productName = "ChatGPT Plus",
            planName = "1 Month Private",
            oldAmount = 4.50,
            newAmount = 5.40,
            currency = "USD",
            amountPkr = 1512.0,
            changedAt = System.currentTimeMillis()
        )

        assertTrue("Must be identified as price hike", hikeEntry.isPriceIncrease)
        assertFalse("Must not be identified as price drop", hikeEntry.isPriceDrop)
        assertEquals(20.0, hikeEntry.percentageChange ?: 0.0, 0.01)
    }

    @Test
    fun testBotCatalogSummaryAggregation() {
        val sampleProducts = listOf(
            PriceObservation(
                id = 1,
                supplierId = 5,
                supplierName = "PT Store BD",
                supplierHandle = "@ptstorebd_bot",
                productName = "ChatGPT",
                planName = "ChatGPT Plus 1 Month Private",
                duration = "1 Month",
                accessType = AccessType.PRIVATE,
                matchingKey = "chatgpt__plus__1_month__private",
                amount = 4.80,
                currency = "USDT",
                amountPkr = 1356.0,
                warranty = "Full Warranty",
                originalRawText = "ChatGPT Plus $4.80"
            ),
            PriceObservation(
                id = 2,
                supplierId = 5,
                supplierName = "PT Store BD",
                supplierHandle = "@ptstorebd_bot",
                productName = "Canva",
                planName = "Canva Pro 1 Year Edu",
                duration = "1 Year",
                accessType = AccessType.EDU,
                matchingKey = "canva__pro__1_year__edu",
                amount = 1.20,
                currency = "USDT",
                amountPkr = 339.0,
                warranty = "1 Year Replacement",
                originalRawText = "Canva Pro $1.20"
            ),
            PriceObservation(
                id = 3,
                supplierId = 5,
                supplierName = "PT Store BD",
                supplierHandle = "@ptstorebd_bot",
                productName = "CapCut",
                planName = "CapCut Pro 1 Month Shared",
                duration = "1 Month",
                accessType = AccessType.SHARED,
                matchingKey = "capcut__pro__1_month__shared",
                amount = 2.00,
                currency = "USDT",
                amountPkr = 565.0,
                warranty = "30 Days Warranty",
                originalRawText = "CapCut Pro $2.00"
            )
        )

        val minUsd = sampleProducts.minOf { it.amount }
        val maxUsd = sampleProducts.maxOf { it.amount }
        val minPkr = sampleProducts.minOf { it.amountPkr }
        val maxPkr = sampleProducts.maxOf { it.amountPkr }
        val distinctTools = sampleProducts.map { it.productName.lowercase() }.distinct().size

        val summary = BotCatalogSummary(
            supplierId = 5,
            supplierName = "PT Store BD",
            supplierHandle = "@ptstorebd_bot",
            source = SupplierSource.TELEGRAM_DISCOVERY.code,
            totalProducts = sampleProducts.size,
            minPriceUsd = minUsd,
            maxPriceUsd = maxUsd,
            minPricePkr = minPkr,
            maxPricePkr = maxPkr,
            distinctToolsCount = distinctTools,
            lastObservedAt = System.currentTimeMillis(),
            categories = sampleProducts.map { it.productName }.distinct(),
            products = sampleProducts
        )

        assertEquals(3, summary.totalProducts)
        assertEquals(3, summary.distinctToolsCount)
        assertEquals(1.20, summary.minPriceUsd, 0.001)
        assertEquals(4.80, summary.maxPriceUsd, 0.001)
        assertEquals(339.0, summary.minPricePkr, 0.001)
        assertEquals(1356.0, summary.maxPricePkr, 0.001)
        assertTrue(summary.categories.contains("ChatGPT"))
        assertTrue(summary.categories.contains("Canva"))
        assertTrue(summary.categories.contains("CapCut"))
    }

    @Test
    fun testCustomerQuotationFormatting() {
        val obs = PriceObservation(
            id = 1,
            supplierId = 1,
            supplierName = "DigiCart Tools",
            supplierHandle = "@getdigitaltools_bot",
            productName = "ChatGPT Plus",
            planName = "ChatGPT Plus 1 Month Private",
            duration = "1 Month",
            accessType = AccessType.PRIVATE,
            matchingKey = "chatgpt__plus__1_month__private",
            amount = 4.80,
            currency = "USDT",
            amountPkr = 1356.0,
            warranty = "Full Warranty (FW)",
            originalRawText = "ChatGPT Plus $4.80"
        )

        val calc = ProfitCalculation(obs.amountPkr, obs.amount, 25.0)

        val quote = """
✨ AI Tool Gems Pakistan ✨
🔥 Tool: ${obs.productName}
🏷 Plan: ${obs.planName}
⏱ Duration: ${obs.duration}
👤 Access: ${obs.accessType.name}
💰 Customer Price: Rs. ${String.format(java.util.Locale.ROOT, "%,.0f", calc.suggestedRetailPkr)} PKR
🛡 Warranty: ${obs.warranty}
⚡ Instant Activation & Full Replacement Guarantee
📩 Order Now via WhatsApp / aitoolgems.tech
        """.trimIndent()

        assertTrue(quote.contains("AI Tool Gems Pakistan"))
        assertTrue(quote.contains("ChatGPT Plus"))
        assertTrue(quote.contains("PRIVATE"))
        assertTrue(quote.contains("Full Warranty (FW)"))
        assertTrue(quote.contains("Customer Price: Rs. 1,700 PKR"))
    }
}
