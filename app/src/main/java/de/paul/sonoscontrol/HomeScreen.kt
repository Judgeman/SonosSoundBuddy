package de.paul.sonoscontrol

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    speakers: List<SpeakerConfig>,
    selectedSpeaker: SpeakerConfig?,
    nowPlaying: NowPlaying?,
    maxVolume: Int,
    playbackError: String?,
    onSelectSpeaker: (String) -> Unit,
    controls: PlaybackControls,
    onOpenSettings: () -> Unit,
    onLoginClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val showsPlayback = state is UiState.SpeakerList && selectedSpeaker != null
    val coverColors = rememberCoverColors(if (showsPlayback) nowPlaying?.imageUrl else null)

    CoverTheme(colors = if (showsPlayback) coverColors else null) { background ->
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(background),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text("Sound Buddy") },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Menü")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Einstellungen") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is UiState.LoggedOut -> LoggedOutContent(onLoginClick)
                    is UiState.LoadingSpeakers -> CircularProgressIndicator()
                    is UiState.Error -> ErrorContent(state.message, onRetryClick)
                    is UiState.SpeakerList -> SpeakerHomeContent(
                        speakers = speakers,
                        selectedSpeaker = selectedSpeaker,
                        nowPlaying = nowPlaying,
                        maxVolume = maxVolume,
                        playbackError = playbackError,
                        onSelectSpeaker = onSelectSpeaker,
                        controls = controls
                    )
                }
            }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Text("Fehler: $message", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetryClick) { Text("Erneut versuchen") }
    }
}

@Composable
private fun SpeakerHomeContent(
    speakers: List<SpeakerConfig>,
    selectedSpeaker: SpeakerConfig?,
    nowPlaying: NowPlaying?,
    maxVolume: Int,
    playbackError: String?,
    onSelectSpeaker: (String) -> Unit,
    controls: PlaybackControls
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (speakers.isEmpty()) {
            HintText("Es sind noch keine Speaker freigegeben.\nÖffne die Einstellungen über das Menü oben rechts.")
            return@Column
        }

        SpeakerDropdown(
            speakers = speakers,
            selectedSpeaker = selectedSpeaker,
            onSelectSpeaker = onSelectSpeaker
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            selectedSpeaker == null -> HintText("Wähle oben einen Speaker aus.")
            nowPlaying != null -> NowPlayingContent(
                nowPlaying = nowPlaying,
                maxVolume = maxVolume,
                playbackError = playbackError,
                controls = controls,
                modifier = Modifier.weight(1f)
            )
            playbackError != null -> HintText(playbackError)
            else -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(32.dp)
    )
}

/** Großes Dropdown im oberen Bereich: zeigt den gewählten Speaker mit seinem Icon. */
@Composable
private fun SpeakerDropdown(
    speakers: List<SpeakerConfig>,
    selectedSpeaker: SpeakerConfig?,
    onSelectSpeaker: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val canChoose = speakers.size > 1

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { anchorWidthPx = it.width }
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = canChoose) { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (selectedSpeaker != null) {
                    SpeakerIconBadge(selectedSpeaker.icon, size = 56.dp)
                } else {
                    SpeakerIconBadge(SpeakerIcon.SPEAKER, size = 56.dp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = selectedSpeaker?.name ?: "Speaker wählen",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (canChoose) {
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = "Speaker auswählen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { anchorWidthPx.toDp() })
        ) {
            speakers.forEach { speaker ->
                DropdownMenuItem(
                    text = {
                        Text(speaker.name, style = MaterialTheme.typography.titleLarge)
                    },
                    leadingIcon = { SpeakerIconBadge(speaker.icon, size = 44.dp) },
                    trailingIcon = {
                        if (speaker.playerId == selectedSpeaker?.playerId) {
                            Icon(Icons.Rounded.Check, contentDescription = "Ausgewählt")
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectSpeaker(speaker.playerId)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** Steuer-Callbacks für die Wiedergabe-Knöpfe. */
class PlaybackControls(
    val onTogglePlayPause: () -> Unit,
    val onSkipToPrevious: () -> Unit,
    val onSkipToNext: () -> Unit,
    val onVolumeChange: (Int) -> Unit
)

/**
 * Füllt den restlichen Bildschirm: Das Cover bekommt den gesamten Platz, der
 * nach Titel, Fortschritt und Knöpfen übrig bleibt, und bleibt dabei quadratisch.
 */
@Composable
private fun NowPlayingContent(
    nowPlaying: NowPlaying,
    maxVolume: Int,
    playbackError: String?,
    controls: PlaybackControls,
    modifier: Modifier = Modifier
) {
    // Zwischen den Abfragen läuft die Position lokal weiter, damit der Balken flüssig bleibt.
    val positionMillis by produceState(nowPlaying.currentPositionMillis(), nowPlaying) {
        while (true) {
            value = nowPlaying.currentPositionMillis()
            delay(500)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                    CoverImage(nowPlaying.imageUrl, modifier = Modifier.fillMaxSize())
                    PlaybackStateBadge(
                        playbackState = nowPlaying.playbackState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
            nowPlaying.volume?.let { volume ->
                Spacer(modifier = Modifier.width(12.dp))
                VolumeBar(
                    volume = volume,
                    maxVolume = maxVolume,
                    onVolumeChange = controls.onVolumeChange,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = nowPlaying.title ?: "Gerade läuft nichts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        nowPlaying.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        val duration = nowPlaying.durationMillis
        LinearProgressIndicator(
            progress = {
                if (duration == null) 0f else (positionMillis.toFloat() / duration).coerceIn(0f, 1f)
            },
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatDuration(positionMillis), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.weight(1f))
            nowPlaying.volume?.let { volume ->
                VolumeLabel(volume = volume, muted = nowPlaying.muted)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = duration?.let(::formatDuration) ?: "Live",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        PlaybackButtons(nowPlaying = nowPlaying, controls = controls)

        if (playbackError != null) {
            Text(
                text = playbackError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VolumeLabel(volume: Int, muted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Lautstärke",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$volume %",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Große, kinderfreundliche Knöpfe: vorheriger Track, Play/Pause, nächster Track. */
@Composable
private fun PlaybackButtons(nowPlaying: NowPlaying, controls: PlaybackControls) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalIconButton(
            onClick = controls.onSkipToPrevious,
            enabled = nowPlaying.canSkipBack,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Vorheriger Track", modifier = Modifier.size(40.dp))
        }
        FilledIconButton(
            onClick = controls.onTogglePlayPause,
            modifier = Modifier.size(88.dp)
        ) {
            Icon(
                imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Abspielen",
                modifier = Modifier.size(56.dp)
            )
        }
        FilledTonalIconButton(
            onClick = controls.onSkipToNext,
            enabled = nowPlaying.canSkip,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Nächster Track", modifier = Modifier.size(40.dp))
        }
    }
}

private val CoverShape = RoundedCornerShape(28.dp)

@Composable
private fun CoverImage(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(elevation = 24.dp, shape = CoverShape)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(120.dp)
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                onError = { Log.w("Cover", "Cover konnte nicht geladen werden: $imageUrl", it.result.throwable) },
                modifier = Modifier.fillMaxSize().clip(CoverShape)
            )
        }
    }
}

@Composable
private fun PlaybackStateBadge(playbackState: String?, modifier: Modifier = Modifier) {
    val (icon: ImageVector, label: String) = when (playbackState) {
        PlaybackStatus.STATE_PLAYING -> Icons.Rounded.PlayArrow to "Läuft"
        PlaybackStatus.STATE_PAUSED -> Icons.Rounded.Pause to "Pausiert"
        PlaybackStatus.STATE_BUFFERING -> Icons.Rounded.HourglassTop to "Lädt …"
        else -> Icons.Rounded.Stop to "Gestoppt"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
