package tech.aitoolgems.supplierpricefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.engine.PriceExtractor

class PriceExtractorTest {

    @Test
    fun testDirectPriceExtraction() {
        val (price1, curr1) = PriceExtractor.extractPrice("ChatGPT Plus (1 Month Private) - Price: $4.80 USDT")
        assertEquals(4.80, price1 ?: 0.0, 0.001)
        assertEquals("USDT", curr1)

        val (price2, curr2) = PriceExtractor.extractPrice("Google Gemini Pro Wholesale: $0.45 USD")
        assertEquals(0.45, price2 ?: 0.0, 0.001)
        assertEquals("USD", curr2)

        val (price3, curr3) = PriceExtractor.extractPrice("Flash Sale! $10.00 → $5.00 USDT")
        assertEquals(5.00, price3 ?: 0.0, 0.001)
        assertEquals("USDT", curr3)
    }

    @Test
    fun testRejectReferralAndBonusMessages() {
        val referralMsg = "🎁 Earn FREE balance! Invite your friends to AI TOOLS and get $0.10 in your wallet"
        assertTrue(PriceExtractor.isReferralOrBonusMessage(referralMsg))

        val (price, _) = PriceExtractor.extractPrice(referralMsg)
        assertNull("Referral reward must not be parsed as a product price!", price)
    }

    @Test
    fun testWarrantyAndStockExtraction() {
        val text1 = "ChatGPT Plus - Full Warranty (FW) | Stock: 25 available"
        assertEquals("Full Warranty (FW)", PriceExtractor.extractWarranty(text1))
        assertEquals("25 in stock", PriceExtractor.extractStock(text1))

        val text2 = "CapCut Pro - 1 Month Warranty | Current stock: 12"
        assertEquals("1 Month Warranty", PriceExtractor.extractWarranty(text2))
        assertEquals("12 in stock", PriceExtractor.extractStock(text2))

        val text3 = "No Warranty (NW) | Currently sold out"
        assertEquals("No Warranty (NW)", PriceExtractor.extractWarranty(text3))
        assertEquals("Out of stock", PriceExtractor.extractStock(text3))
    }

    @Test
    fun testPkrPriceExtraction() {
        val (price1, curr1) = PriceExtractor.extractPrice("Canva Pro 1 Year Private: Rs 2800")
        assertEquals(2800.0, price1 ?: 0.0, 0.001)
        assertEquals("PKR", curr1)

        val (price2, curr2) = PriceExtractor.extractPrice("CapCut 1 Month: PKR 850")
        assertEquals(850.0, price2 ?: 0.0, 0.001)
        assertEquals("PKR", curr2)
    }
}
