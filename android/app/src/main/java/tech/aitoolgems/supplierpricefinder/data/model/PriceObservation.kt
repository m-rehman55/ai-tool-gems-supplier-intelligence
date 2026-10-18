package tech.aitoolgems.supplierpricefinder.data.model

data class PriceObservation(
    val id: Long = 0,
    val supplierId: Long,
    val supplierName: String,
    val supplierHandle: String,
    val productName: String,
    val planName: String,
    val duration: String,
    val accessType: AccessType,
    val matchingKey: String,
    val amount: Double,
    val currency: String,
    val amountPkr: Double,
    val warranty: String = "Not Stated",
    val stockStatus: String = "Available",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val captureMethod: String = "ANDROID_BOT_SCANNER",
    val originalRawText: String,
    val sourceUrl: String? = null,
    val observedAt: Long = System.currentTimeMillis()
)
