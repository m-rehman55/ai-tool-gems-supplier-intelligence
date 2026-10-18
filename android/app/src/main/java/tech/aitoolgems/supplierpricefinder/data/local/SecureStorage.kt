package tech.aitoolgems.supplierpricefinder.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_telegram_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for test environments without hardware keystore
            context.getSharedPreferences("fallback_telegram_prefs", Context.MODE_PRIVATE)
        }
    }

    var apiId: String?
        get() = prefs.getString("tg_api_id", null)
        set(value) = prefs.edit().putString("tg_api_id", value).apply()

    var apiHash: String?
        get() = prefs.getString("tg_api_hash", null)
        set(value) = prefs.edit().putString("tg_api_hash", value).apply()

    var phoneNumber: String?
        get() = prefs.getString("tg_phone", null)
        set(value) = prefs.edit().putString("tg_phone", value).apply()

    var sessionString: String?
        get() = prefs.getString("tg_session_token", null)
        set(value) = prefs.edit().putString("tg_session_token", value).apply()

    val isConfigured: Boolean
        get() = !apiId.isNullOrBlank() && !apiHash.isNullOrBlank()

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }
}
