package de.paul.sonoscontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/*
 * Kleine Überraschung auf dem Homescreen: Alle 10–20 Minuten schaut ein Tier
 * vorbei (gezeichnet in AnimalFigure.kt). Es läuft unten über den Bildschirm
 * (und bleibt meist unterwegs stehen, um zu winken), schaut seitlich herein
 * oder taucht unten auf und winkt. Tippt ein Kind das Tier an, hüpft es vor
 * Freude und ein Herz steigt auf — alle anderen Berührungen gehen an den
 * Homescreen.
 */

/** Pause zwischen zwei Besuchen — gezählt wird nur, solange der Homescreen zu sehen ist. */
private val MinPause = 10.minutes
private val MaxPause = 20.minutes

private const val WalkSpeed = 95f
private const val StepLength = 22f
private const val LeanDegrees = 5f

// Zum Winken wird der Arm länger, damit die Pfote neben dem Kopf zu sehen ist
private const val WavingArmLength = 21f
private const val WaveArmAngle = 2.3f
private const val WaveSwing = 0.3f

private const val WaveSeconds = 2.8f
private const val WavesPerSecond = 2.2f
private const val PeekInSeconds = 0.8f
private const val PeekOutSeconds = 0.6f

// Marienkäfer (schon ein ganzer Käfer, ohne Arme zum Winken) und Pikachu kommen nicht zu Besuch
private val VisitorIcons = ProfileIcon.entries - ProfileIcon.LADYBUG - ProfileIcon.PIKACHU

internal enum class VisitKind {
    /** Läuft unten über den Bildschirm, bleibt meist unterwegs stehen und winkt. */
    WALK,

    /** Schaut seitlich am Rand herein und winkt. */
    PEEK,

    /** Taucht unten am Rand auf und winkt. */
    POP_UP
}

internal class AnimalVisit(
    val look: AnimalLook,
    val kind: VisitKind,
    /** WALK: läuft von links los, PEEK: schaut von links herein. */
    val fromLeft: Boolean,
    /** 0–1: wo das Tier stehen bleibt (WALK), in welcher Höhe es hereinschaut (PEEK) bzw. wo es auftaucht (POP_UP). */
    val spot: Float,
    /** WALK: bleibt unterwegs stehen und winkt. */
    val stops: Boolean
)

/** Der Besuch, der gerade läuft. [visitNow] schickt sofort ein Tier vorbei. */
@Stable
class AnimalVisitorState {
    internal var visit: AnimalVisit? by mutableStateOf(null)
    private var lastIcon: ProfileIcon? = null

    fun visitNow() {
        if (visit != null) return
        // Nicht zweimal hintereinander dasselbe Tier
        val icon = VisitorIcons.filter { it != lastIcon }.random()
        lastIcon = icon
        visit = AnimalVisit(
            look = animalLook(icon),
            // Am liebsten laufen sie über den Bildschirm
            kind = when (Random.nextInt(4)) {
                0, 1 -> VisitKind.WALK
                2 -> VisitKind.PEEK
                else -> VisitKind.POP_UP
            },
            fromLeft = Random.nextBoolean(),
            spot = Random.nextFloat(),
            stops = Random.nextFloat() < 0.7f
        )
    }
}

/**
 * Ebene über dem Homescreen, auf der ab und zu ein Tier vorbeischaut. [active]:
 * Der Homescreen ist zu sehen (App im Vordergrund, kein Popup darüber) — nur
 * dann läuft die Pause weiter und nur dann ist ein Tier unterwegs.
 */
@Composable
fun AnimalVisitors(state: AnimalVisitorState, active: Boolean, modifier: Modifier = Modifier) {
    val currentActive by rememberUpdatedState(active)

    LaunchedEffect(state) {
        while (true) {
            var remaining = Random.nextLong(MinPause.inWholeMilliseconds, MaxPause.inWholeMilliseconds).milliseconds
            while (remaining > Duration.ZERO) {
                snapshotFlow { currentActive }.first { it }
                val started = TimeSource.Monotonic.markNow()
                withTimeoutOrNull(remaining) { snapshotFlow { currentActive }.first { !it } }
                remaining -= started.elapsedNow()
            }
            state.visitNow()
            snapshotFlow { state.visit }.first { it == null }
        }
    }

    // Kommt ein Popup oder geht die App in den Hintergrund, verschwindet das Tier
    LaunchedEffect(active) {
        if (!active) state.visit = null
    }

    val visit = state.visit ?: return
    BoxWithConstraints(modifier) {
        VisitorScene(visit, maxWidth.value, maxHeight.value, onFinished = { state.visit = null })
    }
}

@Composable
private fun VisitorScene(visit: AnimalVisit, width: Float, height: Float, onFinished: () -> Unit) {
    // Auf großen Tablets größer, damit das Tier nicht verloren wirkt
    val scale = (min(width, height) / 480f).coerceIn(1f, 1.8f)
    val script = remember(visit, width, height) { VisitScript(visit, width, height, scale) }
    val currentScript by rememberUpdatedState(script)
    val currentOnFinished by rememberUpdatedState(onFinished)
    var time by remember(visit) { mutableFloatStateOf(0f) }
    var tappedAt by remember(visit) { mutableStateOf<Float?>(null) }

    LaunchedEffect(visit) {
        val start = withFrameNanos { it }
        while (time < currentScript.duration) {
            time = withFrameNanos { (it - start) / 1_000_000_000f }
        }
        currentOnFinished()
    }

    val pose = script.poseAt(time)
    val sinceTap = tappedAt?.let { time - it }
    val hop = hopHeight(sinceTap) * scale
    val heart = heartProgress(sinceTap)
    val head = rememberVectorPainter(visit.look.icon.vector)

    Canvas(
        modifier = Modifier
            .offset(pose.x.dp, (pose.y - hop).dp)
            .size((FigureWidth * scale).dp, (FigureHeight * scale).dp)
            .graphicsLayer {
                rotationZ = pose.rotation
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .pointerInput(visit) { detectTapGestures { tappedAt = time } }
    ) {
        drawAnimalFigure(visit.look, head, pose.figure)
        heart?.let { drawHeart(it) }
    }
}

/** Wo das Tier zu einem Zeitpunkt ist (links oben, in dp), wie es sich neigt und was es tut. */
private class VisitPose(val x: Float, val y: Float, val rotation: Float, val figure: FigurePose)

/** Ablauf eines Besuchs: wo das Tier nach [poseAt] Sekunden ist und was es gerade tut. */
private class VisitScript(
    private val visit: AnimalVisit,
    private val width: Float,
    private val height: Float,
    scale: Float
) {
    private val figureWidth = FigureWidth * scale
    private val figureHeight = FigureHeight * scale
    private val speed = WalkSpeed * scale
    private val stepLength = StepLength * scale

    // WALK: von außen bis zur Stopp-Stelle, dort winken, weiter bis ganz hinaus
    private val walkDistance = width + figureWidth
    private val stopDistance = if (visit.stops) {
        // Auf ganze Schritte gerundet, damit beim Stehenbleiben beide Füße am Boden sind
        val wanted = (width - figureWidth) * (0.25f + 0.5f * visit.spot) + figureWidth
        (wanted / stepLength).roundToInt() * stepLength
    } else {
        walkDistance
    }
    private val arriveAt = stopDistance / speed
    private val leaveAt = arriveAt + if (visit.stops) WaveSeconds else 0f

    val duration: Float = when (visit.kind) {
        VisitKind.WALK -> leaveAt + (walkDistance - stopDistance) / speed
        VisitKind.PEEK, VisitKind.POP_UP -> PeekInSeconds + WaveSeconds + PeekOutSeconds
    }

    fun poseAt(t: Float): VisitPose = when (visit.kind) {
        VisitKind.WALK -> walkPose(t)
        VisitKind.PEEK -> peekPose(t)
        VisitKind.POP_UP -> popUpPose(t)
    }

    private fun walkPose(t: Float): VisitPose {
        val walked = when {
            t < arriveAt -> t * speed
            t < leaveAt -> stopDistance
            else -> stopDistance + (t - leaveAt) * speed
        }
        // Beim Stehenbleiben richtet es sich auf, beim Weiterlaufen lehnt es sich wieder nach vorn
        val lean = when {
            !visit.stops -> 1f
            t < arriveAt -> ramp((arriveAt - t) / 0.3f)
            else -> ramp((t - leaveAt) / 0.3f)
        }
        val direction = if (visit.fromLeft) 1 else -1
        val wave = if (visit.stops) waveAmount(t - arriveAt) else 0f
        return VisitPose(
            x = if (visit.fromLeft) walked - figureWidth else width - walked,
            y = height - figureHeight,
            rotation = direction * LeanDegrees * lean,
            figure = wavingFigure(walked / stepLength * PI.toFloat(), wave, t, waveArm = direction, shadow = true)
        )
    }

    private fun peekPose(t: Float): VisitPose {
        // Seitlich geneigt, ein gutes Stück bleibt hinter dem Rand
        val hiddenX = if (visit.fromLeft) -figureWidth * 1.35f else width + figureWidth * 0.35f
        val shownX = if (visit.fromLeft) -figureWidth * 0.4f else width - figureWidth * 0.6f
        return VisitPose(
            x = hiddenX + (shownX - hiddenX) * appearance(t),
            y = (height - figureHeight) * (0.2f + 0.4f * visit.spot),
            rotation = if (visit.fromLeft) 14f else -14f,
            figure = wavingFigure(0f, waveAmount(t - PeekInSeconds), t, waveArm = if (visit.fromLeft) 1 else -1)
        )
    }

    private fun popUpPose(t: Float): VisitPose {
        val hiddenY = height + 4f
        val shownY = height - figureHeight * 0.75f
        return VisitPose(
            x = (width - figureWidth) * (0.1f + 0.8f * visit.spot),
            y = hiddenY + (shownY - hiddenY) * appearance(t),
            rotation = sin(t * 2.4f) * 4f,
            // Winkt zur Bildschirmmitte hin
            figure = wavingFigure(0f, waveAmount(t - PeekInSeconds), t, waveArm = if (visit.spot < 0.5f) 1 else -1)
        )
    }

    /** 0 = versteckt, 1 = ganz da: flott herein (mit kleinem Überschwinger), winken, wieder weg. */
    private fun appearance(t: Float): Float = when {
        t < PeekInSeconds -> easeOutBack(t / PeekInSeconds)
        t < PeekInSeconds + WaveSeconds -> 1f
        else -> {
            val out = ramp((t - PeekInSeconds - WaveSeconds) / PeekOutSeconds)
            1f - out * out
        }
    }
}

/** Figur, die läuft bzw. steht und mit einem Arm winkt ([wave] 0–1 = wie weit der Arm oben ist). */
private fun wavingFigure(stepPhase: Float, wave: Float, t: Float, waveArm: Int, shadow: Boolean = false): FigurePose {
    val waving = WaveArmAngle + WaveSwing * sin(t * WavesPerSecond * 2f * PI.toFloat())
    fun arm(side: Int): Float {
        val rest = swingingArm(side, stepPhase)
        return if (side == waveArm) rest + (waving - rest) * wave else rest
    }
    fun length(side: Int) = if (side == waveArm) ArmLength + (WavingArmLength - ArmLength) * wave else ArmLength
    return FigurePose(
        stepPhase = stepPhase,
        leftArm = arm(-1),
        rightArm = arm(1),
        leftArmLength = length(-1),
        rightArmLength = length(1),
        shadow = shadow
    )
}

/** 0–1 für den winkenden Arm: geht hoch, winkt [WaveSeconds] lang und geht wieder runter. */
private fun waveAmount(sinceStart: Float): Float = smooth(min(sinceStart, WaveSeconds - sinceStart) / 0.35f)

private fun ramp(x: Float) = x.coerceIn(0f, 1f)

private fun smooth(x: Float): Float {
    val c = ramp(x)
    return c * c * (3f - 2f * c)
}

private fun easeOutBack(x: Float): Float {
    val p = x - 1f
    return 1f + 2.70158f * p * p * p + 1.70158f * p * p
}
