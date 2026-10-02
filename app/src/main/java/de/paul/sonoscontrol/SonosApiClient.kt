package de.paul.sonoscontrol

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
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

class SonosApiException(message: String) : Exception(message)

/**
 * Minimaler Client für die offizielle Sonos Control API
 * (https://api.ws.sonos.com/control/api/v1).
 */
class SonosApiClient(private val accessTokenProvider: () -> String?) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.ws.sonos.com/control/api/v1"
    private val jsonMediaType = "application/json".toMediaType()

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
        return json.decodeFromString(PlaybackMetadata.serializer(), body)
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

    private suspend fun get(url: String): String =
        executeAsync(authorizedRequest(url).get().build())

    private suspend fun post(url: String): String =
        executeAsync(authorizedRequest(url).post("{}".toRequestBody(jsonMediaType)).build())

    private fun authorizedRequest(url: String): Request.Builder {
        val token = accessTokenProvider()
            ?: throw SonosApiException("Kein Access-Token vorhanden — bitte erneut anmelden")

        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
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
                                SonosApiException("HTTP ${it.code}: $bodyString")
                            )
                        } else {
                            continuation.resume(bodyString)
                        }
                    }
                }
            })
        }
}
