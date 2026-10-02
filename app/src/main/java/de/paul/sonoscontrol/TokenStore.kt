package de.paul.sonoscontrol

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert Access- und Refresh-Token verschlüsselt auf dem Gerät
 * (Android Keystore-gestützt über androidx.security-crypto).
 */
class TokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sonos_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    /** Ablaufzeitpunkt des Access-Tokens (System.currentTimeMillis), 0 = unbekannt. */
    var accessTokenExpiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    /** True, wenn der Access-Token bekanntermaßen abgelaufen ist oder gleich abläuft. */
    val isAccessTokenExpiringSoon: Boolean
        get() {
            val expiresAt = accessTokenExpiresAt
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt - EXPIRY_MARGIN_MS
        }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long?) {
        val editor = prefs.edit()
        editor.putString(KEY_ACCESS_TOKEN, accessToken)
        // Ohne neuen Refresh-Token bleibt der bisherige gültig
        if (refreshToken != null) editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.putLong(
            KEY_EXPIRES_AT,
            expiresInSeconds?.let { System.currentTimeMillis() + it * 1000 } ?: 0L
        )
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "access_token_expires_at"

        /** Lieber etwas früher erneuern, als mit einem gerade abgelaufenen Token anzufragen. */
        private const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
    }
}
