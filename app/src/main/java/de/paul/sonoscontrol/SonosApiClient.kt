package de.paul.sonoscontrol

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
    val tokenStore: TokenStore,
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
        if ((metadata.coverUrl == null || metadata.containerCoverUrl == null) && body != lastLoggedMetadataWithoutCover) {
            lastLoggedMetadataWithoutCover = body
            Log.d(TAG, "Kein Cover (Titel oder Playlist/Album) in playbackMetadata für Gruppe $groupId: $body")
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
        val items = (json.parseToJsonElement(body) as? JsonObject)?.get("items") as? JsonArray ?: return emptyList()
        return items.filterIsInstance<JsonObject>().map { item ->
            val favorite = json.decodeFromJsonElement(SonosFavorite.serializer(), item)
            val image = findImageUrl(item)
            // Hilft bei der Fehlersuche, wenn ein Musikdienst sein Cover woanders ablegt
            if (image == null) Log.d(TAG, "Kein Cover im Favoriten „${favorite.name}“: $item")
            favorite.copy(foundImageUrl = image, rawJson = item.toString())
        }
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

    /**
     * Ersetzt die Warteschlange der Gruppe durch den Favoriten; [play] startet ihn sofort.
     * [shuffle] != null setzt den Zufallsmodus gleich beim Laden mit.
     */
    suspend fun loadFavorite(groupId: String, favoriteId: String, play: Boolean = true, shuffle: Boolean? = null) {
        val request = buildJsonObject {
            put("favoriteId", favoriteId)
            put("playOnCompletion", play)
            put("action", "REPLACE")
            if (shuffle != null) put("playModes", buildJsonObject { put("shuffle", shuffle) })
        }
        post("$baseUrl/groups/$groupId/favorites", request.toString())
    }

    /**
     * Ersetzt die Warteschlange der Gruppe durch die Playlist; [play] startet sie sofort.
     * [shuffle] != null setzt den Zufallsmodus gleich beim Laden mit.
     */
    suspend fun loadPlaylist(groupId: String, playlistId: String, play: Boolean = true, shuffle: Boolean? = null) {
        val request = buildJsonObject {
            put("playlistId", playlistId)
            put("playOnCompletion", play)
            put("action", "REPLACE")
            if (shuffle != null) put("playModes", buildJsonObject { put("shuffle", shuffle) })
        }
        post("$baseUrl/groups/$groupId/playlists", request.toString())
    }

    /** Zufallswiedergabe der Gruppe an- oder ausschalten (setPlayModes). */
    suspend fun setShuffle(groupId: String, shuffle: Boolean) {
        val request = buildJsonObject {
            put("playModes", buildJsonObject { put("shuffle", shuffle) })
        }
        post("$baseUrl/groups/$groupId/playback/playMode", request.toString())
    }

    suspend fun play(groupId: String) {
        post("$baseUrl/groups/$groupId/playback/play")
    }

    private suspend fun get(url: String): String =
        authorizedCall(url) { get() }

    private suspend fun post(url: String, body: String = "{}"): String =
        authorizedCall(url) { post(body.toRequestBody(jsonMediaType)) }

    private suspend fun authorizedCall(url: String, withMethod: Request.Builder.() -> Request.Builder): String =
        withAccessToken { token ->
            executeAsync(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .withMethod()
                    .build()
            )
        }

    /**
     * Führt [call] mit einem gültigen Access-Token aus. Wirft [call] eine
     * [SonosApiException] mit HTTP 401, wird der Token erneuert und [call] einmal
     * wiederholt. Auch für Anfragen an den Worker, der den Token bei Sonos prüft.
     */
    suspend fun <T> withAccessToken(call: suspend (token: String) -> T): T {
        val token = validAccessToken()
        try {
            return call(token)
        } catch (e: SonosApiException) {
            if (e.httpCode != 401) throw e
        }
        // Token wurde abgelehnt (z. B. Ablaufzeit unbekannt) → erneuern und einmal wiederholen
        return call(refreshAccessToken(rejectedToken = token))
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

        @Volatile
        private var shared: SonosApiClient? = null

        /**
         * Ein Client für die ganze App: Alle Anfragen teilen sich die Token-Erneuerung,
         * sonst könnten zwei Stellen gleichzeitig mit demselben Refresh-Token erneuern.
         */
        fun shared(context: Context): SonosApiClient =
            shared ?: synchronized(this) {
                shared ?: SonosApiClient(TokenStore(context.applicationContext)).also { shared = it }
            }
    }
}
