package tech.aitoolgems.supplierpricefinder.data.model

/**
 * High-fidelity navigation state for the Telegram Supplier Bot Crawler.
 */
enum class CrawlerState {
    QUEUED,
    OPENING_BOT,
    START_SENT,
    WAITING_FOR_RESPONSE,
    LANGUAGE_SELECTION,
    MEMBERSHIP_GATE,
    JOIN_APPROVAL_REQUIRED,
    CATALOG_MENU,
    CATEGORY_MENU,
    PRODUCT_LIST,
    PRODUCT_DETAIL,
    VARIANT_SELECTION,
    PAGINATION,
    RETURNING_TO_PARENT,
    SAVE_EVIDENCE,
    COMPLETED,
    PARTIAL,
    MANUAL_REVIEW_REQUIRED,
    AUTH_REQUIRED,
    RATE_LIMITED,
    RESPONSE_TIMEOUT,
    NETWORK_ERROR,
    BOT_NOT_FOUND,
    BOT_UNAVAILABLE,
    BLOCKED_TRANSACTION,
    FAILED
}

/**
 * Underlying interaction mechanism for a bot keyboard element.
 */
enum class CrawlerButtonType {
    INLINE_CALLBACK,
    REPLY_KEYBOARD_TEXT,
    URL_LINK,
    WEB_APP,
    BUY_TRANSACTION,
    REQUEST_CONTACT_OR_LOCATION,
    UNKNOWN
}

/**
 * Represents an individual interactive button exposed by a Telegram bot.
 */
data class CrawlerButton(
    val text: String,
    val type: CrawlerButtonType,
    val payload: String = "",
    val url: String? = null
)

/**
 * Represents a menu screen rendered by a bot with text and interactive keyboard rows.
 */
data class CrawlerMenu(
    val chatId: Long,
    val messageId: Long,
    val rawText: String,
    val buttons: List<List<CrawlerButton>> = emptyList(),
    val editDate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Stable fingerprint of the menu to identify distinct states and detect loops.
     */
    fun computeFingerprint(): String {
        val normText = rawText.lowercase().replace("\\s+".toRegex(), " ").take(100)
        val buttonSummary = buttons.flatten().joinToString("|") { "${it.type.name}:${it.text.trim().lowercase()}" }
        return "${normText.hashCode()}_${buttonSummary.hashCode()}"
    }
}

/**
 * Record of an entire crawl execution run across one or all suppliers.
 */
data class ScanRun(
    val id: Long = 0,
    val runUuid: String,
    val startedAt: Long,
    var completedAt: Long? = null,
    val trigger: String, // FULL_CATALOG_SCAN, SINGLE_SUPPLIER_SCAN, MANUAL_RESUME
    var status: String,  // RUNNING, COMPLETED, PARTIAL, CANCELLED, FAILED
    val totalSuppliers: Int,
    var completedSuppliers: Int = 0,
    var partialSuppliers: Int = 0,
    var blockedSuppliers: Int = 0,
    var totalOffersFound: Int = 0,
    var totalVariantsFound: Int = 0,
    var errorLog: String = ""
)

/**
 * Resumable checkpoint for a supplier within a scan run.
 */
data class NavigationCheckpoint(
    val id: Long = 0,
    val runId: Long,
    val supplierId: Long,
    val supplierHandle: String,
    val state: CrawlerState,
    val currentDepth: Int,
    val visitedFingerprints: String, // comma-separated fingerprints
    val breadcrumbPath: String,      // JSON or arrow-separated navigation trail
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Persistent learned pattern for a supplier (e.g. language or ambiguous catalog button confirmation).
 */
data class SupplierPlaybook(
    val id: Long = 0,
    val supplierId: Long,
    val menuFingerprint: String,
    val actionLabel: String,
    val actionType: CrawlerButtonType,
    val payload: String,
    val targetState: CrawlerState,
    val isUserApproved: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Item requiring explicit human operator review (e.g. channel membership gate, ambiguous bare Buy button).
 */
data class ManualReviewItem(
    val id: Long = 0,
    val runId: Long,
    val supplierId: Long,
    val supplierHandle: String,
    val itemType: String, // MEMBERSHIP_GATE, AMBIGUOUS_CATALOG_ENTRY, UNSUPPORTED_MEDIA, AMBIGUOUS_PRICE
    val title: String,
    val promptMessage: String,
    val targetHandleOrUrl: String? = null,
    val optionsJson: String = "",
    val rawEvidence: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Auditable step event logged during a crawl run for complete transparency.
 */
data class NavigationTimelineEvent(
    val id: Long = 0,
    val runId: Long,
    val supplierId: Long,
    val supplierHandle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val state: CrawlerState,
    val actionLabel: String,
    val actionType: CrawlerButtonType,
    val decisionReason: String,
    val outcomeStatus: String
)
