package de.paul.sonoscontrol

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Auf welchem Weg die App die Speaker steuert. */
enum class ControlPath(val label: String) {
    /** Direkt beim Speaker im WLAN (UPnP, Port 1400) — wie die Sonos-App, schnell und ohne Cloud. */
    LOCAL("direkt im WLAN"),
    /** Über die Sonos Control API in der Cloud — braucht die Anmeldung und Internet bei Speakern und Tablet. */
    CLOUD("über die Sonos-Cloud")
}

/** Wiedergabe-Stand und was gerade läuft, in einem Rutsch abgefragt. */
data class Playback(val status: PlaybackStatus, val metadata: PlaybackMetadata)

/**
 * Was die App zum Steuern der Speaker braucht — umgesetzt direkt im WLAN ([LocalSonosClient])
 * und über die Sonos-Cloud ([CloudSonosControl]). Player-Ids sind auf beiden Wegen dieselben
 * (`RINCON_…`), Gruppen-Ids nicht: Eine [SonosGroup.id] gilt nur für den Weg, von dem sie stammt.
 */
interface SonosControl {
    val path: ControlPath

    /** Alle Speaker des Haushalts samt ihren Gruppen. */
    suspend fun getGroups(): GroupsResponse
    suspend fun getPlayback(groupId: String): Playback
    suspend fun getPlaybackStatus(groupId: String): PlaybackStatus
    suspend fun getPlayerVolume(playerId: String): PlayerVolume
    suspend fun setPlayerVolume(playerId: String, volume: Int)
    suspend fun togglePlayPause(groupId: String)
    suspend fun skipToNextTrack(groupId: String)
    suspend fun skipToPreviousTrack(groupId: String)
    suspend fun play(groupId: String)

    /** [playerId] spielt ab jetzt in der Gruppe [groupId] mit. */
    suspend fun addToGroup(groupId: String, playerId: String)

    /** [playerId] verlässt die Gruppe [groupId]; steuerte er sie, übernimmt ein anderer und die Musik läuft dort weiter. */
    suspend fun removeFromGroup(groupId: String, playerId: String)

    /**
     * Spielt einen Sonos-Favoriten oder eine Sonos-Playlist ([MusicSource.FAVORITE], [MusicSource.PLAYLIST])
     * auf der Gruppe und ersetzt dabei die Warteschlange. Wiederholen ist danach immer aus, Zufall wie
     * [shuffle]. Wurde der Eintrag bei Sonos umbenannt oder neu angelegt, wird er über den Namen gesucht.
     */
    suspend fun startMusic(groupId: String, item: MusicItem, shuffle: Boolean)

    suspend fun getPlaylists(): List<SonosPlaylist>
    suspend fun getPlaylistTracks(playlistId: String): List<PlaylistTrackInfo>
}

/** Die Steuerung über die Sonos Control API — so, wie die App bis Version 1.3 alles gesteuert hat. */
class CloudSonosControl(private val api: SonosApiClient) : SonosControl {

    override val path = ControlPath.CLOUD

    private val householdMutex = Mutex()

    @Volatile
    private var householdId: String? = null

    /** Der Haushalt des angemeldeten Kontos, einmal geholt und gemerkt. */
    suspend fun household(): String = householdId ?: householdMutex.withLock {
        householdId ?: api.getFirstHouseholdId().also { householdId = it }
    }

    /** Nach dem Abmelden: Beim nächsten Konto den Haushalt neu holen. */
    fun forgetHousehold() {
        householdId = null
    }

    suspend fun getFavorites(): List<SonosFavorite> = api.getFavorites(household())

    override suspend fun getGroups(): GroupsResponse = api.getGroups(household())

    override suspend fun getPlayback(groupId: String): Playback = coroutineScope {
        val status = async { api.getPlaybackStatus(groupId) }
        val metadata = async { api.getPlaybackMetadata(groupId) }
        Playback(status.await(), metadata.await())
    }

    override suspend fun getPlaybackStatus(groupId: String): PlaybackStatus = api.getPlaybackStatus(groupId)
    override suspend fun getPlayerVolume(playerId: String): PlayerVolume = api.getPlayerVolume(playerId)
    override suspend fun setPlayerVolume(playerId: String, volume: Int) = api.setPlayerVolume(playerId, volume)
    override suspend fun togglePlayPause(groupId: String) = api.togglePlayPause(groupId)
    override suspend fun skipToNextTrack(groupId: String) = api.skipToNextTrack(groupId)
    override suspend fun skipToPreviousTrack(groupId: String) = api.skipToPreviousTrack(groupId)
    override suspend fun play(groupId: String) = api.play(groupId)

    override suspend fun addToGroup(groupId: String, playerId: String) =
        api.modifyGroupMembers(groupId, playerIdsToAdd = listOf(playerId), playerIdsToRemove = emptyList())

    override suspend fun removeFromGroup(groupId: String, playerId: String) =
        api.modifyGroupMembers(groupId, playerIdsToAdd = emptyList(), playerIdsToRemove = listOf(playerId))

    override suspend fun getPlaylists(): List<SonosPlaylist> = api.getPlaylists(household())

    override suspend fun getPlaylistTracks(playlistId: String): List<PlaylistTrackInfo> =
        api.getPlaylist(household(), playlistId).tracks
            .map { PlaylistTrackInfo(title = it.name, album = it.album, artist = it.artist) }

    override suspend fun startMusic(groupId: String, item: MusicItem, shuffle: Boolean) {
        val sourceId = when (item.musicSource) {
            MusicSource.FAVORITE -> resolveFavoriteId(item)
            MusicSource.PLAYLIST -> resolvePlaylistId(item)
            MusicSource.PLAYLIST_ALBUM -> error("Alben aus einer Playlist laufen über das Heimnetz")
        }
        val load: suspend (play: Boolean) -> Unit = { playNow ->
            when (item.musicSource) {
                MusicSource.FAVORITE -> api.loadFavorite(groupId, sourceId, play = playNow)
                else -> api.loadPlaylist(groupId, sourceId, play = playNow)
            }
        }
        // Radio hat keine Warteschlange — dort gibt es keine Reihenfolge, also direkt starten
        if (item.musicType == MusicType.RADIO) {
            load(true)
            return
        }
        if (!shuffle) switchOffLeftoverShuffle(groupId)
        loadRetryingWhenNothingQueued(item) { load(false) }
        // Nach dem Laden ohne Abspielen füllt Sonos die Warteschlange noch —
        // bis dahin lehnt es Befehle ab, deshalb jeweils kurz wiederholen.
        // Zufall muss auch ausdrücklich AUS geschaltet werden, sonst bleibt er vom letzten Mal an.
        // Klappt das nicht (manche Quellen erlauben es nicht), trotzdem abspielen.
        val shuffled = shuffle &&
            runCatching { retryWhileQueueFills { api.setShuffle(groupId, true) } }.isSuccess
        if (!shuffled) runCatching { retryWhileQueueFills { api.setShuffle(groupId, false) } }
        // Wiederholen ebenso ausdrücklich aus, sonst läuft die Auswahl in Dauerschleife, wenn es noch
        // vom letzten Mal oder aus der Sonos-App an ist. Eigener Aufruf, damit eine Quelle, die
        // Wiederholen nicht erlaubt, nicht auch den Zufall verhindert. Klappt es nicht, trotzdem abspielen.
        runCatching { retryWhileQueueFills { api.setRepeatOff(groupId) } }
        // Ohne Sprung würde auch im Zufallsmodus immer der erste Titel zuerst laufen
        if (shuffled) runCatching { retryWhileQueueFills { api.skipToNextTrack(groupId) } }
        retryWhileQueueFills { api.play(groupId) }
    }

    /**
     * Ist beim Laden noch Zufall vom letzten Mal an, wählt Sonos einen zufälligen ersten Titel —
     * beim späteren Ausschalten bleibt es bei diesem, die Reihe startet mittendrin. Deshalb den
     * Zufall vorher ausschalten, aber nur, wenn er wirklich an ist: Direkt nach dem Ausschalten
     * reiht Sonos beim Laden keine Titel ein (HTTP 499). Daher warten, bis Sonos den Zufall als
     * aus meldet, und noch einen Moment länger. Ohne Antwort zum Zufall bleibt alles wie es ist.
     */
    private suspend fun switchOffLeftoverShuffle(groupId: String) {
        val shuffleOn = currentShuffle(groupId) ?: return
        if (!shuffleOn) return
        runCatching { api.setShuffle(groupId, false) }.onFailure {
            AppLog.d(TAG, "Übrig gebliebener Zufall nicht ausgeschaltet: ${it.message}")
            return
        }
        val deadline = SystemClock.elapsedRealtime() + SHUFFLE_OFF_TIMEOUT_MS
        do {
            delay(SHUFFLE_OFF_POLL_MS)
        } while (currentShuffle(groupId) != false && SystemClock.elapsedRealtime() < deadline)
        delay(SHUFFLE_OFF_SETTLE_MS)
    }

    private suspend fun currentShuffle(groupId: String): Boolean? =
        runCatching { api.getPlaybackStatus(groupId).playModes?.shuffle }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            .also { if (it == null) AppLog.d(TAG, "Sonos meldet keinen Zufallsmodus für Gruppe $groupId") }

    /**
     * Lädt mit [load] und versucht es nach kurzer Pause erneut, wenn Sonos dabei keine Titel
     * eingereiht hat (HTTP 499) — höchstens [LOAD_ATTEMPTS]-mal.
     */
    private suspend fun loadRetryingWhenNothingQueued(item: MusicItem, load: suspend () -> Unit) {
        repeat(LOAD_ATTEMPTS - 1) { attempt ->
            try {
                load()
                if (attempt > 0) logLoaded(item, attempt + 1)
                return
            } catch (e: SonosApiException) {
                if (!e.isNoContent) throw e
                AppLog.w(TAG, "„${item.name}“: keine Titel eingereiht (Versuch ${attempt + 1}), gleich noch einmal", e)
            }
            delay(LOAD_RETRY_DELAY_MS)
        }
        load()
        logLoaded(item, LOAD_ATTEMPTS)
    }

    private fun logLoaded(item: MusicItem, attempt: Int) = AppLog.resolved(
        TAG,
        "„${item.name}“: Sonos hatte beim Laden keine Titel eingereiht — im $attempt. Versuch hat es geklappt"
    )

    /**
     * Führt [command] aus und wiederholt ihn, solange Sonos meldet, dass die gerade
     * geladene Warteschlange noch leer ist — höchstens [QUEUE_FILL_TIMEOUT_MS] lang.
     */
    private suspend fun <T> retryWhileQueueFills(command: suspend () -> T): T {
        val deadline = SystemClock.elapsedRealtime() + QUEUE_FILL_TIMEOUT_MS
        while (true) {
            try {
                return command()
            } catch (e: SonosApiException) {
                if (!e.isNoContent || SystemClock.elapsedRealtime() >= deadline) throw e
            }
            delay(QUEUE_FILL_RETRY_MS)
        }
    }

    /**
     * Sonos vergibt die Ids von Favoriten und Playlisten selbst. Wurde dort etwas
     * gelöscht oder neu angelegt, kann die gespeicherte Id inzwischen zu einem
     * anderen Eintrag gehören — dann wird über den Namen gesucht.
     */
    private suspend fun resolveFavoriteId(item: MusicItem): String {
        val favorites = getFavorites()
        return favorites.firstOrNull { it.id == item.sonosId && it.name == item.name }?.id
            ?: favorites.firstOrNull { it.name == item.name }?.id
            ?: throw SonosApiException("„${item.name}“ gibt es nicht mehr in den Sonos-Favoriten.")
    }

    private suspend fun resolvePlaylistId(item: MusicItem): String {
        val playlists = getPlaylists()
        return playlists.firstOrNull { it.id == item.sonosId && it.name == item.name }?.id
            ?: playlists.firstOrNull { it.name == item.name }?.id
            ?: throw SonosApiException("Die Playlist „${item.name}“ gibt es nicht mehr bei Sonos.")
    }

    companion object {
        private const val TAG = "SonosCloud"
        private const val QUEUE_FILL_RETRY_MS = 400L
        private const val QUEUE_FILL_TIMEOUT_MS = 3_000L
        private const val SHUFFLE_OFF_POLL_MS = 300L
        private const val SHUFFLE_OFF_TIMEOUT_MS = 3_000L
        private const val SHUFFLE_OFF_SETTLE_MS = 1_000L
        private const val LOAD_ATTEMPTS = 3
        private const val LOAD_RETRY_DELAY_MS = 1_500L
    }
}
