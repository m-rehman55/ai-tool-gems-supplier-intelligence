package tech.aitoolgems.supplierpricefinder.network.api

import retrofit2.http.GET
import retrofit2.http.Header

// Canboso Reseller API service definitions (Read-only catalog inspection)
interface CanbosoApiService {
    @GET("api/v2/telegram-buyer/products")
    suspend fun getProducts(
        @Header("X-API-Key") apiKey: String
    ): CanbosoProductsResponse
}
