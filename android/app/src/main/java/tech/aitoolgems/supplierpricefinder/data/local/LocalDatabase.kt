package tech.aitoolgems.supplierpricefinder.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import tech.aitoolgems.supplierpricefinder.data.model.*

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "supplier_price_finder.db"
        const val DATABASE_VERSION = 5

        const val TABLE_SUPPLIERS = "suppliers"
        const val TABLE_OBSERVATIONS = "price_observations"
        const val TABLE_PRICE_HISTORY = "price_history"
        const val TABLE_SCAN_RUNS = "scan_runs"
        const val TABLE_NAVIGATION_CHECKPOINTS = "navigation_checkpoints"
        const val TABLE_SUPPLIER_PLAYBOOKS = "supplier_playbooks"
        const val TABLE_MANUAL_REVIEW_QUEUE = "manual_review_queue"
        const val TABLE_NAVIGATION_TIMELINE = "navigation_timeline"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SUPPLIERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                canonical_name TEXT NOT NULL UNIQUE,
                primary_handle TEXT NOT NULL,
                all_handles TEXT,
                entity_type TEXT DEFAULT 'BOT',
                public_url TEXT,
                supported_currencies TEXT,
                is_enabled INTEGER DEFAULT 1,
                reliability_score REAL DEFAULT 75.0,
                risk_score REAL DEFAULT 15.0,
                risk_flags TEXT,
                notes TEXT,
                last_checked_at INTEGER,
                source TEXT DEFAULT 'SEED_DATA',
                telegram_chat_id INTEGER,
                last_sync_at INTEGER,
                has_active_menu INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_OBSERVATIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER,
                supplier_name TEXT NOT NULL,
                supplier_handle TEXT NOT NULL,
                product_name TEXT NOT NULL,
                plan_name TEXT NOT NULL,
                duration TEXT NOT NULL,
                access_type TEXT NOT NULL,
                matching_key TEXT NOT NULL,
                amount REAL NOT NULL,
                currency TEXT NOT NULL,
                amount_pkr REAL NOT NULL,
                warranty TEXT,
                stock_status TEXT,
                verification_status TEXT,
                risk_level TEXT,
                capture_method TEXT,
                original_raw_text TEXT NOT NULL,
                source_url TEXT,
                observed_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PRICE_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                observation_id INTEGER,
                supplier_id INTEGER,
                product_name TEXT NOT NULL,
                plan_name TEXT NOT NULL,
                old_amount REAL,
                new_amount REAL NOT NULL,
                currency TEXT NOT NULL,
                amount_pkr REAL NOT NULL,
                changed_at INTEGER NOT NULL
            )
        """.trimIndent())

        createAdvancedCrawlerTables(db)
        seedInitialData(db)
    }

    private fun createAdvancedCrawlerTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SCAN_RUNS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_uuid TEXT NOT NULL UNIQUE,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                trigger TEXT NOT NULL,
                status TEXT NOT NULL,
                total_suppliers INTEGER NOT NULL,
                completed_suppliers INTEGER DEFAULT 0,
                partial_suppliers INTEGER DEFAULT 0,
                blocked_suppliers INTEGER DEFAULT 0,
                total_offers_found INTEGER DEFAULT 0,
                total_variants_found INTEGER DEFAULT 0,
                error_log TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_NAVIGATION_CHECKPOINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL,
                supplier_id INTEGER NOT NULL,
                supplier_handle TEXT NOT NULL,
                state TEXT NOT NULL,
                current_depth INTEGER NOT NULL,
                visited_fingerprints TEXT,
                breadcrumb_path TEXT,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SUPPLIER_PLAYBOOKS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER NOT NULL,
                menu_fingerprint TEXT NOT NULL,
                action_label TEXT NOT NULL,
                action_type TEXT NOT NULL,
                payload TEXT,
                target_state TEXT NOT NULL,
                is_user_approved INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_MANUAL_REVIEW_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL,
                supplier_id INTEGER NOT NULL,
                supplier_handle TEXT NOT NULL,
                item_type TEXT NOT NULL,
                title TEXT NOT NULL,
                prompt_message TEXT NOT NULL,
                target_handle_or_url TEXT,
                options_json TEXT,
                raw_evidence TEXT,
                status TEXT DEFAULT 'PENDING',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_NAVIGATION_TIMELINE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL,
                supplier_id INTEGER NOT NULL,
                supplier_handle TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                state TEXT NOT NULL,
                action_label TEXT NOT NULL,
                action_type TEXT NOT NULL,
                decision_reason TEXT NOT NULL,
                outcome_status TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive SQLite migration (Never drop tables!)
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_PRICE_HISTORY (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observation_id INTEGER,
                    supplier_id INTEGER,
                    product_name TEXT NOT NULL,
                    plan_name TEXT NOT NULL,
                    old_amount REAL,
                    new_amount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    amount_pkr REAL NOT NULL,
                    changed_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
        if (oldVersion < 4) {
            createAdvancedCrawlerTables(db)
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE $TABLE_SUPPLIERS ADD COLUMN source TEXT DEFAULT 'SEED_DATA'")
                db.execSQL("ALTER TABLE $TABLE_SUPPLIERS ADD COLUMN telegram_chat_id INTEGER")
                db.execSQL("ALTER TABLE $TABLE_SUPPLIERS ADD COLUMN last_sync_at INTEGER")
                db.execSQL("ALTER TABLE $TABLE_SUPPLIERS ADD COLUMN has_active_menu INTEGER DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_SCAN_RUNS ADD COLUMN search_policy TEXT DEFAULT 'FAST_LOOKUP'")
            } catch (e: Exception) {
                // Ignore if column already exists
            }
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        // Prepopulate canonical bots
        for (sup in SeedSuppliers.CANONICAL_BOTS) {
            val cv = ContentValues().apply {
                put("canonical_name", sup.canonicalName)
                put("primary_handle", sup.primaryHandle)
                put("all_handles", sup.allHandles)
                put("entity_type", sup.entityType)
                put("public_url", sup.publicUrl)
                put("supported_currencies", sup.supportedCurrencies)
                put("is_enabled", if (sup.isEnabled) 1 else 0)
                put("reliability_score", sup.reliabilityScore)
                put("risk_score", sup.riskScore)
                put("risk_flags", sup.riskFlags)
                put("notes", sup.notes)
            }
            db.insertWithOnConflict(TABLE_SUPPLIERS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        // Prepopulate verified wholesale price catalog observations
        for (obs in SeedObservations.CANONICAL_OBSERVATIONS) {
            val cv = ContentValues().apply {
                put("supplier_id", obs.supplierId)
                put("supplier_name", obs.supplierName)
                put("supplier_handle", obs.supplierHandle)
                put("product_name", obs.productName)
                put("plan_name", obs.planName)
                put("duration", obs.duration)
                put("access_type", obs.accessType.name)
                put("matching_key", obs.matchingKey)
                put("amount", obs.amount)
                put("currency", obs.currency)
                put("amount_pkr", obs.amountPkr)
                put("warranty", obs.warranty)
                put("stock_status", obs.stockStatus)
                put("verification_status", obs.verificationStatus.name)
                put("risk_level", obs.riskLevel.name)
                put("capture_method", obs.captureMethod)
                put("original_raw_text", obs.originalRawText)
                put("source_url", obs.sourceUrl)
                put("observed_at", obs.observedAt)
            }
            db.insert(TABLE_OBSERVATIONS, null, cv)
        }
    }

    fun ensureInitialData() {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_OBSERVATIONS", null)
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        if (count == 0) {
            seedInitialData(db)
        }
    }

    fun getAllSuppliers(filterSource: String? = null): List<Supplier> {
        val list = mutableListOf<Supplier>()
        val db = readableDatabase
        val query = when (filterSource?.uppercase()) {
            "TELEGRAM", "TELEGRAM_DISCOVERY" -> "SELECT * FROM $TABLE_SUPPLIERS WHERE source = 'TELEGRAM_DISCOVERY' ORDER BY canonical_name ASC"
            "SEED", "SEED_DATA" -> "SELECT * FROM $TABLE_SUPPLIERS WHERE source = 'SEED_DATA' ORDER BY canonical_name ASC"
            "IMPORTED" -> "SELECT * FROM $TABLE_SUPPLIERS WHERE source = 'IMPORTED' ORDER BY canonical_name ASC"
            "API", "AUTHORIZED_API" -> "SELECT * FROM $TABLE_SUPPLIERS WHERE source = 'AUTHORIZED_API' ORDER BY canonical_name ASC"
            else -> "SELECT * FROM $TABLE_SUPPLIERS ORDER BY canonical_name ASC"
        }
        val cursor = db.rawQuery(query, null)
        cursor.use { c ->
            val sourceIdx = c.getColumnIndex("source")
            val tgChatIdIdx = c.getColumnIndex("telegram_chat_id")
            val lastSyncIdx = c.getColumnIndex("last_sync_at")
            val activeMenuIdx = c.getColumnIndex("has_active_menu")

            while (c.moveToNext()) {
                val sourceVal = if (sourceIdx >= 0 && !c.isNull(sourceIdx)) c.getString(sourceIdx) else "SEED_DATA"
                val tgChatIdVal = if (tgChatIdIdx >= 0 && !c.isNull(tgChatIdIdx)) c.getLong(tgChatIdIdx) else null
                val lastSyncVal = if (lastSyncIdx >= 0 && !c.isNull(lastSyncIdx)) c.getLong(lastSyncIdx) else null
                val activeMenuVal = if (activeMenuIdx >= 0) c.getInt(activeMenuIdx) == 1 else false

                list.add(
                    Supplier(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        canonicalName = c.getString(c.getColumnIndexOrThrow("canonical_name")),
                        primaryHandle = c.getString(c.getColumnIndexOrThrow("primary_handle")),
                        allHandles = c.getString(c.getColumnIndexOrThrow("all_handles")) ?: "",
                        entityType = c.getString(c.getColumnIndexOrThrow("entity_type")) ?: "BOT",
                        publicUrl = c.getString(c.getColumnIndexOrThrow("public_url")),
                        supportedCurrencies = c.getString(c.getColumnIndexOrThrow("supported_currencies")) ?: "USD,USDT",
                        isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1,
                        reliabilityScore = c.getFloat(c.getColumnIndexOrThrow("reliability_score")),
                        riskScore = c.getFloat(c.getColumnIndexOrThrow("risk_score")),
                        riskFlags = c.getString(c.getColumnIndexOrThrow("risk_flags")) ?: "",
                        notes = c.getString(c.getColumnIndexOrThrow("notes")) ?: "",
                        lastCheckedAt = if (c.isNull(c.getColumnIndexOrThrow("last_checked_at"))) null else c.getLong(c.getColumnIndexOrThrow("last_checked_at")),
                        source = sourceVal,
                        telegramChatId = tgChatIdVal,
                        lastSyncAt = lastSyncVal,
                        hasActiveMenu = activeMenuVal
                    )
                )
            }
        }
        return list
    }

    fun syncDiscoveredTelegramBots(bots: List<DiscoveredTelegramBot>): Int {
        val db = writableDatabase
        var syncedCount = 0
        val now = System.currentTimeMillis()
        for (bot in bots) {
            val handle = if (bot.username.isNotBlank()) "@${bot.username.removePrefix("@")}" else "@bot_${bot.chatId}"
            val canonicalName = if (bot.title.isNotBlank()) bot.title else handle

            // Check existing by telegram_chat_id or primary_handle or canonical_name
            val normHandle = handle.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)
            val cursor = db.rawQuery(
                "SELECT id FROM $TABLE_SUPPLIERS WHERE telegram_chat_id = ? OR lower(replace(primary_handle, '@', '')) = ? OR canonical_name = ?",
                arrayOf(bot.chatId.toString(), normHandle, canonicalName)
            )
            var existingId: Long? = null
            cursor.use {
                if (it.moveToFirst()) {
                    existingId = it.getLong(0)
                }
            }

            val cv = ContentValues().apply {
                put("canonical_name", canonicalName)
                put("primary_handle", handle)
                put("entity_type", "BOT")
                put("source", "TELEGRAM_DISCOVERY")
                put("telegram_chat_id", bot.chatId)
                put("last_sync_at", now)
                put("has_active_menu", if (bot.hasActiveMenu) 1 else 0)
                put("is_enabled", 1)
            }

            if (existingId != null) {
                db.update(TABLE_SUPPLIERS, cv, "id = ?", arrayOf(existingId.toString()))
            } else {
                cv.put("reliability_score", 85.0f)
                cv.put("risk_score", 10.0f)
                cv.put("supported_currencies", "USD,USDT,PKR")
                db.insert(TABLE_SUPPLIERS, null, cv)
            }
            syncedCount++
        }
        return syncedCount
    }

    fun getSuppliersCountBySource(): Map<String, Int> {
        val db = readableDatabase
        val map = mutableMapOf<String, Int>()
        val cursor = db.rawQuery("SELECT source, COUNT(*) FROM $TABLE_SUPPLIERS GROUP BY source", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val source = c.getString(0) ?: "SEED_DATA"
                val count = c.getInt(1)
                map[source] = count
            }
        }
        return map
    }

    fun setSupplierEnabled(supplierId: Long, enabled: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        db.update(TABLE_SUPPLIERS, cv, "id = ?", arrayOf(supplierId.toString()))
    }

    fun addSupplier(supplier: Supplier): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("canonical_name", supplier.canonicalName)
            put("primary_handle", supplier.primaryHandle)
            put("all_handles", supplier.allHandles)
            put("entity_type", supplier.entityType)
            put("public_url", supplier.publicUrl)
            put("supported_currencies", supplier.supportedCurrencies)
            put("is_enabled", if (supplier.isEnabled) 1 else 0)
            put("reliability_score", supplier.reliabilityScore)
            put("risk_score", supplier.riskScore)
            put("risk_flags", supplier.riskFlags)
            put("notes", supplier.notes)
            put("last_checked_at", System.currentTimeMillis())
            put("source", supplier.source)
            put("telegram_chat_id", supplier.telegramChatId)
            put("last_sync_at", supplier.lastSyncAt)
            put("has_active_menu", if (supplier.hasActiveMenu) 1 else 0)
        }

        // Normalized handle comparison to avoid orphaned observations or ID recreation
        val normHandle = supplier.primaryHandle.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)
        val existingCursor = db.rawQuery(
            "SELECT id FROM $TABLE_SUPPLIERS WHERE lower(replace(primary_handle, '@', '')) = ? OR canonical_name = ?",
            arrayOf(normHandle, supplier.canonicalName)
        )
        var existingId: Long? = null
        existingCursor.use {
            if (it.moveToFirst()) {
                existingId = it.getLong(0)
            }
        }

        return if (existingId != null) {
            db.update(TABLE_SUPPLIERS, cv, "id = ?", arrayOf(existingId.toString()))
            existingId!!
        } else {
            db.insert(TABLE_SUPPLIERS, null, cv)
        }
    }

    fun getAllWholesaleProducts(): List<PriceObservation> {
        val list = mutableListOf<PriceObservation>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_OBSERVATIONS ORDER BY product_name ASC, amount_pkr ASC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToObservation(c))
            }
        }
        return list
    }

    fun saveObservation(obs: PriceObservation): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("supplier_id", obs.supplierId)
            put("supplier_name", obs.supplierName)
            put("supplier_handle", obs.supplierHandle)
            put("product_name", obs.productName)
            put("plan_name", obs.planName)
            put("duration", obs.duration)
            put("access_type", obs.accessType.name)
            put("matching_key", obs.matchingKey)
            put("amount", obs.amount)
            put("currency", obs.currency)
            put("amount_pkr", obs.amountPkr)
            put("warranty", obs.warranty)
            put("stock_status", obs.stockStatus)
            put("verification_status", obs.verificationStatus.name)
            put("risk_level", obs.riskLevel.name)
            put("capture_method", obs.captureMethod)
            put("original_raw_text", obs.originalRawText)
            put("source_url", obs.sourceUrl)
            put("observed_at", obs.observedAt)
        }

        // Check if observation already exists for this supplier and matching key
        val existingCursor = db.rawQuery(
            "SELECT id, amount, currency, amount_pkr FROM $TABLE_OBSERVATIONS WHERE supplier_id = ? AND matching_key = ?",
            arrayOf(obs.supplierId.toString(), obs.matchingKey)
        )
        var existingId: Long? = null
        var previousAmount: Double? = null
        existingCursor.use {
            if (it.moveToFirst()) {
                existingId = it.getLong(0)
                previousAmount = it.getDouble(1)
            }
        }

        return if (existingId != null) {
            // Append-only history logging on price fluctuations (ups and downs)
            if (previousAmount != null && previousAmount != obs.amount) {
                val histCv = ContentValues().apply {
                    put("observation_id", existingId)
                    put("supplier_id", obs.supplierId)
                    put("product_name", obs.productName)
                    put("plan_name", obs.planName)
                    put("old_amount", previousAmount)
                    put("new_amount", obs.amount)
                    put("currency", obs.currency)
                    put("amount_pkr", obs.amountPkr)
                    put("changed_at", obs.observedAt)
                }
                db.insert(TABLE_PRICE_HISTORY, null, histCv)
            }
            db.update(TABLE_OBSERVATIONS, cv, "id = ?", arrayOf(existingId.toString()))
            existingId!!
        } else {
            val newId = db.insert(TABLE_OBSERVATIONS, null, cv)
            val histCv = ContentValues().apply {
                put("observation_id", newId)
                put("supplier_id", obs.supplierId)
                put("product_name", obs.productName)
                put("plan_name", obs.planName)
                put("old_amount", null as Double?)
                put("new_amount", obs.amount)
                put("currency", obs.currency)
                put("amount_pkr", obs.amountPkr)
                put("changed_at", obs.observedAt)
            }
            db.insert(TABLE_PRICE_HISTORY, null, histCv)
            newId
        }
    }

    fun getObservationsForQuery(query: String): List<PriceObservation> {
        val q = query.lowercase().trim()
        val list = mutableListOf<PriceObservation>()
        val db = readableDatabase
        val cursor = if (q.isEmpty()) {
            db.rawQuery("SELECT * FROM $TABLE_OBSERVATIONS ORDER BY amount_pkr ASC", null)
        } else {
            val terms = q.split("\\s+".toRegex()).filter { it.length > 1 }
            if (terms.isEmpty()) {
                db.rawQuery(
                    "SELECT * FROM $TABLE_OBSERVATIONS WHERE lower(product_name) LIKE ? OR lower(plan_name) LIKE ? OR lower(matching_key) LIKE ? OR lower(original_raw_text) LIKE ? ORDER BY amount_pkr ASC",
                    arrayOf("%$q%", "%$q%", "%$q%", "%$q%")
                )
            } else {
                val whereClauses = terms.map {
                    "(lower(product_name) LIKE ? OR lower(plan_name) LIKE ? OR lower(matching_key) LIKE ? OR lower(original_raw_text) LIKE ?)"
                }.joinToString(" OR ")
                val args = terms.flatMap { listOf("%$it%", "%$it%", "%$it%", "%$it%") }.toTypedArray()
                db.rawQuery(
                    "SELECT * FROM $TABLE_OBSERVATIONS WHERE $whereClauses ORDER BY amount_pkr ASC",
                    args
                )
            }
        }
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToObservation(c))
            }
        }
        return list
    }

    fun getObservationsForSupplierAndQuery(supplierId: Long, query: String): List<PriceObservation> {
        val q = query.lowercase().trim()
        val list = mutableListOf<PriceObservation>()
        val db = readableDatabase
        val terms = q.split("\\s+".toRegex()).filter { it.length > 1 }
        val cursor = if (terms.isEmpty()) {
            db.rawQuery(
                "SELECT * FROM $TABLE_OBSERVATIONS WHERE supplier_id = ? AND (lower(product_name) LIKE ? OR lower(plan_name) LIKE ? OR lower(matching_key) LIKE ? OR lower(original_raw_text) LIKE ?) ORDER BY amount_pkr ASC",
                arrayOf(supplierId.toString(), "%$q%", "%$q%", "%$q%", "%$q%")
            )
        } else {
            val whereClauses = terms.map {
                "(lower(product_name) LIKE ? OR lower(plan_name) LIKE ? OR lower(matching_key) LIKE ? OR lower(original_raw_text) LIKE ?)"
            }.joinToString(" OR ")
            val args = (listOf(supplierId.toString()) + terms.flatMap { listOf("%$it%", "%$it%", "%$it%", "%$it%") }).toTypedArray()
            db.rawQuery(
                "SELECT * FROM $TABLE_OBSERVATIONS WHERE supplier_id = ? AND ($whereClauses) ORDER BY amount_pkr ASC",
                args
            )
        }
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToObservation(c))
            }
        }
        return list
    }

    /**
     * Retrieves all tools/products discovered for an individual supplier bot.
     */
    fun getObservationsForSupplier(supplierId: Long): List<PriceObservation> {
        val list = mutableListOf<PriceObservation>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_OBSERVATIONS WHERE supplier_id = ? ORDER BY product_name ASC, amount_pkr ASC",
            arrayOf(supplierId.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToObservation(c))
            }
        }
        return list
    }

    /**
     * Returns the count of distinct products associated with a specific supplier bot.
     */
    fun getProductsCountForSupplier(supplierId: Long): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_OBSERVATIONS WHERE supplier_id = ?",
            arrayOf(supplierId.toString())
        )
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }

    /**
     * Generates a comprehensive catalog profile & statistics for a specific bot.
     */
    fun getSupplierCatalogSummary(supplierId: Long): BotCatalogSummary {
        val products = getObservationsForSupplier(supplierId)
        val supplier = getAllSuppliers().firstOrNull { it.id == supplierId }
        val sName = supplier?.canonicalName ?: (products.firstOrNull()?.supplierName ?: "Supplier Bot")
        val sHandle = supplier?.primaryHandle ?: (products.firstOrNull()?.supplierHandle ?: "@supplier_bot")
        val source = supplier?.source ?: "TELEGRAM_DISCOVERY"

        val minUsd = products.minOfOrNull { it.amount } ?: 0.0
        val maxUsd = products.maxOfOrNull { it.amount } ?: 0.0
        val minPkr = products.minOfOrNull { it.amountPkr } ?: 0.0
        val maxPkr = products.maxOfOrNull { it.amountPkr } ?: 0.0
        val distinctTools = products.map { it.productName.lowercase().trim() }.distinct().size
        val lastObserved = products.maxOfOrNull { it.observedAt }
        val categories = products.map { it.productName }.distinct()

        return BotCatalogSummary(
            supplierId = supplierId,
            supplierName = sName,
            supplierHandle = sHandle,
            source = source,
            totalProducts = products.size,
            minPriceUsd = minUsd,
            maxPriceUsd = maxUsd,
            minPricePkr = minPkr,
            maxPricePkr = maxPkr,
            distinctToolsCount = distinctTools,
            lastObservedAt = lastObserved,
            categories = categories,
            products = products
        )
    }

    /**
     * Retrieves chronological price changes for a specific supplier.
     */
    fun getPriceHistoryForSupplier(supplierId: Long): List<PriceHistoryEntry> {
        val list = mutableListOf<PriceHistoryEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PRICE_HISTORY WHERE supplier_id = ? ORDER BY changed_at DESC",
            arrayOf(supplierId.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToPriceHistory(c))
            }
        }
        return list
    }

    /**
     * Retrieves all price fluctuations (ups and downs) across all bots.
     */
    fun getAllPriceFluctuations(limit: Int = 50): List<PriceHistoryEntry> {
        val list = mutableListOf<PriceHistoryEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PRICE_HISTORY WHERE old_amount IS NOT NULL AND old_amount != new_amount ORDER BY changed_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToPriceHistory(c))
            }
        }
        return list
    }

    private fun cursorToPriceHistory(c: android.database.Cursor): PriceHistoryEntry {
        val oldAmtIdx = c.getColumnIndexOrThrow("old_amount")
        val oldAmt = if (c.isNull(oldAmtIdx)) null else c.getDouble(oldAmtIdx)
        return PriceHistoryEntry(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            observationId = c.getLong(c.getColumnIndexOrThrow("observation_id")),
            supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
            productName = c.getString(c.getColumnIndexOrThrow("product_name")),
            planName = c.getString(c.getColumnIndexOrThrow("plan_name")),
            oldAmount = oldAmt,
            newAmount = c.getDouble(c.getColumnIndexOrThrow("new_amount")),
            currency = c.getString(c.getColumnIndexOrThrow("currency")),
            amountPkr = c.getDouble(c.getColumnIndexOrThrow("amount_pkr")),
            changedAt = c.getLong(c.getColumnIndexOrThrow("changed_at"))
        )
    }

    private fun cursorToObservation(c: android.database.Cursor): PriceObservation {
        return PriceObservation(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
            supplierName = c.getString(c.getColumnIndexOrThrow("supplier_name")),
            supplierHandle = c.getString(c.getColumnIndexOrThrow("supplier_handle")),
            productName = c.getString(c.getColumnIndexOrThrow("product_name")),
            planName = c.getString(c.getColumnIndexOrThrow("plan_name")),
            duration = c.getString(c.getColumnIndexOrThrow("duration")),
            accessType = AccessType.valueOf(c.getString(c.getColumnIndexOrThrow("access_type"))),
            matchingKey = c.getString(c.getColumnIndexOrThrow("matching_key")),
            amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
            currency = c.getString(c.getColumnIndexOrThrow("currency")),
            amountPkr = c.getDouble(c.getColumnIndexOrThrow("amount_pkr")),
            warranty = c.getString(c.getColumnIndexOrThrow("warranty")) ?: "Not Stated",
            stockStatus = c.getString(c.getColumnIndexOrThrow("stock_status")) ?: "Available",
            verificationStatus = VerificationStatus.valueOf(c.getString(c.getColumnIndexOrThrow("verification_status"))),
            riskLevel = RiskLevel.valueOf(c.getString(c.getColumnIndexOrThrow("risk_level"))),
            captureMethod = c.getString(c.getColumnIndexOrThrow("capture_method")) ?: "ANDROID_BOT_SCANNER",
            originalRawText = c.getString(c.getColumnIndexOrThrow("original_raw_text")),
            sourceUrl = c.getString(c.getColumnIndexOrThrow("source_url")),
            observedAt = c.getLong(c.getColumnIndexOrThrow("observed_at"))
        )
    }

    // =========================================================================
    // ADVANCED CRAWLER PERSISTENCE METHODS
    // =========================================================================

    fun startScanRun(runUuid: String, trigger: String, totalSuppliers: Int, searchPolicy: String = "FAST_LOOKUP"): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("run_uuid", runUuid)
            put("started_at", System.currentTimeMillis())
            put("trigger", trigger)
            put("status", "RUNNING")
            put("total_suppliers", totalSuppliers)
            put("completed_suppliers", 0)
            put("partial_suppliers", 0)
            put("blocked_suppliers", 0)
            put("total_offers_found", 0)
            put("total_variants_found", 0)
            put("search_policy", searchPolicy)
            put("error_log", "")
        }
        return db.insert(TABLE_SCAN_RUNS, null, cv)
    }

    fun updateScanRun(scanRun: ScanRun) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("completed_at", scanRun.completedAt)
            put("status", scanRun.status)
            put("completed_suppliers", scanRun.completedSuppliers)
            put("partial_suppliers", scanRun.partialSuppliers)
            put("blocked_suppliers", scanRun.blockedSuppliers)
            put("total_offers_found", scanRun.totalOffersFound)
            put("total_variants_found", scanRun.totalVariantsFound)
            put("search_policy", scanRun.searchPolicy)
            put("error_log", scanRun.errorLog)
        }
        db.update(TABLE_SCAN_RUNS, cv, "id = ?", arrayOf(scanRun.id.toString()))
    }

    fun getLatestScanRun(): ScanRun? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SCAN_RUNS ORDER BY started_at DESC LIMIT 1", null)
        cursor.use { c ->
            if (c.moveToFirst()) {
                val policyIdx = c.getColumnIndex("search_policy")
                val policyVal = if (policyIdx >= 0 && !c.isNull(policyIdx)) c.getString(policyIdx) else "FAST_LOOKUP"
                return ScanRun(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    runUuid = c.getString(c.getColumnIndexOrThrow("run_uuid")),
                    startedAt = c.getLong(c.getColumnIndexOrThrow("started_at")),
                    completedAt = if (c.isNull(c.getColumnIndexOrThrow("completed_at"))) null else c.getLong(c.getColumnIndexOrThrow("completed_at")),
                    trigger = c.getString(c.getColumnIndexOrThrow("trigger")),
                    status = c.getString(c.getColumnIndexOrThrow("status")),
                    totalSuppliers = c.getInt(c.getColumnIndexOrThrow("total_suppliers")),
                    completedSuppliers = c.getInt(c.getColumnIndexOrThrow("completed_suppliers")),
                    partialSuppliers = c.getInt(c.getColumnIndexOrThrow("partial_suppliers")),
                    blockedSuppliers = c.getInt(c.getColumnIndexOrThrow("blocked_suppliers")),
                    totalOffersFound = c.getInt(c.getColumnIndexOrThrow("total_offers_found")),
                    totalVariantsFound = c.getInt(c.getColumnIndexOrThrow("total_variants_found")),
                    searchPolicy = policyVal,
                    errorLog = c.getString(c.getColumnIndexOrThrow("error_log")) ?: ""
                )
            }
        }
        return null
    }

    fun getAllScanRuns(): List<ScanRun> {
        val list = mutableListOf<ScanRun>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SCAN_RUNS ORDER BY started_at DESC", null)
        cursor.use { c ->
            val policyIdx = c.getColumnIndex("search_policy")
            while (c.moveToNext()) {
                val policyVal = if (policyIdx >= 0 && !c.isNull(policyIdx)) c.getString(policyIdx) else "FAST_LOOKUP"
                list.add(
                    ScanRun(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        runUuid = c.getString(c.getColumnIndexOrThrow("run_uuid")),
                        startedAt = c.getLong(c.getColumnIndexOrThrow("started_at")),
                        completedAt = if (c.isNull(c.getColumnIndexOrThrow("completed_at"))) null else c.getLong(c.getColumnIndexOrThrow("completed_at")),
                        trigger = c.getString(c.getColumnIndexOrThrow("trigger")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        totalSuppliers = c.getInt(c.getColumnIndexOrThrow("total_suppliers")),
                        completedSuppliers = c.getInt(c.getColumnIndexOrThrow("completed_suppliers")),
                        partialSuppliers = c.getInt(c.getColumnIndexOrThrow("partial_suppliers")),
                        blockedSuppliers = c.getInt(c.getColumnIndexOrThrow("blocked_suppliers")),
                        totalOffersFound = c.getInt(c.getColumnIndexOrThrow("total_offers_found")),
                        totalVariantsFound = c.getInt(c.getColumnIndexOrThrow("total_variants_found")),
                        searchPolicy = policyVal,
                        errorLog = c.getString(c.getColumnIndexOrThrow("error_log")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun saveCheckpoint(checkpoint: NavigationCheckpoint): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("run_id", checkpoint.runId)
            put("supplier_id", checkpoint.supplierId)
            put("supplier_handle", checkpoint.supplierHandle)
            put("state", checkpoint.state.name)
            put("current_depth", checkpoint.currentDepth)
            put("visited_fingerprints", checkpoint.visitedFingerprints)
            put("breadcrumb_path", checkpoint.breadcrumbPath)
            put("updated_at", checkpoint.updatedAt)
        }
        val existing = getCheckpoint(checkpoint.supplierId)
        return if (existing != null) {
            db.update(TABLE_NAVIGATION_CHECKPOINTS, cv, "supplier_id = ?", arrayOf(checkpoint.supplierId.toString()))
            existing.id
        } else {
            db.insert(TABLE_NAVIGATION_CHECKPOINTS, null, cv)
        }
    }

    fun getCheckpoint(supplierId: Long): NavigationCheckpoint? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAVIGATION_CHECKPOINTS WHERE supplier_id = ?", arrayOf(supplierId.toString()))
        cursor.use { c ->
            if (c.moveToFirst()) {
                return NavigationCheckpoint(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    runId = c.getLong(c.getColumnIndexOrThrow("run_id")),
                    supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
                    supplierHandle = c.getString(c.getColumnIndexOrThrow("supplier_handle")),
                    state = CrawlerState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
                    currentDepth = c.getInt(c.getColumnIndexOrThrow("current_depth")),
                    visitedFingerprints = c.getString(c.getColumnIndexOrThrow("visited_fingerprints")) ?: "",
                    breadcrumbPath = c.getString(c.getColumnIndexOrThrow("breadcrumb_path")) ?: "",
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return null
    }

    fun clearCheckpoint(supplierId: Long) {
        val db = writableDatabase
        db.delete(TABLE_NAVIGATION_CHECKPOINTS, "supplier_id = ?", arrayOf(supplierId.toString()))
    }

    fun savePlaybook(playbook: SupplierPlaybook): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("supplier_id", playbook.supplierId)
            put("menu_fingerprint", playbook.menuFingerprint)
            put("action_label", playbook.actionLabel)
            put("action_type", playbook.actionType.name)
            put("payload", playbook.payload)
            put("target_state", playbook.targetState.name)
            put("is_user_approved", if (playbook.isUserApproved) 1 else 0)
            put("created_at", playbook.createdAt)
        }
        return db.insert(TABLE_SUPPLIER_PLAYBOOKS, null, cv)
    }

    fun getPlaybook(supplierId: Long, menuFingerprint: String): SupplierPlaybook? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_SUPPLIER_PLAYBOOKS WHERE supplier_id = ? AND menu_fingerprint = ? AND is_user_approved = 1",
            arrayOf(supplierId.toString(), menuFingerprint)
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return SupplierPlaybook(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
                    menuFingerprint = c.getString(c.getColumnIndexOrThrow("menu_fingerprint")),
                    actionLabel = c.getString(c.getColumnIndexOrThrow("action_label")),
                    actionType = CrawlerButtonType.valueOf(c.getString(c.getColumnIndexOrThrow("action_type"))),
                    payload = c.getString(c.getColumnIndexOrThrow("payload")) ?: "",
                    targetState = CrawlerState.valueOf(c.getString(c.getColumnIndexOrThrow("target_state"))),
                    isUserApproved = c.getInt(c.getColumnIndexOrThrow("is_user_approved")) == 1,
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return null
    }

    fun getAllPlaybooks(): List<SupplierPlaybook> {
        val list = mutableListOf<SupplierPlaybook>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SUPPLIER_PLAYBOOKS ORDER BY created_at DESC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    SupplierPlaybook(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
                        menuFingerprint = c.getString(c.getColumnIndexOrThrow("menu_fingerprint")),
                        actionLabel = c.getString(c.getColumnIndexOrThrow("action_label")),
                        actionType = CrawlerButtonType.valueOf(c.getString(c.getColumnIndexOrThrow("action_type"))),
                        payload = c.getString(c.getColumnIndexOrThrow("payload")) ?: "",
                        targetState = CrawlerState.valueOf(c.getString(c.getColumnIndexOrThrow("target_state"))),
                        isUserApproved = c.getInt(c.getColumnIndexOrThrow("is_user_approved")) == 1,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun deletePlaybook(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_SUPPLIER_PLAYBOOKS, "id = ?", arrayOf(id.toString()))
    }

    fun addManualReviewItem(item: ManualReviewItem): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("run_id", item.runId)
            put("supplier_id", item.supplierId)
            put("supplier_handle", item.supplierHandle)
            put("item_type", item.itemType)
            put("title", item.title)
            put("prompt_message", item.promptMessage)
            put("target_handle_or_url", item.targetHandleOrUrl)
            put("options_json", item.optionsJson)
            put("raw_evidence", item.rawEvidence)
            put("status", item.status)
            put("created_at", item.createdAt)
        }
        return db.insert(TABLE_MANUAL_REVIEW_QUEUE, null, cv)
    }

    fun getPendingReviewItems(): List<ManualReviewItem> {
        val list = mutableListOf<ManualReviewItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MANUAL_REVIEW_QUEUE WHERE status = 'PENDING' ORDER BY created_at ASC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    ManualReviewItem(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        runId = c.getLong(c.getColumnIndexOrThrow("run_id")),
                        supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
                        supplierHandle = c.getString(c.getColumnIndexOrThrow("supplier_handle")),
                        itemType = c.getString(c.getColumnIndexOrThrow("item_type")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        promptMessage = c.getString(c.getColumnIndexOrThrow("prompt_message")),
                        targetHandleOrUrl = c.getString(c.getColumnIndexOrThrow("target_handle_or_url")),
                        optionsJson = c.getString(c.getColumnIndexOrThrow("options_json")) ?: "",
                        rawEvidence = c.getString(c.getColumnIndexOrThrow("raw_evidence")) ?: "",
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun updateReviewItemStatus(id: Long, status: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("status", status)
        }
        db.update(TABLE_MANUAL_REVIEW_QUEUE, cv, "id = ?", arrayOf(id.toString()))
    }

    fun addTimelineEvent(event: NavigationTimelineEvent): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("run_id", event.runId)
            put("supplier_id", event.supplierId)
            put("supplier_handle", event.supplierHandle)
            put("timestamp", event.timestamp)
            put("state", event.state.name)
            put("action_label", event.actionLabel)
            put("action_type", event.actionType.name)
            put("decision_reason", event.decisionReason)
            put("outcome_status", event.outcomeStatus)
        }
        return db.insert(TABLE_NAVIGATION_TIMELINE, null, cv)
    }

    fun getTimelineForRun(runId: Long): List<NavigationTimelineEvent> {
        val list = mutableListOf<NavigationTimelineEvent>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAVIGATION_TIMELINE WHERE run_id = ? ORDER BY timestamp ASC", arrayOf(runId.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    NavigationTimelineEvent(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        runId = c.getLong(c.getColumnIndexOrThrow("run_id")),
                        supplierId = c.getLong(c.getColumnIndexOrThrow("supplier_id")),
                        supplierHandle = c.getString(c.getColumnIndexOrThrow("supplier_handle")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        state = CrawlerState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),
                        actionLabel = c.getString(c.getColumnIndexOrThrow("action_label")),
                        actionType = CrawlerButtonType.valueOf(c.getString(c.getColumnIndexOrThrow("action_type"))),
                        decisionReason = c.getString(c.getColumnIndexOrThrow("decision_reason")),
                        outcomeStatus = c.getString(c.getColumnIndexOrThrow("outcome_status"))
                    )
                )
            }
        }
        return list
    }
}
