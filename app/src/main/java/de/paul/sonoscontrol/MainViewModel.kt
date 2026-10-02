package de.paul.sonoscontrol

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface UiState {
    data object LoggedOut : UiState
    data object LoadingSpeakers : UiState
    data class SpeakerList(val players: List<SonosPlayer>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    val authManager = SonosAuthManager()
    private val apiClient = SonosApiClient { tokenStore.accessToken }

    var uiState: UiState by mutableStateOf(
        if (tokenStore.accessToken != null) UiState.LoadingSpeakers else UiState.LoggedOut
    )
        private set

    init {
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
                val householdId = apiClient.getFirstHouseholdId()
                val players = apiClient.getPlayers(householdId)
                UiState.SpeakerList(players)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unbekannter Fehler beim Laden der Speaker")
            }
        }
    }

    fun logout() {
        tokenStore.clear()
        uiState = UiState.LoggedOut
    }
}

class MainViewModelFactory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(tokenStore) as T
    }
}
