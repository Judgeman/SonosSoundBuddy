package de.paul.sonoscontrol

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Anzahl der Stufen in der Leiste — wenige, große Felder sind für Kinder leichter zu treffen. */
private const val VOLUME_STEPS = 10

/**
 * Senkrechte Lautstärke-Leiste in Stufen: Tippen auf eine Stufe oder Ziehen
 * über die Leiste setzt die Lautstärke, + und − gehen eine Stufe weiter.
 * Die oberste Stufe entspricht der in den Settings festgelegten Maximal-Lautstärke.
 */
@Composable
fun VolumeBar(
    volume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = maxVolume > 0
    val level = if (enabled) {
        (volume.toFloat() / maxVolume * VOLUME_STEPS).roundToInt().coerceIn(0, VOLUME_STEPS)
    } else {
        0
    }
    fun volumeFor(step: Int): Int = (maxVolume * step.toFloat() / VOLUME_STEPS).roundToInt()

    val currentOnChange by rememberUpdatedState(onVolumeChange)
    val currentMax by rememberUpdatedState(maxVolume)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(72.dp)
            .semantics { contentDescription = "Lautstärke $volume Prozent" }
    ) {
        FilledTonalIconButton(
            onClick = { onVolumeChange(volumeFor((level + 1).coerceAtMost(VOLUME_STEPS))) },
            enabled = enabled && level < VOLUME_STEPS,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Lauter", modifier = Modifier.size(36.dp))
        }

        // Stufen von oben (laut) nach unten (leise); nach oben werden sie breiter
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        currentOnChange(stepVolume(offset.y, size.height.toFloat(), currentMax))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        currentOnChange(stepVolume(change.position.y, size.height.toFloat(), currentMax))
                    }
                }
        ) {
            for (step in VOLUME_STEPS downTo 1) {
                val filled = step <= level
                val color by animateColorAsState(
                    targetValue = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                    },
                    label = "volumeStep"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(0.5f + 0.5f * step / VOLUME_STEPS)
                        .background(color, RoundedCornerShape(10.dp))
                )
            }
        }

        FilledTonalIconButton(
            onClick = { onVolumeChange(volumeFor((level - 1).coerceAtLeast(0))) },
            enabled = enabled && level > 0,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Leiser", modifier = Modifier.size(36.dp))
        }
    }
}

/** Rechnet eine Touch-Position (y von oben) in die Lautstärke der getroffenen Stufe um. */
private fun stepVolume(y: Float, height: Float, maxVolume: Int): Int {
    if (height <= 0f) return 0
    val step = ceil((height - y) / height * VOLUME_STEPS).toInt().coerceIn(0, VOLUME_STEPS)
    return (maxVolume * step.toFloat() / VOLUME_STEPS).roundToInt()
}
