package tech.aitoolgems.supplierpricefinder.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureStorage(context: Context, private val isTestEnvironment: Boolean = false) {

    var encryptionError: String? = null
        private set

    private val prefs: SharedPreferences? by lazy {
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
            encryptionError = e.message ?: "Failed to initialize Android Keystore hardware encryption."
            if (isTestEnvironment) {
                context.getSharedPreferences("test_mock_prefs", Context.MODE_PRIVATE)
            } else {
                null // Fail closed: Never store credentials in unencrypted plaintext on real devices!
            }
        }
    }

    val isEncryptionAvailable: Boolean
        get() = prefs != null

    var apiId: String?
        get() = prefs?.getString("tg_api_id", null)
        set(value) {
            if (prefs == null) {
                throw SecurityException("Cannot save API ID: Secure hardware storage unavailable ($encryptionError)")
            }
            prefs?.edit()?.putString("tg_api_id", value)?.apply()
        }

    var apiHash: String?
        get() = prefs?.getString("tg_api_hash", null)
        set(value) {
            if (prefs == null) {
                throw SecurityException("Cannot save API Hash: Secure hardware storage unavailable ($encryptionError)")
            }
            prefs?.edit()?.putString("tg_api_hash", value)?.apply()
        }

    var phoneNumber: String?
        get() = prefs?.getString("tg_phone", null)
        set(value) {
            prefs?.edit()?.putString("tg_phone", value)?.apply()
        }

    var sessionString: String?
        get() = prefs?.getString("tg_session_token", null)
        set(value) {
            prefs?.edit()?.putString("tg_session_token", value)?.apply()
        }

    var bunnyApiKey: String
        get() = prefs?.getString("bunny_api_key", null) ?: DEFAULT_BUNNY_API_KEY
        set(value) {
            prefs?.edit()?.putString("bunny_api_key", value)?.apply()
        }

    var canbosoApiKey: String
        get() = prefs?.getString("canboso_api_key", null) ?: DEFAULT_CANBOSO_API_KEY
        set(value) {
            prefs?.edit()?.putString("canboso_api_key", value)?.apply()
        }

    val isConfigured: Boolean
        get() = !apiId.isNullOrBlank() && !apiHash.isNullOrBlank()

    fun clearCredentials() {
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        const val DEFAULT_BUNNY_API_KEY = "bn_00f8e3dd13db43b27384a437b24df05d"
        const val DEFAULT_CANBOSO_API_KEY = "tgb_bf132f47e0c72707d99a5927fc65e5998f5563236ce20859"
    }
}
