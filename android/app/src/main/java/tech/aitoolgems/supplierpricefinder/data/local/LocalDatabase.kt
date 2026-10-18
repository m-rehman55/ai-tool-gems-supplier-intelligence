package tech.aitoolgems.supplierpricefinder.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import tech.aitoolgems.supplierpricefinder.data.model.*

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "supplier_price_finder.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SUPPLIERS = "suppliers"
        const val TABLE_OBSERVATIONS = "price_observations"
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
                last_checked_at INTEGER
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
            db.insert(TABLE_SUPPLIERS, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OBSERVATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUPPLIERS")
        onCreate(db)
    }

    fun getAllSuppliers(): List<Supplier> {
        val list = mutableListOf<Supplier>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SUPPLIERS ORDER BY canonical_name ASC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
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
                        lastCheckedAt = if (c.isNull(c.getColumnIndexOrThrow("last_checked_at"))) null else c.getLong(c.getColumnIndexOrThrow("last_checked_at"))
                    )
                )
            }
        }
        return list
    }

    fun setSupplierEnabled(supplierId: Long, enabled: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        db.update(TABLE_SUPPLIERS, cv, "id = ?", arrayOf(supplierId.toString()))
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
        return db.insert(TABLE_OBSERVATIONS, null, cv)
    }

    fun getObservationsForQuery(query: String): List<PriceObservation> {
        val q = query.lowercase().trim()
        val list = mutableListOf<PriceObservation>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_OBSERVATIONS WHERE lower(product_name) LIKE ? OR lower(matching_key) LIKE ? ORDER BY amount_pkr ASC",
            arrayOf("%$q%", "%$q%")
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    PriceObservation(
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
                )
            }
        }
        return list
    }
}
