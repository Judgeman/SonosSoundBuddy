package de.paul.sonoscontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(TokenStore(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SonosScreen(
                        state = viewModel.uiState,
                        onLoginClick = { viewModel.authManager.startLogin(this) },
                        onRetryClick = { viewModel.loadSpeakers() },
                        onLogoutClick = { viewModel.logout() }
                    )
                }
            }
        }
    }

    // launchMode="singleTask" -> der Redirect landet hier, nicht in onCreate() einer neuen Instanz
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == SonosConfig.APP_CALLBACK_SCHEME && uri.host == SonosConfig.APP_CALLBACK_HOST) {
            viewModel.handleAuthCallback(uri)
        }
    }
}

@Composable
fun SonosScreen(
    state: UiState,
    onLoginClick: () -> Unit,
    onRetryClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is UiState.LoggedOut -> LoggedOutContent(onLoginClick)
            is UiState.LoadingSpeakers -> CircularProgressIndicator()
            is UiState.Error -> ErrorContent(state.message, onRetryClick)
            is UiState.SpeakerList -> SpeakerListContent(state.players, onLogoutClick)
        }
    }
}

@Composable
private fun LoggedOutContent(onLoginClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nicht mit Sonos verbunden")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLoginClick) {
            Text("Mit Sonos anmelden")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Fehler: $message")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetryClick) { Text("Erneut versuchen") }
    }
}

@Composable
private fun SpeakerListContent(players: List<SonosPlayer>, onLogoutClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Deine Sonos-Speaker", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (players.isEmpty()) {
            Text("Keine Speaker gefunden.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(players) { player ->
                    ListItem(
                        headlineContent = { Text(player.name) },
                        supportingContent = { Text(player.icon ?: "Sonos-Speaker") }
                    )
                    HorizontalDivider()
                }
            }
        }

        TextButton(onClick = onLogoutClick) { Text("Abmelden") }
    }
}
