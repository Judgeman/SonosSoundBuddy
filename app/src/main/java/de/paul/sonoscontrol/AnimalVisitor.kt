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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
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
 * vorbei. Es läuft unten über den Bildschirm (und bleibt meist unterwegs
 * stehen, um zu winken), schaut seitlich herein oder taucht unten auf und
 * winkt. Die Köpfe sind die Profil-Tiere aus AnimalIcons.kt, darunter ein
 * kleiner gezeichneter Körper. Tippt ein Kind das Tier an, hüpft es vor Freude
 * und ein Herz steigt auf — alle anderen Berührungen gehen an den Homescreen.
 */

/** Pause zwischen zwei Besuchen — gezählt wird nur, solange der Homescreen zu sehen ist. */
private val MinPause = 10.minutes
private val MaxPause = 20.minutes

// Maße der Figur in dp bei Grundgröße; auf großen Tablets wird sie mitskaliert
private const val FigureWidth = 80f
private const val FigureHeight = 92f
private const val HeadSize = 64f

private const val WalkSpeed = 95f
private const val StepLength = 22f
private const val LeanDegrees = 5f

// Arm-Winkel in Radiant, gemessen von senkrecht unten, positiv nach außen.
// Zum Winken wird der Arm länger, damit die Pfote neben dem Kopf zu sehen ist.
private const val ArmLength = 16f
private const val WavingArmLength = 21f
private const val ArmWidth = 7f
private const val PawRadius = 4.6f
private const val RestArmAngle = 0.5f
private const val WaveArmAngle = 2.3f
private const val WaveSwing = 0.3f

private const val WaveSeconds = 2.8f
private const val WavesPerSecond = 2.2f
private const val PeekInSeconds = 0.8f
private const val PeekOutSeconds = 0.6f
private const val HopSeconds = 0.45f
private const val HopHeight = 26f
private const val HeartSeconds = 1.1f

private val HeartColor = Color(0xFFFF4F81)

/** Körperfarben passend zum Kopf aus AnimalIcons.kt. */
internal class VisitorLook(
    val icon: ProfileIcon,
    val body: Color,
    val arms: Color,
    val feet: Color,
    val belly: Color?
) {
    val bodyOutline = darker(body)
    val armsOutline = darker(arms)
    val feetOutline = darker(feet)
}

private fun darker(color: Color) = lerp(color, Color.Black, 0.25f)

private fun look(icon: ProfileIcon, body: Long, feet: Long, belly: Long? = null, arms: Long = body) =
    VisitorLook(icon, Color(body), Color(arms), Color(feet), belly?.let { Color(it) })

// Marienkäfer (schon ein ganzer Käfer) und Pikachu kommen nicht zu Besuch
private val VisitorLooks = listOf(
    look(ProfileIcon.CAT, body = 0xFFFFA94D, feet = 0xFFFFF3E0, belly = 0xFFFFF3E0),
    look(ProfileIcon.DOG, body = 0xFFD9A86C, feet = 0xFF8D5A3B, belly = 0xFFF5E1C0),
    look(ProfileIcon.BEAR, body = 0xFF9C6B4E, feet = 0xFF7A5038, belly = 0xFFE3C3A3),
    look(ProfileIcon.PANDA, body = 0xFFFFFFFF, feet = 0xFF2B2B2B, arms = 0xFF2B2B2B),
    look(ProfileIcon.FOX, body = 0xFFFF7A33, feet = 0xFF5D3A1A, belly = 0xFFFFF3E0),
    look(ProfileIcon.FROG, body = 0xFF66BB6A, feet = 0xFF43A047, belly = 0xFFC5E1A5),
    look(ProfileIcon.LION, body = 0xFFFFCC4D, feet = 0xFFE07B24, belly = 0xFFFFE9A8),
    look(ProfileIcon.PIG, body = 0xFFF8BBD0, feet = 0xFFF06292),
    look(ProfileIcon.MOUSE, body = 0xFFB0B0B8, feet = 0xFFF8BBD0, belly = 0xFFE4E4EA),
    look(ProfileIcon.BUNNY, body = 0xFFFFFFFF, feet = 0xFFF8BBD0),
    look(ProfileIcon.OWL, body = 0xFF8D6E63, feet = 0xFFFFB300, belly = 0xFFFFE0B2),
    look(ProfileIcon.PENGUIN, body = 0xFF37474F, feet = 0xFFFFA000, belly = 0xFFFFFFFF),
    look(ProfileIcon.MONKEY, body = 0xFF8D6E63, feet = 0xFFFFDDB8, belly = 0xFFFFDDB8),
    look(ProfileIcon.KOALA, body = 0xFF90A4AE, feet = 0xFF607D8B, belly = 0xFFECEFF1),
    look(ProfileIcon.CHICK, body = 0xFFFFE082, feet = 0xFFFF8F00, arms = 0xFFFFCA28),
    look(ProfileIcon.COW, body = 0xFFFFFFFF, feet = 0xFF6D4C41),
    look(ProfileIcon.UNICORN, body = 0xFFFFFFFF, feet = 0xFFB39DDB)
)

internal enum class VisitKind {
    /** Läuft unten über den Bildschirm, bleibt meist unterwegs stehen und winkt. */
    WALK,

    /** Schaut seitlich am Rand herein und winkt. */
    PEEK,

    /** Taucht unten am Rand auf und winkt. */
    POP_UP
}

internal class AnimalVisit(
    val look: VisitorLook,
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
        val look = VisitorLooks.filter { it.icon != lastIcon }.random()
        lastIcon = look.icon
        visit = AnimalVisit(
            look = look,
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
    val hop = sinceTap?.takeIf { it < HopSeconds }?.let { sin(PI.toFloat() * it / HopSeconds) * HopHeight * scale } ?: 0f
    val heart = sinceTap?.takeIf { it < HeartSeconds }?.let { it / HeartSeconds }
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
        drawVisitor(visit.look, head, pose, heart)
    }
}

/** Haltung der Figur zu einem Zeitpunkt: Position (links oben, in dp), Neigung, Schritte und Winken. */
private class Pose(
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
    /** Schritt-Phase in Radiant: Bei jedem Vielfachen von π stehen beide Füße am Boden. */
    val stepPhase: Float = 0f,
    /** 0–1: wie weit der winkende Arm oben ist. */
    val wave: Float = 0f,
    val wavePhase: Float = 0f,
    /** Welcher Arm winkt: 1 = rechts, −1 = links (vom Betrachter aus). */
    val waveArm: Int = 1,
    val shadow: Boolean = false
)

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

    fun poseAt(t: Float): Pose = when (visit.kind) {
        VisitKind.WALK -> walkPose(t)
        VisitKind.PEEK -> peekPose(t)
        VisitKind.POP_UP -> popUpPose(t)
    }

    private fun walkPose(t: Float): Pose {
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
        return Pose(
            x = if (visit.fromLeft) walked - figureWidth else width - walked,
            y = height - figureHeight,
            rotation = direction * LeanDegrees * lean,
            stepPhase = walked / stepLength * PI.toFloat(),
            wave = if (visit.stops) waveAmount(t - arriveAt) else 0f,
            wavePhase = wavePhase(t),
            waveArm = direction,
            shadow = true
        )
    }

    private fun peekPose(t: Float): Pose {
        // Seitlich geneigt, ein gutes Stück bleibt hinter dem Rand
        val hiddenX = if (visit.fromLeft) -figureWidth * 1.35f else width + figureWidth * 0.35f
        val shownX = if (visit.fromLeft) -figureWidth * 0.4f else width - figureWidth * 0.6f
        return Pose(
            x = hiddenX + (shownX - hiddenX) * appearance(t),
            y = (height - figureHeight) * (0.2f + 0.4f * visit.spot),
            rotation = if (visit.fromLeft) 14f else -14f,
            wave = waveAmount(t - PeekInSeconds),
            wavePhase = wavePhase(t),
            waveArm = if (visit.fromLeft) 1 else -1
        )
    }

    private fun popUpPose(t: Float): Pose {
        val hiddenY = height + 4f
        val shownY = height - figureHeight * 0.75f
        return Pose(
            x = (width - figureWidth) * (0.1f + 0.8f * visit.spot),
            y = hiddenY + (shownY - hiddenY) * appearance(t),
            rotation = sin(t * 2.4f) * 4f,
            wave = waveAmount(t - PeekInSeconds),
            wavePhase = wavePhase(t),
            // Winkt zur Bildschirmmitte hin
            waveArm = if (visit.spot < 0.5f) 1 else -1
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

private fun wavePhase(t: Float) = t * WavesPerSecond * 2f * PI.toFloat()

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

/**
 * Zeichnet das Tier in Grundgröße-Koordinaten (FigureWidth × FigureHeight),
 * auf die Größe des Canvas skaliert: Füße, Körper, Kopf, Arme und nach einem
 * Tipp das aufsteigende Herz ([heart] = Fortschritt 0–1).
 */
private fun DrawScope.drawVisitor(look: VisitorLook, head: VectorPainter, pose: Pose, heart: Float?) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    val step = sin(pose.stepPhase)
    val outline = Stroke(width = 1.4f * u)

    if (pose.shadow) {
        drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - 22f * u, 85.5f * u), Size(44f * u, 6f * u))
    }

    // Füße: abwechselnd ist einer in der Luft
    for (side in -1..1 step 2) {
        val lift = max(0f, side * step) * 6f * u
        val topLeft = Offset(cx + (side * 9f - 7.5f) * u, 79.5f * u - lift)
        val footSize = Size(15f * u, 9f * u)
        drawOval(look.feet, topLeft, footSize)
        drawOval(look.feetOutline, topLeft, footSize, style = outline)
    }

    // Körper, Kopf und Arme wippen bei jedem Schritt mit
    translate(top = -abs(step) * 2.5f * u) {
        val bodyTopLeft = Offset(cx - 15f * u, 49f * u)
        val bodySize = Size(30f * u, 31f * u)
        drawOval(look.body, bodyTopLeft, bodySize)
        drawOval(look.bodyOutline, bodyTopLeft, bodySize, style = outline)
        look.belly?.let { drawOval(it, Offset(cx - 9f * u, 60f * u), Size(18f * u, 17f * u)) }

        translate(left = cx - HeadSize / 2f * u) {
            with(head) { draw(Size(HeadSize * u, HeadSize * u)) }
        }

        // Arme schwingen gegengleich zu den Füßen; einer winkt
        for (side in -1..1 step 2) {
            val rest = RestArmAngle - side * step * 0.35f
            val wave = if (side == pose.waveArm) pose.wave else 0f
            val waving = WaveArmAngle + WaveSwing * sin(pose.wavePhase)
            val angle = rest + (waving - rest) * wave
            val length = ArmLength + (WavingArmLength - ArmLength) * wave
            val shoulder = Offset(cx + side * 12f * u, 61f * u)
            val paw = shoulder + Offset(side * sin(angle), cos(angle)) * (length * u)
            drawLine(look.armsOutline, shoulder, paw, strokeWidth = (ArmWidth + 2.8f) * u, cap = StrokeCap.Round)
            drawLine(look.arms, shoulder, paw, strokeWidth = ArmWidth * u, cap = StrokeCap.Round)
            drawCircle(look.arms, PawRadius * u, paw)
            drawCircle(look.armsOutline, PawRadius * u, paw, style = outline)
        }
    }

    if (heart != null) {
        val center = Offset(cx + 16f * u, (4f - 34f * heart) * u)
        drawPath(heartPath(center, (7f + 4f * heart) * u), HeartColor.copy(alpha = 1f - heart * heart))
    }
}

private fun heartPath(center: Offset, size: Float) = Path().apply {
    val x = center.x
    val y = center.y
    moveTo(x, y + size)
    cubicTo(x - size * 1.6f, y + size * 0.1f, x - size * 0.9f, y - size * 1.1f, x, y - size * 0.35f)
    cubicTo(x + size * 0.9f, y - size * 1.1f, x + size * 1.6f, y + size * 0.1f, x, y + size)
    close()
}
