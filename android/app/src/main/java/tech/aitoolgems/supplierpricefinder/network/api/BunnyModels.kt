package tech.aitoolgems.supplierpricefinder.network.api

import com.squareup.moshi.Json

// Data models for Bunny AI Reseller API (https://bunnytoolsbot.tech/developer)

data class BunnyProductsResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "count") val count: Int = 0,
    @Json(name = "products") val products: List<BunnyProduct> = emptyList()
)

data class BunnyProduct(
    @Json(name = "product_id") val productId: Long,
    @Json(name = "category") val category: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "base_price") val basePrice: Double = 0.0,
    @Json(name = "stock_count") val stockCount: Int? = null,
    @Json(name = "stock_type") val stockType: String? = null,
    @Json(name = "description") val description: String? = null
)
