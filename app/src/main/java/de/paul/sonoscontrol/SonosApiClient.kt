package de.paul.sonoscontrol

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SonosApiException(message: String, val httpCode: Int? = null) : Exception(message)

/** Die Anmeldung lässt sich nicht mehr erneuern (Refresh-Token ungültig/widerrufen). */
class SessionExpiredException :
    Exception("Die Anmeldung bei Sonos ist abgelaufen. Bitte einmal neu anmelden.")

@Serializable
private data class RefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null
)

/**
 * Minimaler Client für die offizielle Sonos Control API
 * (https://api.ws.sonos.com/control/api/v1).
 *
 * Access-Tokens laufen nach 24 h ab. Der Client erneuert sie selbstständig
 * über den Worker (POST /refresh): vorab, wenn die Ablaufzeit bekannt ist,
 * und sonst bei einer 401-Antwort — danach wird die Anfrage einmal wiederholt.
 */
class SonosApiClient(
    private val tokenStore: TokenStore,
    private val baseUrl: String = "https://api.ws.sonos.com/control/api/v1",
    private val refreshUrl: String = SonosConfig.WORKER_REFRESH_URL
) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()
    private var lastLoggedMetadataWithoutCover: String? = null
    private val refreshMutex = Mutex()

    suspend fun getFirstHouseholdId(): String {
        val body = get("$baseUrl/households")
        val response = json.decodeFromString(HouseholdsResponse.serializer(), body)
        return response.households.firstOrNull()?.id
            ?: throw SonosApiException("Kein Household im Sonos-Account gefunden")
    }

    suspend fun getPlayers(householdId: String): List<SonosPlayer> =
        getGroups(householdId).players

    suspend fun getGroups(householdId: String): GroupsResponse {
        val body = get("$baseUrl/households/$householdId/groups")
        return json.decodeFromString(GroupsResponse.serializer(), body)
    }

    suspend fun getPlaybackStatus(groupId: String): PlaybackStatus {
        val body = get("$baseUrl/groups/$groupId/playback")
        return json.decodeFromString(PlaybackStatus.serializer(), body)
    }

    suspend fun getPlaybackMetadata(groupId: String): PlaybackMetadata {
        val body = get("$baseUrl/groups/$groupId/playbackMetadata")
        val metadata = json.decodeFromString(PlaybackMetadata.serializer(), body)
        // Hilft bei der Fehlersuche, wenn eine Quelle kein Cover liefert (nur bei Änderung loggen)
        if (metadata.coverUrl == null && body != lastLoggedMetadataWithoutCover) {
            lastLoggedMetadataWithoutCover = body
            Log.d(TAG, "Kein Cover in playbackMetadata für Gruppe $groupId: $body")
        }
        return metadata
    }

    suspend fun getPlayerVolume(playerId: String): PlayerVolume {
        val body = get("$baseUrl/players/$playerId/playerVolume")
        return json.decodeFromString(PlayerVolume.serializer(), body)
    }

    suspend fun togglePlayPause(groupId: String) {
        post("$baseUrl/groups/$groupId/playback/togglePlayPause")
    }

    suspend fun skipToNextTrack(groupId: String) {
        post("$baseUrl/groups/$groupId/playback/skipToNextTrack")
    }

    suspend fun skipToPreviousTrack(groupId: String) {
        post("$baseUrl/groups/$groupId/playback/skipToPreviousTrack")
    }

    suspend fun setPlayerVolume(playerId: String, volume: Int) {
        post("$baseUrl/players/$playerId/playerVolume", """{"volume":${volume.coerceIn(0, 100)}}""")
    }

    suspend fun getFavorites(householdId: String): List<SonosFavorite> {
        val body = get("$baseUrl/households/$householdId/favorites")
        return json.decodeFromString(FavoritesResponse.serializer(), body).items
    }

    suspend fun getPlaylists(householdId: String): List<SonosPlaylist> {
        val body = get("$baseUrl/households/$householdId/playlists")
        return json.decodeFromString(PlaylistsResponse.serializer(), body).playlists
    }

    suspend fun getPlaylist(householdId: String, playlistId: String): PlaylistDetails {
        val request = buildJsonObject { put("playlistId", playlistId) }
        val body = post("$baseUrl/households/$householdId/playlists/getPlaylist", request.toString())
        return json.decodeFromString(PlaylistDetails.serializer(), body)
    }

    /** Ersetzt die Warteschlange der Gruppe durch den Favoriten und spielt ihn sofort ab. */
    suspend fun loadFavorite(groupId: String, favoriteId: String) {
        val request = buildJsonObject {
            put("favoriteId", favoriteId)
            put("playOnCompletion", true)
            put("action", "REPLACE")
        }
        post("$baseUrl/groups/$groupId/favorites", request.toString())
    }

    /** Ersetzt die Warteschlange der Gruppe durch die Playlist und spielt sie sofort ab. */
    suspend fun loadPlaylist(groupId: String, playlistId: String) {
        val request = buildJsonObject {
            put("playlistId", playlistId)
            put("playOnCompletion", true)
            put("action", "REPLACE")
        }
        post("$baseUrl/groups/$groupId/playlists", request.toString())
    }

    private suspend fun get(url: String): String =
        authorizedCall(url) { get() }

    private suspend fun post(url: String, body: String = "{}"): String =
        authorizedCall(url) { post(body.toRequestBody(jsonMediaType)) }

    private suspend fun authorizedCall(url: String, withMethod: Request.Builder.() -> Request.Builder): String {
        fun request(token: String) = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .withMethod()
            .build()

        val token = validAccessToken()
        try {
            return executeAsync(request(token))
        } catch (e: SonosApiException) {
            if (e.httpCode != 401) throw e
        }
        // Token wurde abgelehnt (z. B. Ablaufzeit unbekannt) → erneuern und einmal wiederholen
        return executeAsync(request(refreshAccessToken(rejectedToken = token)))
    }

    private suspend fun validAccessToken(): String {
        val token = tokenStore.accessToken ?: throw SessionExpiredException()
        return if (tokenStore.isAccessTokenExpiringSoon) refreshAccessToken(rejectedToken = token) else token
    }

    /**
     * Holt über den Worker einen neuen Access-Token. Laufen mehrere Anfragen
     * gleichzeitig in einen abgelaufenen Token, erneuert nur die erste — die
     * anderen bekommen danach direkt den neuen Token.
     */
    private suspend fun refreshAccessToken(rejectedToken: String): String = refreshMutex.withLock {
        val current = tokenStore.accessToken
        if (current != null && current != rejectedToken && !tokenStore.isAccessTokenExpiringSoon) {
            return@withLock current
        }
        val refreshToken = tokenStore.refreshToken ?: throw SessionExpiredException()

        val request = Request.Builder()
            .url(refreshUrl)
            .post(json.encodeToString(mapOf("refresh_token" to refreshToken)).toRequestBody(jsonMediaType))
            .build()
        val body = try {
            executeAsync(request)
        } catch (e: SonosApiException) {
            // Nur 401 heißt "Refresh-Token ungültig" — alles andere ist vorübergehend
            if (e.httpCode == 401) throw SessionExpiredException()
            throw SonosApiException("Anmeldung konnte nicht erneuert werden (HTTP ${e.httpCode})", e.httpCode)
        }

        val tokens = json.decodeFromString(RefreshResponse.serializer(), body)
        tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
        Log.d(TAG, "Access-Token erneuert")
        tokens.accessToken
    }

    private suspend fun executeAsync(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyString = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            continuation.resumeWithException(
                                SonosApiException("HTTP ${it.code}: $bodyString", it.code)
                            )
                        } else {
                            continuation.resume(bodyString)
                        }
                    }
                }
            })
        }

    companion object {
        private const val TAG = "SonosApi"
    }
}
