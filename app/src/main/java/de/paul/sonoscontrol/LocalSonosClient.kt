package de.paul.sonoscontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.Inet4Address
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Ein Eintrag aus dem lokalen Musik-Verzeichnis eines Speakers (Favorit oder Sonos-Playlist).
 * [freshArtUrl]: Cover über den Speaker selbst (`/getaa?u=<Inhalt>`) — er holt es jedes Mal
 * frisch beim Musikdienst, auch wenn die gespeicherte [artUrl] längst abgelaufen ist.
 */
data class LocalEntry(
    val id: String,
    val title: String,
    val artUrl: String?,
    val freshArtUrl: String? = null,
    val artists: List<String> = emptyList(),
    /** Album eines Titels (`upnp:album`). */
    val album: String? = null
)

/** Ein Befehl an den Speaker im Heimnetz wurde abgelehnt; [errorCode] ist der UPnP-Fehler, falls genannt. */
class LocalSonosException(message: String, val errorCode: String? = null) : IOException(message)

/** Speaker im Heimnetz laut Zonen-Topologie: Kennung (wie die Player-Id der Control API), Raum, Adresse. */
private data class ZoneMember(val uuid: String, val name: String?, val baseUrl: String, val coordinatorUuid: String)

/** Cover und Künstler aus dem Heimnetz, nach Sonos-Id und nach Name. */
class LocalCovers(
    private val favorites: List<LocalEntry>,
    private val playlists: List<LocalEntry>
) {
    /** Cover-Adressen für einen Katalog-Eintrag, beste zuerst. */
    fun coversFor(entry: CatalogEntry): List<String> {
        val local = find(entry) ?: return emptyList()
        return listOfNotNull(local.artUrl, local.freshArtUrl)
    }

    /** Künstler laut Speaker — bei Sonos-Playlisten die ihrer Titel, die häufigsten zuerst. */
    fun artistsFor(entry: CatalogEntry): List<String> = find(entry)?.artists.orEmpty()

    /** Control-API-Id "13" entspricht lokal "FV:2/13" bzw. "SQ:13" — sonst über den Namen. */
    private fun find(entry: CatalogEntry): LocalEntry? {
        val (list, localId) = when (entry.source) {
            MusicSource.FAVORITE -> favorites to "FV:2/${entry.sonosId}"
            MusicSource.PLAYLIST -> playlists to "SQ:${entry.sonosId}"
            // Steht nicht im Katalog
            MusicSource.PLAYLIST_ALBUM -> return null
        }
        return list.firstOrNull { it.id == localId && it.title.equals(entry.name, ignoreCase = true) }
            ?: list.firstOrNull { it.title.trim().equals(entry.name.trim(), ignoreCase = true) }
    }
}

/**
 * Fragt einen Sonos-Speaker direkt im Heimnetz (UPnP, Port 1400) nach Covern und Künstlern.
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
     * Lädt Favoriten und Sonos-Playlisten samt Covern und Künstlern vom Speaker.
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
                // Die Titel nennen die Künstler der Playlist (alle, damit die Suche sie findet).
                // Hat sie selbst kein Bild, das Cover der ersten Titel nehmen — die frische
                // Adresse über den Speaker zuerst, die gespeicherte kann abgelaufen sein.
                // (Die Abspiel-Adresse der Playlist selbst taugt nicht für /getaa.)
                val tracks = browse(base, playlist.id)
                val art = playlist.artUrl ?: tracks.firstNotNullOfOrNull { track -> track.freshArtUrl ?: track.artUrl }
                val artists = playlist.artists.ifEmpty { rankArtists(tracks.flatMap { it.artists }) }
                playlist.copy(artUrl = art, freshArtUrl = null, artists = artists)
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

    /**
     * Die Titel einer Sonos-Playlist mit Album, Künstler und Cover, in ihrer Reihenfolge — null, wenn
     * kein Speaker im Heimnetz antwortet oder die Playlist dort fehlt.
     */
    suspend fun playlistTracks(playlistId: String, addressHint: String?): List<PlaylistTrackInfo>? {
        val base = speakerBaseUrl ?: baseUrlFrom(addressHint) ?: discoverSpeaker() ?: run {
            Log.d(TAG, "Kein Sonos-Speaker im Heimnetz gefunden")
            return null
        }
        return try {
            browseAll(base, "SQ:$playlistId").first
                .map { track ->
                    PlaylistTrackInfo(
                        title = track.title.takeIf { it.isNotBlank() },
                        album = track.album,
                        artist = track.artists.firstOrNull(),
                        imageUrls = listOfNotNull(track.artUrl, track.freshArtUrl)
                    )
                }
                .also { speakerBaseUrl = base }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nicht erreichbar → beim nächsten Mal neu suchen; hat er abgelehnt, ist er aber da
            if (e !is LocalSonosException) speakerBaseUrl = null
            Log.w(TAG, "Titel der Playlist $playlistId nicht von $base geladen", e)
            null
        }
    }

    /**
     * Spielt nur ein Album aus einer Sonos-Playlist auf der Gruppe von [playerId] — die Control API
     * kann nur ganze Playlisten. Dazu kommt die Playlist in die Warteschlange des Gruppen-Koordinators,
     * dann nimmt die App alles heraus, was nicht zum Album gehört (Titel mit diesem Album-Namen, siehe
     * [albumKey]). [playerName] hilft, falls die Kennung im Heimnetz anders lautet.
     * Wirft eine [LocalSonosException] mit verständlicher Meldung, wenn etwas nicht klappt.
     */
    suspend fun playAlbumFromPlaylist(
        playerId: String,
        playerName: String?,
        playlistId: String,
        playlistName: String,
        albumName: String,
        shuffle: Boolean,
        addressHint: String?
    ) {
        val members = zoneTopology(addressHint)
        val member = members.firstOrNull { it.uuid == playerId }
            ?: members.firstOrNull { playerName != null && it.name.equals(playerName, ignoreCase = true) }
            ?: throw LocalSonosException("Den Speaker „${playerName ?: playerId}“ gibt es gerade nicht im Heimnetz.")
        // Warteschlange und Wiedergabe gehören dem Koordinator der Gruppe
        val coordinator = members.firstOrNull { it.uuid == member.coordinatorUuid } ?: member
        try {
            playAlbumOn(coordinator, playlistId, playlistName, albumName, shuffle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalSonosException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Speaker ${coordinator.name} unter ${coordinator.baseUrl} nicht erreichbar", e)
            throw LocalSonosException("Der Speaker „${coordinator.name ?: playerName ?: playerId}“ ist im WLAN gerade nicht erreichbar.")
        }
    }

    /** Die eigentlichen Schritte von [playAlbumFromPlaylist], alle beim Koordinator der Gruppe. */
    private suspend fun playAlbumOn(
        coordinator: ZoneMember,
        playlistId: String,
        playlistName: String,
        albumName: String,
        shuffle: Boolean
    ) {
        val base = coordinator.baseUrl
        suspend fun transport(action: String, vararg arguments: Pair<String, String>): String =
            soap(base, AV_TRANSPORT_PATH, "AVTransport", action, "InstanceID" to "0", *arguments)

        // Zufall vom letzten Mal vorher aus, damit die Warteschlange in der Reihenfolge der Playlist bleibt.
        // Ob Wiederholen an ist, bleibt, wie es war.
        val previousMode = ignoringRefusal { textOf(transport("GetTransportSettings"), "PlayMode")?.trim() }
        val repeat = repeatOf(previousMode)
        if (previousMode != null && previousMode != playMode(shuffle = false, repeat)) {
            ignoringRefusal { transport("SetPlayMode", "NewPlayMode" to playMode(shuffle = false, repeat)) }
        }

        transport("RemoveAllTracksFromQueue")
        val added = try {
            transport(
                "AddURIToQueue",
                "EnqueuedURI" to "file:///jffs/settings/savedqueues.rsq#$playlistId",
                "EnqueuedURIMetaData" to playlistDidl(playlistId, playlistName),
                "DesiredFirstTrackNumberEnqueued" to "0",
                "EnqueueAsNext" to "0"
            )
        } catch (e: LocalSonosException) {
            if (e.errorCode == ERROR_NO_SUCH_OBJECT) {
                throw LocalSonosException("Die Playlist „$playlistName“ gibt es bei Sonos nicht mehr.", e.errorCode)
            }
            throw e
        }
        val expectedLength = textOf(added, "NewQueueLength")?.trim()?.toIntOrNull()

        // Welche Plätze der Warteschlange zum Album gehören — so, wie der Speaker sie jetzt hat
        val (queue, queueLength) = queueWhenFilled(base, expectedLength)
        val key = albumKey(albumName)
        val keep = queue.mapIndexedNotNull { index, entry ->
            val name = PlaylistTrackInfo(title = entry.title, album = entry.album).albumName()
            if (name == null || albumKey(name) != key) return@mapIndexedNotNull null
            QUEUE_POSITION.find(entry.id)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
        }.toSet()
        if (keep.isEmpty()) {
            // Nicht die ganze Playlist in der Warteschlange lassen — der Play-Knopf würde sie sonst starten
            ignoringRefusal { transport("RemoveAllTracksFromQueue") }
            throw LocalSonosException("„$albumName“ steckt nicht mehr in der Playlist „$playlistName“.")
        }
        rangesToRemove(keep, queueLength).forEach { (start, count) ->
            transport(
                "RemoveTrackRangeFromQueue",
                "UpdateID" to "0",
                "StartingIndex" to start.toString(),
                "NumberOfTracks" to count.toString()
            )
        }
        Log.d(TAG, "„$albumName“: ${keep.size} von $queueLength Titeln der Playlist „$playlistName“ in der Warteschlange")

        transport("SetAVTransportURI", "CurrentURI" to "x-rincon-queue:${coordinator.uuid}#0", "CurrentURIMetaData" to "")
        ignoringRefusal { transport("SetPlayMode", "NewPlayMode" to playMode(shuffle, repeat)) }
        // Im Zufallsmodus auch mit einem zufälligen Titel beginnen, sonst der Reihe nach von vorn
        val first = if (shuffle) Random.nextInt(keep.size) + 1 else 1
        transport("Seek", "Unit" to "TRACK_NR", "Target" to first.toString())
        transport("Play", "Speed" to "1")
    }

    /**
     * Speaker im Heimnetz mit ihren Gruppen — gefragt beim zuletzt benutzten Speaker, bei dem aus
     * [addressHint] oder einem per mDNS gefundenen.
     */
    private suspend fun zoneTopology(addressHint: String?): List<ZoneMember> {
        for (base in listOfNotNull(speakerBaseUrl, baseUrlFrom(addressHint)).distinct()) {
            try {
                zoneMembers(base).takeIf { it.isNotEmpty() }?.let {
                    speakerBaseUrl = base
                    return it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.d(TAG, "Speaker unter $base antwortet nicht: ${e.message}")
            }
            if (speakerBaseUrl == base) speakerBaseUrl = null
        }
        val found = discoverSpeaker() ?: throw LocalSonosException(NO_SPEAKER_MESSAGE)
        val members = try {
            zoneMembers(found)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Zonen-Topologie von $found nicht geladen", e)
            throw LocalSonosException(NO_SPEAKER_MESSAGE)
        }
        if (members.isEmpty()) throw LocalSonosException(NO_SPEAKER_MESSAGE)
        speakerBaseUrl = found
        return members
    }

    /** Alle Speaker samt Koordinator ihrer Gruppe, laut ZoneGroupTopology. */
    private suspend fun zoneMembers(base: String): List<ZoneMember> {
        val response = soap(base, ZONE_TOPOLOGY_PATH, "ZoneGroupTopology", "GetZoneGroupState")
        val state = textOf(response, "ZoneGroupState") ?: return emptyList()
        val parser = Xml.newPullParser().apply { setInput(StringReader(state)) }
        val members = mutableListOf<ZoneMember>()
        var coordinator: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "ZoneGroup" -> coordinator = parser.getAttributeValue(null, "Coordinator")
                // Satelliten (Surround, Sub) stehen als eigene Elemente darin und spielen nicht selbst
                "ZoneGroupMember" -> {
                    val uuid = parser.getAttributeValue(null, "UUID") ?: continue
                    val location = parser.getAttributeValue(null, "Location") ?: continue
                    val memberBase = Regex("^(https?://[^/]+)").find(location)?.groupValues?.get(1) ?: continue
                    members += ZoneMember(uuid, parser.getAttributeValue(null, "ZoneName"), memberBase, coordinator ?: uuid)
                }
            }
        }
        return members
    }

    /**
     * Die Warteschlange, sobald sie so lang ist wie beim Einreihen gemeldet ([expectedLength]) —
     * höchstens [QUEUE_FILL_TIMEOUT_MS] lang. Zweites Ergebnis: ihre Länge.
     */
    private suspend fun queueWhenFilled(base: String, expectedLength: Int?): Pair<List<LocalEntry>, Int> {
        val deadline = SystemClock.elapsedRealtime() + QUEUE_FILL_TIMEOUT_MS
        while (true) {
            val (entries, length) = browseAll(base, "Q:0")
            if (expectedLength == null || length >= expectedLength || SystemClock.elapsedRealtime() >= deadline) {
                return entries to length
            }
            delay(QUEUE_FILL_POLL_MS)
        }
    }

    /** Für Nebensachen wie den Zufallsmodus: Lehnt der Speaker ab, geht es trotzdem weiter. */
    private suspend fun <T> ignoringRefusal(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: LocalSonosException) {
            Log.d(TAG, "Übergangen: ${e.message}")
            null
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
        browsePage(base, objectId, start = 0, count = count).entries

    private class BrowsePage(val entries: List<LocalEntry>, val returned: Int, val total: Int)

    private suspend fun browsePage(
        base: String,
        objectId: String,
        start: Int,
        count: Int,
        keepUntitled: Boolean = false
    ): BrowsePage {
        val response = soap(
            base, CONTENT_DIRECTORY_PATH, "ContentDirectory", "Browse",
            "ObjectID" to objectId,
            "BrowseFlag" to "BrowseDirectChildren",
            "Filter" to "*",
            "StartingIndex" to start.toString(),
            "RequestedCount" to count.toString(),
            "SortCriteria" to ""
        )
        // Hunderte Titel auf einmal — nicht auf dem Haupt-Thread lesen
        return withContext(Dispatchers.Default) {
            val didl = textOf(response, "Result")
            val entries = didl?.let { parseDidl(it, base, keepUntitled) }.orEmpty()
            BrowsePage(
                entries = entries,
                returned = textOf(response, "NumberReturned")?.trim()?.toIntOrNull() ?: entries.size,
                total = textOf(response, "TotalMatches")?.trim()?.toIntOrNull() ?: entries.size
            )
        }
    }

    /**
     * Alle Kinder eines Containers, seitenweise — eine Playlist mit Hörbüchern hat schnell
     * Hunderte Titel, und der Speaker liefert nicht alle auf einmal. Auch Einträge ohne Titel,
     * damit die Plätze in der Warteschlange stimmen. Zweites Ergebnis: Anzahl laut Speaker.
     */
    private suspend fun browseAll(base: String, objectId: String): Pair<List<LocalEntry>, Int> {
        val entries = mutableListOf<LocalEntry>()
        var total = 0
        var start = 0
        while (true) {
            val page = browsePage(base, objectId, start, BROWSE_PAGE_SIZE, keepUntitled = true)
            entries += page.entries
            total = page.total
            start += page.returned
            if (page.returned <= 0 || start >= page.total || start >= MAX_BROWSE_ITEMS) break
        }
        return entries to maxOf(total, entries.size)
    }

    /**
     * Ein SOAP-Aufruf an einen Dienst des Speakers, z. B. AVTransport oder ContentDirectory.
     * [arguments] werden für XML maskiert. Antwortet der Speaker mit einem Fehler, kommt eine
     * [LocalSonosException] mit dem UPnP-Fehlercode.
     */
    private suspend fun soap(
        base: String,
        path: String,
        service: String,
        action: String,
        vararg arguments: Pair<String, String>
    ): String = withContext(Dispatchers.IO) {
        val args = arguments.joinToString("") { (name, value) -> "<$name>${escapeXml(value)}</$name>" }
        val body = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:$service:1">$args</u:$action></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(base + path)
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = runCatching { textOf(text, "errorCode")?.trim() }.getOrNull()
                Log.w(TAG, "UPnP $action bei $base: HTTP ${response.code}, Fehler $code")
                throw LocalSonosException("Der Speaker hat „$action“ abgelehnt (HTTP ${response.code}, Fehler ${code ?: "?"})", code)
            }
            text
        }
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

    /** Erster Künstler in einem DIDL — etwa dem eingebetteten des Favoriten (`r:resMD`). */
    private fun artistInDidl(didl: String): String? = runCatching {
        val parser = Xml.newPullParser().apply { setInput(StringReader(didl)) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') in ARTIST_TAGS) {
                parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { return@runCatching it }
            }
        }
        null
    }.getOrNull()

    private fun parseDidl(didl: String, base: String, keepUntitled: Boolean = false): List<LocalEntry> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(didl)) }
        val entries = mutableListOf<LocalEntry>()
        var id: String? = null
        var title: String? = null
        var art: String? = null
        var res: String? = null
        var artist: String? = null
        var album: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (name) {
                    "item", "container" -> {
                        id = parser.getAttributeValue(null, "id")
                        title = null
                        art = null
                        res = null
                        artist = null
                        album = null
                    }
                    // Abspiel-Adresse des Inhalts, z. B. x-rincon-cpcontainer:…?sid=204&…
                    "res" -> if (res == null) res = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    "title" -> if (title == null) title = parser.nextText()
                    "album" -> if (album == null) album = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    // dc:creator, upnp:artist oder upnp:albumArtist — bei Titeln und manchen Alben
                    in ARTIST_TAGS -> if (artist == null) artist = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    // Favoriten tragen die Daten des eigentlichen Inhalts als eingebettetes DIDL mit
                    "resMD" -> if (artist == null) artist = artistInDidl(parser.nextText())
                    // Erstes Bild nehmen; relative Pfade (/getaa?…) liefert der Speaker selbst
                    "albumArtURI" -> if (art == null) {
                        art = parser.nextText().trim().let { if (it.startsWith("/")) base + it else it }
                            .let(::normalizeImageUrl)
                    }
                }
                XmlPullParser.END_TAG -> if (name == "item" || name == "container") {
                    val entryId = id
                    val entryTitle = title ?: "".takeIf { keepUntitled }
                    val fresh = res?.let { "$base/getaa?s=1&u=" + java.net.URLEncoder.encode(it, "UTF-8") }
                    if (entryId != null && entryTitle != null) {
                        entries += LocalEntry(entryId, entryTitle, art, fresh, listOfNotNull(artist), album)
                    }
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
        private val ARTIST_TAGS = setOf("creator", "artist", "albumArtist")
        private const val CONTENT_DIRECTORY_PATH = "/MediaServer/ContentDirectory/Control"
        private const val AV_TRANSPORT_PATH = "/MediaRenderer/AVTransport/Control"
        private const val ZONE_TOPOLOGY_PATH = "/ZoneGroupTopology/Control"
        /** So viele Einträge fragt die App je Browse-Aufruf ab. */
        private const val BROWSE_PAGE_SIZE = 100
        /** Mehr Titel nimmt Sonos ohnehin nicht in eine Warteschlange. */
        private const val MAX_BROWSE_ITEMS = 10_000
        private const val QUEUE_FILL_TIMEOUT_MS = 4_000L
        private const val QUEUE_FILL_POLL_MS = 300L
        /** UPnP-Fehler „No such object“: Die Playlist gibt es auf dem Speaker nicht (mehr). */
        private const val ERROR_NO_SUCH_OBJECT = "701"
        const val NO_SPEAKER_MESSAGE = "Kein Sonos-Speaker im WLAN gefunden. Alben aus einer Playlist lassen sich " +
            "nur abspielen, wenn das Tablet im selben WLAN ist wie die Speaker."

        /** Wiederholen aus einem UPnP-Wiedergabemodus: "ALL", "ONE" oder null = aus. */
        private fun repeatOf(playMode: String?): String? = when (playMode) {
            "REPEAT_ALL", "SHUFFLE" -> "ALL"
            "REPEAT_ONE", "SHUFFLE_REPEAT_ONE" -> "ONE"
            else -> null
        }

        /** UPnP-Wiedergabemodus aus Zufall und Wiederholen ([repeatOf]). */
        private fun playMode(shuffle: Boolean, repeat: String?): String = when (repeat) {
            "ALL" -> if (shuffle) "SHUFFLE" else "REPEAT_ALL"
            "ONE" -> if (shuffle) "SHUFFLE_REPEAT_ONE" else "REPEAT_ONE"
            else -> if (shuffle) "SHUFFLE_NOREPEAT" else "NORMAL"
        }
        private val QUEUE_POSITION = Regex("^Q:0/(\\d+)$")

        private fun escapeXml(text: String): String = buildString(text.length) {
            text.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }

        /**
         * Metadaten einer Sonos-Playlist zum Einreihen — so, wie die Sonos-App sie mitschickt.
         * Die Playlist liegt auf dem Speaker selbst; die Titel kennt er also schon.
         */
        private fun playlistDidl(playlistId: String, title: String): String =
            """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """ +
                """xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">""" +
                """<container id="SQ:${escapeXml(playlistId)}" parentID="SQ:" restricted="true">""" +
                """<dc:title>${escapeXml(title)}</dc:title><upnp:class>object.container.playlistContainer</upnp:class>""" +
                """<desc id="cdudn" nameSpace="urn:schemas-rinconnetworks-com:metadata-1-0/">RINCON_AssociatedZPUDN</desc>""" +
                """</container></DIDL-Lite>"""
    }
}
