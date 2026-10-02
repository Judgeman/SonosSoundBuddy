package de.paul.sonoscontrol

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.util.UUID

/**
 * Baut die Sonos-Login-URL und öffnet sie in einem Custom Tab (empfohlener
 * "external user-agent"-Ansatz für OAuth nach RFC 8252 — keine WebView).
 */
class SonosAuthManager {

    var pendingState: String? = null
        private set

    fun startLogin(context: Context) {
        val state = UUID.randomUUID().toString()
        pendingState = state

        val loginUri = Uri.parse("https://api.sonos.com/login/v3/oauth").buildUpon()
            .appendQueryParameter("client_id", SonosConfig.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SonosConfig.OAUTH_SCOPE)
            .appendQueryParameter("redirect_uri", SonosConfig.WORKER_CALLBACK_URL)
            .build()

        android.util.Log.d("SonosAuth", "Login-URL: $loginUri")

        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, loginUri)
    }

    /** Muss vor jeder Token-Übernahme geprüft werden (CSRF-Schutz). */
    fun isValidState(receivedState: String?): Boolean {
        return receivedState != null && receivedState == pendingState
    }
}
