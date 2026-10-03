package de.paul.sonoscontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.Inet4Address
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Ein Eintrag aus dem lokalen Musik-Verzeichnis eines Speakers (Favorit oder Sonos-Playlist). */
data class LocalEntry(val id: String, val title: String, val artUrl: String?)

/** Cover aus dem Heimnetz, nach Sonos-Id und nach Name. */
class LocalCovers(
    private val favorites: List<LocalEntry>,
    private val playlists: List<LocalEntry>
) {
    /** Control-API-Id "13" entspricht lokal "FV:2/13" bzw. "SQ:13" — sonst über den Namen. */
    fun coverFor(entry: CatalogEntry): String? {
        val (list, localId) = when (entry.source) {
            MusicSource.FAVORITE -> favorites to "FV:2/${entry.sonosId}"
            MusicSource.PLAYLIST -> playlists to "SQ:${entry.sonosId}"
        }
        return list.firstOrNull { it.id == localId && it.title.equals(entry.name, ignoreCase = true) }?.artUrl
            ?: list.firstOrNull { it.title.trim().equals(entry.name.trim(), ignoreCase = true) }?.artUrl
    }
}

/**
 * Fragt einen Sonos-Speaker direkt im Heimnetz (UPnP, Port 1400) nach Covern.
 * Die Cloud-API liefert für Sonos-Playlisten nie ein Bild und für manche
 * Favoriten auch nicht — die Speaker selbst kennen sie aber, genau wie die Sonos-App.
 * Den Speaker findet die App per mDNS (`_sonos._tcp`).
 */
class LocalSonosClient(context: Context) {

    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** z. B. "http://192.168.1.23:1400" — einmal gefunden, wird sie wiederverwendet. */
    @Volatile
    private var speakerBaseUrl: String? = null

    /**
     * Lädt Favoriten und Sonos-Playlisten samt Covern vom Speaker.
     * [addressHint]: eine Cover-URL der Wiedergabe (http://IP:1400/…), falls bekannt.
     */
    suspend fun loadCovers(addressHint: String?): LocalCovers? {
        val base = speakerBaseUrl ?: baseUrlFrom(addressHint) ?: discoverSpeaker() ?: run {
            Log.d(TAG, "Kein Sonos-Speaker im Heimnetz gefunden")
            return null
        }
        return try {
            val favorites = browse(base, "FV:2")
            val playlists = browse(base, "SQ:").map { playlist ->
                // Hat die Playlist selbst kein Bild, das Cover des ersten Titels nehmen
                if (playlist.artUrl != null) playlist
                else playlist.copy(artUrl = browse(base, playlist.id, count = 5).firstNotNullOfOrNull { it.artUrl })
            }
            speakerBaseUrl = base
            Log.d(TAG, "Lokal ${favorites.size} Favoriten, ${playlists.size} Playlisten von $base")
            LocalCovers(favorites, playlists)
        } catch (e: Exception) {
            // Speaker nicht mehr unter der Adresse erreichbar → beim nächsten Mal neu suchen
            speakerBaseUrl = null
            Log.w(TAG, "Lokale Abfrage bei $base fehlgeschlagen", e)
            null
        }
    }

    private fun baseUrlFrom(url: String?): String? {
        val match = url?.let { Regex("^http://([0-9.]+):1400/").find(it) } ?: return null
        return "http://${match.groupValues[1]}:1400"
    }

    private suspend fun discoverSpeaker(): String? = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var resolving = false
            lateinit var discovery: NsdManager.DiscoveryListener

            fun finish(result: String?) {
                if (!continuation.isActive) return
                runCatching { nsdManager.stopServiceDiscovery(discovery) }
                continuation.resume(result)
            }

            discovery = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "mDNS-Suche nicht gestartet ($errorCode)")
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // Es reicht ein Speaker; Android erlaubt nur eine Auflösung gleichzeitig
                    if (resolving) return
                    resolving = true
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            resolving = false
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host
                            if (host is Inet4Address) {
                                finish("http://${host.hostAddress}:1400")
                            } else {
                                resolving = false
                            }
                        }
                    })
                }
            }

            continuation.invokeOnCancellation { runCatching { nsdManager.stopServiceDiscovery(discovery) } }
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        }
    }

    /** UPnP ContentDirectory „Browse": Kinder eines Containers (FV:2 = Favoriten, SQ: = Playlisten). */
    private suspend fun browse(base: String, objectId: String, count: Int = 500): List<LocalEntry> =
        withContext(Dispatchers.IO) {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                |<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                |<s:Body><u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                |<ObjectID>$objectId</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter>
                |<StartingIndex>0</StartingIndex><RequestedCount>$count</RequestedCount><SortCriteria></SortCriteria>
                |</u:Browse></s:Body></s:Envelope>""".trimMargin()
            val request = Request.Builder()
                .url("$base/MediaServer/ContentDirectory/Control")
                .header("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"")
                .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .build()
            val response = http.newCall(request).execute().use {
                if (!it.isSuccessful) throw java.io.IOException("UPnP Browse $objectId: HTTP ${it.code}")
                it.body?.string().orEmpty()
            }
            val didl = textOf(response, "Result") ?: return@withContext emptyList()
            parseDidl(didl, base)
        }

    /** Text des ersten Elements [tag] (ohne Namespace-Präfix). */
    private fun textOf(xml: String, tag: String): String? {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') == tag) {
                return parser.nextText()
            }
        }
        return null
    }

    private fun parseDidl(didl: String, base: String): List<LocalEntry> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(didl)) }
        val entries = mutableListOf<LocalEntry>()
        var id: String? = null
        var title: String? = null
        var art: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (name) {
                    "item", "container" -> {
                        id = parser.getAttributeValue(null, "id")
                        title = null
                        art = null
                    }
                    "title" -> if (title == null) title = parser.nextText()
                    // Erstes Bild nehmen; relative Pfade (/getaa?…) liefert der Speaker selbst
                    "albumArtURI" -> if (art == null) {
                        art = parser.nextText().trim().let { if (it.startsWith("/")) base + it else it }
                            .let(::normalizeImageUrl)
                    }
                }
                XmlPullParser.END_TAG -> if (name == "item" || name == "container") {
                    val entryId = id
                    val entryTitle = title
                    if (entryId != null && entryTitle != null) entries += LocalEntry(entryId, entryTitle, art)
                    id = null
                }
            }
        }
        return entries
    }

    companion object {
        private const val TAG = "SonosLocal"
        private const val SERVICE_TYPE = "_sonos._tcp."
        private const val DISCOVERY_TIMEOUT_MS = 6_000L
    }
}
