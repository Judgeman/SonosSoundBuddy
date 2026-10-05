package de.paul.sonoscontrol

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Dauer, die eine Note vom Cover bis in den Lautsprecher braucht. */
private const val NoteTravelMillis = 1800
private const val NoteCount = 3

private val NoteColors = listOf(Color(0xFFFF8A3D), Color(0xFF00A88F), Color(0xFFE91E63))

// Maße der Szene (in dp): links unten das Cover, rechts der Lautsprecher
private const val SceneWidth = 300f
private const val SceneHeight = 200f
private const val CoverSize = 96f
private const val CoverTop = SceneHeight - CoverSize - 4f
private const val SpeakerLeft = 150f
private const val SpeakerTop = 24f
private const val SpeakerWidth = 110f
private const val SpeakerHeight = SceneHeight - SpeakerTop
private const val NoteSize = 36f

// Flugbahn der Noten (quadratische Bézier-Kurve): vom Cover hoch und in den Lautsprecher
private val NoteStart = Offset(CoverSize * 0.7f, CoverTop - 6f)
private val NoteControl = Offset(130f, -30f)
private val NoteEnd = Offset(SpeakerLeft + SpeakerWidth / 2f, SpeakerTop + SpeakerHeight * 0.62f)

/**
 * Kleine Animation, solange Musik gestartet wird: Aus dem (kleinen) Cover
 * hüpfen Noten hinüber in einen freundlichen Lautsprecher, der bei jeder
 * ankommenden Note wummert. Darunter hüpfende Punkte — so sehen die Kinder,
 * dass gerade fleißig gearbeitet wird und sie kurz warten müssen.
 */
@Composable
fun StartingMusicAnimation(item: MusicItem, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "startingMusic")
    val travel = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(NoteTravelMillis, easing = LinearEasing)),
        label = "travel"
    )
    val bob = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(NoteTravelMillis / 3, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val blink = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3200
                1f at 2900
                0.1f at 3000
                1f at 3100
            }
        ),
        label = "blink"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        // Auf schmalen Bildschirmen die ganze Szene verkleinern statt abzuschneiden
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val factor = (maxWidth / SceneWidth.dp).coerceAtMost(1f)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SceneWidth.dp * factor, SceneHeight.dp * factor)) {
                Box(
                    modifier = Modifier
                        .requiredSize(SceneWidth.dp, SceneHeight.dp)
                        .graphicsLayer {
                            scaleX = factor
                            scaleY = factor
                        }
                ) {
                    Scene(item, travel, bob, blink)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gleich geht's los", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(6.dp))
            BouncingDots(travel)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Einen kleinen Moment Geduld bitte",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Scene(item: MusicItem, travel: State<Float>, bob: State<Float>, blink: State<Float>) {
    Speaker(travel, blink, modifier = Modifier.fillMaxSize())

    // Das Cover wippt im Takt mit, damit man sieht, was gleich kommt
    Box(
        modifier = Modifier
            .offset(0.dp, CoverTop.dp)
            .graphicsLayer {
                translationY = bob.value * 4.dp.toPx()
                rotationZ = bob.value * 4f
            }
    ) {
        MusicItemImage(item, size = CoverSize.dp)
    }

    repeat(NoteCount) { index -> FlyingNote(travel, index) }
}

/** Fortschritt (0..1) der Note [index] auf ihrem Weg zum Lautsprecher. */
private fun noteProgress(travel: Float, index: Int): Float = (travel + index.toFloat() / NoteCount) % 1f

@Composable
private fun FlyingNote(travel: State<Float>, index: Int) {
    Icon(
        Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = NoteColors[index % NoteColors.size],
        modifier = Modifier
            .size(NoteSize.dp)
            .graphicsLayer {
                val p = noteProgress(travel.value, index)
                val q = 1f - p
                // Punkt auf der Bézier-Kurve
                val x = q * q * NoteStart.x + 2 * q * p * NoteControl.x + p * p * NoteEnd.x
                val y = q * q * NoteStart.y + 2 * q * p * NoteControl.y + p * p * NoteEnd.y
                translationX = (x - NoteSize / 2f).dp.toPx()
                translationY = (y - NoteSize / 2f).dp.toPx()
                rotationZ = sin(p * 4f * PI.toFloat()) * 18f
                val grow = (p / 0.15f).coerceAtMost(1f)
                val shrink = ((1f - p) / 0.25f).coerceAtMost(1f)
                val scale = grow * (0.4f + 0.6f * shrink)
                scaleX = scale
                scaleY = scale
                alpha = grow * shrink.coerceAtLeast(0f)
            }
    )
}

/**
 * Lautsprecher mit Augen, gezeichnet auf einem Canvas über die ganze Szene.
 * Kommt eine Note an, wird er kurz größer und sendet Schallwellen aus.
 */
@Composable
private fun Speaker(travel: State<Float>, blink: State<Float>, modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.primary
    val wave = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        // Alle NoteTravelMillis / NoteCount kommt eine Note an (Phase 0)
        val phase = (travel.value * NoteCount) % 1f
        val bump = if (phase < 0.08f) phase / 0.08f else ((1f - phase) / 0.92f).let { it * it * it }

        val left = SpeakerLeft.dp.toPx()
        val top = SpeakerTop.dp.toPx()
        val width = SpeakerWidth.dp.toPx()
        val height = SpeakerHeight.dp.toPx()
        val cone = Offset(NoteEnd.x.dp.toPx(), NoteEnd.y.dp.toPx())

        // Schallwellen rechts neben dem Lautsprecher, wandern nach außen und verblassen
        repeat(2) { i ->
            val waveProgress = (phase + i * 0.5f) % 1f
            val radius = width * (0.62f + 0.22f * waveProgress)
            drawArc(
                color = wave.copy(alpha = (1f - waveProgress) * 0.8f),
                startAngle = -35f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cone.x - radius, cone.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val speakerScale = 1f + 0.07f * bump
        withTransform({ scale(speakerScale, speakerScale, pivot = Offset(left + width / 2f, top + height)) }) {
            drawRoundRect(
                color = body,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(28.dp.toPx())
            )

            // Augen, die zum Cover schauen (wo die Noten herkommen) und ab und zu blinzeln
            val eyeRadius = 13.dp.toPx()
            val eyeY = top + height * 0.2f
            listOf(left + width * 0.32f, left + width * 0.68f).forEach { eyeX ->
                val eyeHeight = eyeRadius * 2 * blink.value
                drawOval(
                    color = Color.White,
                    topLeft = Offset(eyeX - eyeRadius, eyeY - eyeHeight / 2f),
                    size = Size(eyeRadius * 2, eyeHeight)
                )
                if (blink.value > 0.5f) {
                    drawCircle(
                        color = Color(0xFF21005D),
                        radius = 6.dp.toPx(),
                        center = Offset(eyeX - 4.dp.toPx(), eyeY - 2.dp.toPx())
                    )
                }
            }

            // Membran, die bei jeder Note mitschwingt
            drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = width * 0.36f, center = cone)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = width * 0.36f,
                center = cone,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = width * 0.16f * (1f + 0.35f * bump), center = cone)
        }
    }
}

/** Drei Punkte, die nacheinander hüpfen — „es wird gearbeitet". */
@Composable
private fun BouncingDots(travel: State<Float>) {
    val color = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val bounce = abs(sin((travel.value * 3f - index * 0.2f) * PI.toFloat()))
                        translationY = -bounce * 6.dp.toPx()
                    }
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
