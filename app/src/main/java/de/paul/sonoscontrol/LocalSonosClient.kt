package de.paul.sonoscontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
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
    val album: String? = null,
    /** Abspiel-Adresse (`res`), z. B. `x-rincon-cpcontainer:…` — damit startet der Speaker den Inhalt. */
    val res: String? = null,
    /** Metadaten zur Abspiel-Adresse (bei Favoriten das eingebettete DIDL aus `r:resMD`). */
    val resMetaData: String? = null,
    /** Art des Inhalts (`upnp:class`), bei Favoriten die des eigentlichen Inhalts. */
    val upnpClass: String? = null,
    /** Beschreibung, bei Favoriten z. B. „Apple Music-Playlist“ (`r:description`). */
    val description: String? = null
)

/**
 * Ein Befehl an den Speaker im Heimnetz wurde abgelehnt oder der Speaker antwortet nicht — mit
 * verständlicher Meldung. [errorCode] ist der UPnP-Fehler, falls der Speaker einen genannt hat.
 */
open class LocalSonosException(message: String, val errorCode: String? = null) : IOException(message)

/** Im WLAN antwortet gar kein Sonos-Speaker — dann steuert die App über die Sonos-Cloud. */
class NoSpeakerInWifiException : LocalSonosException(LocalSonosClient.NO_SPEAKER_MESSAGE)

/**
 * Speaker im Heimnetz laut Zonen-Topologie: Kennung (wie die Player-Id der Control API), Raum, Adresse,
 * Koordinator seiner Gruppe. [invisible]: zweiter Speaker eines Stereopaars o. Ä., spielt nicht selbst.
 */
private data class ZoneMember(
    val uuid: String,
    val name: String?,
    val baseUrl: String,
    val coordinatorUuid: String,
    val invisible: Boolean = false
)

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

    /**
     * Der Katalog allein aus dem Heimnetz — falls die Sonos-Cloud nicht antwortet. Ids wie in der
     * Control API ("FV:2/13" → "13", "SQ:13" → "13"); Cover ergänzt [coversFor].
     */
    fun catalogEntries(): List<CatalogEntry> =
        favorites.filter { it.id.startsWith(FAVORITE_PREFIX) }.map { favorite ->
            CatalogEntry(
                source = MusicSource.FAVORITE,
                sonosId = favorite.id.removePrefix(FAVORITE_PREFIX),
                name = favorite.title,
                description = favorite.description,
                imageUrl = null,
                type = musicTypeOf(favorite),
                artists = favorite.artists,
                rawData = favorite.resMetaData ?: favorite.res
            )
        } + playlists.filter { it.id.startsWith(PLAYLIST_PREFIX) }.map { playlist ->
            CatalogEntry(
                source = MusicSource.PLAYLIST,
                sonosId = playlist.id.removePrefix(PLAYLIST_PREFIX),
                name = playlist.title,
                description = null,
                imageUrl = null,
                type = MusicType.PLAYLIST,
                artists = playlist.artists
            )
        }

    private fun musicTypeOf(favorite: LocalEntry): MusicType {
        val type = favorite.upnpClass.orEmpty()
        val uri = favorite.res.orEmpty()
        return when {
            type.contains("audioBroadcast") || LocalSonosClient.isRadioUri(uri) -> MusicType.RADIO
            type.contains("playlistContainer") -> MusicType.PLAYLIST
            type.contains("album", ignoreCase = true) -> MusicType.ALBUM
            type.startsWith("object.item.audioItem") -> MusicType.TRACK
            else -> MusicType.OTHER
        }
    }

    /** Control-API-Id "13" entspricht lokal "FV:2/13" bzw. "SQ:13" — sonst über den Namen. */
    private fun find(entry: CatalogEntry): LocalEntry? {
        val (list, localId) = when (entry.source) {
            MusicSource.FAVORITE -> favorites to "$FAVORITE_PREFIX${entry.sonosId}"
            MusicSource.PLAYLIST -> playlists to "$PLAYLIST_PREFIX${entry.sonosId}"
            // Steht nicht im Katalog
            MusicSource.PLAYLIST_ALBUM -> return null
        }
        return list.firstOrNull { it.id == localId && it.title.equals(entry.name, ignoreCase = true) }
            ?: list.firstOrNull { it.title.trim().equals(entry.name.trim(), ignoreCase = true) }
    }

    private companion object {
        const val FAVORITE_PREFIX = "FV:2/"
        const val PLAYLIST_PREFIX = "SQ:"
    }
}

/**
 * Steuert die Sonos-Speaker direkt im Heimnetz (UPnP, Port 1400) — so wie die Sonos-App, ohne den
 * Umweg über die Sonos-Cloud: Speaker und Gruppen, Wiedergabe, Lautstärke, Musik starten, Gruppieren.
 * Außerdem kennen die Speaker Cover und Künstler, die die Cloud-API nicht liefert (für
 * Sonos-Playlisten nie ein Bild, für manche Favoriten auch nicht).
 *
 * Den ersten Speaker findet die App unter den zuletzt bekannten Adressen, sonst per mDNS
 * (`_sonos._tcp`); alle weiteren nennt die Zonen-Topologie. Gruppen-Ids sind hier die Kennung des
 * Gruppen-Koordinators — er nimmt alle Befehle zur Wiedergabe der Gruppe an.
 */
class LocalSonosClient(context: Context) : SonosControl {

    override val path = ControlPath.LOCAL

    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Für gemerkte Adressen: Ist dort niemand mehr (neue IP vom Router), nicht lange warten. */
    private val probeHttp = http.newBuilder()
        .connectTimeout(PROBE_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** z. B. "http://192.168.1.23:1400" — einmal gefunden, wird sie wiederverwendet. */
    @Volatile
    private var speakerBaseUrl: String? = null

    /** Adresse eines Speakers aus einer Cover-Adresse der Wiedergabe — ein Tipp für die nächste Suche. */
    @Volatile
    private var hintedBaseUrl: String? = null

    /** Alle Speaker laut letzter Zonen-Topologie — für die Adressen; null = beim nächsten Mal neu holen. */
    @Volatile
    private var members: List<ZoneMember>? = null

    /**
     * In den Einstellungen gewählt: immer über die Sonos-Cloud steuern, auch wenn die Speaker
     * im WLAN erreichbar sind. Gilt nur für dieses Tablet.
     */
    var preferCloud: Boolean
        get() = prefs.getBoolean(KEY_PREFER_CLOUD, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PREFER_CLOUD, value).apply()
        }

    /** Adressen der Speaker vom letzten Mal — so geht es beim App-Start ohne mDNS-Suche. */
    private var knownAddresses: List<String>
        get() = prefs.getString(KEY_ADDRESSES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ADDRESSES, value.joinToString("\n")).apply()
        }

    /**
     * Eine Cover-Adresse der Wiedergabe (`http://IP:1400/…`) verrät, wo ein Speaker steht — auch wenn
     * die App gerade über die Cloud steuert. Die nächste Suche im WLAN fragt dort zuerst.
     */
    fun noteAddress(url: String?) {
        baseUrlFrom(url)?.let { hintedBaseUrl = it }
    }

    // --- Steuerung (SonosControl) ------------------------------------------

    override suspend fun getGroups(): GroupsResponse = groupsOf(zoneTopology(addressHint = null))

    /**
     * Versucht es direkt unter [addresses] (`http://IP:1400`) — ohne Suche im WLAN. Die Sonos-Cloud
     * nennt die Adressen der Speaker; damit klappt die Steuerung im WLAN auch dort, wo die Suche
     * nichts findet. null, wenn unter keiner Adresse ein Speaker antwortet.
     */
    suspend fun connectTo(addresses: List<String>): GroupsResponse? {
        for (base in addresses.distinct().take(MAX_PROBED_ADDRESSES)) {
            try {
                zoneMembers(base, probeHttp).takeIf { it.isNotEmpty() }?.let { return groupsOf(remember(base, it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppLog.d(TAG, "Speaker unter $base (Adresse aus der Cloud) antwortet nicht: ${e.message}")
            }
        }
        return null
    }

    private fun groupsOf(all: List<ZoneMember>): GroupsResponse {
        val groups = all.groupBy { it.coordinatorUuid }.map { (coordinatorUuid, groupMembers) ->
            val coordinator = groupMembers.firstOrNull { it.uuid == coordinatorUuid } ?: groupMembers.first()
            SonosGroup(
                id = coordinatorUuid,
                name = coordinator.name.orEmpty(),
                coordinatorId = coordinatorUuid,
                playerIds = groupMembers.filterNot { it.invisible }.map { it.uuid }
            )
        }
        val players = all.filterNot { it.invisible }.map { SonosPlayer(id = it.uuid, name = it.name ?: it.uuid) }
        return GroupsResponse(groups = groups, players = players)
    }

    override suspend fun getPlayback(groupId: String): Playback = onSpeaker(groupId) { coordinator ->
        val base = coordinator.baseUrl
        coroutineScope {
            val info = async { transport(base, "GetTransportInfo") }
            val settings = async { ignoringRefusal { transport(base, "GetTransportSettings") } }
            val position = async { transport(base, "GetPositionInfo") }
            val media = async { ignoringRefusal { transport(base, "GetMediaInfo") } }
            val actions = async { ignoringRefusal { transport(base, "GetCurrentTransportActions") } }
            val positionInfo = position.await()
            val status = PlaybackStatus(
                playbackState = playbackState(textOf(info.await(), "CurrentTransportState")),
                positionMillis = parseDuration(textOf(positionInfo, "RelTime")) ?: 0L,
                availablePlaybackActions = actions.await()?.let { textOf(it, "Actions") }?.let { text ->
                    val available = text.split(',').map { it.trim() }
                    PlaybackActions(canSkip = "Next" in available, canSkipBack = "Previous" in available)
                },
                playModes = settings.await()?.let { textOf(it, "PlayMode") }
                    ?.let { PlayModes(shuffle = it.trim().startsWith("SHUFFLE")) }
            )
            Playback(status, playbackMetadata(base, positionInfo, media.await()))
        }
    }

    override suspend fun getPlaybackStatus(groupId: String): PlaybackStatus = onSpeaker(groupId) { coordinator ->
        PlaybackStatus(playbackState = playbackState(textOf(transport(coordinator.baseUrl, "GetTransportInfo"), "CurrentTransportState")))
    }

    override suspend fun getPlayerVolume(playerId: String): PlayerVolume = onSpeaker(playerId) { player ->
        coroutineScope {
            val volume = async { rendering(player.baseUrl, "GetVolume", "Channel" to "Master") }
            val mute = async { ignoringRefusal { rendering(player.baseUrl, "GetMute", "Channel" to "Master") } }
            PlayerVolume(
                volume = textOf(volume.await(), "CurrentVolume")?.trim()?.toIntOrNull() ?: 0,
                muted = mute.await()?.let { textOf(it, "CurrentMute")?.trim() == "1" } ?: false
            )
        }
    }

    override suspend fun setPlayerVolume(playerId: String, volume: Int) = onSpeaker<Unit>(playerId) { player ->
        rendering(player.baseUrl, "SetVolume", "Channel" to "Master", "DesiredVolume" to volume.coerceIn(0, 100).toString())
    }

    override suspend fun togglePlayPause(groupId: String) = onSpeaker<Unit>(groupId) { coordinator ->
        val base = coordinator.baseUrl
        val state = textOf(transport(base, "GetTransportInfo"), "CurrentTransportState")?.trim()
        if (state == "PLAYING" || state == "TRANSITIONING") {
            try {
                transport(base, "Pause")
            } catch (e: LocalSonosException) {
                // Manche Radiosender lassen sich nicht pausieren, nur anhalten
                AppLog.d(TAG, "Pause abgelehnt, stattdessen anhalten: ${e.message}")
                transport(base, "Stop")
            }
        } else {
            transport(base, "Play", "Speed" to "1")
        }
    }

    override suspend fun skipToNextTrack(groupId: String) = onSpeaker<Unit>(groupId) { transport(it.baseUrl, "Next") }

    override suspend fun skipToPreviousTrack(groupId: String) = onSpeaker<Unit>(groupId) { transport(it.baseUrl, "Previous") }

    override suspend fun play(groupId: String) = onSpeaker<Unit>(groupId) { transport(it.baseUrl, "Play", "Speed" to "1") }

    /** Der Speaker folgt ab jetzt dem Koordinator der Gruppe ([groupId] ist dessen Kennung). */
    override suspend fun addToGroup(groupId: String, playerId: String) = onSpeaker<Unit>(playerId) { player ->
        transport(player.baseUrl, "SetAVTransportURI", "CurrentURI" to "x-rincon:$groupId", "CurrentURIMetaData" to "")
    }

    /**
     * Ein Mitspieler geht einfach allein weiter. Geht der Koordinator, gibt er die Gruppe samt Musik
     * erst an einen anderen Speaker ab (DelegateGroupCoordinationTo) — klappt das nicht, geht er
     * trotzdem, und Sonos bestimmt selbst einen neuen Koordinator.
     */
    override suspend fun removeFromGroup(groupId: String, playerId: String) {
        if (playerId != groupId) {
            onSpeaker<Unit>(playerId) { transport(it.baseUrl, "BecomeCoordinatorOfStandaloneGroup") }
            return
        }
        val successor = zoneTopology(addressHint = null)
            .firstOrNull { it.coordinatorUuid == groupId && it.uuid != playerId && !it.invisible }
        onSpeaker<Unit>(playerId) { coordinator ->
            if (successor != null) {
                try {
                    transport(
                        coordinator.baseUrl, "DelegateGroupCoordinationTo",
                        "NewCoordinator" to successor.uuid,
                        "RejoinGroup" to "0"
                    )
                    return@onSpeaker
                } catch (e: LocalSonosException) {
                    AppLog.d(TAG, "Gruppe nicht an ${successor.name} übergeben: ${e.message}")
                }
            }
            transport(coordinator.baseUrl, "BecomeCoordinatorOfStandaloneGroup")
        }
    }

    override suspend fun getPlaylists(): List<SonosPlaylist> = onAnySpeaker { base ->
        browse(base, "SQ:").filter { it.id.startsWith("SQ:") }
            .map { SonosPlaylist(id = it.id.removePrefix("SQ:"), name = it.title) }
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<PlaylistTrackInfo> = onAnySpeaker { base ->
        browseAll(base, "SQ:$playlistId").first.map { trackInfo(it) }
    }

    /**
     * Ein Favorit startet so, wie die Sonos-App ihn startet: Radiosender und andere Streams direkt
     * (SetAVTransportURI), alles andere über die Warteschlange. Eine Sonos-Playlist liegt auf dem
     * Speaker selbst und kommt als Ganzes in die Warteschlange.
     */
    override suspend fun startMusic(groupId: String, item: MusicItem, shuffle: Boolean) {
        when (item.musicSource) {
            MusicSource.FAVORITE -> {
                val favorites = onSpeaker(groupId) { browse(it.baseUrl, "FV:2") }
                val favorite = favorites.firstOrNull { it.id == "FV:2/${item.sonosId}" && it.title == item.name }
                    ?: favorites.firstOrNull { it.title == item.name }
                    ?: favorites.firstOrNull { it.title.trim().equals(item.name.trim(), ignoreCase = true) }
                    ?: throw LocalSonosException("„${item.name}“ gibt es nicht mehr in den Sonos-Favoriten.")
                val uri = favorite.res
                    ?: throw LocalSonosException("Den Favoriten „${item.name}“ kann der Speaker nicht abspielen.")
                val metadata = favorite.resMetaData.orEmpty()
                onSpeaker<Unit>(groupId) { coordinator ->
                    if (DIRECT_PLAY_PREFIXES.any { uri.startsWith(it) }) {
                        transport(coordinator.baseUrl, "SetAVTransportURI", "CurrentURI" to uri, "CurrentURIMetaData" to metadata)
                        transport(coordinator.baseUrl, "Play", "Speed" to "1")
                    } else {
                        playFromQueue(coordinator, item.name, shuffle) { enqueue(coordinator.baseUrl, uri, metadata) }
                    }
                }
            }
            MusicSource.PLAYLIST -> {
                val playlists = getPlaylists()
                val playlist = playlists.firstOrNull { it.id == item.sonosId && it.name == item.name }
                    ?: playlists.firstOrNull { it.name == item.name }
                    ?: throw LocalSonosException("Die Playlist „${item.name}“ gibt es nicht mehr bei Sonos.")
                onSpeaker<Unit>(groupId) { coordinator ->
                    playFromQueue(coordinator, item.name, shuffle) {
                        enqueuePlaylist(coordinator.baseUrl, playlist.id, playlist.name)
                    }
                }
            }
            MusicSource.PLAYLIST_ALBUM -> error("Alben aus einer Playlist startet playAlbumFromPlaylist")
        }
    }

    /**
     * Ersetzt die Warteschlange des Koordinators durch das, was [enqueue] einreiht (gibt die neue Länge
     * zurück, falls bekannt), und spielt sie ab — der Reihe nach oder zufällig, Wiederholen immer aus.
     */
    private suspend fun playFromQueue(coordinator: ZoneMember, name: String, shuffle: Boolean, enqueue: suspend () -> Int?) {
        val base = coordinator.baseUrl
        // Zufall vom letzten Mal vorher aus, damit die Warteschlange in ihrer Reihenfolge bleibt
        ignoringRefusal { transport(base, "SetPlayMode", "NewPlayMode" to PLAY_MODE_NORMAL) }
        transport(base, "RemoveAllTracksFromQueue")
        val length = enqueue()
        if (length == 0) throw LocalSonosException("Sonos hat für „$name“ keine Titel eingereiht.")
        transport(base, "SetAVTransportURI", "CurrentURI" to "x-rincon-queue:${coordinator.uuid}#0", "CurrentURIMetaData" to "")
        // Wiederholen bleibt aus, sonst liefe die Auswahl in Dauerschleife
        ignoringRefusal { transport(base, "SetPlayMode", "NewPlayMode" to if (shuffle) PLAY_MODE_SHUFFLE else PLAY_MODE_NORMAL) }
        // Im Zufallsmodus auch mit einem zufälligen Titel beginnen, sonst der Reihe nach von vorn
        val first = if (shuffle && length != null && length > 1) Random.nextInt(length) + 1 else 1
        ignoringRefusal { transport(base, "Seek", "Unit" to "TRACK_NR", "Target" to first.toString()) }
        transport(base, "Play", "Speed" to "1")
    }

    /** Reiht [uri] am Ende der Warteschlange ein; Ergebnis: ihre neue Länge, falls genannt. */
    private suspend fun enqueue(base: String, uri: String, metadata: String): Int? {
        val added = transport(
            base, "AddURIToQueue",
            "EnqueuedURI" to uri,
            "EnqueuedURIMetaData" to metadata,
            "DesiredFirstTrackNumberEnqueued" to "0",
            "EnqueueAsNext" to "0"
        )
        return textOf(added, "NewQueueLength")?.trim()?.toIntOrNull()
    }

    /** Reiht eine Sonos-Playlist ein, die auf dem Speaker liegt. */
    private suspend fun enqueuePlaylist(base: String, playlistId: String, playlistName: String): Int? =
        try {
            enqueue(base, "file:///jffs/settings/savedqueues.rsq#$playlistId", playlistDidl(playlistId, playlistName))
        } catch (e: LocalSonosException) {
            if (e.errorCode == ERROR_NO_SUCH_OBJECT) {
                throw LocalSonosException("Die Playlist „$playlistName“ gibt es bei Sonos nicht mehr.", e.errorCode)
            }
            throw e
        }

    /**
     * Führt [block] mit dem Speaker [uuid] aus. Antwortet er nicht (z. B. neue IP vom Router), wird
     * die Topologie beim nächsten Mal neu geholt, und es kommt eine verständliche Meldung.
     */
    private suspend fun <T> onSpeaker(uuid: String, block: suspend (ZoneMember) -> T): T {
        val speaker = member(uuid)
        return try {
            block(speaker)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalSonosException) {
            throw e
        } catch (e: IOException) {
            AppLog.w(TAG, "Speaker ${speaker.name} unter ${speaker.baseUrl} nicht erreichbar", e)
            forgetAddresses(speaker.baseUrl)
            throw LocalSonosException("Der Speaker „${speaker.name ?: uuid}“ ist im WLAN gerade nicht erreichbar.")
        }
    }

    /** Für Abfragen, die jeder Speaker des Haushalts beantworten kann (Favoriten, Playlisten). */
    private suspend fun <T> onAnySpeaker(block: suspend (base: String) -> T): T {
        val base = speakerBaseUrl ?: run {
            zoneTopology(addressHint = null)
            speakerBaseUrl
        } ?: throw NoSpeakerInWifiException()
        return try {
            block(base)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalSonosException) {
            throw e
        } catch (e: IOException) {
            AppLog.w(TAG, "Speaker unter $base nicht erreichbar", e)
            forgetAddresses(base)
            throw LocalSonosException("Der Sonos-Speaker im WLAN antwortet gerade nicht.")
        }
    }

    /** Ein Speaker laut Topologie — aus der gemerkten, sonst frisch geholt. */
    private suspend fun member(uuid: String): ZoneMember =
        members?.firstOrNull { it.uuid == uuid }
            ?: zoneTopology(addressHint = null).firstOrNull { it.uuid == uuid }
            ?: throw LocalSonosException("Den Speaker gibt es gerade nicht im WLAN.")

    /** Unter [base] antwortet niemand mehr → beim nächsten Mal Topologie und Speaker neu suchen. */
    private fun forgetAddresses(base: String) {
        members = null
        if (speakerBaseUrl == base) speakerBaseUrl = null
    }

    private suspend fun transport(base: String, action: String, vararg arguments: Pair<String, String>): String =
        soap(base, AV_TRANSPORT_PATH, "AVTransport", action, "InstanceID" to "0", *arguments)

    private suspend fun rendering(base: String, action: String, vararg arguments: Pair<String, String>): String =
        soap(base, RENDERING_CONTROL_PATH, "RenderingControl", action, "InstanceID" to "0", *arguments)

    /**
     * Was gerade läuft, aus GetPositionInfo (der Titel) und GetMediaInfo (die Quelle, z. B. der
     * Radiosender). Bei Radio steht der Sender als Container da und der laufende Titel als [PlaybackMetadata.streamInfo].
     */
    private fun playbackMetadata(base: String, positionInfo: String, mediaInfo: String?): PlaybackMetadata {
        val trackUri = textOf(positionInfo, "TrackURI").orEmpty()
        val track = didlFields(textOf(positionInfo, "TrackMetaData"))
        val mediaUri = mediaInfo?.let { textOf(it, "CurrentURI") }.orEmpty()
        val media = didlFields(mediaInfo?.let { textOf(it, "CurrentURIMetaData") })
        val trackArt = track["albumArtURI"]?.let { absoluteUrl(base, it) }
        val mediaArt = media["albumArtURI"]?.let { absoluteUrl(base, it) }
        val isLiveStream = LIVE_STREAM_PREFIXES.any { mediaUri.startsWith(it) || trackUri.startsWith(it) } ||
            track["class"].orEmpty().contains("audioBroadcast")
        if (isLiveStream) {
            val station = media["title"] ?: track["title"]?.takeUnless { looksLikeUri(it) }
            return PlaybackMetadata(
                container = MetadataContainer(name = station, type = "station", imageUrl = trackArt ?: mediaArt),
                // ZPSTR_CONNECTING, ZPSTR_BUFFERING …: Zwischenstände, keine Titel
                streamInfo = track["streamContent"]?.takeUnless { it.startsWith("ZPSTR_") }
            )
        }
        val title = track["title"]?.takeUnless { looksLikeUri(it) }
        val item = if (title != null || trackArt != null) {
            MetadataItem(
                Track(
                    name = title,
                    imageUrl = trackArt,
                    durationMillis = parseDuration(textOf(positionInfo, "TrackDuration")),
                    artist = (track["creator"] ?: track["artist"] ?: track["albumArtist"])?.let { NamedItem(it) },
                    album = track["album"]?.let { NamedItem(it) }
                )
            )
        } else {
            null
        }
        val container = media["title"]?.takeUnless { looksLikeUri(it) }?.let { MetadataContainer(name = it, imageUrl = mediaArt) }
        return PlaybackMetadata(container = container, currentItem = item)
    }

    // --- Cover, Playlisten, Alben ------------------------------------------

    /**
     * Lädt Favoriten und Sonos-Playlisten samt Covern und Künstlern vom Speaker.
     * [addressHint]: eine Cover-URL der Wiedergabe (http://IP:1400/…), falls bekannt.
     */
    suspend fun loadCovers(addressHint: String?): LocalCovers? {
        val base = speakerBaseUrl ?: baseUrlFrom(addressHint) ?: knownAddresses.firstOrNull() ?: discoverSpeaker() ?: run {
            AppLog.d(TAG, "Kein Sonos-Speaker im Heimnetz gefunden")
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
            rememberAddress(base)
            AppLog.d(TAG, "Lokal ${favorites.size} Favoriten, ${playlists.size} Playlisten von $base")
            LocalCovers(favorites, playlists)
        } catch (e: Exception) {
            // Speaker nicht mehr unter der Adresse erreichbar → beim nächsten Mal neu suchen
            speakerBaseUrl = null
            AppLog.w(TAG, "Lokale Abfrage bei $base fehlgeschlagen", e)
            null
        }
    }

    /**
     * Die Titel einer Sonos-Playlist mit Album, Künstler und Cover, in ihrer Reihenfolge — null, wenn
     * kein Speaker im Heimnetz antwortet oder die Playlist dort fehlt.
     */
    suspend fun playlistTracks(playlistId: String, addressHint: String?): List<PlaylistTrackInfo>? {
        val base = speakerBaseUrl ?: baseUrlFrom(addressHint) ?: knownAddresses.firstOrNull() ?: discoverSpeaker() ?: run {
            AppLog.d(TAG, "Kein Sonos-Speaker im Heimnetz gefunden")
            return null
        }
        return try {
            browseAll(base, "SQ:$playlistId").first
                .map { trackInfo(it) }
                .also { rememberAddress(base) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nicht erreichbar → beim nächsten Mal neu suchen; hat er abgelehnt, ist er aber da
            if (e !is LocalSonosException) speakerBaseUrl = null
            AppLog.w(TAG, "Titel der Playlist $playlistId nicht von $base geladen", e)
            null
        }
    }

    private fun trackInfo(track: LocalEntry) = PlaylistTrackInfo(
        title = track.title.takeIf { it.isNotBlank() },
        album = track.album,
        artist = track.artists.firstOrNull(),
        imageUrls = listOfNotNull(track.artUrl, track.freshArtUrl)
    )

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
            AppLog.w(TAG, "Speaker ${coordinator.name} unter ${coordinator.baseUrl} nicht erreichbar", e)
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

        // Zufall und Wiederholen vom letzten Mal vorher aus, damit die Warteschlange in der Reihenfolge
        // der Playlist bleibt
        val previousMode = ignoringRefusal { textOf(transport(base, "GetTransportSettings"), "PlayMode")?.trim() }
        if (previousMode != null && previousMode != PLAY_MODE_NORMAL) {
            ignoringRefusal { transport(base, "SetPlayMode", "NewPlayMode" to PLAY_MODE_NORMAL) }
        }

        transport(base, "RemoveAllTracksFromQueue")
        val expectedLength = enqueuePlaylist(base, playlistId, playlistName)

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
            ignoringRefusal { transport(base, "RemoveAllTracksFromQueue") }
            throw LocalSonosException("„$albumName“ steckt nicht mehr in der Playlist „$playlistName“.")
        }
        rangesToRemove(keep, queueLength).forEach { (start, count) ->
            transport(
                base, "RemoveTrackRangeFromQueue",
                "UpdateID" to "0",
                "StartingIndex" to start.toString(),
                "NumberOfTracks" to count.toString()
            )
        }
        AppLog.d(TAG, "„$albumName“: ${keep.size} von $queueLength Titeln der Playlist „$playlistName“ in der Warteschlange")

        transport(base, "SetAVTransportURI", "CurrentURI" to "x-rincon-queue:${coordinator.uuid}#0", "CurrentURIMetaData" to "")
        // Wiederholen bleibt aus, sonst liefe das Album in Dauerschleife
        ignoringRefusal { transport(base, "SetPlayMode", "NewPlayMode" to if (shuffle) PLAY_MODE_SHUFFLE else PLAY_MODE_NORMAL) }
        // Im Zufallsmodus auch mit einem zufälligen Titel beginnen, sonst der Reihe nach von vorn
        val first = if (shuffle) Random.nextInt(keep.size) + 1 else 1
        transport(base, "Seek", "Unit" to "TRACK_NR", "Target" to first.toString())
        transport(base, "Play", "Speed" to "1")
    }

    /**
     * Speaker im Heimnetz mit ihren Gruppen — gefragt beim zuletzt benutzten Speaker, bei dem aus
     * [addressHint], unter den Adressen vom letzten Mal oder bei einem per mDNS gefundenen.
     * Wirft [NoSpeakerInWifiException], wenn keiner antwortet.
     */
    private suspend fun zoneTopology(addressHint: String?): List<ZoneMember> {
        val remembered = knownAddresses.take(MAX_PROBED_ADDRESSES)
        val candidates = (listOfNotNull(speakerBaseUrl, baseUrlFrom(addressHint), hintedBaseUrl) + remembered).distinct()
        // Adressen, unter denen niemand geantwortet hat — fürs Protokoll, falls es woanders doch klappt
        val silent = mutableListOf<String>()
        for (base in candidates) {
            try {
                zoneMembers(base, probeHttp).takeIf { it.isNotEmpty() }?.let {
                    logReachedElsewhere(silent, base)
                    return remember(base, it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppLog.w(TAG, "Speaker unter $base antwortet nicht", e)
                silent += base
            }
            if (speakerBaseUrl == base) speakerBaseUrl = null
        }
        val found = discoverSpeaker() ?: throw NoSpeakerInWifiException()
        val members = try {
            zoneMembers(found, http)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLog.w(TAG, "Zonen-Topologie von $found nicht geladen", e)
            throw NoSpeakerInWifiException()
        }
        if (members.isEmpty()) throw NoSpeakerInWifiException()
        logReachedElsewhere(silent, found)
        return remember(found, members)
    }

    private fun logReachedElsewhere(silent: List<String>, base: String) {
        if (silent.isEmpty()) return
        AppLog.resolved(TAG, "Unter ${silent.joinToString()} antwortete kein Speaker — unter $base ist einer erreichbar")
    }

    /** Unter [base] hat ein Speaker geantwortet → für das nächste Mal merken, auch über einen Neustart hinweg. */
    private fun rememberAddress(base: String) {
        speakerBaseUrl = base
        val known = knownAddresses
        if (base !in known) knownAddresses = listOf(base) + known
    }

    /** Merkt sich Topologie und Adressen — auch über einen Neustart der App hinweg. */
    private fun remember(base: String, found: List<ZoneMember>): List<ZoneMember> {
        speakerBaseUrl = base
        members = found
        val addresses = (listOf(base) + found.filterNot { it.invisible }.map { it.baseUrl }).distinct()
        if (addresses != knownAddresses) knownAddresses = addresses
        return found
    }

    /** Alle Speaker samt Koordinator ihrer Gruppe, laut ZoneGroupTopology. */
    private suspend fun zoneMembers(base: String, client: OkHttpClient): List<ZoneMember> {
        val response = soap(base, ZONE_TOPOLOGY_PATH, "ZoneGroupTopology", "GetZoneGroupState", client = client)
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
                    members += ZoneMember(
                        uuid = uuid,
                        name = parser.getAttributeValue(null, "ZoneName"),
                        baseUrl = memberBase,
                        coordinatorUuid = coordinator ?: uuid,
                        invisible = parser.getAttributeValue(null, "Invisible") == "1"
                    )
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
            AppLog.d(TAG, "Übergangen: ${e.message}")
            null
        }

    private fun baseUrlFrom(url: String?): String? {
        val match = url?.let { Regex("^http://([0-9.]+):1400/").find(it) } ?: return null
        return "http://${match.groupValues[1]}:1400"
    }

    /**
     * Sucht einen Speaker im WLAN — gleichzeitig per mDNS (`_sonos._tcp`) und per SSDP (so findet auch
     * die Sonos-App ihre Speaker). Wer zuerst einen findet, gewinnt; null, wenn beide nichts finden.
     */
    private suspend fun discoverSpeaker(): String? = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
        coroutineScope {
            val mdns = async { discoverByMdns() }
            val ssdp = async { discoverBySsdp() }
            val (fromMdns, first) = select<Pair<Boolean, String?>> {
                mdns.onAwait { true to it }
                ssdp.onAwait { false to it }
            }
            val found = first ?: if (fromMdns) ssdp.await() else mdns.await()
            mdns.cancel()
            ssdp.cancel()
            found?.also { AppLog.d(TAG, "Speaker im WLAN gefunden: $it") }
        }
    }

    /**
     * SSDP: eine Suchanfrage an alle Geräte im WLAN (UDP-Multicast und -Broadcast, Port 1900); jeder
     * Sonos-Speaker antwortet direkt mit seiner Adresse. Braucht keine Hilfe von Android und klappt
     * deshalb auch auf Tablets, deren mDNS-Suche hakt.
     */
    private suspend fun discoverBySsdp(): String? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_RECEIVE_TIMEOUT_MS
                socket.broadcast = true
                val request = SSDP_SEARCH.toByteArray(Charsets.US_ASCII)
                val targets = listOf("239.255.255.250", "255.255.255.255")
                    .map { InetSocketAddress(InetAddress.getByName(it), SSDP_PORT) }
                val buffer = ByteArray(2048)
                val deadline = SystemClock.elapsedRealtime() + SSDP_TIMEOUT_MS
                var sentAt = 0L
                while (SystemClock.elapsedRealtime() < deadline) {
                    ensureActive()
                    val now = SystemClock.elapsedRealtime()
                    // UDP kann verloren gehen — die Anfrage deshalb ein paarmal schicken
                    if (sentAt == 0L || now - sentAt >= SSDP_RESEND_MS) {
                        targets.forEach { target ->
                            runCatching { socket.send(DatagramPacket(request, request.size, target)) }
                                .onFailure { AppLog.d(TAG, "SSDP-Anfrage an ${target.hostString} nicht gesendet: ${it.message}") }
                        }
                        sentAt = now
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    ssdpSpeakerAddress(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let { return@withContext it }
                }
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d(TAG, "SSDP-Suche fehlgeschlagen: ${e.message}")
            null
        }
    }

    /**
     * mDNS über Android: alle gemeldeten Speaker der Reihe nach auflösen, bis einer eine IPv4-Adresse
     * nennt. Android erlaubt nur eine Auflösung gleichzeitig, deshalb warten die übrigen in [pending].
     * Endet erst mit einem Fund (oder wenn die Suche gar nicht startet) — die Zeit begrenzt [discoverSpeaker].
     */
    private suspend fun discoverByMdns(): String? = suspendCancellableCoroutine { continuation ->
        val lock = Any()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        lateinit var discovery: NsdManager.DiscoveryListener

        fun finish(result: String?) {
            if (!continuation.isActive) return
            runCatching { nsdManager.stopServiceDiscovery(discovery) }
            continuation.resume(result)
        }

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving) null else pending.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    AppLog.d(TAG, "mDNS: Speaker nicht aufgelöst ($errorCode), nächster")
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = serviceInfo.host
                    if (host is Inet4Address) {
                        finish("http://${host.hostAddress}:1400")
                    } else {
                        synchronized(lock) { resolving = false }
                        resolveNext()
                    }
                }
            }
            try {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(next, listener)
            } catch (e: Exception) {
                AppLog.d(TAG, "mDNS: Auflösen nicht gestartet: ${e.message}")
                synchronized(lock) { resolving = false }
                resolveNext()
            }
        }

        discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLog.w(TAG, "mDNS-Suche nicht gestartet ($errorCode)")
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { pending.addLast(serviceInfo) }
                resolveNext()
            }
        }

        continuation.invokeOnCancellation { runCatching { nsdManager.stopServiceDiscovery(discovery) } }
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (e: Exception) {
            AppLog.w(TAG, "mDNS-Suche nicht gestartet: ${e.message}")
            if (continuation.isActive) continuation.resume(null)
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
        vararg arguments: Pair<String, String>,
        client: OkHttpClient = http
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
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = runCatching { textOf(text, "errorCode")?.trim() }.getOrNull()
                AppLog.w(TAG, "UPnP $action bei $base: HTTP ${response.code}, Fehler $code")
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

    /**
     * Die ersten Werte der bekannten Felder ([DIDL_FIELDS]) in einem DIDL, ohne Namespace-Präfix —
     * z. B. „title“, „creator“, „albumArtURI“, „streamContent“. Leer bei leerem oder kaputtem DIDL.
     */
    private fun didlFields(didl: String?): Map<String, String> {
        if (didl.isNullOrBlank() || didl == "NOT_IMPLEMENTED") return emptyMap()
        return runCatching {
            val parser = Xml.newPullParser().apply { setInput(StringReader(didl)) }
            val fields = mutableMapOf<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val name = parser.name.substringAfter(':')
                if (name in DIDL_FIELDS && name !in fields) {
                    parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { fields[name] = it }
                }
            }
            fields
        }.getOrElse {
            AppLog.d(TAG, "DIDL nicht lesbar: ${it.message}")
            emptyMap()
        }
    }

    /** Art des Inhalts (`upnp:class`) in einem eingebetteten DIDL, etwa dem eines Favoriten. */
    private fun classInDidl(didl: String): String? = didlFields(didl)["class"]

    private fun parseDidl(didl: String, base: String, keepUntitled: Boolean = false): List<LocalEntry> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(didl)) }
        val entries = mutableListOf<LocalEntry>()
        var id: String? = null
        var title: String? = null
        var art: String? = null
        var res: String? = null
        var artist: String? = null
        var album: String? = null
        var resMd: String? = null
        var itemClass: String? = null
        var description: String? = null
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
                        resMd = null
                        itemClass = null
                        description = null
                    }
                    // Abspiel-Adresse des Inhalts, z. B. x-rincon-cpcontainer:…?sid=204&…
                    "res" -> if (res == null) res = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    "title" -> if (title == null) title = parser.nextText()
                    "album" -> if (album == null) album = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    // dc:creator, upnp:artist oder upnp:albumArtist — bei Titeln und manchen Alben
                    in ARTIST_TAGS -> if (artist == null) artist = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    // Favoriten tragen die Daten des eigentlichen Inhalts als eingebettetes DIDL mit
                    "resMD" -> {
                        val md = parser.nextText().trim().takeIf { it.isNotEmpty() }
                        resMd = md
                        if (artist == null && md != null) artist = artistInDidl(md)
                    }
                    "class" -> if (itemClass == null) itemClass = parser.nextText().trim().takeIf { it.isNotEmpty() }
                    "description" -> if (description == null) description = parser.nextText().trim().takeIf { it.isNotEmpty() }
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
                        entries += LocalEntry(
                            id = entryId,
                            title = entryTitle,
                            artUrl = art,
                            freshArtUrl = fresh,
                            artists = listOfNotNull(artist),
                            album = album,
                            res = res,
                            resMetaData = resMd,
                            // Bei Favoriten zählt die Art des eigentlichen Inhalts, nicht „Favorit“
                            upnpClass = resMd?.let { classInDidl(it) } ?: itemClass,
                            description = description
                        )
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
        private const val SSDP_PORT = 1900
        private const val SSDP_TIMEOUT_MS = 3_500L
        private const val SSDP_RESEND_MS = 800L
        private const val SSDP_RECEIVE_TIMEOUT_MS = 300
        private const val SSDP_SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"
        private val SSDP_LOCATION = Regex("^LOCATION:\\s*(http://[0-9.]+:1400)/", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        /** Adresse aus der SSDP-Antwort eines Sonos-Speakers (`LOCATION: http://IP:1400/xml/…`), sonst null. */
        internal fun ssdpSpeakerAddress(response: String): String? {
            if (!response.contains("Sonos", ignoreCase = true) && !response.contains("ZonePlayer", ignoreCase = true)) return null
            return SSDP_LOCATION.find(response)?.groupValues?.get(1)
        }
        private const val PREFS_NAME = "sonos_local"
        private const val KEY_PREFER_CLOUD = "prefer_cloud"
        private const val KEY_ADDRESSES = "speaker_addresses"
        /** So lange wartet die App höchstens auf eine gemerkte Adresse, bevor sie die nächste probiert. */
        private const val PROBE_CONNECT_TIMEOUT_MS = 1_200L
        private const val MAX_PROBED_ADDRESSES = 3
        private const val RENDERING_CONTROL_PATH = "/MediaRenderer/RenderingControl/Control"
        private val DIDL_FIELDS = setOf("title", "creator", "artist", "albumArtist", "album", "albumArtURI", "streamContent", "class")

        /**
         * Abspiel-Adressen, die ohne Warteschlange direkt laufen (Radio, Line-in, TV) — so entscheidet
         * es auch die Sonos-App beim Starten eines Favoriten. Alles andere kommt in die Warteschlange.
         */
        private val DIRECT_PLAY_PREFIXES = listOf(
            "x-sonosapi-stream:", "x-sonosapi-radio:", "x-sonosapi-hls:", "x-sonosprog-http:",
            "x-rincon-mp3radio:", "x-rincon-stream:", "x-sonos-htastream:", "pndrradio:", "aac:", "hls-radio:"
        )

        /** Live-Sender ohne Titelliste: Anzeige mit Sendername und dem, was gerade läuft. */
        private val LIVE_STREAM_PREFIXES = listOf(
            "x-sonosapi-stream:", "x-sonosapi-hls:", "x-rincon-mp3radio:", "x-rincon-stream:",
            "x-sonos-htastream:", "aac:", "hls-radio:"
        )

        /** Ist das die Adresse eines Radiosenders oder Radio-Programms? */
        fun isRadioUri(uri: String): Boolean =
            uri.startsWith("x-sonosapi-stream:") || uri.startsWith("x-sonosapi-radio:") ||
                uri.startsWith("x-rincon-mp3radio:") || uri.startsWith("x-sonosapi-hls:") ||
                uri.startsWith("aac:") || uri.startsWith("hls-radio:")

        private fun looksLikeUri(text: String): Boolean = text.contains("://") || text.startsWith("x-")

        private fun absoluteUrl(base: String, raw: String): String? =
            normalizeImageUrl(if (raw.startsWith("/")) base + raw else raw)

        /** UPnP-Zustand → Zustand wie in der Control API. */
        private fun playbackState(transportState: String?): String = when (transportState?.trim()) {
            "PLAYING" -> PlaybackStatus.STATE_PLAYING
            "TRANSITIONING" -> PlaybackStatus.STATE_BUFFERING
            "PAUSED_PLAYBACK" -> PlaybackStatus.STATE_PAUSED
            else -> PlaybackStatus.STATE_IDLE
        }

        /** "0:03:12" (auch "0:03:12.345") → Millisekunden; null bei "NOT_IMPLEMENTED" o. Ä. */
        private fun parseDuration(text: String?): Long? {
            val parts = text?.trim()?.split(':')?.takeIf { it.size == 3 } ?: return null
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
            return ((hours * 60 + minutes) * 60 + seconds) * 1000
        }
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
        const val NO_SPEAKER_MESSAGE = "Kein Sonos-Speaker im WLAN gefunden. Ist das Tablet im selben WLAN wie die Speaker?"

        /** UPnP-Wiedergabemodus: der Reihe nach, ohne Wiederholen. */
        private const val PLAY_MODE_NORMAL = "NORMAL"
        /** UPnP-Wiedergabemodus: zufällig, ohne Wiederholen („SHUFFLE“ allein hieße bei Sonos: mit Wiederholen). */
        private const val PLAY_MODE_SHUFFLE = "SHUFFLE_NOREPEAT"
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
