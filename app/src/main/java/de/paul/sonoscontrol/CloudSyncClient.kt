package de.paul.sonoscontrol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Was zuletzt in die Cloud hochgeladen wurde. */
@Serializable
data class CloudState(
    val version: String,
    val updatedAt: Long,
    val deviceName: String = ""
)

/** Ein Stand aus der Cloud samt Version. */
@Serializable
data class CloudSnapshot(
    val version: String,
    val updatedAt: Long,
    val deviceName: String = "",
    val snapshot: SyncSnapshot
)

@Serializable
private data class UploadRequest(val deviceName: String, val images: List<String>, val snapshot: SyncSnapshot)

@Serializable
private data class MissingRequest(val hashes: List<String>)

/** Fehler mit einer Meldung, die so angezeigt werden kann. */
class CloudSyncException(message: String) : IOException(message)

/**
 * Spricht mit dem Abgleich-Speicher im Cloudflare-Worker (`/sync/…`).
 *
 * Jede Anfrage trägt den Sonos-Access-Token und den Haushalt; der Worker
 * fragt Sonos, ob der Token zu dem Haushalt gehört. Daten eines anderen
 * Haushalts oder Kontos bekommt die App so nie zu sehen.
 */
class CloudSyncClient(
    private val api: SonosApiClient,
    private val baseUrl: String = SonosConfig.WORKER_BASE_URL
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    /** Zuletzt hochgeladener Stand, null = noch nie etwas hochgeladen. */
    suspend fun state(household: String): CloudState? =
        send(household, "/sync/state")?.let { syncJson.decodeFromString(CloudState.serializer(), it.decodeToString()) }

    suspend fun snapshot(household: String): CloudSnapshot? =
        send(household, "/sync/snapshot")?.let {
            syncJson.decodeFromString(CloudSnapshot.serializer(), it.decodeToString())
                .also { cloud -> SyncPackage.checkFormat(cloud.snapshot) }
        }

    /** Welche der Bilder noch fehlen und hochgeladen werden müssen. */
    suspend fun missingImages(household: String, hashes: List<String>): List<String> {
        val body = syncJson.encodeToString(MissingRequest.serializer(), MissingRequest(hashes))
        val response = send(household, "/sync/images/missing", "POST", body.toRequestBody(jsonType))
            ?: throw outdatedWorker()
        val missing = syncJson.parseToJsonElement(response.decodeToString()) as? JsonObject
        return missing?.get("missing")?.let {
            syncJson.decodeFromJsonElement(ListSerializer(String.serializer()), it)
        }.orEmpty()
    }

    suspend fun uploadImage(household: String, hash: String, bytes: ByteArray) {
        send(household, "/sync/images/$hash", "PUT", bytes.toRequestBody("image/jpeg".toMediaType()))
            ?: throw outdatedWorker()
    }

    /** Bild aus der Cloud, null wenn es dort nicht (mehr) liegt. */
    suspend fun image(household: String, hash: String): ByteArray? = send(household, "/sync/images/$hash")

    suspend fun upload(household: String, deviceName: String, snapshot: SyncSnapshot, images: List<String>): CloudState {
        val body = syncJson.encodeToString(UploadRequest.serializer(), UploadRequest(deviceName, images, snapshot))
        val response = send(household, "/sync/snapshot", "PUT", body.toRequestBody(jsonType))
            ?: throw outdatedWorker()
        return syncJson.decodeFromString(CloudState.serializer(), response.decodeToString())
    }

    /** Löscht den Stand dieses Haushalts samt Bildern aus der Cloud. */
    suspend fun delete(household: String) {
        send(household, "/sync", "DELETE")
    }

    /**
     * Schickt eine Anfrage an den Worker. Gibt den Inhalt der Antwort zurück,
     * null bei „gibt es nicht“ (404 vom Abgleich selbst).
     */
    private suspend fun send(
        household: String,
        path: String,
        method: String = "GET",
        body: RequestBody? = null
    ): ByteArray? = api.withAccessToken { token ->
        val url = (baseUrl + path).toHttpUrl().newBuilder().addQueryParameter("household", household).build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                when {
                    response.isSuccessful -> bytes
                    // Die App erneuert den Token und versucht es einmal wieder
                    response.code == 401 -> throw SonosApiException("Token vom Worker abgelehnt", 401)
                    response.code == 404 -> if (errorCode(bytes) == "not_found") null else throw outdatedWorker()
                    else -> throw describe(response.code, errorCode(bytes))
                }
            }
        }
    }

    private fun errorCode(bytes: ByteArray): String? = runCatching {
        (syncJson.parseToJsonElement(bytes.decodeToString()) as? JsonObject)?.get("error")?.jsonPrimitive?.content
    }.getOrNull()

    private fun describe(code: Int, error: String?): CloudSyncException = CloudSyncException(
        when (error) {
            "sync_not_configured" ->
                "Im Worker ist noch kein Speicher für den Abgleich eingerichtet (KV-Binding SYNC_KV, siehe README)."
            "forbidden_household" -> "Der Worker erlaubt keinen Zugriff auf diesen Sonos-Haushalt."
            "missing_images" -> "Beim Hochladen fehlen Bilder. Bitte noch einmal versuchen."
            "sonos_unreachable" -> "Sonos ist gerade nicht erreichbar. Später noch einmal versuchen."
            "invalid_request" -> "Der Stand ist zu groß oder enthält zu viele Bilder für den Abgleich."
            else -> "Der Abgleich ist fehlgeschlagen (HTTP $code)."
        }
    )

    private fun outdatedWorker() =
        CloudSyncException("Der Worker kennt den Abgleich noch nicht. Bitte die neue Version deployen (siehe README).")
}
