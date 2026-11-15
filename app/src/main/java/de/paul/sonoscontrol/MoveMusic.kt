package de.paul.sonoscontrol

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * „Musik mitnehmen“: Die Kinder hören im Kinderzimmer, gehen ins Bad zum Zähneputzen und nehmen
 * die Musik mit — ein Knopf neben dem Speaker, ein Popup mit den anderen Räumen als große Kacheln,
 * und solange die Musik umzieht, hüpfen Noten vom einen Raum in den anderen.
 */

private val MoveBadgeColor = Color(0xFFFF8A3D)
private val MoveNoteColors = listOf(Color(0xFFFF8A3D), Color(0xFF00A88F), Color(0xFFE91E63))
private const val MoveNoteMillis = 1500
private val RoomBadgeSize = 88.dp
private val MoveNoteSize = 32.dp

/**
 * Knopf neben dem Speaker-Dropdown, gleich hoch wie dieses: eine Note mit Pfeil — „die Musik kommt
 * mit“. Ausgegraut, solange nichts geladen ist, was mitkommen könnte.
 */
@Composable
fun MoveMusicButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        MoveMusicBadge(size = 56.dp, modifier = Modifier.padding(14.dp))
    }
}

/** Bunter Kreis mit Note und Pfeil. */
@Composable
private fun MoveMusicBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MoveBadgeColor, CircleShape)
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = "Musik mitnehmen",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -size * 0.1f, y = -size * 0.06f)
                .size(size * 0.56f)
        )
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = size * 0.1f, bottom = size * 0.12f)
                .size(size * 0.36f)
        )
    }
}

/**
 * Großes Popup „Wo möchtest du weiterhören?“: alle anderen Speaker des Homescreens als Kacheln mit
 * ihrem Icon — so finden auch Kinder, die noch nicht lesen, das Bad an der Badewanne.
 */
@Composable
fun MoveMusicDialog(
    from: SpeakerConfig,
    targets: List<SpeakerConfig>,
    onMove: (playerId: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                ) {
                    SpeakerIconBadge(from.icon, size = 64.dp)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(36.dp)
                    )
                    Text(
                        "Wo möchtest du weiterhören?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Schließen", modifier = Modifier.size(40.dp))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    items(targets, key = { it.playerId }) { speaker ->
                        RoomTile(speaker = speaker, onClick = { onMove(speaker.playerId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomTile(speaker: SpeakerConfig, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp)
        ) {
            SpeakerIconBadge(speaker.icon, size = 96.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                speaker.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Popup, solange die Musik umzieht: zeigt, von wo nach wo, und fängt alle Berührungen (und die
 * Zurück-Taste) ab — wie beim Starten von Musik, damit niemand dazwischen tippt.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MovingMusicOverlay(moving: MovingMusic?) {
    // Letzten Umzug merken, damit beim Ausblenden noch etwas zu sehen ist
    var lastMoving by remember { mutableStateOf<MovingMusic?>(null) }
    if (moving != null) lastMoving = moving

    if (moving != null) BackHandler {}

    AnimatedVisibility(visible = moving != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp)
        ) {
            val shown = lastMoving ?: return@Box
            // Normales App-Theme: in den Cover-Farben wäre die Schrift auf der Karte weiß auf hell
            SoundBuddyTheme {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .animateEnterExit(enter = scaleIn(initialScale = 0.8f), exit = scaleOut(targetScale = 0.8f))
                ) {
                    MovingMusicAnimation(shown, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

/** Links der bisherige Raum, rechts der neue, dazwischen hüpfen bunte Noten hinüber. */
@Composable
private fun MovingMusicAnimation(moving: MovingMusic, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "movingMusic")
    val travel = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MoveNoteMillis, easing = LinearEasing)),
        label = "travel"
    )
    val bob = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MoveNoteMillis / 3), RepeatMode.Reverse),
        label = "bob"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            "Die Musik kommt mit!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            RoomBadge(moving.from)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(RoomBadgeSize)
            ) {
                val trackWidth = (maxWidth - MoveNoteSize).coerceAtLeast(0.dp)
                MoveNoteColors.forEachIndexed { index, color ->
                    // Jede Note ist um ein Drittel versetzt unterwegs, auf einem Bogen von links nach rechts
                    val phase = index / MoveNoteColors.size.toFloat()
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset {
                                val f = (travel.value + phase) % 1f
                                IntOffset(
                                    x = (trackWidth.toPx() * f).roundToInt(),
                                    y = (-sin(f * PI).toFloat() * (RoomBadgeSize / 3).toPx()).roundToInt()
                                )
                            }
                            .graphicsLayer {
                                val f = (travel.value + phase) % 1f
                                alpha = sin(f * PI).toFloat().coerceIn(0f, 1f)
                            }
                            .size(MoveNoteSize)
                    )
                }
            }
            // Der neue Raum wippt schon vor Vorfreude
            RoomBadge(
                moving.to,
                modifier = Modifier.graphicsLayer { translationY = bob.value * 4.dp.toPx() }
            )
        }
    }
}

@Composable
private fun RoomBadge(speaker: SpeakerConfig, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 130.dp)
    ) {
        SpeakerIconBadge(speaker.icon, size = RoomBadgeSize)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            speaker.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
