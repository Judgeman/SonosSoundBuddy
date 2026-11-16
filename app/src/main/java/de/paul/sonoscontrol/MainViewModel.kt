package de.paul.sonoscontrol

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UiState {
    /** [reason] erklärt, warum neu angemeldet werden muss (z. B. Anmeldung abgelaufen). */
    data class LoggedOut(val reason: String? = null) : UiState
    data object LoadingSpeakers : UiState
    data class SpeakerList(val players: List<SonosPlayer>) : UiState
    data class Error(val message: String) : UiState
}

/**
 * [Speakers], [ProfileEditor], [MusicLibrary] (die zentrale Musikauswahl) mit
 * [CategoryEditor] (eine Kategorie) und [MusicCatalog], sowie [Sync], [ReleaseNotes] und [Log] (das Protokoll)
 * sind Unterseiten der Settings.
 */
enum class Screen { Home, Settings, Speakers, ProfileEditor, MusicLibrary, CategoryEditor, MusicCatalog, Sync, ReleaseNotes, Log }

/** Inhalt des Sonos-Katalogs (Favoriten + Playlisten) beim Zusammenstellen der Musikauswahl. */
sealed interface CatalogState {
    data object Loading : CatalogState
    data class Loaded(val entries: List<CatalogEntry>) : CatalogState
    data class Error(val message: String) : CatalogState
}

/** Titelliste einer Sonos-Playlist, zum Reinschauen beim Zusammenstellen. */
data class PlaylistPreview(
    val entry: CatalogEntry,
    val tracks: List<PlaylistTrack>? = null,
    val error: String? = null
)

/** Aktuelle Wiedergabe des ausgewählten Speakers, wie sie der Homescreen anzeigt. */
data class NowPlaying(
    val groupId: String,
    val title: String?,
    val subtitle: String?,
    val imageUrl: String?,
    /** Cover der Playlist bzw. des Albums, falls Sonos eins liefert. */
    val containerImageUrl: String? = null,
    val playbackState: String?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val volume: Int?,
    val muted: Boolean,
    val canSkip: Boolean,
    val canSkipBack: Boolean,
    /** SystemClock.elapsedRealtime() zum Zeitpunkt der Abfrage — für die Positions-Interpolation. */
    val fetchedAtMillis: Long,
    /** Über welchen Weg abgefragt wurde — [groupId] gilt nur dort. */
    val path: ControlPath? = null
) {
    val isPlaying: Boolean get() = playbackState == PlaybackStatus.STATE_PLAYING

    /** Position inklusive der seit der letzten Abfrage vergangenen Zeit. */
    fun currentPositionMillis(now: Long = SystemClock.elapsedRealtime()): Long {
        val position = positionMillis + if (isPlaying) now - fetchedAtMillis else 0L
        return durationMillis?.let { position.coerceAtMost(it) } ?: position
    }
}

/** Die Musik zieht gerade von [from] nach [to] um („Mitnehmen“). */
data class MovingMusic(val from: SpeakerConfig, val to: SpeakerConfig)

class MainViewModel(
    private val tokenStore: TokenStore,
    /** Gemeinsam mit dem Abgleich, damit Tokens nur an einer Stelle erneuert werden. */
    private val apiClient: SonosApiClient,
    private val repository: SettingsRepository,
    private val localClient: LocalSonosClient
) : ViewModel() {

    val authManager = SonosAuthManager()

    var uiState: UiState by mutableStateOf(
        if (tokenStore.accessToken != null) UiState.LoadingSpeakers else UiState.LoggedOut()
    )
        private set

    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    var speakerConfigs: List<SpeakerConfig> by mutableStateOf(emptyList())
        private set

    var settings: AppSettings by mutableStateOf(AppSettings())
        private set

    var selectedPlayerId: String? by mutableStateOf(null)
        private set

    var nowPlaying: NowPlaying? by mutableStateOf(null)
        private set

    var playbackError: String? by mutableStateOf(null)
        private set

    /** Die aktuelle Fehlermeldung wurde mit „Okay" weggeklickt. */
    private var playbackErrorDismissed: Boolean by mutableStateOf(false)

    /** Seit wann die aktuelle Fehlermeldung steht (SystemClock.elapsedRealtime()) — fürs Protokoll. */
    private var playbackErrorSinceMillis = 0L

    /**
     * Fehler, der groß über dem Homescreen angezeigt wird. Einmal weggeklickt,
     * erscheint derselbe Fehler erst wieder, wenn zwischendurch alles geklappt hat
     * oder ein anderer Fehler auftritt — sonst ploppt er bei jeder Abfrage neu auf.
     */
    val visiblePlaybackError: String?
        get() = playbackError?.takeUnless { playbackErrorDismissed }

    var isRefreshingSpeakers: Boolean by mutableStateOf(false)
        private set

    /** Ergebnis von „Speaker-Liste aktualisieren", als Snackbar in den Settings angezeigt. */
    var speakerRefreshMessage: String? by mutableStateOf(null)
        private set

    var profiles: List<ProfileWithMusic> by mutableStateOf(emptyList())
        private set

    /** Die zentrale Musikauswahl: alle Kategorien, egal welches Profil sie sieht. */
    var categories: List<CategoryWithMusic> by mutableStateOf(emptyList())
        private set

    /**
     * Je Profil ([ChildProfile.syncId]) die [MusicItem.playedKey] der Musik, die es schon gespielt hat —
     * auf einem der Tablets. null = noch nicht geladen.
     */
    var playedMusic: Map<String, Set<String>>? by mutableStateOf(null)
        private set

    private var playLog: List<PlayLogEntry> by mutableStateOf(emptyList())

    /**
     * Was auf diesem Tablet zuletzt gespielt wurde, je Kind und Musik nur das letzte Mal — und nur,
     * was es in der Musikauswahl und bei den Profilen noch gibt.
     */
    val recentPlays: List<RecentPlay>
        get() {
            val items = categories.flatMap { it.items }.associateBy { it.playedKey }
            val profilesBySyncId = profiles.associate { it.profile.syncId to it.profile }
            val played = playedMusic.orEmpty()
            return playLog
                .distinctBy { it.profileSyncId to it.musicKey }
                .mapNotNull { entry ->
                    val item = items[entry.musicKey] ?: return@mapNotNull null
                    val profile = profilesBySyncId[entry.profileSyncId] ?: return@mapNotNull null
                    val isNew = entry.musicKey !in played[entry.profileSyncId].orEmpty()
                    RecentPlay(item, profile, entry.playedAt, isNew)
                }
                .take(RECENT_PLAYS_SHOWN)
        }

    var selectedProfileId: Long? by mutableStateOf(null)
        private set

    /** Profil, das gerade in den Settings bearbeitet wird. */
    var editingProfileId: Long? by mutableStateOf(null)
        private set

    /** Kategorie, deren Unterseite in der Musikauswahl gerade offen ist. */
    var editingCategoryId: Long? by mutableStateOf(null)
        private set

    /** Kategorie, für die gerade Musik aus dem Katalog ausgesucht wird. */
    var catalogCategoryId: Long? by mutableStateOf(null)
        private set

    /** Was im offenen Katalog an den Anfang gesetzt wurde — damit es dort in der Reihenfolge des Antippens steht. */
    private val catalogAddedAtStart = mutableSetOf<String>()

    var catalogState: CatalogState by mutableStateOf(CatalogState.Loading)
        private set

    var playlistPreview: PlaylistPreview? by mutableStateOf(null)
        private set

    /** Playlist-Kategorien, die gerade mit ihrer Sonos-Playlist abgeglichen werden. */
    var refreshingPlaylistCategories: Set<Long> by mutableStateOf(emptySet())
        private set

    /** Warum der letzte Abgleich einer Playlist-Kategorie nicht geklappt hat, je Kategorie. */
    var playlistCategoryErrors: Map<Long, String> by mutableStateOf(emptyMap())
        private set

    /** Fehler beim Übernehmen eines eigenen Kategorie-Bilds, als Snackbar auf der Profil-Seite. */
    var imageImportError: String? by mutableStateOf(null)
        private set

    /** Wird gerade Musik aus der Auswahl gestartet? Zeigt auf dem Homescreen einen Ladekreis. */
    var startingMusic: MusicItem? by mutableStateOf(null)
        private set

    val isStartingMusic: Boolean get() = startingMusic != null

    /** Zieht die Musik gerade auf einen anderen Speaker um? Solange sperrt ein Popup den Homescreen. */
    var movingMusic: MovingMusic? by mutableStateOf(null)
        private set

    var showPasswordPrompt: Boolean by mutableStateOf(false)
        private set

    var passwordWrong: Boolean by mutableStateOf(false)
        private set

    /**
     * Was das Tier am Cover macht: wie in der Kategorie eingestellt, aus der auf
     * dem gewählten Speaker zuletzt etwas gestartet wurde. Ist das unbekannt
     * (noch nichts gestartet, Kategorie gelöscht oder nach einem Abgleich neu
     * angelegt), tanzt es.
     */
    val coverAnimation: CoverAnimation
        get() {
            val categoryId = selectedPlayerId?.let { settings.lastStartedCategories[it] } ?: return CoverAnimation.DANCE
            return categories.firstOrNull { it.category.id == categoryId }?.category?.coverAnimationMode
                ?: CoverAnimation.DANCE
        }

    /** Ob die App gerade zu sehen ist — nur dann wird abgefragt und kommen Tiere zu Besuch. */
    var isInForeground: Boolean by mutableStateOf(false)
        private set

    /** Steuerung über die Sonos-Cloud — Rückfall, wenn im WLAN kein Speaker antwortet, oder so eingestellt. */
    private val cloudControl = CloudSonosControl(apiClient)

    /** Der Weg, über den die App gerade steuert; null, solange die Speaker noch nicht geladen sind. */
    private var control: SonosControl? = null

    /** Für die Einstellungen: Wie die App gerade steuert (null = noch nicht verbunden). */
    var controlPath: ControlPath? by mutableStateOf(null)
        private set

    /** Warum gerade über die Cloud gesteuert wird, obwohl das WLAN eingestellt ist — sonst null. */
    var controlFallbackReason: String? by mutableStateOf(null)
        private set

    /** In den Einstellungen gewählt: immer über die Sonos-Cloud steuern. */
    var preferCloud: Boolean by mutableStateOf(localClient.preferCloud)
        private set

    /** Fehlgeschlagene Abfragen im WLAN hintereinander — ab [LOCAL_FAILURES_BEFORE_RECONNECT] wird neu verbunden. */
    private var localFailures = 0

    /** Wann die App zuletzt versucht hat, die Speaker im WLAN zu erreichen. */
    private var localTriedAtMillis = 0L
    private var reconnectJob: Job? = null
    private var settingsLoaded = false
    private var pollJob: Job? = null
    private var lastCommandAtMillis = 0L
    private var volumeJob: Job? = null
    private var profilesLoaded = false
    private var catalogJob: Job? = null
    private val playlistRefreshMutex = Mutex()
    private var playlistsRefreshedAt = 0L

    /** Ein Speaker, aus dem die Musik mitgenommen wurde und der gleich aus der Gruppe geht. */
    private class PendingLeave(val targetPlayerId: String, val job: Job)

    /** Je bisherigem Speaker ([SpeakerConfig.playerId]), wann und wohin er die Musik abgibt. */
    private val pendingLeaves = mutableMapOf<String, PendingLeave>()

    /** Speaker, die auf dem Homescreen gewählt werden dürfen und gerade im Haushalt verfügbar sind. */
    val selectableSpeakers: List<SpeakerConfig>
        get() {
            val available = (uiState as? UiState.SpeakerList)?.players?.map { it.id }?.toSet()
                ?: return emptyList()
            return speakerConfigs.filter { it.enabled && it.playerId in available }
        }

    /** Ids der aktuell erreichbaren Player, oder null wenn die Liste noch nicht geladen ist. */
    val availablePlayerIds: Set<String>?
        get() = (uiState as? UiState.SpeakerList)?.players?.map { it.id }?.toSet()

    val selectedSpeaker: SpeakerConfig?
        get() = speakerConfigs.firstOrNull { it.playerId == selectedPlayerId }

    /** Obergrenze der Lautstärke-Leiste für den gewählten Speaker. */
    val selectedMaxVolume: Int
        get() = selectedPlayerId?.let(::maxVolumeFor) ?: 100

    /** Profile, die auf diesem Tablet aktiviert sind. */
    val selectableProfiles: List<ProfileWithMusic>
        get() = profiles.filter { it.profile.enabled }

    val selectedProfile: ProfileWithMusic?
        get() = selectableProfiles.firstOrNull { it.profile.id == selectedProfileId }

    /**
     * Was das gewählte Profil schon gespielt hat; alles andere zeigt seine Musikauswahl als neu.
     * null = noch nicht geladen, dann ist nichts neu.
     */
    val selectedProfilePlayed: Set<String>?
        get() {
            val played = playedMusic ?: return null
            return selectedProfile?.let { played[it.profile.syncId].orEmpty() }
        }

    val editingProfile: ProfileWithMusic?
        get() = profiles.firstOrNull { it.profile.id == editingProfileId }

    val editingCategory: CategoryWithMusic?
        get() = categories.firstOrNull { it.category.id == editingCategoryId }

    val catalogCategory: CategoryWithMusic?
        get() = categories.firstOrNull { it.category.id == catalogCategoryId }

    private fun maxVolumeFor(playerId: String): Int =
        speakerConfigs.firstOrNull { it.playerId == playerId }?.maxVolume ?: 100

    init {
        viewModelScope.launch {
            repository.settings.collect {
                settings = it
                settingsLoaded = true
                updateSelection()
                updateProfileSelection()
            }
        }
        viewModelScope.launch {
            repository.profiles.collect {
                profiles = it
                profilesLoaded = true
                updateProfileSelection()
                // Profil wurde gelöscht, während seine Unterseite offen war
                if (editingProfileId != null && editingProfile == null) {
                    editingProfileId = null
                    if (screen == Screen.ProfileEditor) screen = Screen.Settings
                }
            }
        }
        viewModelScope.launch {
            repository.categories.collect {
                categories = it
                // Kategorie wurde gelöscht, während ihr Katalog offen war
                if (catalogCategoryId != null && catalogCategory == null) closeCatalog()
                // … oder ihre Unterseite
                if (editingCategoryId != null && editingCategory == null) {
                    editingCategoryId = null
                    if (screen == Screen.CategoryEditor) screen = Screen.MusicLibrary
                }
            }
        }
        viewModelScope.launch {
            repository.speakerConfigs.collect {
                speakerConfigs = it
                updateSelection()
            }
        }
        viewModelScope.launch {
            repository.playedMusic.collect { playedMusic = it }
        }
        viewModelScope.launch {
            repository.playLog.collect { playLog = it }
        }
        AppLog.i(TAG, "App gestartet (Version ${BuildConfig.VERSION_NAME})")
        if (uiState is UiState.LoadingSpeakers) startLoadingSpeakers("App gestartet")
        viewModelScope.launch {
            runCatching { repository.deleteUnusedImages() }
                .onFailure { AppLog.w(TAG, "Aufräumen der Bilder fehlgeschlagen", it) }
        }
        viewModelScope.launch {
            // Hat dieses Tablet eine Playlist-Kategorie noch mit einer älteren Version übernommen?
            runCatching { repository.restorePlaylistLinks() }
                .onSuccess { if (it > 0) AppLog.d(TAG, "$it Playlist-Kategorie(n) wieder mit ihrer Playlist verbunden") }
                .onFailure { AppLog.w(TAG, "Playlist-Kategorien nicht geprüft", it) }
        }
    }

    /** Wird aus MainActivity.handleIntent() mit der sonoscontrol://callback-Uri aufgerufen. */
    fun handleAuthCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        val state = uri.getQueryParameter("state")
        val accessToken = uri.getQueryParameter("access_token")
        val refreshToken = uri.getQueryParameter("refresh_token")

        if (!authManager.isValidState(state)) {
            uiState = errorState("Ungültiger state-Parameter — Login abgebrochen (möglicher CSRF-Versuch).")
            return
        }
        if (error != null) {
            uiState = errorState("Sonos-Login fehlgeschlagen: $error")
            return
        }
        if (accessToken.isNullOrBlank()) {
            uiState = errorState("Kein Access-Token in der Antwort erhalten.")
            return
        }

        tokenStore.saveTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = uri.getQueryParameter("expires_in")?.toLongOrNull()
        )

        startLoadingSpeakers("bei Sonos angemeldet")
    }

    /** „Nochmal versuchen“ auf der Fehlerseite. */
    fun loadSpeakers() = startLoadingSpeakers("„Nochmal versuchen“ gedrückt")

    /** Die Fehlerseite statt des Startbildschirms — steht damit auch im Protokoll. */
    private fun errorState(message: String, cause: Throwable? = null): UiState.Error {
        AppLog.e(TAG, "Fehlerseite statt des Startbildschirms angezeigt: $message", cause)
        return UiState.Error(message)
    }

    /** [trigger]: wodurch geladen wird — fürs Protokoll, falls vorher die Fehlerseite stand. */
    private fun startLoadingSpeakers(trigger: String) {
        val failedBefore = (uiState as? UiState.Error)?.message
        uiState = UiState.LoadingSpeakers
        viewModelScope.launch {
            uiState = try {
                UiState.SpeakerList(fetchAndSyncPlayers().players).also {
                    if (failedBefore != null) {
                        AppLog.resolved(
                            TAG,
                            "Fehlerseite ist weg, die Speaker sind geladen ($trigger). Meldung war: ${firstLine(failedBefore)}"
                        )
                    }
                    refreshMusicImagesQuietly()
                    refreshPlaylistCategoriesQuietly()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                errorState(e.message ?: "Unbekannter Fehler beim Laden der Speaker", e)
            }
            updateSelection()
        }
    }

    private class SyncResult(val players: List<SonosPlayer>, val added: Int)

    private suspend fun fetchAndSyncPlayers(): SyncResult {
        val players = connect().players
        return SyncResult(players, repository.syncPlayers(players))
    }

    /**
     * Wählt den Weg zum Steuern: direkt im WLAN, wenn dort ein Speaker antwortet (und in den
     * Einstellungen nicht die Cloud gewählt ist), sonst über die Sonos-Cloud. Ergebnis: Speaker und Gruppen.
     */
    private suspend fun connect(): GroupsResponse {
        var localError: Exception? = null
        if (!localClient.preferCloud) {
            localTriedAtMillis = SystemClock.elapsedRealtime()
            // Lief zuletzt etwas über die Cloud, verrät das Cover der Wiedergabe oft die Adresse eines Speakers
            localClient.noteAddress(nowPlaying?.imageUrl)
            try {
                return localClient.getGroups().also { useControl(localClient, fallbackReason = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Steuert die App deshalb schon über die Cloud, nicht bei jedem neuen Versuch wieder ins Protokoll
                if (control !== cloudControl || controlFallbackReason != e.message) {
                    AppLog.w(TAG, "Speaker im WLAN nicht erreicht, versuche es über die Sonos-Cloud", e)
                }
                localError = e
            }
        }
        val groups = try {
            cloudControl.getGroups()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            if (localError == null) throw e
            throw SonosApiException(
                "${localError.message} Auch über die Sonos-Cloud ging es nicht: ${e.message ?: "unbekannter Fehler"}"
            )
        }
        // Die Cloud nennt die Adressen der Speaker im Heimnetz — damit geht es oft doch direkt
        if (localError != null) {
            val addresses = groups.players.mapNotNull { it.localBaseUrl }
            try {
                localClient.connectTo(addresses)?.let { local ->
                    AppLog.resolved(
                        TAG,
                        "Die Suche im WLAN fand keinen Speaker (${localError.message}) — über die Adressen aus der " +
                            "Sonos-Cloud ist er doch direkt erreichbar"
                    )
                    useControl(localClient, fallbackReason = null)
                    return local
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Adressen aus der Sonos-Cloud nicht genutzt", e)
            }
        }
        useControl(cloudControl, fallbackReason = localError?.message)
        return groups
    }

    /** Ab jetzt über [newControl] steuern. Gruppen-Ids gelten je Weg — bei einem Wechsel neu abfragen. */
    private fun useControl(newControl: SonosControl, fallbackReason: String?) {
        val changed = control !== newControl
        // Lief es bisher nur ersatzweise über die Cloud?
        val wasFallback = control === cloudControl && controlFallbackReason != null
        control = newControl
        controlPath = newControl.path
        controlFallbackReason = fallbackReason
        localFailures = 0
        if (changed) {
            when {
                fallbackReason != null -> AppLog.resolved(
                    TAG,
                    "Im WLAN kein Speaker erreicht — die App steuert ersatzweise über die Sonos-Cloud. Grund: $fallbackReason"
                )
                wasFallback && newControl !== cloudControl -> AppLog.resolved(
                    TAG,
                    "Speaker im WLAN wieder erreicht — die App steuert wieder direkt im WLAN statt über die Sonos-Cloud"
                )
                else -> AppLog.i(TAG, "Steuerung jetzt ${newControl.path.label}")
            }
            // Die Anzeige bleibt stehen, bis die erste Abfrage über den neuen Weg da ist
            restartPolling("Steuerung jetzt ${newControl.path.label}", keepPlayback = true)
        }
    }

    /**
     * Still neu verbinden, ohne Ladebildschirm — etwa wenn im WLAN mehrmals hintereinander nichts
     * ankam (dann ggf. über die Cloud) oder die App über die Cloud steuert, obwohl das WLAN eingestellt ist.
     */
    private fun reconnectQuietly() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            try {
                val players = fetchAndSyncPlayers().players
                // Unverändert? Dann nichts anfassen, sonst zeichnet sich der Startbildschirm neu
                if ((uiState as? UiState.SpeakerList)?.players != players) uiState = UiState.SpeakerList(players)
                updateSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                AppLog.w(TAG, "Still neu verbinden hat nicht geklappt", e)
            }
        }
    }

    /** In den Einstellungen: immer über die Sonos-Cloud steuern oder (wieder) direkt im WLAN. */
    fun changePreferCloud(enabled: Boolean) {
        if (enabled == preferCloud) return
        localClient.preferCloud = enabled
        preferCloud = enabled
        refreshSpeakers()
    }

    /** Lädt die Speaker des Haushalts neu (Knopf in den Settings), ohne den Bildschirm zu wechseln. */
    fun refreshSpeakers() {
        if (isRefreshingSpeakers) return
        isRefreshingSpeakers = true
        speakerRefreshMessage = null
        viewModelScope.launch {
            try {
                val result = fetchAndSyncPlayers()
                val foundIds = result.players.map { it.id }.toSet()
                val missing = speakerConfigs.count { it.playerId !in foundIds }
                uiState = UiState.SpeakerList(result.players)
                updateSelection()
                speakerRefreshMessage = describeRefresh(result.added, missing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                AppLog.w(TAG, "Speaker-Liste aktualisieren (Einstellungen) fehlgeschlagen", e)
                speakerRefreshMessage = "Speaker-Liste konnte nicht geladen werden: " +
                    (e.message ?: "Unbekannter Fehler")
            } finally {
                isRefreshingSpeakers = false
            }
        }
    }

    private fun describeRefresh(added: Int, missing: Int): String {
        val found = when (added) {
            0 -> "Keine neuen Speaker gefunden"
            1 -> "1 neuer Speaker gefunden"
            else -> "$added neue Speaker gefunden"
        }
        val text = if (missing > 0) "$found · $missing nicht erreichbar" else found
        return if (controlFallbackReason != null) "$text · Steuerung über die Sonos-Cloud (im WLAN kein Speaker erreicht)" else text
    }

    fun logout() = signOut(reason = null)

    /** Die Anmeldung ließ sich nicht erneuern → zurück zum Login, mit Hinweis warum. */
    private fun handleSessionExpired(e: SessionExpiredException) = signOut(reason = e.message)

    private fun signOut(reason: String?) {
        if (reason != null) {
            AppLog.e(TAG, "Abgemeldet, die Anmeldeseite steht statt des Startbildschirms: $reason")
        } else {
            AppLog.i(TAG, "Bei Sonos abgemeldet (Einstellungen)")
        }
        tokenStore.clear()
        stopPolling()
        volumeJob?.cancel()
        // Erst leeren, dann abbrechen: Ein abgebrochener Auftrag räumt sonst gleich selbst in der Liste auf
        val leaves = pendingLeaves.values.toList()
        pendingLeaves.clear()
        leaves.forEach { it.job.cancel() }
        control = null
        controlPath = null
        controlFallbackReason = null
        cloudControl.forgetHousehold()
        selectedPlayerId = null
        nowPlaying = null
        clearPlaybackError("abgemeldet")
        screen = Screen.Home
        editingProfileId = null
        editingCategoryId = null
        catalogCategoryId = null
        uiState = UiState.LoggedOut(reason)
    }

    // --- Speaker-Auswahl -------------------------------------------------

    fun selectSpeaker(playerId: String) {
        if (playerId == selectedPlayerId) return
        selectedPlayerId = playerId
        AppLog.d(TAG, "Speaker gewählt: ${speakerName(playerId)}")
        viewModelScope.launch { repository.setLastSelectedPlayer(playerId) }
        restartPolling("anderer Speaker gewählt")
    }

    /**
     * Ermittelt den auf dem Homescreen gewählten Speaker:
     * genau ein freigegebener Speaker → immer dieser, sonst die bisherige
     * Auswahl, sonst der zuletzt gewählte aus der Datenbank.
     */
    private fun updateSelection() {
        if (!settingsLoaded || uiState !is UiState.SpeakerList) return
        val selectableIds = selectableSpeakers.map { it.playerId }
        val newSelection = when {
            selectableIds.size == 1 -> selectableIds.first()
            selectedPlayerId in selectableIds -> selectedPlayerId
            settings.lastSelectedPlayerId in selectableIds -> settings.lastSelectedPlayerId
            else -> null
        }
        if (newSelection == selectedPlayerId) {
            if (pollJob == null) restartPolling("Abfrage der Wiedergabe startet")
            return
        }
        selectedPlayerId = newSelection
        if (newSelection != null && newSelection != settings.lastSelectedPlayerId) {
            viewModelScope.launch { repository.setLastSelectedPlayer(newSelection) }
        }
        restartPolling("Speaker-Auswahl hat sich geändert")
    }

    // --- Profil-Auswahl --------------------------------------------------

    fun selectProfile(profileId: Long) {
        if (profileId == selectedProfileId) return
        selectedProfileId = profileId
        viewModelScope.launch { repository.setLastSelectedProfile(profileId) }
    }

    /**
     * Wie bei den Speakern: genau ein aktives Profil → immer dieses, sonst die
     * bisherige Auswahl, sonst das zuletzt gewählte aus der Datenbank.
     */
    private fun updateProfileSelection() {
        if (!settingsLoaded || !profilesLoaded) return
        val selectableIds = selectableProfiles.map { it.profile.id }
        val newSelection = when {
            selectableIds.size == 1 -> selectableIds.first()
            selectedProfileId in selectableIds -> selectedProfileId
            settings.lastSelectedProfileId in selectableIds -> settings.lastSelectedProfileId
            else -> null
        }
        if (newSelection == selectedProfileId) return
        selectedProfileId = newSelection
        if (newSelection != null && newSelection != settings.lastSelectedProfileId) {
            viewModelScope.launch { repository.setLastSelectedProfile(newSelection) }
        }
    }

    // --- Wiedergabe ------------------------------------------------------

    fun onForegroundChanged(foreground: Boolean) {
        if (foreground != isInForeground) AppLog.d(TAG, if (foreground) "App im Vordergrund" else "App im Hintergrund")
        isInForeground = foreground
        if (!foreground) {
            stopPolling()
            return
        }
        // Lag beim letzten Mal z. B. kein Netz an, beim Zurückkommen still neu versuchen
        if (uiState is UiState.Error && tokenStore.accessToken != null) {
            startLoadingSpeakers("App wieder geöffnet")
        } else {
            restartPolling("App wieder geöffnet")
            // Lief die Steuerung nur ersatzweise über die Cloud, das WLAN erneut versuchen
            if (control === cloudControl && !localClient.preferCloud) reconnectQuietly()
        }
        // Inzwischen in der Sonos-App neue Alben in eine Playlist-Kategorie gelegt?
        refreshPlaylistCategoriesQuietly()
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * [reason]: warum neu abgefragt wird — steht im Protokoll, falls dabei eine Fehlermeldung verschwindet.
     * [keepPlayback]: Die Anzeige bleibt, bis die nächste Abfrage da ist (derselbe Speaker, anderer Weg).
     */
    private fun restartPolling(reason: String, keepPlayback: Boolean = false) {
        stopPolling()
        // Während die Musik umzieht, fragt der Umzug selbst ab und startet danach neu
        if (movingMusic != null) return
        if (!keepPlayback) nowPlaying = null
        clearPlaybackError("die Abfrage der Wiedergabe beginnt neu: $reason")
        val playerId = selectedPlayerId ?: return
        val c = control ?: return
        if (!isInForeground || screen != Screen.Home) return

        pollJob = viewModelScope.launch {
            var failures = 0
            var failingSinceMillis = 0L
            while (isActive) {
                try {
                    val pollStartedAt = SystemClock.elapsedRealtime()
                    var fetched = fetchNowPlaying(c, playerId)
                    // Wurde die Lautstärke woanders (Sonos-App, Taste am Speaker) über das
                    // Maximum gestellt, wird sie wieder auf das Maximum begrenzt.
                    val max = maxVolumeFor(playerId)
                    val volume = fetched.volume
                    if (volume != null && volume > max) {
                        c.setPlayerVolume(playerId, max)
                        fetched = fetched.copy(volume = max)
                    }
                    // Kurz nach einem Befehl kann Sonos noch den alten Stand liefern. Während Musik
                    // gestartet wird, gibt es Zwischenstände (z. B. erst Titel 1, dann der Zufallstitel) —
                    // die nicht zeigen. Und keine Abfrage übernehmen, die vor dem Ende eines Befehls begann.
                    val settled = SystemClock.elapsedRealtime() - lastCommandAtMillis > commandRefreshDelay(c)
                    if (settled && !isStartingMusic && pollStartedAt > lastCommandAtMillis) {
                        nowPlaying = fetched
                    }
                    if (failures > 0) {
                        val recovery = "die Abfrage der Wiedergabe klappt wieder (nach ${attempts(failures)}, " +
                            "${secondsSince(failingSinceMillis)} s)"
                        // Stand eine Meldung da, sagt das Protokoll gleich, dass sie damit verschwunden ist
                        if (playbackError == null) AppLog.resolved(TAG, "Aussetzer ohne Fehlermeldung überstanden: $recovery")
                        clearPlaybackError(recovery, recovered = true)
                    } else {
                        clearPlaybackError("die nächste Abfrage der Wiedergabe hat geklappt", recovered = true)
                    }
                    localFailures = 0
                    failures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SessionExpiredException) {
                    handleSessionExpired(e)
                    return@launch
                } catch (e: Exception) {
                    if (failures++ == 0) failingSinceMillis = SystemClock.elapsedRealtime()
                    // Ohne Zähler im Text: So zählt derselbe Eintrag im Protokoll hoch, statt alle 2 Sekunden einen neuen
                    AppLog.w(TAG, "Abfrage der Wiedergabe fehlgeschlagen: ${e.message}", e)
                    // Ein einzelner Aussetzer (wackliges Netz) soll nicht gleich eine Meldung aufpoppen
                    // lassen — solange noch etwas zu sehen ist, erst ab dem zweiten in Folge
                    if (nowPlaying == null || failures >= POLL_FAILURES_BEFORE_ERROR) {
                        reportPlaybackError(
                            e.message ?: "Wiedergabe konnte nicht geladen werden",
                            during = "Abfrage der Wiedergabe",
                            cause = e
                        )
                    }
                    // Im WLAN mehrmals nichts angekommen → neu verbinden: neue Adresse, sonst über die Cloud
                    if (c === localClient && ++localFailures >= LOCAL_FAILURES_BEFORE_RECONNECT) {
                        localFailures = 0
                        AppLog.d(TAG, "Im WLAN $LOCAL_FAILURES_BEFORE_RECONNECT-mal nichts angekommen — neu verbinden")
                        reconnectQuietly()
                    }
                }
                // Nur ersatzweise über die Cloud? Dann ab und zu wieder das WLAN versuchen
                if (c === cloudControl && !localClient.preferCloud && !isStartingMusic &&
                    SystemClock.elapsedRealtime() - localTriedAtMillis >= LOCAL_RETRY_INTERVAL_MS
                ) {
                    reconnectQuietly()
                }
                delay(if (c.path == ControlPath.LOCAL) LOCAL_POLL_INTERVAL_MS else POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * [during]: wobei es passiert ist, fürs Protokoll. [userAction]: ein Knopfdruck ist
     * fehlgeschlagen → Meldung immer (wieder) zeigen.
     */
    private fun reportPlaybackError(message: String, during: String, userAction: Boolean = false, cause: Throwable? = null) {
        // Steuert die App nur ersatzweise über die Cloud, gehört der Grund dazu — meist ist er das eigentliche Problem
        val reason = controlFallbackReason?.takeIf { control === cloudControl }
        val text = when {
            reason != null -> "$message\n\nDirekt im WLAN ging es nicht: $reason"
            control === cloudControl && preferCloud ->
                "$message\n\nIn den Einstellungen ist „Über die Sonos-Cloud steuern“ eingeschaltet."
            else -> message
        }
        // Dieselbe Meldung steht bei jeder weiteren gescheiterten Abfrage noch da — nur einmal ins Protokoll
        if (userAction || text != playbackError) {
            AppLog.e(TAG, "Fehlermeldung auf dem Startbildschirm bei „$during“: $text", cause)
        }
        if (playbackError == null) playbackErrorSinceMillis = SystemClock.elapsedRealtime()
        if (userAction || text != playbackError) playbackErrorDismissed = false
        playbackError = text
    }

    /**
     * [reason]: wodurch die Meldung verschwindet — steht im Protokoll, denn die Kinder können nicht
     * vorlesen, was da stand. [recovered]: Es hat wieder geklappt, statt dass nur die Anzeige neu beginnt.
     */
    private fun clearPlaybackError(reason: String, recovered: Boolean = false) {
        playbackError?.let { shown ->
            val how = if (recovered) "verschwunden" else "ausgeblendet"
            val dismissed = if (playbackErrorDismissed) ", war schon mit „Okay“ weggeklickt" else ""
            AppLog.resolved(
                TAG,
                "Fehlermeldung nach ${secondsSince(playbackErrorSinceMillis)} s $how$dismissed — $reason. " +
                    "Meldung war: ${firstLine(shown)}"
            )
        }
        playbackError = null
        playbackErrorDismissed = false
    }

    fun dismissPlaybackError() {
        if (playbackError != null && !playbackErrorDismissed) {
            AppLog.resolved(
                TAG,
                "Fehlermeldung nach ${secondsSince(playbackErrorSinceMillis)} s mit „Okay“ weggeklickt. " +
                    "Meldung war: ${firstLine(playbackError.orEmpty())}"
            )
        }
        playbackErrorDismissed = true
    }

    private fun secondsSince(elapsedRealtimeMillis: Long): Long =
        (SystemClock.elapsedRealtime() - elapsedRealtimeMillis) / 1000

    private fun firstLine(text: String): String = text.lineSequence().firstOrNull().orEmpty()

    private fun attempts(count: Int): String = if (count == 1) "1 Fehlversuch" else "$count Fehlversuchen"

    /** Name des Speakers fürs Protokoll. */
    private fun speakerName(playerId: String): String =
        speakerConfigs.firstOrNull { it.playerId == playerId }?.name ?: playerId

    /** Kurz nach einem Befehl kann Sonos noch den alten Stand liefern — im WLAN weniger lange. */
    private fun commandRefreshDelay(c: SonosControl): Long =
        if (c.path == ControlPath.LOCAL) LOCAL_COMMAND_REFRESH_DELAY_MS else COMMAND_REFRESH_DELAY_MS

    private fun musicRefreshDelay(c: SonosControl): Long =
        if (c.path == ControlPath.LOCAL) LOCAL_MUSIC_REFRESH_DELAY_MS else MUSIC_REFRESH_DELAY_MS

    /** Die Wiedergabe hängt an der Gruppe, in der der Player gerade spielt. */
    private suspend fun findGroup(c: SonosControl, playerId: String): SonosGroup =
        c.getGroups().groups
            .firstOrNull { playerId in it.playerIds }
            ?: throw SonosApiException("Speaker ist gerade nicht erreichbar")

    /**
     * Führt [block] mit der Gruppe aus, in der [playerId] gerade spielt. Sonos vergibt eine neue
     * Gruppen-Id, sobald sich die Gruppe ändert — die Cloud nennt kurz danach manchmal noch die alte
     * und lehnt sie dann ab (HTTP 410, ERROR_RESOURCE_GONE). Dann nach kurzer Pause mit frisch
     * geholter Gruppe wiederholen, höchstens [GROUP_GONE_ATTEMPTS]-mal.
     */
    private suspend fun <T> withGroup(c: SonosControl, playerId: String, block: suspend (SonosGroup) -> T): T {
        repeat(GROUP_GONE_ATTEMPTS - 1) { attempt ->
            try {
                return block(findGroup(c, playerId)).also { if (attempt > 0) logGroupFound(playerId, attempt + 1) }
            } catch (e: SonosApiException) {
                if (!e.isGroupGone) throw e
                AppLog.w(
                    TAG,
                    "Gruppe von ${speakerName(playerId)} gibt es nicht mehr (Versuch ${attempt + 1}), gleich noch einmal",
                    e
                )
            }
            delay(GROUP_GONE_RETRY_MS)
        }
        return block(findGroup(c, playerId)).also { logGroupFound(playerId, GROUP_GONE_ATTEMPTS) }
    }

    private fun logGroupFound(playerId: String, attempt: Int) = AppLog.resolved(
        TAG,
        "Sonos meldete die Gruppe von ${speakerName(playerId)} als nicht mehr vorhanden — " +
            "mit der neu geholten Gruppe hat es im $attempt. Versuch geklappt"
    )

    private suspend fun fetchNowPlaying(c: SonosControl, playerId: String): NowPlaying =
        withGroup(c, playerId) { group -> fetchNowPlaying(c, playerId, group) }

    private suspend fun fetchNowPlaying(c: SonosControl, playerId: String, group: SonosGroup): NowPlaying =
        coroutineScope {
            val current = async { c.getPlayback(group.id) }
            val volume = async { runCatching { c.getPlayerVolume(playerId) }.getOrNull() }

            val (playback, meta) = current.await()
            val track = meta.currentItem?.track
            val playerVolume = volume.await()

            val actions = playback.availablePlaybackActions
            NowPlaying(
                groupId = group.id,
                title = track?.name ?: meta.container?.name,
                subtitle = track?.artist?.name
                    ?: meta.streamInfo
                    ?: meta.container?.name?.takeIf { track?.name != null },
                imageUrl = meta.coverUrl,
                containerImageUrl = meta.containerCoverUrl,
                playbackState = playback.playbackState,
                positionMillis = playback.positionMillis,
                durationMillis = track?.durationMillis?.takeIf { it > 0 },
                volume = playerVolume?.volume,
                muted = playerVolume?.muted ?: false,
                canSkip = actions?.canSkip ?: true,
                canSkipBack = actions?.canSkipBack ?: true,
                fetchedAtMillis = SystemClock.elapsedRealtime(),
                path = c.path
            )
        }

    fun togglePlayPause() {
        val current = nowPlaying ?: return
        // Sofort umschalten, damit der Knopf direkt reagiert — die nächste Abfrage korrigiert ggf.
        nowPlaying = current.copy(
            playbackState = if (current.isPlaying) PlaybackStatus.STATE_PAUSED else PlaybackStatus.STATE_PLAYING,
            positionMillis = current.currentPositionMillis(),
            fetchedAtMillis = SystemClock.elapsedRealtime()
        )
        sendGroupCommand(current, if (current.isPlaying) "Pause" else "Abspielen") { c, groupId -> c.togglePlayPause(groupId) }
    }

    fun skipToNext() {
        val current = nowPlaying ?: return
        sendGroupCommand(current, "Nächster Titel") { c, groupId -> c.skipToNextTrack(groupId) }
    }

    fun skipToPrevious() {
        val current = nowPlaying ?: return
        sendGroupCommand(current, "Vorheriger Titel") { c, groupId -> c.skipToPreviousTrack(groupId) }
    }

    /**
     * Ein Befehl an die Gruppe, in der der gewählte Speaker spielt. Die Gruppe aus der letzten Abfrage
     * kann inzwischen eine andere sein (Musik mitgenommen, in der Sonos-App umgruppiert): Im WLAN wird
     * sie deshalb frisch geholt (kostet kaum Zeit), über die Cloud erst, wenn Sonos die alte Id als
     * nicht mehr vorhanden ablehnt (HTTP 410, ERROR_RESOURCE_GONE) — dann einmal mit der aktuellen.
     */
    private fun sendGroupCommand(current: NowPlaying, what: String, command: suspend (SonosControl, String) -> Unit) {
        val playerId = selectedPlayerId ?: return
        sendPlaybackCommand(what) { c ->
            // Im WLAN kostet die Abfrage kaum Zeit; und die Gruppen-Id vom anderen Weg gilt hier nicht
            if (c.path == ControlPath.LOCAL || current.path != c.path) {
                withGroup(c, playerId) { command(c, it.id) }
                return@sendPlaybackCommand
            }
            try {
                command(c, current.groupId)
            } catch (e: SonosApiException) {
                if (!e.isGroupGone) throw e
                AppLog.w(TAG, "„$what“: Gruppe ${current.groupId} gibt es nicht mehr — Befehl an die aktuelle Gruppe", e)
                withGroup(c, playerId) { command(c, it.id) }
                AppLog.resolved(
                    TAG,
                    "„$what“: Sonos meldete die Gruppe als nicht mehr vorhanden — mit der aktuellen Gruppe hat es geklappt"
                )
            }
        }
    }

    /**
     * Setzt die Lautstärke des gewählten Speakers, begrenzt auf sein Maximum.
     * Beim Ziehen über die Leiste kommen viele Werte schnell hintereinander —
     * gesendet wird nur der letzte, die Anzeige folgt aber sofort.
     */
    fun setVolume(volume: Int) {
        val playerId = selectedPlayerId ?: return
        val c = control ?: return
        val current = nowPlaying ?: return
        val target = volume.coerceIn(0, maxVolumeFor(playerId))
        if (target == current.volume) return

        lastCommandAtMillis = SystemClock.elapsedRealtime()
        nowPlaying = current.copy(volume = target)
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            delay(VOLUME_DEBOUNCE_MS)
            try {
                c.setPlayerVolume(playerId, target)
                lastCommandAtMillis = SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                reportPlaybackError(
                    e.message ?: "Lautstärke konnte nicht geändert werden",
                    during = "Lautstärke auf $target",
                    userAction = true,
                    cause = e
                )
            }
        }
    }

    /**
     * Spielt einen Eintrag aus der Musikauswahl auf dem gewählten Speaker ab.
     * Die Warteschlange wird dabei ersetzt. [shuffle] = null heißt: so, wie es
     * in der Kategorie eingestellt ist (bei „Kinder entscheiden" kommt die Wahl
     * der Kinder als true/false herein). [onNewMusicPlayed] kommt, wenn das gewählte
     * Profil die Musik bis eben noch nie gespielt hat — damit die anderen Tablets davon erfahren.
     */
    fun playMusic(item: MusicItem, shuffle: Boolean? = null, onNewMusicPlayed: () -> Unit = {}) {
        val playerId = selectedPlayerId ?: return
        if (control == null) return
        // Jetzt merken: Bis Sonos die Musik angenommen hat, könnte schon ein anderes Profil gewählt sein
        val profileSyncId = selectedProfile?.profile?.syncId
        val order = categories
            .firstOrNull { it.category.id == item.categoryId }?.category?.playOrderMode ?: PlayOrder.ORDERED
        val useShuffle = shuffle ?: (order == PlayOrder.SHUFFLE)
        startingMusic = item
        sendPlaybackCommand(
            what = "Musik starten: ${item.name}",
            isMusicStart = true,
            onFinished = { startingMusic = null },
            // Erst wenn Sonos die Musik angenommen hat, ist sie nicht mehr neu
            onSent = {
                if (profileSyncId != null) {
                    viewModelScope.launch { if (repository.markPlayed(profileSyncId, item)) onNewMusicPlayed() }
                }
            },
            // Ist das Cover noch nicht auf dem Tablet gespeichert (z. B. abgelaufene Apple-Music-
            // Adresse), das frische aus der Wiedergabe nehmen — für Playlisten das der Playlist
            onPlaying = { playing ->
                viewModelScope.launch {
                    // Danach richtet sich, ob das Tier am Cover tanzt oder vorliest
                    repository.setLastStartedCategory(playerId, item.categoryId)
                    repository.storeCoverFromPlayback(item.id, playing.containerImageUrl, playing.imageUrl)
                }
            }
        ) { c ->
            // Wurde die Musik hier gerade erst mitgenommen, erst ganz aus der Gruppe — die neue Musik
            // soll nur hier laufen, nicht auch im anderen Raum
            leaveNowIfPending(c, playerId)
            val shuffleTracks = useShuffle && item.hasMultipleTracks
            // Nur ein Album aus einer Playlist — das kann nur der Speaker im Heimnetz
            if (item.musicSource == MusicSource.PLAYLIST_ALBUM) {
                playAlbumFromPlaylist(c, playerId, item, shuffle = shuffleTracks)
                return@sendPlaybackCommand
            }
            withGroup(c, playerId) { c.startMusic(it.id, item, shuffle = shuffleTracks) }
        }
    }

    /**
     * Die Sonos-Playlist einer Playlist-Kategorie: wie beim Starten einer Playlist über Id und Namen, sonst
     * den Namen — und sonst nur die Id, falls die Playlist in der Sonos-App umbenannt wurde.
     */
    private suspend fun findPlaylist(c: SonosControl, playlistId: String, name: String?): SonosPlaylist {
        val playlists = c.getPlaylists()
        return playlists.firstOrNull { it.id == playlistId && it.name == name }
            ?: name?.let { wanted -> playlists.firstOrNull { it.name == wanted } }
            ?: playlists.firstOrNull { it.id == playlistId }
            ?: throw SonosApiException("Die Playlist „${name ?: playlistId}“ gibt es nicht mehr bei Sonos.")
    }

    /**
     * Spielt ein Album aus der Playlist seiner Kategorie — nur dieses, nicht die ganze Playlist. Die
     * Control API kann das nicht, deshalb über den Speaker im Heimnetz ([LocalSonosClient.playAlbumFromPlaylist]).
     */
    private suspend fun playAlbumFromPlaylist(c: SonosControl, playerId: String, item: MusicItem, shuffle: Boolean) {
        val category = repository.getCategory(item.categoryId)
        val playlist = findPlaylist(c, category?.playlistId ?: item.sonosId, category?.playlistName)
        localClient.playAlbumFromPlaylist(
            playerId = playerId,
            playerName = speakerConfigs.firstOrNull { it.playerId == playerId }?.name,
            playlistId = playlist.id,
            playlistName = playlist.name,
            albumName = item.name,
            shuffle = shuffle,
            addressHint = nowPlaying?.imageUrl
        )
    }

    /**
     * [onSent]: [command] hat geklappt. [onPlaying]: danach ist auch die neue Wiedergabe
     * abgefragt — bleibt aus, wenn inzwischen ein anderer Speaker gewählt wurde.
     */
    private fun sendPlaybackCommand(
        /** Was der Befehl tut — fürs Protokoll. */
        what: String,
        isMusicStart: Boolean = false,
        onFinished: () -> Unit = {},
        onSent: () -> Unit = {},
        onPlaying: (NowPlaying) -> Unit = {},
        command: suspend (SonosControl) -> Unit
    ) {
        val playerId = selectedPlayerId ?: return onFinished()
        val c = control ?: return onFinished()
        AppLog.d(TAG, "$what (${speakerName(playerId)}, ${c.path.label})")
        lastCommandAtMillis = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            try {
                command(c)
                lastCommandAtMillis = SystemClock.elapsedRealtime()
                onSent()
                // Sonos braucht einen Moment, bis Track und Status aktualisiert sind
                delay(if (isMusicStart) musicRefreshDelay(c) else commandRefreshDelay(c))
                // Inzwischen auf den anderen Weg gewechselt? Dann gilt die Gruppen-Id dieser Abfrage dort nicht
                if (selectedPlayerId == playerId && control === c) {
                    nowPlaying = fetchNowPlaying(c, playerId).also(onPlaying)
                    // Abfragen, die noch während des Befehls begonnen haben, sind damit überholt
                    lastCommandAtMillis = SystemClock.elapsedRealtime()
                    clearPlaybackError("„$what“ hat geklappt", recovered = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                reportPlaybackError(
                    e.message ?: "Befehl konnte nicht gesendet werden",
                    during = what,
                    userAction = true,
                    cause = e
                )
            } finally {
                onFinished()
            }
        }
    }

    // --- Musik mitnehmen -------------------------------------------------

    /**
     * Nimmt die Musik des gewählten Speakers zu [targetPlayerId] mit — etwa vom Kinderzimmer ins
     * Bad zum Zähneputzen: Der Ziel-Speaker kommt in die Gruppe und spielt an derselben Stelle mit.
     * Der bisherige spielt noch [MOVE_SOURCE_STOP_DELAY_MS] weiter, damit die Kinder Zeit haben,
     * hinüberzugehen, dann geht er aus der Gruppe ([scheduleLeave]). Danach ist der Ziel-Speaker
     * gewählt. Spielt der bisherige mit weiteren Speakern zusammen, spielen die weiter.
     */
    fun moveMusicTo(targetPlayerId: String) {
        val sourcePlayerId = selectedPlayerId ?: return
        val c = control ?: return
        if (targetPlayerId == sourcePlayerId || movingMusic != null || isStartingMusic) return
        val from = speakerConfigs.firstOrNull { it.playerId == sourcePlayerId } ?: return
        val to = speakerConfigs.firstOrNull { it.playerId == targetPlayerId } ?: return
        movingMusic = MovingMusic(from, to)
        AppLog.d(TAG, "Musik mitnehmen: von ${from.name} nach ${to.name} (${c.path.label})")
        stopPolling()
        volumeJob?.cancel()
        lastCommandAtMillis = SystemClock.elapsedRealtime()
        // Geht die Musik dorthin zurück, wo sie gerade erst weggenommen wurde? Dann soll sie dort weiterspielen
        val returningLeave = cancelPendingLeave(targetPlayerId)
        viewModelScope.launch {
            var moved = false
            var playing: NowPlaying? = null
            var error: String? = null
            var errorCause: Exception? = null
            try {
                withGroup(c, sourcePlayerId) { group ->
                    if (targetPlayerId !in group.playerIds) {
                        // Nicht lauter loslegen als erlaubt — sonst regelt erst die nächste Abfrage herunter
                        limitVolume(c, targetPlayerId)
                        c.addToGroup(group.id, targetPlayerId)
                    }
                }
                moved = true
                scheduleLeave(c, sourcePlayerId, targetPlayerId)
                // Das Tier am Cover macht dort weiter, was es hier gemacht hat (vorlesen oder tanzen)
                settings.lastStartedCategories[sourcePlayerId]?.let {
                    repository.setLastStartedCategory(targetPlayerId, it)
                }
                playing = fetchMovedMusic(c, targetPlayerId)
            } catch (e: CancellationException) {
                movingMusic = null
                throw e
            } catch (e: SessionExpiredException) {
                movingMusic = null
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                error = e.message ?: "Die Musik konnte nicht mitgenommen werden"
                errorCause = e
            }
            // Zurückholen hat nicht geklappt → der Ziel-Speaker geht doch noch aus der Gruppe, sonst spielten beide weiter
            if (!moved && returningLeave != null) scheduleLeave(c, targetPlayerId, returningLeave.targetPlayerId)
            movingMusic = null
            if (moved) {
                selectedPlayerId = targetPlayerId
                launch { repository.setLastSelectedPlayer(targetPlayerId) }
            }
            restartPolling("Musik mitnehmen ist fertig")
            // Gleich die Wiedergabe am neuen Ort zeigen statt eines Ladekreises bis zur ersten Abfrage
            if (playing != null && control === c) nowPlaying = playing
            error?.let {
                reportPlaybackError(it, during = "Musik mitnehmen nach ${to.name}", userAction = true, cause = errorCause)
            }
        }
    }

    /** Stellt [playerId] auf sein Maximum, falls er gerade lauter steht. Klappt das nicht, regelt die nächste Abfrage. */
    private suspend fun limitVolume(c: SonosControl, playerId: String) {
        val max = maxVolumeFor(playerId)
        if (max >= 100) return
        try {
            if (c.getPlayerVolume(playerId).volume > max) c.setPlayerVolume(playerId, max)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Lautstärke von ${speakerName(playerId)} vor dem Mitnehmen nicht begrenzt", e)
        }
    }

    /**
     * Wiedergabe auf dem Ziel-Speaker, sobald er mitspielt. null, wenn die Abfrage nicht klappt — die
     * Musik ist trotzdem mitgekommen, dann zeigt die nächste Abfrage sie.
     */
    private suspend fun fetchMovedMusic(c: SonosControl, playerId: String): NowPlaying? {
        delay(MOVE_REFRESH_DELAY_MS)
        return try {
            fetchNowPlaying(c, playerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Wiedergabe nach dem Mitnehmen nicht abgefragt", e)
            null
        }
    }

    /**
     * Nimmt [sourcePlayerId] nach [MOVE_SOURCE_STOP_DELAY_MS] aus der Gruppe — bis dahin spielen
     * beide Speaker, damit die Kinder Zeit haben, in den anderen Raum zu gehen.
     */
    private fun scheduleLeave(c: SonosControl, sourcePlayerId: String, targetPlayerId: String) {
        cancelPendingLeave(sourcePlayerId)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                delay(MOVE_SOURCE_STOP_DELAY_MS)
                leaveGroupRetrying(c, sourcePlayerId, targetPlayerId)
            } finally {
                // Nur den eigenen Eintrag entfernen, nicht einen inzwischen neu geplanten
                if (pendingLeaves[sourcePlayerId]?.job === coroutineContext[Job]) pendingLeaves.remove(sourcePlayerId)
            }
        }
        pendingLeaves[sourcePlayerId] = PendingLeave(targetPlayerId, job)
        job.start()
    }

    /** [playerId] soll doch nicht aus der Gruppe gehen (die Musik wird gerade dorthin zurückgeholt). */
    private fun cancelPendingLeave(playerId: String): PendingLeave? =
        pendingLeaves.remove(playerId)?.also { it.job.cancel() }

    /**
     * Soll [playerId] gleich aus der Gruppe gehen, weil die Musik mitgenommen wurde, dann jetzt
     * schon — etwa bevor er neue Musik spielt. Sonst liefe die neue Musik auch im anderen Raum, und
     * hier hörte sie nach Ablauf der Zeit trotzdem auf.
     */
    private suspend fun leaveNowIfPending(c: SonosControl, playerId: String) {
        val pending = pendingLeaves.remove(playerId) ?: return
        pending.job.cancel()
        leaveGroup(c, playerId, pending.targetPlayerId)
    }

    /**
     * [leaveGroup] mit ein paar Versuchen — klappt es gar nicht, spielt der bisherige Speaker weiter.
     * Dann eine Meldung, damit ein Erwachsener ihn ausmacht.
     */
    private suspend fun leaveGroupRetrying(c: SonosControl, sourcePlayerId: String, targetPlayerId: String) {
        repeat(LEAVE_ATTEMPTS) { attempt ->
            try {
                leaveGroup(c, sourcePlayerId, targetPlayerId)
                if (attempt > 0) {
                    AppLog.resolved(
                        TAG,
                        "${speakerName(sourcePlayerId)} ließ sich nach dem Mitnehmen erst nicht aus der Gruppe nehmen — " +
                            "im ${attempt + 1}. Versuch hat es geklappt"
                    )
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return
            } catch (e: Exception) {
                AppLog.w(
                    TAG,
                    "${speakerName(sourcePlayerId)} nach dem Mitnehmen nicht aus der Gruppe genommen (Versuch ${attempt + 1})",
                    e
                )
                if (attempt == LEAVE_ATTEMPTS - 1) {
                    val name = speakerConfigs.firstOrNull { it.playerId == sourcePlayerId }?.name ?: "Der Speaker"
                    reportPlaybackError(
                        "„$name“ spielt noch weiter: ${e.message ?: "Unbekannter Fehler"}",
                        during = "Musik mitnehmen: bisherigen Speaker ausmachen",
                        userAction = true,
                        cause = e
                    )
                } else {
                    delay(LEAVE_RETRY_DELAY_MS)
                }
            }
        }
    }

    /**
     * Nimmt [sourcePlayerId] aus der Gruppe, in die die Musik mitgenommen wurde — aber nur, solange
     * [targetPlayerId] noch mit ihm spielt. Hat inzwischen jemand umgruppiert, bleibt alles, wie es
     * ist. Hat der bisherige Speaker die Gruppe gesteuert, übernimmt ein anderer; lief die Musik und
     * steht danach still, wird sie einmal angeschubst.
     */
    private suspend fun leaveGroup(c: SonosControl, sourcePlayerId: String, targetPlayerId: String) {
        var left = false
        val wasPlaying = withGroup(c, sourcePlayerId) { group ->
            if (targetPlayerId !in group.playerIds) return@withGroup null
            val playing = currentlyPlaying(c, group.id)
            c.removeFromGroup(group.id, sourcePlayerId)
            left = true
            playing
        }
        if (!left) return
        AppLog.d(TAG, "${speakerName(sourcePlayerId)} hat die Musik an ${speakerName(targetPlayerId)} abgegeben")
        if (wasPlaying != true) return
        delay(MOVE_REFRESH_DELAY_MS)
        withGroup(c, targetPlayerId) { targetGroup ->
            if (currentlyPlaying(c, targetGroup.id) == false) {
                AppLog.resolved(TAG, "Musik stand nach dem Mitnehmen still — wieder gestartet")
                c.play(targetGroup.id)
            }
        }
    }

    /**
     * Spielt die Gruppe gerade (oder lädt)? null, wenn Sonos nicht antwortet. Gibt es die Gruppe nicht
     * mehr, kommt der Fehler durch — [withGroup] versucht es dann mit der aktuellen.
     */
    private suspend fun currentlyPlaying(c: SonosControl, groupId: String): Boolean? =
        runCatching { c.getPlaybackStatus(groupId) }
            .onFailure {
                if (it is CancellationException || it is SessionExpiredException) throw it
                if (it is SonosApiException && it.isGroupGone) throw it
            }
            .getOrNull()
            ?.let { it.isPlaying || it.isBuffering }

    // --- Settings & Passwort ---------------------------------------------

    fun openSettings() {
        if (settings.isLocked) {
            passwordWrong = false
            showPasswordPrompt = true
        } else {
            enterSettings()
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            if (repository.verifyPassword(password)) {
                showPasswordPrompt = false
                passwordWrong = false
                enterSettings()
            } else {
                passwordWrong = true
            }
        }
    }

    fun dismissPasswordPrompt() {
        showPasswordPrompt = false
        passwordWrong = false
    }

    private fun enterSettings() {
        screen = Screen.Settings
        stopPolling()
    }

    /** Zurück-Taste und Zurück-Pfeil: eine Ebene nach oben. */
    fun navigateBack() {
        when (screen) {
            Screen.MusicCatalog -> closeCatalog()
            Screen.CategoryEditor -> {
                screen = Screen.MusicLibrary
                editingCategoryId = null
            }
            // Zurück dorthin, von wo die Musikauswahl geöffnet wurde
            Screen.MusicLibrary -> screen = if (editingProfile != null) Screen.ProfileEditor else Screen.Settings
            Screen.Speakers -> {
                screen = Screen.Settings
                speakerRefreshMessage = null
            }
            Screen.ProfileEditor -> {
                screen = Screen.Settings
                editingProfileId = null
            }
            Screen.Sync -> screen = Screen.Settings
            Screen.ReleaseNotes -> screen = Screen.Settings
            Screen.Log -> screen = Screen.Settings
            Screen.Settings -> closeSettings()
            Screen.Home -> Unit
        }
    }

    fun closeSettings() {
        screen = Screen.Home
        editingProfileId = null
        editingCategoryId = null
        catalogCategoryId = null
        speakerRefreshMessage = null
        // Neue Speaker wurden in den Settings gesehen → beim nächsten Mal nicht mehr „Neu"
        viewModelScope.launch { repository.clearNewFlags() }
        restartPolling("Einstellungen geschlossen")
    }

    /** Öffnet die Speaker-Einstellungen. */
    fun openSpeakers() {
        screen = Screen.Speakers
    }

    /** Öffnet „Tablets abgleichen“. */
    fun openSync() {
        screen = Screen.Sync
    }

    /** Öffnet die Release Notes. */
    fun openReleaseNotes() {
        screen = Screen.ReleaseNotes
    }

    /** Öffnet das Protokoll. */
    fun openLog() {
        screen = Screen.Log
    }

    fun setSpeakerEnabled(playerId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setSpeakerEnabled(playerId, enabled) }
    }

    fun setSpeakerMaxVolume(playerId: String, maxVolume: Int) {
        viewModelScope.launch { repository.setSpeakerMaxVolume(playerId, maxVolume) }
    }

    fun deleteSpeaker(playerId: String) {
        viewModelScope.launch { repository.deleteSpeaker(playerId) }
    }

    fun setSpeakerIcon(playerId: String, icon: SpeakerIcon) {
        viewModelScope.launch { repository.setSpeakerIcon(playerId, icon) }
    }

    // --- Profile und Musikauswahl (Settings) -----------------------------

    /** Legt ein Profil an und öffnet direkt seine Unterseite. */
    fun createProfile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val icon = ProfileIcon.suggestion(profiles.map { it.profile.icon })
            val id = repository.createProfile(name, icon)
            openProfile(id)
        }
    }

    fun openProfile(profileId: Long) {
        editingProfileId = profileId
        screen = Screen.ProfileEditor
    }

    fun setProfileName(profileId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.setProfileName(profileId, name) }
    }

    fun setProfileIcon(profileId: Long, icon: ProfileIcon) {
        viewModelScope.launch { repository.setProfileIcon(profileId, icon) }
    }

    fun setProfileEnabled(profileId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setProfileEnabled(profileId, enabled) }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch { repository.deleteProfile(profileId) }
    }

    /** Öffnet die zentrale Musikauswahl — aus den Settings oder von einer Profil-Seite. */
    fun openLibrary() {
        screen = Screen.MusicLibrary
    }

    /** Öffnet die Unterseite einer Kategorie mit ihrer Musik und ihren Einstellungen. */
    fun openCategory(categoryId: Long) {
        editingCategoryId = categoryId
        screen = Screen.CategoryEditor
        // Eine Playlist-Kategorie zeigt gleich den Stand ihrer Playlist
        if (categories.any { it.category.id == categoryId && it.category.isPlaylistCategory }) {
            refreshPlaylistCategoryNow(categoryId)
        }
    }

    // --- Kategorien aus einer Sonos-Playlist -----------------------------

    /** Die Sonos-Playlisten zur Auswahl für eine Playlist-Kategorie — aus dem Katalog. */
    fun loadPlaylistChoices() {
        if (catalogState !is CatalogState.Loaded) loadCatalog()
    }

    /** Legt eine Kategorie an, deren Einträge die Alben der Playlist sind, und öffnet sie. */
    fun createPlaylistCategory(playlist: CatalogEntry) {
        if (playlist.source != MusicSource.PLAYLIST) return
        viewModelScope.launch {
            val id = repository.createPlaylistCategory(playlist)
            editingCategoryId = id
            screen = Screen.CategoryEditor
            refreshPlaylistCategory(id)
        }
    }

    /** Hängt eine Playlist-Kategorie an eine andere Sonos-Playlist; deren Alben ersetzen die bisherigen. */
    fun changeCategoryPlaylist(categoryId: Long, playlist: CatalogEntry) {
        if (playlist.source != MusicSource.PLAYLIST) return
        viewModelScope.launch {
            repository.setCategoryPlaylist(categoryId, playlist.sonosId, playlist.name)
            refreshPlaylistCategory(categoryId)
        }
    }

    /** Gleicht eine Playlist-Kategorie sofort mit ihrer Playlist ab („Jetzt abgleichen“). */
    fun refreshPlaylistCategoryNow(categoryId: Long) {
        viewModelScope.launch { refreshPlaylistCategory(categoryId) }
    }

    /** Beim Öffnen der Musikauswahl auf dem Homescreen: neue Alben sollen gleich zu sehen sein. */
    fun onMusicPickerOpened() = refreshPlaylistCategoriesQuietly()

    /**
     * Gleicht alle Playlist-Kategorien mit ihren Sonos-Playlisten ab — nach dem Laden der Speaker, beim
     * Öffnen der Musikauswahl und wenn die App wieder in den Vordergrund kommt, aber höchstens alle
     * [PLAYLIST_REFRESH_INTERVAL_MS]. Fehler stehen nur auf der Seite der Kategorie.
     */
    private fun refreshPlaylistCategoriesQuietly() {
        if (tokenStore.accessToken == null) return
        val ids = categories.filter { it.category.isPlaylistCategory }.map { it.category.id }
        if (ids.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (playlistsRefreshedAt != 0L && now - playlistsRefreshedAt < PLAYLIST_REFRESH_INTERVAL_MS) return
        playlistsRefreshedAt = now
        viewModelScope.launch { ids.forEach { refreshPlaylistCategory(it) } }
    }

    /**
     * Holt die Titel der Playlist einer Kategorie — vom Speaker im Heimnetz (mit Covern), sonst aus der
     * Sonos-Cloud — und übernimmt ihre Alben als Einträge. Klappt das nicht, bleibt alles, wie es ist.
     */
    private suspend fun refreshPlaylistCategory(categoryId: Long) = playlistRefreshMutex.withLock {
        val category = repository.getCategory(categoryId) ?: return@withLock
        val playlistId = category.playlistId ?: return@withLock
        refreshingPlaylistCategories = refreshingPlaylistCategories + categoryId
        try {
            // Bevor die Speaker geladen sind, über die Cloud — sonst auf dem Weg, über den die App gerade steuert
            val playlist = findPlaylist(control ?: cloudControl, playlistId, category.playlistName)
            if (playlist.id != playlistId || playlist.name != category.playlistName) {
                repository.setCategoryPlaylist(categoryId, playlist.id, playlist.name)
            }
            val expected = playlist.trackCount
            // Der Speaker kennt auch die Cover; fehlen dort Titel, lieber die Liste aus der Cloud
            val tracks = localClient.playlistTracks(playlist.id, addressHint = nowPlaying?.imageUrl)
                ?.takeIf { expected == null || it.size >= expected }
                ?: cloudControl.getPlaylistTracks(playlist.id)
            // Eine leere Antwort zu einer Playlist mit Titeln würde alle Einträge löschen
            if (tracks.isEmpty() && expected != 0) {
                throw SonosApiException("Die Titel der Playlist „${playlist.name}“ konnten nicht geladen werden.")
            }
            repository.updatePlaylistAlbums(categoryId, playlist.id, groupAlbums(tracks))
            playlistCategoryErrors = playlistCategoryErrors - categoryId
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            AppLog.w(TAG, "Playlist-Kategorie „${category.name}“ nicht abgeglichen", e)
            playlistCategoryErrors = playlistCategoryErrors +
                (categoryId to (e.message ?: "Die Playlist konnte nicht geladen werden."))
        } finally {
            refreshingPlaylistCategories = refreshingPlaylistCategories - categoryId
        }
    }

    /** Neue Kategorien sind erst einmal für alle Profile sichtbar. */
    fun createCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCategory(name) }
    }

    fun setCategoryVisible(categoryId: Long, profileId: Long, visible: Boolean) {
        viewModelScope.launch { repository.setCategoryVisible(categoryId, profileId, visible) }
    }

    fun renameCategory(categoryId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.setCategoryName(categoryId, name) }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { repository.deleteCategory(categoryId) }
    }

    fun setCategoryImage(categoryId: Long, image: CustomImage) {
        viewModelScope.launch { repository.setCategoryImage(categoryId, image) }
    }

    fun importCategoryImage(categoryId: Long, uri: Uri) = importImage { repository.importCategoryImage(categoryId, uri) }

    /** Eigenes Bild für einen Musik-Eintrag, z. B. für Sonos-Playlisten ohne Cover. */
    fun setMusicItemImage(itemId: Long, image: CustomImage) {
        viewModelScope.launch { repository.setItemImage(itemId, image) }
    }

    fun importMusicItemImage(itemId: Long, uri: Uri) = importImage { repository.importItemImage(itemId, uri) }

    private fun importImage(import: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                import()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Eigenes Bild nicht übernommen (Einstellungen)", e)
                imageImportError = e.message ?: "Das Bild konnte nicht übernommen werden."
            }
        }
    }

    fun dismissImageImportError() {
        imageImportError = null
    }

    fun setCategoryPlayOrder(categoryId: Long, playOrder: PlayOrder) {
        viewModelScope.launch { repository.setCategoryPlayOrder(categoryId, playOrder) }
    }

    /** Verschiebt eine Kategorie in der Musikauswahl um [offset] Plätze (−1 = nach oben). */
    fun moveCategory(categoryId: Long, offset: Int) {
        val ids = categories.map { it.category.id }
        val from = ids.indexOf(categoryId)
        if (from < 0) return
        val to = (from + offset).coerceIn(0, ids.lastIndex)
        if (from == to) return
        val reordered = ids.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorderCategories(reordered) }
    }

    /** Neue Reihenfolge der Kategorien nach Drag & Drop. */
    fun reorderCategories(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderCategories(orderedIds) }
    }

    fun setCategoryItemSort(categoryId: Long, sort: ItemSort, descending: Boolean) {
        viewModelScope.launch { repository.setCategoryItemSort(categoryId, sort, descending) }
    }

    fun setCategoryCoverAnimation(categoryId: Long, animation: CoverAnimation) {
        viewModelScope.launch { repository.setCategoryCoverAnimation(categoryId, animation) }
    }

    /** Verschiebt einen Eintrag in der manuellen Reihenfolge seiner Kategorie. */
    fun moveMusicItem(itemId: Long, offset: Int) {
        val category = categories
            .firstOrNull { category -> category.items.any { it.id == itemId } } ?: return
        if (category.category.itemSortMode != ItemSort.MANUAL) return
        val items = category.items.map { it.id }
        val from = items.indexOf(itemId)
        val to = (from + offset).coerceIn(0, items.lastIndex)
        if (from == to) return
        val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorderItems(reordered) }
    }

    /** Neue manuelle Reihenfolge der Musik einer Kategorie nach Drag & Drop. */
    fun reorderMusicItems(categoryId: Long, orderedIds: List<Long>) {
        val category = categories.firstOrNull { it.category.id == categoryId } ?: return
        if (category.category.itemSortMode != ItemSort.MANUAL) return
        viewModelScope.launch { repository.reorderItems(orderedIds) }
    }

    /** Ob „Musik hinzufügen“ neue Einträge an den Anfang statt ans Ende setzt. */
    fun setAddMusicAtStart(atStart: Boolean) {
        viewModelScope.launch { repository.setAddMusicAtStart(atStart) }
    }

    fun removeMusicItem(itemId: Long) {
        viewModelScope.launch { repository.removeMusicItem(itemId) }
    }

    /**
     * Markiert [items] für die Profile ([ChildProfile.syncId]) wieder als neu bzw. als gespielt —
     * auch wenn es hier schon so aussieht, damit es auf allen Tablets gilt. Die anderen Tablets
     * erfahren es beim Schließen der Einstellungen.
     */
    fun setMusicNew(items: List<MusicItem>, profileSyncIds: List<String>, new: Boolean) {
        viewModelScope.launch { repository.setPlayed(profileSyncIds, items, played = !new) }
    }

    /** „Wieder neu“ unter „Zuletzt gespielt“: z. B. nach dem Ausprobieren durch die Eltern. */
    fun markNewAgain(play: RecentPlay) = setMusicNew(listOf(play.item), listOf(play.profile.syncId), new = true)

    /** Öffnet den Sonos-Katalog, um Musik zu einer Kategorie hinzuzufügen. */
    fun openCatalog(categoryId: Long) {
        catalogCategoryId = categoryId
        catalogAddedAtStart.clear()
        screen = Screen.MusicCatalog
        if (catalogState !is CatalogState.Loaded) loadCatalog()
    }

    fun closeCatalog() {
        catalogCategoryId = null
        playlistPreview = null
        if (screen == Screen.MusicCatalog) {
            screen = if (editingCategory != null) Screen.CategoryEditor else Screen.MusicLibrary
        }
    }

    /** Lädt Favoriten und Playlisten des Haushalts (auch über „Neu laden" im Katalog). */
    fun loadCatalog() {
        catalogJob?.cancel()
        if (tokenStore.accessToken == null) {
            catalogState = CatalogState.Error("Melde dich zuerst bei Sonos an, um den Musik-Katalog zu sehen.")
            return
        }
        catalogState = CatalogState.Loading
        catalogJob = viewModelScope.launch {
            catalogState = try {
                CatalogState.Loaded(fetchCatalog().also { repository.refreshMusicImages(it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                AppLog.w(TAG, "Musik-Katalog nicht geladen (Einstellungen)", e)
                CatalogState.Error(e.message ?: "Musik-Katalog konnte nicht geladen werden")
            }
        }
    }

    private suspend fun fetchCatalog(): List<CatalogEntry> {
        // Cover (und Künstler) direkt beim Speaker im Heimnetz holen — dieselben, die die Sonos-App zeigt.
        // Die Cloud liefert für Sonos-Playlisten nie eins und für manche Favoriten keins,
        // das sich laden lässt. Klappt das lokal nicht, bleibt es beim Cover aus der Cloud.
        // Abgelaufene Adressen (Apple Music: nur 24 h gültig) gar nicht erst versuchen.
        val (cloudEntries, local) = coroutineScope {
            val cloud = async {
                try {
                    Result.success(
                        coroutineScope {
                            val favorites = async { cloudControl.getFavorites() }
                            val playlists = async { cloudControl.getPlaylists() }
                            favorites.await().map(CatalogEntry::from) + playlists.await().map(CatalogEntry::from)
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            val local = async { localClient.loadCovers(addressHint = nowPlaying?.imageUrl) }
            cloud.await() to local.await()
        }
        // Antwortet die Cloud nicht, reicht auch der Katalog vom Speaker im Heimnetz
        val entries = cloudEntries.getOrElse { error ->
            local?.catalogEntries()?.takeIf { it.isNotEmpty() }
                ?.also {
                    AppLog.resolved(
                        TAG,
                        "Die Sonos-Cloud lieferte den Musik-Katalog nicht (${error.message}) — er kommt vom Speaker im Heimnetz"
                    )
                }
                ?: throw error
        }
        return entries.map { entry ->
            val localCovers = local?.coversFor(entry).orEmpty()
            val cloudCovers = imageUrlCandidates(entry.imageUrl)
            // Speaker-Cover zuerst, das aus der Cloud als Ersatz, falls es nicht lädt
            val all = (localCovers + cloudCovers).distinct()
            val usable = all.filterNot(::isExpiredUrl)
            val origin = listOfNotNull(
                "Speaker im Heimnetz".takeIf { localCovers.any { it in usable } },
                "Sonos-Cloud".takeIf { cloudCovers.any { it in usable } }
            ).joinToString(", sonst ").ifEmpty { null }
            val expired = all.size - usable.size
            // Noch gültige Apple-Music-Adressen sofort sichern — morgen sind sie abgelaufen
            val stored = repository.catalogCover(entry, freshSignedUrl = usable.firstOrNull(::isSignedUrl))
            entry.copy(
                imageUrl = joinImageUrls(listOfNotNull(stored) + usable),
                // Die Cloud nennt den Künstler selten, der Speaker kennt ihn oft (bei Playlisten aus den Titeln)
                artists = entry.artists.ifEmpty { local?.artistsFor(entry).orEmpty() },
                coverOrigin = listOfNotNull(
                    "auf dem Tablet gespeichert".takeIf { stored != null },
                    origin,
                    "$expired abgelaufene Adresse(n) übersprungen".takeIf { expired > 0 }
                ).joinToString(" · ").ifEmpty { null }
            )
        }
    }

    /** Cover der gespeicherten Musikauswahl still im Hintergrund auffrischen. */
    private fun refreshMusicImagesQuietly() {
        if (control == null) return
        if (categories.none { it.items.isNotEmpty() }) return
        viewModelScope.launch {
            try {
                repository.refreshMusicImages(fetchCatalog())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d(TAG, "Cover der Musikauswahl nicht aufgefrischt: ${e.message}")
            }
        }
    }

    /** Fügt einen Katalog-Eintrag zur offenen Kategorie hinzu bzw. nimmt ihn wieder heraus. */
    fun toggleCatalogEntry(entry: CatalogEntry) {
        val category = catalogCategory ?: return
        val present = category.items.any { it.catalogKey == entry.key }
        // Am Anfang: hinter das, was in diesem Katalog schon nach oben gesetzt wurde,
        // damit mehrere neue Einträge oben in der Reihenfolge des Antippens stehen
        val insertAt = if (settings.addMusicAtStart && !present) {
            val presentKeys = category.items.map { it.catalogKey }.toSet()
            catalogAddedAtStart.count { it in presentKeys }.also { catalogAddedAtStart += entry.key }
        } else {
            null
        }
        viewModelScope.launch {
            if (present) {
                repository.removeMusic(category.category.id, entry)
            } else {
                repository.addMusic(category.category.id, entry, insertAt)
            }
        }
    }

    fun showPlaylistPreview(entry: CatalogEntry) {
        playlistPreview = PlaylistPreview(entry)
        viewModelScope.launch {
            val preview = try {
                // Vom Speaker im Heimnetz, sonst aus der Cloud
                val tracks = localClient.playlistTracks(entry.sonosId, addressHint = nowPlaying?.imageUrl)
                    ?: cloudControl.getPlaylistTracks(entry.sonosId)
                PlaylistPreview(entry, tracks = tracks.map { PlaylistTrack(name = it.title, artist = it.artist, album = it.album) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                AppLog.w(TAG, "Titel der Playlist „${entry.name}“ nicht geladen (Einstellungen)", e)
                PlaylistPreview(entry, error = e.message ?: "Titel konnten nicht geladen werden")
            }
            // Inzwischen geschlossen oder eine andere Playlist geöffnet → verwerfen
            if (playlistPreview?.entry == entry) playlistPreview = preview
        }
    }

    fun dismissPlaylistPreview() {
        playlistPreview = null
    }

    fun savePassword(password: String) {
        viewModelScope.launch { repository.setPassword(password) }
    }

    fun removePassword() {
        viewModelScope.launch { repository.removePassword() }
    }

    fun setPasswordRequired(required: Boolean) {
        viewModelScope.launch { repository.setPasswordRequired(required) }
    }

    companion object {
        private const val TAG = "SoundBuddy"
        /** Abfrage-Takt über die Cloud — im WLAN geht es schneller und kostet nichts. */
        private const val POLL_INTERVAL_MS = 5_000L
        private const val LOCAL_POLL_INTERVAL_MS = 2_000L
        private const val COMMAND_REFRESH_DELAY_MS = 600L
        private const val LOCAL_COMMAND_REFRESH_DELAY_MS = 300L
        private const val VOLUME_DEBOUNCE_MS = 150L
        /** Neue Musik zu laden dauert bei Sonos etwas länger als ein Skip. */
        private const val MUSIC_REFRESH_DELAY_MS = 1_500L
        private const val LOCAL_MUSIC_REFRESH_DELAY_MS = 800L
        /** So viele Abfragen im WLAN dürfen hintereinander scheitern, bevor die App neu verbindet. */
        private const val LOCAL_FAILURES_BEFORE_RECONNECT = 3
        /** Erst so viele gescheiterte Abfragen in Folge zeigen eine Meldung, solange noch etwas zu sehen ist. */
        private const val POLL_FAILURES_BEFORE_ERROR = 2
        /** Steuert die App nur ersatzweise über die Cloud, versucht sie es so oft wieder im WLAN. */
        private const val LOCAL_RETRY_INTERVAL_MS = 60_000L
        /** So oft versucht die App einen Befehl, wenn Sonos die Gruppe als nicht mehr vorhanden meldet. */
        private const val GROUP_GONE_ATTEMPTS = 3
        private const val GROUP_GONE_RETRY_MS = 700L
        /** Bis die Gruppe nach dem Mitnehmen steht und die Musik am neuen Ort läuft. */
        private const val MOVE_REFRESH_DELAY_MS = 1_500L
        /** So lange spielt der bisherige Speaker nach dem Mitnehmen noch mit — Zeit zum Hinübergehen. */
        private const val MOVE_SOURCE_STOP_DELAY_MS = 15_000L
        private const val LEAVE_ATTEMPTS = 3
        private const val LEAVE_RETRY_DELAY_MS = 5_000L
        /** So viele Einträge zeigt „Zuletzt gespielt“ in den Einstellungen. */
        private const val RECENT_PLAYS_SHOWN = 5
        /** Playlist-Kategorien gleicht die App von selbst höchstens so oft mit Sonos ab. */
        private const val PLAYLIST_REFRESH_INTERVAL_MS = 30_000L
    }
}

class MainViewModelFactory(
    private val tokenStore: TokenStore,
    private val apiClient: SonosApiClient,
    private val repository: SettingsRepository,
    private val localClient: LocalSonosClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore, apiClient, repository, localClient) as T
    }
}
