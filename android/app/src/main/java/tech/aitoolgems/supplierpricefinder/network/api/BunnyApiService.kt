package tech.aitoolgems.supplierpricefinder.network.api

import retrofit2.http.GET
import retrofit2.http.Header

// Bunny AI Reseller API service definitions (Read-only catalog inspection)
interface BunnyApiService {
    @GET("api/v1/products")
    suspend fun getProducts(
        @Header("X-API-Key") apiKey: String
    ): BunnyProductsResponse
}
