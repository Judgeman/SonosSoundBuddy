package de.paul.sonoscontrol

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState {
    /** [reason] erklärt, warum neu angemeldet werden muss (z. B. Anmeldung abgelaufen). */
    data class LoggedOut(val reason: String? = null) : UiState
    data object LoadingSpeakers : UiState
    data class SpeakerList(val players: List<SonosPlayer>) : UiState
    data class Error(val message: String) : UiState
}

/**
 * [ProfileEditor], [MusicLibrary] (die zentrale Musikauswahl), [MusicCatalog] und [Sync]
 * sind Unterseiten der Settings.
 */
enum class Screen { Home, Settings, ProfileEditor, MusicLibrary, MusicCatalog, Sync }

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
    val fetchedAtMillis: Long
) {
    val isPlaying: Boolean get() = playbackState == PlaybackStatus.STATE_PLAYING

    /** Position inklusive der seit der letzten Abfrage vergangenen Zeit. */
    fun currentPositionMillis(now: Long = SystemClock.elapsedRealtime()): Long {
        val position = positionMillis + if (isPlaying) now - fetchedAtMillis else 0L
        return durationMillis?.let { position.coerceAtMost(it) } ?: position
    }
}

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

    var selectedProfileId: Long? by mutableStateOf(null)
        private set

    /** Profil, das gerade in den Settings bearbeitet wird. */
    var editingProfileId: Long? by mutableStateOf(null)
        private set

    /** Kategorie, für die gerade Musik aus dem Katalog ausgesucht wird. */
    var catalogCategoryId: Long? by mutableStateOf(null)
        private set

    var catalogState: CatalogState by mutableStateOf(CatalogState.Loading)
        private set

    var playlistPreview: PlaylistPreview? by mutableStateOf(null)
        private set

    /** Fehler beim Übernehmen eines eigenen Kategorie-Bilds, als Snackbar auf der Profil-Seite. */
    var imageImportError: String? by mutableStateOf(null)
        private set

    /** Wird gerade Musik aus der Auswahl gestartet? Zeigt auf dem Homescreen einen Ladekreis. */
    var startingMusic: MusicItem? by mutableStateOf(null)
        private set

    val isStartingMusic: Boolean get() = startingMusic != null

    var showPasswordPrompt: Boolean by mutableStateOf(false)
        private set

    var passwordWrong: Boolean by mutableStateOf(false)
        private set

    /** Ob die App gerade zu sehen ist — nur dann wird abgefragt und kommen Tiere zu Besuch. */
    var isInForeground: Boolean by mutableStateOf(false)
        private set

    private var householdId: String? = null
    private var settingsLoaded = false
    private var pollJob: Job? = null
    private var lastCommandAtMillis = 0L
    private var volumeJob: Job? = null
    private var profilesLoaded = false
    private var catalogJob: Job? = null

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

    val editingProfile: ProfileWithMusic?
        get() = profiles.firstOrNull { it.profile.id == editingProfileId }

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
            }
        }
        viewModelScope.launch {
            repository.speakerConfigs.collect {
                speakerConfigs = it
                updateSelection()
            }
        }
        if (uiState is UiState.LoadingSpeakers) loadSpeakers()
        viewModelScope.launch {
            runCatching { repository.deleteUnusedImages() }
                .onFailure { Log.w(TAG, "Aufräumen der Bilder fehlgeschlagen", it) }
        }
    }

    /** Wird aus MainActivity.handleIntent() mit der sonoscontrol://callback-Uri aufgerufen. */
    fun handleAuthCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        val state = uri.getQueryParameter("state")
        val accessToken = uri.getQueryParameter("access_token")
        val refreshToken = uri.getQueryParameter("refresh_token")

        if (!authManager.isValidState(state)) {
            uiState = UiState.Error("Ungültiger state-Parameter — Login abgebrochen (möglicher CSRF-Versuch).")
            return
        }
        if (error != null) {
            uiState = UiState.Error("Sonos-Login fehlgeschlagen: $error")
            return
        }
        if (accessToken.isNullOrBlank()) {
            uiState = UiState.Error("Kein Access-Token in der Antwort erhalten.")
            return
        }

        tokenStore.saveTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = uri.getQueryParameter("expires_in")?.toLongOrNull()
        )

        loadSpeakers()
    }

    fun loadSpeakers() {
        uiState = UiState.LoadingSpeakers
        viewModelScope.launch {
            uiState = try {
                UiState.SpeakerList(fetchAndSyncPlayers().players).also { refreshMusicImagesQuietly() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unbekannter Fehler beim Laden der Speaker")
            }
            updateSelection()
        }
    }

    private class SyncResult(val players: List<SonosPlayer>, val added: Int)

    private suspend fun fetchAndSyncPlayers(): SyncResult {
        val id = apiClient.getFirstHouseholdId()
        householdId = id
        val players = apiClient.getPlayers(id)
        return SyncResult(players, repository.syncPlayers(players))
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
        return if (missing > 0) "$found · $missing nicht erreichbar" else found
    }

    fun logout() = signOut(reason = null)

    /** Die Anmeldung ließ sich nicht erneuern → zurück zum Login, mit Hinweis warum. */
    private fun handleSessionExpired(e: SessionExpiredException) = signOut(reason = e.message)

    private fun signOut(reason: String?) {
        tokenStore.clear()
        stopPolling()
        volumeJob?.cancel()
        householdId = null
        selectedPlayerId = null
        nowPlaying = null
        clearPlaybackError()
        screen = Screen.Home
        editingProfileId = null
        catalogCategoryId = null
        uiState = UiState.LoggedOut(reason)
    }

    // --- Speaker-Auswahl -------------------------------------------------

    fun selectSpeaker(playerId: String) {
        if (playerId == selectedPlayerId) return
        selectedPlayerId = playerId
        viewModelScope.launch { repository.setLastSelectedPlayer(playerId) }
        restartPolling()
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
            if (pollJob == null) restartPolling()
            return
        }
        selectedPlayerId = newSelection
        if (newSelection != null && newSelection != settings.lastSelectedPlayerId) {
            viewModelScope.launch { repository.setLastSelectedPlayer(newSelection) }
        }
        restartPolling()
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
        isInForeground = foreground
        if (!foreground) {
            stopPolling()
            return
        }
        // Lag beim letzten Mal z. B. kein Netz an, beim Zurückkommen still neu versuchen
        if (uiState is UiState.Error && tokenStore.accessToken != null) loadSpeakers() else restartPolling()
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun restartPolling() {
        stopPolling()
        nowPlaying = null
        clearPlaybackError()
        val playerId = selectedPlayerId ?: return
        val household = householdId ?: return
        if (!isInForeground || screen != Screen.Home) return

        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val pollStartedAt = SystemClock.elapsedRealtime()
                    var fetched = fetchNowPlaying(household, playerId)
                    // Wurde die Lautstärke woanders (Sonos-App, Taste am Speaker) über das
                    // Maximum gestellt, wird sie wieder auf das Maximum begrenzt.
                    val max = maxVolumeFor(playerId)
                    val volume = fetched.volume
                    if (volume != null && volume > max) {
                        apiClient.setPlayerVolume(playerId, max)
                        fetched = fetched.copy(volume = max)
                    }
                    // Kurz nach einem Befehl kann Sonos noch den alten Stand liefern. Während Musik
                    // gestartet wird, gibt es Zwischenstände (z. B. erst Titel 1, dann der Zufallstitel) —
                    // die nicht zeigen. Und keine Abfrage übernehmen, die vor dem Ende eines Befehls begann.
                    val settled = SystemClock.elapsedRealtime() - lastCommandAtMillis > COMMAND_REFRESH_DELAY_MS
                    if (settled && !isStartingMusic && pollStartedAt > lastCommandAtMillis) {
                        nowPlaying = fetched
                    }
                    clearPlaybackError()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SessionExpiredException) {
                    handleSessionExpired(e)
                    return@launch
                } catch (e: Exception) {
                    reportPlaybackError(e.message ?: "Wiedergabe konnte nicht geladen werden")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** [userAction]: ein Knopfdruck ist fehlgeschlagen → Meldung immer (wieder) zeigen. */
    private fun reportPlaybackError(message: String, userAction: Boolean = false) {
        if (userAction || message != playbackError) playbackErrorDismissed = false
        playbackError = message
    }

    private fun clearPlaybackError() {
        playbackError = null
        playbackErrorDismissed = false
    }

    fun dismissPlaybackError() {
        playbackErrorDismissed = true
    }

    /** Die Wiedergabe hängt an der Gruppe, in der der Player gerade spielt. */
    private suspend fun findGroup(householdId: String, playerId: String): SonosGroup =
        apiClient.getGroups(householdId).groups
            .firstOrNull { playerId in it.playerIds }
            ?: throw SonosApiException("Speaker ist gerade nicht erreichbar")

    private suspend fun fetchNowPlaying(householdId: String, playerId: String): NowPlaying =
        coroutineScope {
            val group = findGroup(householdId, playerId)

            val status = async { apiClient.getPlaybackStatus(group.id) }
            val metadata = async { apiClient.getPlaybackMetadata(group.id) }
            val volume = async { runCatching { apiClient.getPlayerVolume(playerId) }.getOrNull() }

            val meta = metadata.await()
            val track = meta.currentItem?.track
            val playback = status.await()
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
                fetchedAtMillis = SystemClock.elapsedRealtime()
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
        sendPlaybackCommand { apiClient.togglePlayPause(current.groupId) }
    }

    fun skipToNext() {
        val current = nowPlaying ?: return
        sendPlaybackCommand { apiClient.skipToNextTrack(current.groupId) }
    }

    fun skipToPrevious() {
        val current = nowPlaying ?: return
        sendPlaybackCommand { apiClient.skipToPreviousTrack(current.groupId) }
    }

    /**
     * Setzt die Lautstärke des gewählten Speakers, begrenzt auf sein Maximum.
     * Beim Ziehen über die Leiste kommen viele Werte schnell hintereinander —
     * gesendet wird nur der letzte, die Anzeige folgt aber sofort.
     */
    fun setVolume(volume: Int) {
        val playerId = selectedPlayerId ?: return
        val current = nowPlaying ?: return
        val target = volume.coerceIn(0, maxVolumeFor(playerId))
        if (target == current.volume) return

        lastCommandAtMillis = SystemClock.elapsedRealtime()
        nowPlaying = current.copy(volume = target)
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            delay(VOLUME_DEBOUNCE_MS)
            try {
                apiClient.setPlayerVolume(playerId, target)
                lastCommandAtMillis = SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                reportPlaybackError(e.message ?: "Lautstärke konnte nicht geändert werden", userAction = true)
            }
        }
    }

    /**
     * Spielt einen Eintrag aus der Musikauswahl auf dem gewählten Speaker ab.
     * Die Warteschlange wird dabei ersetzt. [shuffle] = null heißt: so, wie es
     * in der Kategorie eingestellt ist (bei „Kinder entscheiden" kommt die Wahl
     * der Kinder als true/false herein).
     */
    fun playMusic(item: MusicItem, shuffle: Boolean? = null) {
        val playerId = selectedPlayerId ?: return
        val household = householdId ?: return
        val order = categories
            .firstOrNull { it.category.id == item.categoryId }?.category?.playOrderMode ?: PlayOrder.ORDERED
        val useShuffle = shuffle ?: (order == PlayOrder.SHUFFLE)
        startingMusic = item
        sendPlaybackCommand(
            refreshDelayMillis = MUSIC_REFRESH_DELAY_MS,
            onFinished = { startingMusic = null },
            // Ist das Cover noch nicht auf dem Tablet gespeichert (z. B. abgelaufene Apple-Music-
            // Adresse), das frische aus der Wiedergabe nehmen — für Playlisten das der Playlist
            onPlaying = { playing ->
                viewModelScope.launch {
                    repository.storeCoverFromPlayback(item.id, playing.containerImageUrl, playing.imageUrl)
                }
            }
        ) {
            val group = findGroup(household, playerId)
            val type = item.musicType
            // Radio hat keine Warteschlange — dort gibt es keine Reihenfolge, also direkt starten
            val controlsOrder = type != MusicType.RADIO
            when (item.musicSource) {
                MusicSource.FAVORITE ->
                    apiClient.loadFavorite(group.id, resolveFavoriteId(household, item), play = !controlsOrder)
                MusicSource.PLAYLIST ->
                    apiClient.loadPlaylist(group.id, resolvePlaylistId(household, item), play = !controlsOrder)
            }
            if (controlsOrder) {
                // Zufall muss auch ausdrücklich AUS geschaltet werden, sonst bleibt er vom letzten Mal an.
                // Klappt das nicht (manche Quellen erlauben es nicht), trotzdem abspielen.
                val shuffled = useShuffle && item.hasMultipleTracks &&
                    runCatching { apiClient.setShuffle(group.id, true) }.isSuccess
                if (!shuffled) runCatching { apiClient.setShuffle(group.id, false) }
                // Ohne Sprung würde auch im Zufallsmodus immer der erste Titel zuerst laufen
                if (shuffled) runCatching { apiClient.skipToNextTrack(group.id) }
                apiClient.play(group.id)
            }
        }
    }

    /**
     * Sonos vergibt die Ids von Favoriten und Playlisten selbst. Wurde dort etwas
     * gelöscht oder neu angelegt, kann die gespeicherte Id inzwischen zu einem
     * anderen Eintrag gehören — dann wird über den Namen gesucht.
     */
    private suspend fun resolveFavoriteId(household: String, item: MusicItem): String {
        val favorites = apiClient.getFavorites(household)
        return favorites.firstOrNull { it.id == item.sonosId && it.name == item.name }?.id
            ?: favorites.firstOrNull { it.name == item.name }?.id
            ?: throw SonosApiException("„${item.name}“ gibt es nicht mehr in den Sonos-Favoriten.")
    }

    private suspend fun resolvePlaylistId(household: String, item: MusicItem): String {
        val playlists = apiClient.getPlaylists(household)
        return playlists.firstOrNull { it.id == item.sonosId && it.name == item.name }?.id
            ?: playlists.firstOrNull { it.name == item.name }?.id
            ?: throw SonosApiException("Die Playlist „${item.name}“ gibt es nicht mehr bei Sonos.")
    }

    private fun sendPlaybackCommand(
        refreshDelayMillis: Long = COMMAND_REFRESH_DELAY_MS,
        onFinished: () -> Unit = {},
        onPlaying: (NowPlaying) -> Unit = {},
        command: suspend () -> Unit
    ) {
        val playerId = selectedPlayerId ?: return onFinished()
        val household = householdId ?: return onFinished()
        lastCommandAtMillis = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            try {
                command()
                lastCommandAtMillis = SystemClock.elapsedRealtime()
                // Sonos braucht einen Moment, bis Track und Status aktualisiert sind
                delay(refreshDelayMillis)
                if (selectedPlayerId == playerId) {
                    nowPlaying = fetchNowPlaying(household, playerId).also(onPlaying)
                    // Abfragen, die noch während des Befehls begonnen haben, sind damit überholt
                    lastCommandAtMillis = SystemClock.elapsedRealtime()
                    clearPlaybackError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
            } catch (e: Exception) {
                reportPlaybackError(e.message ?: "Befehl konnte nicht gesendet werden", userAction = true)
            } finally {
                onFinished()
            }
        }
    }

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
            // Zurück dorthin, von wo die Musikauswahl geöffnet wurde
            Screen.MusicLibrary -> screen = if (editingProfile != null) Screen.ProfileEditor else Screen.Settings
            Screen.ProfileEditor -> {
                screen = Screen.Settings
                editingProfileId = null
            }
            Screen.Sync -> screen = Screen.Settings
            Screen.Settings -> closeSettings()
            Screen.Home -> Unit
        }
    }

    fun closeSettings() {
        screen = Screen.Home
        editingProfileId = null
        catalogCategoryId = null
        speakerRefreshMessage = null
        // Neue Speaker wurden in den Settings gesehen → beim nächsten Mal nicht mehr „Neu"
        viewModelScope.launch { repository.clearNewFlags() }
        restartPolling()
    }

    /** Öffnet „Tablets abgleichen“. */
    fun openSync() {
        screen = Screen.Sync
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

    fun setCategoryItemSort(categoryId: Long, sort: ItemSort, descending: Boolean) {
        viewModelScope.launch { repository.setCategoryItemSort(categoryId, sort, descending) }
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

    fun removeMusicItem(itemId: Long) {
        viewModelScope.launch { repository.removeMusicItem(itemId) }
    }

    /** Öffnet den Sonos-Katalog, um Musik zu einer Kategorie hinzuzufügen. */
    fun openCatalog(categoryId: Long) {
        catalogCategoryId = categoryId
        screen = Screen.MusicCatalog
        if (catalogState !is CatalogState.Loaded) loadCatalog()
    }

    fun closeCatalog() {
        catalogCategoryId = null
        playlistPreview = null
        if (screen == Screen.MusicCatalog) screen = Screen.MusicLibrary
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
                val household = householdId ?: apiClient.getFirstHouseholdId().also { householdId = it }
                CatalogState.Loaded(fetchCatalog(household).also { repository.refreshMusicImages(it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
                CatalogState.Error(e.message ?: "Musik-Katalog konnte nicht geladen werden")
            }
        }
    }

    private suspend fun fetchCatalog(household: String): List<CatalogEntry> {
        val entries = coroutineScope {
            val favorites = async { apiClient.getFavorites(household) }
            val playlists = async { apiClient.getPlaylists(household) }
            favorites.await().map(CatalogEntry::from) + playlists.await().map(CatalogEntry::from)
        }
        // Cover direkt beim Speaker im Heimnetz holen — dieselben, die die Sonos-App zeigt.
        // Die Cloud liefert für Sonos-Playlisten nie eins und für manche Favoriten keins,
        // das sich laden lässt. Klappt das lokal nicht, bleibt es beim Cover aus der Cloud.
        // Abgelaufene Adressen (Apple Music: nur 24 h gültig) gar nicht erst versuchen.
        val local = localClient.loadCovers(addressHint = nowPlaying?.imageUrl)
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
        val household = householdId ?: return
        if (categories.none { it.items.isNotEmpty() }) return
        viewModelScope.launch {
            try {
                repository.refreshMusicImages(fetchCatalog(household))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Cover der Musikauswahl nicht aufgefrischt: ${e.message}")
            }
        }
    }

    /** Fügt einen Katalog-Eintrag zur offenen Kategorie hinzu bzw. nimmt ihn wieder heraus. */
    fun toggleCatalogEntry(entry: CatalogEntry) {
        val category = catalogCategory ?: return
        val present = category.items.any { it.catalogKey == entry.key }
        viewModelScope.launch {
            if (present) {
                repository.removeMusic(category.category.id, entry)
            } else {
                repository.addMusic(category.category.id, entry)
            }
        }
    }

    fun showPlaylistPreview(entry: CatalogEntry) {
        playlistPreview = PlaylistPreview(entry)
        viewModelScope.launch {
            val preview = try {
                val household = householdId ?: apiClient.getFirstHouseholdId().also { householdId = it }
                PlaylistPreview(entry, tracks = apiClient.getPlaylist(household, entry.sonosId).tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                handleSessionExpired(e)
                return@launch
            } catch (e: Exception) {
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
        private const val POLL_INTERVAL_MS = 5_000L
        private const val COMMAND_REFRESH_DELAY_MS = 600L
        private const val VOLUME_DEBOUNCE_MS = 150L
        /** Neue Musik zu laden dauert bei Sonos etwas länger als ein Skip. */
        private const val MUSIC_REFRESH_DELAY_MS = 1_500L
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
