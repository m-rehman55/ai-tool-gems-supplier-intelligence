package tech.aitoolgems.supplierpricefinder.data.model

data class Supplier(
    val id: Long = 0,
    val canonicalName: String,
    val primaryHandle: String,
    val allHandles: String = "",
    val entityType: String = "BOT",
    val publicUrl: String? = null,
    val supportedCurrencies: String = "USD,USDT",
    val isEnabled: Boolean = true,
    val reliabilityScore: Float = 75f,
    val riskScore: Float = 15f,
    val riskFlags: String = "",
    val notes: String = "",
    val lastCheckedAt: Long? = null,
    val source: String = "SEED_DATA",
    val telegramChatId: Long? = null,
    val lastSyncAt: Long? = null,
    val hasActiveMenu: Boolean = false
)
