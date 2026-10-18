package tech.aitoolgems.supplierpricefinder.network.api

import com.squareup.moshi.Json

// Data models for Canboso Reseller API (https://canboso.com/api/swagger)

data class CanbosoProductsResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "lang") val lang: String? = null,
    @Json(name = "walletCurrency") val walletCurrency: String? = null,
    @Json(name = "products") val products: List<CanbosoProduct> = emptyList()
)

data class CanbosoProduct(
    @Json(name = "productId") val productId: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "emoji") val emoji: String? = null,
    @Json(name = "productType") val productType: String? = null,
    @Json(name = "price") val price: CanbosoPrice? = null,
    @Json(name = "availability") val availability: CanbosoAvailability? = null
)

data class CanbosoPrice(
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "currency") val currency: String = "USD",
    @Json(name = "text") val text: String? = null
)

data class CanbosoAvailability(
    @Json(name = "available") val available: Int = 0,
    @Json(name = "sold") val sold: Int = 0
)
