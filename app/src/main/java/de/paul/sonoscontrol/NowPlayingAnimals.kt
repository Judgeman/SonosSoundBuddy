package de.paul.sonoscontrol

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/*
 * Das Tier des gewählten Profils begleitet die Wiedergabe (Figur aus
 * AnimalFigure.kt): Ein kleines läuft auf dem Fortschrittsbalken mit, ein
 * größeres sitzt auf der Ecke des Covers, tanzt zur Musik und schläft bei
 * Pause.
 */

/** Platz über dem Fortschrittsbalken, in dem das kleine Tier läuft. */
val ProgressWalkerHeight = 56.dp

private const val WalkerScale = 0.65f
private const val WalkerStepsPerSecond = 2.4f

private const val BeatSeconds = 0.6f
private const val ZzzSeconds = 3.6f
private const val NoteSeconds = 2.4f

private val NoteColors = listOf(Color(0xFFFF8A3D), Color(0xFF00BFA5), Color(0xFFFF4F9A))
private val ZzzColor = Color(0xFFE8EAF6)
private val ZzzOutline = Color(0xFF3F3D56)

/**
 * Das kleine Tier über dem Fortschrittsbalken. Es steht an der Stelle des
 * Fortschritts und läuft, solange die Musik spielt; bei Pause geht es den
 * angefangenen Schritt zu Ende und bleibt stehen. [progress] `null` = Live
 * (Radio): Dann läuft es in der Mitte auf der Stelle.
 *
 * Gedacht für eine Fläche von [ProgressWalkerHeight] direkt über dem Balken;
 * die Füße reichen ein Stück auf den Balken.
 */
@Composable
fun ProgressWalker(icon: ProfileIcon, progress: Float?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val look = animalLook(icon)
    val head = rememberVectorPainter(icon.vector)
    val shownProgress by animateFloatAsState(progress ?: 0.5f, tween(500, easing = LinearEasing), label = "walkerProgress")
    val lean by animateFloatAsState(if (isPlaying) 1f else 0f, tween(400), label = "walkerLean")

    // Gezählte Schritte; bei jeder ganzen Zahl stehen beide Füße am Boden
    var steps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        val until = if (isPlaying) Float.POSITIVE_INFINITY else ceil(steps)
        var last = withFrameNanos { it }
        while (steps < until) {
            withFrameNanos { now ->
                steps = min(until, steps + (now - last) / 1_000_000_000f * WalkerStepsPerSecond)
                last = now
            }
            // Zwei Schritte sind eine ganze Runde — so wird die Zahl nicht beliebig groß
            if (isPlaying && steps >= 2f) steps -= 2f
        }
    }

    BoxWithConstraints(modifier) {
        val width = FigureWidth * WalkerScale
        val height = FigureHeight * WalkerScale
        val travel = maxWidth.value - width
        Canvas(
            modifier = Modifier
                .offset {
                    val x = (shownProgress * maxWidth.value - width / 2f).coerceIn(0f, travel.coerceAtLeast(0f))
                    IntOffset(x.dp.roundToPx(), (ProgressWalkerHeight.value + 4f - height).dp.roundToPx())
                }
                .size(width.dp, height.dp)
                .graphicsLayer {
                    rotationZ = 5f * lean
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            val stepPhase = steps * PI.toFloat()
            drawAnimalFigure(
                look,
                head,
                FigurePose(
                    stepPhase = stepPhase,
                    leftArm = swingingArm(-1, stepPhase),
                    rightArm = swingingArm(1, stepPhase)
                )
            )
        }
    }
}

/**
 * Das Tier auf der unteren rechten Ecke des Covers: tanzt mit fliegenden
 * Noten, solange Musik läuft, und schläft bei Pause mit geschlossenen Augen
 * und „Zzz". Beim Laden steht es wach da. Antippen lässt es hüpfen, und ein
 * Herz steigt auf. [modifier] sollte die ganze Cover-Fläche abdecken.
 */
@Composable
fun CoverDancer(icon: ProfileIcon, isPlaying: Boolean, isBuffering: Boolean, modifier: Modifier = Modifier) {
    val look = animalLook(icon)
    val head = rememberVectorPainter(icon.vector)
    val dance by animateFloatAsState(if (isPlaying) 1f else 0f, tween(500), label = "dance")
    val awake by animateFloatAsState(if (isPlaying || isBuffering) 1f else 0f, tween(700), label = "awake")
    val clock = produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    var tappedAt by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier) {
        // Etwa ein Viertel der Cover-Breite, auf kleinen und großen Bildschirmen in Grenzen
        val width = (maxWidth.value * 0.26f).coerceIn(64f, 150f)
        val scale = width / FigureWidth
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (maxWidth.value * 0.04f).dp)
                .size(width.dp, (FigureHeight * scale).dp)
                .graphicsLayer {
                    val t = clock.value
                    val beat = sin(PI.toFloat() * t / BeatSeconds)
                    // Hüpft auf jeden Schlag und schaukelt hin und her
                    val bounce = abs(beat) * 5f * dance + hopHeight(tappedAt?.let { t - it })
                    translationY = -bounce * scale * density
                    rotationZ = beat * 7f * dance
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .pointerInput(Unit) { detectTapGestures { tappedAt = clock.value } }
        ) {
            val t = clock.value
            val asleep = 1f - awake
            if (asleep > 0f) drawZzz(t, asleep)
            if (dance > 0f) drawNotes(t, dance)
            drawAnimalFigure(look, head, dancerPose(t, dance, asleep))
            heartProgress(tappedAt?.let { t - it })?.let { drawHeart(it) }
        }
    }
}

/** Tanzen: Füße im Takt, Arme abwechselnd hoch, Kopf wippt. Schlafen: Kopf zur Seite, Augen zu, ruhiges Atmen. */
private fun dancerPose(t: Float, dance: Float, asleep: Float): FigurePose {
    val beat = sin(PI.toFloat() * t / BeatSeconds)
    val up = 2.6f
    val left = dance * (0.5f + 0.5f * beat)
    val right = dance * (0.5f - 0.5f * beat)
    val breathing = sin(2f * PI.toFloat() * t / 4f)
    return FigurePose(
        stepPhase = PI.toFloat() * t / BeatSeconds,
        stepAmount = 0.6f * dance,
        leftArm = RestArmAngle + (up - RestArmAngle) * left,
        rightArm = RestArmAngle + (up - RestArmAngle) * right,
        leftArmLength = ArmLength + 4f * left,
        rightArmLength = ArmLength + 4f * right,
        headTilt = -beat * 6f * dance + (12f + 1.5f * breathing) * asleep,
        eyesClosed = asleep > 0.5f,
        shadow = true
    )
}

/** Drei „Z", die nacheinander vom Kopf schräg nach oben steigen, größer werden und verblassen. */
private fun DrawScope.drawZzz(t: Float, amount: Float) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    for (i in 0 until 3) {
        val phase = (t / ZzzSeconds + i / 3f) % 1f
        val alpha = sin(PI.toFloat() * phase) * amount
        val letter = (6f + 8f * phase) * u
        val x = cx + (16f + 24f * phase + 3f * sin(phase * 6f)) * u
        val y = (6f - 44f * phase) * u
        val z = Path().apply {
            moveTo(x, y)
            lineTo(x + letter, y)
            lineTo(x, y + letter)
            lineTo(x + letter, y + letter)
        }
        drawPath(z, ZzzOutline.copy(alpha = alpha), style = Stroke(4.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(z, ZzzColor.copy(alpha = alpha), style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Bunte Noten, die abwechselnd links und rechts neben dem Tier aufsteigen. */
private fun DrawScope.drawNotes(t: Float, amount: Float) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    for (i in NoteColors.indices) {
        val phase = (t / NoteSeconds + i / NoteColors.size.toFloat()) % 1f
        val side = if (i % 2 == 0) -1 else 1
        val alpha = sin(PI.toFloat() * phase) * amount
        val x = cx + side * (30f + 10f * phase) * u + sin(phase * 2f * PI.toFloat()) * 4f * u
        val y = (40f - 50f * phase) * u
        drawNote(Offset(x, y), 10f * u, NoteColors[i].copy(alpha = alpha))
    }
}

/** Achtelnote: Kopf, Hals und Fähnchen; [at] ist die Mitte des Notenkopfs. */
private fun DrawScope.drawNote(at: Offset, size: Float, color: Color) {
    val stemX = at.x + size * 0.32f
    val stemTop = at.y - size * 1.2f
    drawCircle(color, size * 0.38f, at)
    drawLine(color, Offset(stemX, at.y), Offset(stemX, stemTop), strokeWidth = size * 0.16f, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(stemX, stemTop)
        cubicTo(
            stemX + size * 0.45f, stemTop + size * 0.1f,
            stemX + size * 0.6f, stemTop + size * 0.4f,
            stemX + size * 0.5f, stemTop + size * 0.75f
        )
    }
    drawPath(flag, color, style = Stroke(size * 0.16f, cap = StrokeCap.Round))
}
