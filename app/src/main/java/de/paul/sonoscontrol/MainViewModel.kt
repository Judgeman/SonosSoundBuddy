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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState {
    data object LoggedOut : UiState
    data object LoadingSpeakers : UiState
    data class SpeakerList(val players: List<SonosPlayer>) : UiState
    data class Error(val message: String) : UiState
}

enum class Screen { Home, Settings }

/** Aktuelle Wiedergabe des ausgewählten Speakers, wie sie der Homescreen anzeigt. */
data class NowPlaying(
    val groupId: String,
    val title: String?,
    val subtitle: String?,
    val imageUrl: String?,
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
    private val repository: SettingsRepository
) : ViewModel() {

    val authManager = SonosAuthManager()
    private val apiClient = SonosApiClient { tokenStore.accessToken }

    var uiState: UiState by mutableStateOf(
        if (tokenStore.accessToken != null) UiState.LoadingSpeakers else UiState.LoggedOut
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

    var showPasswordPrompt: Boolean by mutableStateOf(false)
        private set

    var passwordWrong: Boolean by mutableStateOf(false)
        private set

    private var householdId: String? = null
    private var settingsLoaded = false
    private var isInForeground = false
    private var pollJob: Job? = null
    private var lastCommandAtMillis = 0L
    private var volumeJob: Job? = null

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

    private fun maxVolumeFor(playerId: String): Int =
        speakerConfigs.firstOrNull { it.playerId == playerId }?.maxVolume ?: 100

    init {
        viewModelScope.launch {
            repository.settings.collect {
                settings = it
                settingsLoaded = true
                updateSelection()
            }
        }
        viewModelScope.launch {
            repository.speakerConfigs.collect {
                speakerConfigs = it
                updateSelection()
            }
        }
        if (uiState is UiState.LoadingSpeakers) loadSpeakers()
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

        tokenStore.accessToken = accessToken
        tokenStore.refreshToken = refreshToken

        loadSpeakers()
    }

    fun loadSpeakers() {
        uiState = UiState.LoadingSpeakers
        viewModelScope.launch {
            uiState = try {
                val id = apiClient.getFirstHouseholdId()
                householdId = id
                val players = apiClient.getPlayers(id)
                repository.syncPlayers(players)
                UiState.SpeakerList(players)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unbekannter Fehler beim Laden der Speaker")
            }
            updateSelection()
        }
    }

    fun logout() {
        tokenStore.clear()
        stopPolling()
        householdId = null
        selectedPlayerId = null
        screen = Screen.Home
        uiState = UiState.LoggedOut
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

    // --- Wiedergabe ------------------------------------------------------

    fun onForegroundChanged(foreground: Boolean) {
        isInForeground = foreground
        if (foreground) restartPolling() else stopPolling()
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun restartPolling() {
        stopPolling()
        nowPlaying = null
        playbackError = null
        val playerId = selectedPlayerId ?: return
        val household = householdId ?: return
        if (!isInForeground || screen != Screen.Home) return

        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    var fetched = fetchNowPlaying(household, playerId)
                    // Wurde die Lautstärke woanders (Sonos-App, Taste am Speaker) über das
                    // Maximum gestellt, wird sie wieder auf das Maximum begrenzt.
                    val max = maxVolumeFor(playerId)
                    val volume = fetched.volume
                    if (volume != null && volume > max) {
                        apiClient.setPlayerVolume(playerId, max)
                        fetched = fetched.copy(volume = max)
                    }
                    // Kurz nach einem Befehl kann Sonos noch den alten Stand liefern
                    if (SystemClock.elapsedRealtime() - lastCommandAtMillis > COMMAND_REFRESH_DELAY_MS) {
                        nowPlaying = fetched
                    }
                    playbackError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    playbackError = e.message ?: "Wiedergabe konnte nicht geladen werden"
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchNowPlaying(householdId: String, playerId: String): NowPlaying =
        coroutineScope {
            // Die Wiedergabe hängt an der Gruppe, in der der Player gerade spielt.
            val group = apiClient.getGroups(householdId).groups
                .firstOrNull { playerId in it.playerIds }
                ?: throw SonosApiException("Speaker ist gerade nicht erreichbar")

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
            } catch (e: Exception) {
                playbackError = e.message ?: "Lautstärke konnte nicht geändert werden"
            }
        }
    }

    private fun sendPlaybackCommand(command: suspend () -> Unit) {
        val playerId = selectedPlayerId ?: return
        val household = householdId ?: return
        lastCommandAtMillis = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            try {
                command()
                lastCommandAtMillis = SystemClock.elapsedRealtime()
                // Sonos braucht einen Moment, bis Track und Status aktualisiert sind
                delay(COMMAND_REFRESH_DELAY_MS)
                if (selectedPlayerId == playerId) {
                    nowPlaying = fetchNowPlaying(household, playerId)
                    playbackError = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                playbackError = e.message ?: "Befehl konnte nicht gesendet werden"
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

    fun closeSettings() {
        screen = Screen.Home
        restartPolling()
    }

    fun setSpeakerEnabled(playerId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setSpeakerEnabled(playerId, enabled) }
    }

    fun setSpeakerMaxVolume(playerId: String, maxVolume: Int) {
        viewModelScope.launch { repository.setSpeakerMaxVolume(playerId, maxVolume) }
    }

    fun setSpeakerIcon(playerId: String, icon: SpeakerIcon) {
        viewModelScope.launch { repository.setSpeakerIcon(playerId, icon) }
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
        private const val POLL_INTERVAL_MS = 5_000L
        private const val COMMAND_REFRESH_DELAY_MS = 600L
        private const val VOLUME_DEBOUNCE_MS = 150L
    }
}

class MainViewModelFactory(
    private val tokenStore: TokenStore,
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore, repository) as T
    }
}
