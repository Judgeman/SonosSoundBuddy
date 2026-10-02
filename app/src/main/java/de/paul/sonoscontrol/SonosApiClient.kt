package de.paul.sonoscontrol

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
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

    suspend fun getFirstHouseholdId(): String {
        val body = get("$baseUrl/households")
        val response = json.decodeFromString(HouseholdsResponse.serializer(), body)
        return response.households.firstOrNull()?.id
            ?: throw SonosApiException("Kein Household im Sonos-Account gefunden")
    }

    suspend fun getPlayers(householdId: String): List<SonosPlayer> {
        val body = get("$baseUrl/households/$householdId/groups")
        val response = json.decodeFromString(GroupsResponse.serializer(), body)
        return response.players
    }

    private suspend fun get(url: String): String {
        val token = accessTokenProvider()
            ?: throw SonosApiException("Kein Access-Token vorhanden — bitte erneut anmelden")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return executeAsync(request)
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
