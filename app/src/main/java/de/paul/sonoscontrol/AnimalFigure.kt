package de.paul.sonoscontrol

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/*
 * Die Profil-Tiere als kleine Figur: Kopf aus AnimalIcons.kt bzw.
 * CharacterIcons.kt, darunter ein gezeichneter Körper mit Füßen, Armen und
 * Pfoten. Für Tierbesuch (AnimalVisitor.kt), das Tier auf dem
 * Fortschrittsbalken und das Tier am Cover (NowPlayingAnimals.kt).
 */

// Maße der Figur in dp bei Grundgröße — gezeichnet wird auf die Größe des Canvas skaliert
internal const val FigureWidth = 80f
internal const val FigureHeight = 92f
private const val HeadSize = 64f

// Arm-Winkel in Radiant, gemessen von senkrecht unten, positiv nach außen
internal const val RestArmAngle = 0.5f
internal const val ArmLength = 16f
private const val ArmWidth = 7f
private const val PawRadius = 4.6f
private const val ShoulderX = 12f
private const val ShoulderY = 61f

// Antippen: Das Tier hüpft vor Freude, und ein Herz steigt auf
private const val HopSeconds = 0.45f
private const val HopHeight = 26f
private const val HeartSeconds = 1.1f
private val HeartColor = Color(0xFFFF4F81)

private const val DARK = 0xFF2B2B2B
private const val WHITE = 0xFFFFFFFF

/** Geschlossenes Auge zum Schlafen, in Koordinaten des 24×24-Icons: Lid in Fellfarbe über dem Auge, darauf ein Bogen. */
internal class SleepyEye(val x: Float, val y: Float, val radius: Float, lid: Long, line: Long = DARK) {
    val lid = Color(lid)
    val line = Color(line)
}

/**
 * Aussehen der Figur: Farben passend zum Kopf. Ohne [body] (Marienkäfer) sitzt der Kopf direkt auf den Füßen.
 * [spot] ist ein Fleck seitlich auf dem Körper.
 */
internal class AnimalLook(
    val icon: ProfileIcon,
    val body: Color?,
    val arms: Color,
    val feet: Color,
    val belly: Color?,
    val spot: Color?,
    val eyes: List<SleepyEye>
) {
    val bodyOutline = body?.let(::darker)
    val armsOutline = darker(arms)
    val feetOutline = darker(feet)
}

private fun darker(color: Color) = lerp(color, Color.Black, 0.25f)

private fun look(
    icon: ProfileIcon,
    body: Long?,
    feet: Long,
    belly: Long? = null,
    arms: Long = body ?: feet,
    spot: Long? = null,
    eyes: List<SleepyEye>
) = AnimalLook(icon, body?.let { Color(it) }, Color(arms), Color(feet), belly?.let { Color(it) }, spot?.let { Color(it) }, eyes)

/** Zwei gleich große Augen auf gleicher Höhe mit derselben Lidfarbe. */
private fun eyePair(leftX: Float, rightX: Float, y: Float, radius: Float, lid: Long, line: Long = DARK) =
    listOf(SleepyEye(leftX, y, radius, lid, line), SleepyEye(rightX, y, radius, lid, line))

private val Looks: Map<ProfileIcon, AnimalLook> = listOf(
    look(ProfileIcon.CAT, body = 0xFFFFA94D, feet = 0xFFFFF3E0, belly = 0xFFFFF3E0, eyes = eyePair(9.0f, 15.0f, 12.6f, 1.0f, 0xFFFFA94D)),
    look(
        ProfileIcon.DOG, body = 0xFFD9A86C, feet = 0xFF8D5A3B, belly = 0xFFF5E1C0,
        // Das rechte Auge sitzt im dunkleren Fleck
        eyes = listOf(SleepyEye(9.3f, 11.8f, 0.95f, 0xFFD9A86C), SleepyEye(14.7f, 11.8f, 0.95f, 0xFFB07A45))
    ),
    look(ProfileIcon.BEAR, body = 0xFF9C6B4E, feet = 0xFF7A5038, belly = 0xFFE3C3A3, eyes = eyePair(9.0f, 15.0f, 12.4f, 0.9f, 0xFF9C6B4E)),
    look(
        ProfileIcon.PANDA, body = WHITE, feet = DARK, arms = DARK,
        eyes = eyePair(9.0f, 15.0f, 12.2f, 0.85f, DARK, line = WHITE)
    ),
    look(ProfileIcon.FOX, body = 0xFFFF7A33, feet = 0xFF5D3A1A, belly = 0xFFFFF3E0, eyes = eyePair(9.2f, 14.8f, 11.6f, 0.9f, 0xFFFF7A33)),
    // Beim Frosch verschwindet der ganze weiße Augapfel unter dem Lid
    look(ProfileIcon.FROG, body = 0xFF66BB6A, feet = 0xFF43A047, belly = 0xFFC5E1A5, eyes = eyePair(7.4f, 16.6f, 8.6f, 1.9f, 0xFF66BB6A)),
    look(ProfileIcon.LION, body = 0xFFFFCC4D, feet = 0xFFE07B24, belly = 0xFFFFE9A8, eyes = eyePair(9.6f, 14.4f, 12.0f, 0.85f, 0xFFFFCC4D)),
    look(ProfileIcon.PIG, body = 0xFFF8BBD0, feet = 0xFFF06292, eyes = eyePair(8.9f, 15.1f, 11.6f, 0.9f, 0xFFF8BBD0)),
    look(ProfileIcon.MOUSE, body = 0xFFB0B0B8, feet = 0xFFF8BBD0, belly = 0xFFE4E4EA, eyes = eyePair(9.6f, 14.4f, 13.2f, 0.9f, 0xFFB0B0B8)),
    look(ProfileIcon.BUNNY, body = WHITE, feet = 0xFFF8BBD0, eyes = eyePair(9.6f, 14.4f, 13.8f, 0.85f, WHITE)),
    look(ProfileIcon.OWL, body = 0xFF8D6E63, feet = 0xFFFFB300, belly = 0xFFFFE0B2, eyes = eyePair(8.7f, 15.3f, 11.4f, 1.8f, 0xFFFFE0B2)),
    look(ProfileIcon.PENGUIN, body = 0xFF37474F, feet = 0xFFFFA000, belly = WHITE, eyes = eyePair(9.6f, 14.4f, 12.2f, 0.9f, WHITE)),
    look(ProfileIcon.MONKEY, body = 0xFF8D6E63, feet = 0xFFFFDDB8, belly = 0xFFFFDDB8, eyes = eyePair(9.8f, 14.2f, 11.4f, 0.9f, 0xFFFFDDB8)),
    look(ProfileIcon.KOALA, body = 0xFF90A4AE, feet = 0xFF607D8B, belly = 0xFFECEFF1, eyes = eyePair(8.8f, 15.2f, 12.2f, 0.85f, 0xFF90A4AE)),
    look(ProfileIcon.CHICK, body = 0xFFFFE082, feet = 0xFFFF8F00, arms = 0xFFFFCA28, eyes = eyePair(9.4f, 14.6f, 11.6f, 1.0f, 0xFFFFE082)),
    look(ProfileIcon.LADYBUG, body = null, feet = DARK, eyes = eyePair(10.5f, 13.5f, 5.5f, 0.6f, DARK, line = WHITE)),
    look(ProfileIcon.COW, body = WHITE, feet = 0xFF6D4C41, eyes = eyePair(9.3f, 14.7f, 11.2f, 1.15f, WHITE)),
    // Beide Augen sitzen in den hellbraunen Flecken; dunkelbrauner Fleck an der Seite wie beim Vorbild
    look(ProfileIcon.FLUFFY_DOG, body = WHITE, feet = WHITE, spot = 0xFF6B4A36, eyes = eyePair(9.2f, 14.8f, 12.0f, 0.95f, 0xFFC98B4F)),
    // Das Einhorn hat lachende, schon geschlossene Augen; zum Schlafen werden sie zu Bögen nach unten
    look(ProfileIcon.UNICORN, body = WHITE, feet = 0xFFB39DDB, eyes = eyePair(9.3f, 14.7f, 12.6f, 1.2f, WHITE, line = 0xFF4A3B5C)),
    look(ProfileIcon.PIKACHU, body = 0xFFFFD93B, feet = 0xFFE6B800, eyes = eyePair(8.6f, 15.4f, 13.2f, 1.35f, 0xFFFFD93B))
).associateBy { it.icon }

internal fun animalLook(icon: ProfileIcon): AnimalLook = Looks.getValue(icon)

/** Haltung der Figur. Winkel der Arme wie [RestArmAngle], Längen in dp der Grundgröße. */
internal class FigurePose(
    /** Schritt-Phase in Radiant: Bei jedem Vielfachen von π stehen beide Füße am Boden. */
    val stepPhase: Float = 0f,
    /** 0–1: wie hoch die Füße gehen und wie stark der Körper dabei wippt. */
    val stepAmount: Float = 1f,
    val leftArm: Float = RestArmAngle,
    val rightArm: Float = RestArmAngle,
    val leftArmLength: Float = ArmLength,
    val rightArmLength: Float = ArmLength,
    /** Neigung des Kopfes in Grad, positiv im Uhrzeigersinn. */
    val headTilt: Float = 0f,
    val eyesClosed: Boolean = false,
    val shadow: Boolean = false
)

/** Arm beim Laufen: schwingt gegengleich zu den Füßen. [side]: −1 links, 1 rechts (vom Betrachter aus). */
internal fun swingingArm(side: Int, stepPhase: Float, amount: Float = 1f) =
    RestArmAngle - side * sin(stepPhase) * 0.35f * amount

/**
 * Winkel und Länge eines Arms, dessen Pfote bei ([x] | [y]) liegt — x von der
 * Mitte der Figur aus, beides in dp der Grundgröße. Zum Festhalten von Dingen.
 */
internal fun armTo(side: Int, x: Float, y: Float): Pair<Float, Float> {
    val outward = side * (x - side * ShoulderX)
    val down = y - ShoulderY
    return atan2(outward, down) to hypot(outward, down)
}

/**
 * Zeichnet die Figur auf die ganze Fläche (Seitenverhältnis FigureWidth × FigureHeight).
 * [held] zeichnet, was das Tier in den Pfoten hält (z. B. ein Buch): vor Körper und
 * Armen, nur die Pfoten liegen darauf; der Parameter ist die Breite einer dp der
 * Grundgröße in Pixeln.
 */
internal fun DrawScope.drawAnimalFigure(
    look: AnimalLook,
    head: VectorPainter,
    pose: FigurePose,
    held: (DrawScope.(u: Float) -> Unit)? = null
) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    val step = sin(pose.stepPhase) * pose.stepAmount
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
        val body = look.body
        if (body != null) {
            val bodyTopLeft = Offset(cx - 15f * u, 49f * u)
            val bodySize = Size(30f * u, 31f * u)
            drawOval(body, bodyTopLeft, bodySize)
            drawOval(look.bodyOutline ?: body, bodyTopLeft, bodySize, style = outline)
            look.belly?.let { drawOval(it, Offset(cx - 9f * u, 60f * u), Size(18f * u, 17f * u)) }
            look.spot?.let { drawOval(it, Offset(cx + 2.5f * u, 61f * u), Size(9f * u, 11f * u)) }
        }

        // Ohne Körper sitzt der Kopf direkt auf den Füßen
        val headTop = if (body == null) 23f * u else 0f
        rotate(pose.headTilt, pivot = Offset(cx, headTop + 52f * u)) {
            translate(left = cx - HeadSize / 2f * u, top = headTop) {
                with(head) { draw(Size(HeadSize * u, HeadSize * u)) }
                if (pose.eyesClosed) drawClosedEyes(look.eyes, HeadSize * u / 24f)
            }
        }

        if (body != null) {
            val leftPaw = drawArm(look, -1, pose.leftArm, pose.leftArmLength, cx, u)
            val rightPaw = drawArm(look, 1, pose.rightArm, pose.rightArmLength, cx, u)
            held?.invoke(this, u)
            drawPaw(look, leftPaw, u, outline)
            drawPaw(look, rightPaw, u, outline)
        } else {
            held?.invoke(this, u)
        }
    }
}

/** Zeichnet den Arm ohne Pfote und gibt zurück, wo die Pfote hingehört. */
private fun DrawScope.drawArm(look: AnimalLook, side: Int, angle: Float, length: Float, cx: Float, u: Float): Offset {
    val shoulder = Offset(cx + side * ShoulderX * u, ShoulderY * u)
    val paw = shoulder + Offset(side * sin(angle), cos(angle)) * (length * u)
    drawLine(look.armsOutline, shoulder, paw, strokeWidth = (ArmWidth + 2.8f) * u, cap = StrokeCap.Round)
    drawLine(look.arms, shoulder, paw, strokeWidth = ArmWidth * u, cap = StrokeCap.Round)
    return paw
}

private fun DrawScope.drawPaw(look: AnimalLook, at: Offset, u: Float, outline: Stroke) {
    drawCircle(look.arms, PawRadius * u, at)
    drawCircle(look.armsOutline, PawRadius * u, at, style = outline)
}

/** Lider über die Augen des Icons legen; [k] rechnet Icon-Koordinaten in Pixel um. */
private fun DrawScope.drawClosedEyes(eyes: List<SleepyEye>, k: Float) {
    for (eye in eyes) {
        val r = eye.radius
        drawCircle(eye.lid, r * 1.3f * k, Offset(eye.x * k, eye.y * k))
        val lid = Path().apply {
            moveTo((eye.x - r * 1.05f) * k, (eye.y - r * 0.1f) * k)
            cubicTo(
                (eye.x - r * 0.5f) * k, (eye.y + r * 0.75f) * k,
                (eye.x + r * 0.5f) * k, (eye.y + r * 0.75f) * k,
                (eye.x + r * 1.05f) * k, (eye.y - r * 0.1f) * k
            )
        }
        drawPath(lid, eye.line, style = Stroke(width = max(0.4f, r * 0.42f) * k, cap = StrokeCap.Round))
    }
}

/** Wie hoch das Tier [sinceTap] Sekunden nach einem Tipp gerade hüpft, in dp der Grundgröße. */
internal fun hopHeight(sinceTap: Float?): Float =
    sinceTap?.takeIf { it in 0f..HopSeconds }?.let { sin(PI.toFloat() * it / HopSeconds) * HopHeight } ?: 0f

/** Fortschritt 0–1 des Herzens nach einem Tipp, `null` wenn keins zu sehen ist. */
internal fun heartProgress(sinceTap: Float?): Float? =
    sinceTap?.takeIf { it in 0f..HeartSeconds }?.let { it / HeartSeconds }

/** Herz, das über dem Kopf der Figur aufsteigt und verblasst. */
internal fun DrawScope.drawHeart(progress: Float) {
    val u = size.width / FigureWidth
    val x = size.width / 2f + 16f * u
    val y = (4f - 34f * progress) * u
    val r = (7f + 4f * progress) * u
    val heart = Path().apply {
        moveTo(x, y + r)
        cubicTo(x - r * 1.6f, y + r * 0.1f, x - r * 0.9f, y - r * 1.1f, x, y - r * 0.35f)
        cubicTo(x + r * 0.9f, y - r * 1.1f, x + r * 1.6f, y + r * 0.1f, x, y + r)
        close()
    }
    drawPath(heart, HeartColor.copy(alpha = 1f - progress * progress))
}
