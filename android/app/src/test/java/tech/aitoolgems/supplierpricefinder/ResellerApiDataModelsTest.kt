package tech.aitoolgems.supplierpricefinder

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import tech.aitoolgems.supplierpricefinder.data.model.AccessType
import tech.aitoolgems.supplierpricefinder.data.model.SupplierSource
import tech.aitoolgems.supplierpricefinder.engine.PlanMatcher
import tech.aitoolgems.supplierpricefinder.network.api.BunnyProductsResponse
import tech.aitoolgems.supplierpricefinder.network.api.CanbosoProductsResponse

class ResellerApiDataModelsTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testBunnyApiResponseDeserialization() {
        val sampleJson = """
        {
          "success": true,
          "count": 2,
          "products": [
            {
              "product_id": 3,
              "category": "General",
              "name": "CapCut Teams Plan 30 Days 25 Days FW",
              "base_price": 1.80,
              "stock_count": 14,
              "stock_type": "unique",
              "description": "Format: Mail Pass. Limit login to multiple devices."
            },
            {
              "product_id": 12,
              "category": "AI Tools",
              "name": "ChatGPT Plus Private 1 Month",
              "base_price": 4.50,
              "stock_count": 0,
              "stock_type": "account",
              "description": "Full warranty 30 days"
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(BunnyProductsResponse::class.java)
        val response = adapter.fromJson(sampleJson)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals(2, response.count)
        assertEquals(2, response.products.size)

        val capcut = response.products[0]
        assertEquals(3L, capcut.productId)
        assertEquals("CapCut Teams Plan 30 Days 25 Days FW", capcut.name)
        assertEquals(1.80, capcut.basePrice, 0.001)
        assertEquals(14, capcut.stockCount)

        // Verify PlanMatcher mapping
        assertEquals("CapCut Pro", PlanMatcher.extractProductName(capcut.name))
        assertEquals("30 Days", PlanMatcher.parseDuration(capcut.name))
        assertEquals(AccessType.TEAM, PlanMatcher.parseAccessType(capcut.name))
    }

    @Test
    fun testCanbosoApiResponseDeserialization() {
        val sampleJson = """
        {
          "success": true,
          "lang": "en",
          "walletCurrency": "USD",
          "products": [
            {
              "productId": "6ac8dee6fac01298222713dc",
              "name": "Unlimited API Unlimit Token Claude 1 Day (10rpm) - no warranty",
              "description": "Models: Full model Fable, Claude Opus, Sonnet, Haiku. Warranty policy: No warranty",
              "emoji": "claude",
              "productType": "account",
              "price": {
                "amount": 11.39,
                "currency": "USD",
                "text": "$11.39"
              },
              "availability": {
                "available": 5,
                "sold": 8
              },
              "promotions": []
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(CanbosoProductsResponse::class.java)
        val response = adapter.fromJson(sampleJson)

        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals("USD", response.walletCurrency)
        assertEquals(1, response.products.size)

        val claude = response.products[0]
        assertEquals("6ac8dee6fac01298222713dc", claude.productId)
        assertEquals("Unlimited API Unlimit Token Claude 1 Day (10rpm) - no warranty", claude.name)
        assertNotNull(claude.price)
        assertEquals(11.39, claude.price!!.amount, 0.001)
        assertEquals("USD", claude.price!!.currency)
        assertNotNull(claude.availability)
        assertEquals(5, claude.availability!!.available)

        // Verify PlanMatcher mapping
        assertEquals("Claude Pro", PlanMatcher.extractProductName(claude.name))
        assertEquals("1 Day", PlanMatcher.parseDuration(claude.name))
        assertEquals(AccessType.API, PlanMatcher.parseAccessType(claude.name))
    }

    @Test
    fun testAuthorizedApiSupplierSourceEnum() {
        assertEquals("AUTHORIZED_API", SupplierSource.AUTHORIZED_API.code)
        assertEquals("Authorized API", SupplierSource.AUTHORIZED_API.label)
    }
}
