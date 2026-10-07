package de.paul.sonoscontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Der SoundBuddy für Fehlermeldungen: ein kleiner lila Lautsprecher mit
 * Kulleraugen, der seinen ausgesteckten Stecker in der Hand hält. Er schaut
 * abwechselnd auf den Stecker und kratzt sich ratlos am Kopf, über ihm wippt
 * ein Fragezeichen — „Huch, da hat etwas nicht geklappt", ohne traurig oder
 * erschreckend zu wirken.
 */

// Maße der Szene in Einheiten — gezeichnet wird auf die Größe des Canvas skaliert
private const val SceneWidth = 170f
private const val SceneHeight = 150f
private const val GroundY = 142f

private const val BodyLeft = 46f
private const val BodyTop = 40f
private const val BodyWidth = 78f
private const val BodyHeight = 90f
private const val CenterX = BodyLeft + BodyWidth / 2f

private val LeftShoulder = Offset(BodyLeft + 3f, 92f)
private val RightShoulder = Offset(BodyLeft + BodyWidth - 3f, 88f)
private val Cone = Offset(CenterX, 107f)
private val QuestionMark = Offset(58f, 18f)

/** Ein „Hmm" dauert so lange: einmal zum Stecker schauen, einmal am Kopf kratzen. */
private const val PonderSeconds = 4.2f
private const val SwaySeconds = 3.2f
private const val BlinkSeconds = 3.7f
private const val ScratchesPerSecond = 5f

private val BodyColor = Color(0xFF9F87FF)
private val BodyOutline = darker(BodyColor)
private val FeetColor = Color(0xFF7A5CE6)
private val FaceColor = Color(0xFF2B2440)
private val CheekColor = Color(0xFFFF8FB8)
private val CableColor = Color(0xFF5E5A73)
private val PlugColor = Color(0xFFF7F5FF)
private val PlugOutline = Color(0xFF9E99B3)
private val PinColor = Color(0xFFC9C4D9)
private val QuestionColor = Color(0xFFFF8A3D)
private val QuestionOutline = darker(QuestionColor)
private val HeartColor = Color(0xFFFF4F81)

private fun darker(color: Color) = lerp(color, Color.Black, 0.25f)

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Die animierte Figur für Fehlermeldungen. Läuft endlos, solange sie zu sehen
 * ist; Antippen lässt sie hüpfen und lachen, und ein Herz steigt auf. Seitenverhältnis
 * etwa 17 : 15 — bei anderem Format wird sie mittig eingepasst.
 */
@Composable
fun ErrorBuddy(modifier: Modifier = Modifier) {
    val clock = produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    var tappedAt by remember { mutableStateOf<Float?>(null) }

    Canvas(modifier = modifier.pointerInput(Unit) { detectTapGestures { tappedAt = clock.value } }) {
        val t = clock.value
        val sinceTap = tappedAt?.let { t - it }
        val u = min(size.width / SceneWidth, size.height / SceneHeight)
        translate(left = (size.width - SceneWidth * u) / 2f, top = (size.height - SceneHeight * u) / 2f) {
            scale(u, pivot = Offset.Zero) {
                drawScene(t, hopHeight(sinceTap) * 0.7f, heartProgress(sinceTap))
            }
        }
    }
}

/** Die ganze Szene in Einheiten von [SceneWidth] × [SceneHeight]. */
private fun DrawScope.drawScene(t: Float, hop: Float, heart: Float?) {
    // −1: schaut auf den Stecker, 1: kratzt sich am Kopf; dazwischen sanfte Übergänge
    val ponder = (sin(2f * PI.toFloat() * t / PonderSeconds) * 2.5f).coerceIn(-1f, 1f)
    val scratching = smoothstep((ponder + 1f) / 2f)
    val sway = sin(2f * PI.toFloat() * t / SwaySeconds)

    // Schatten bleibt am Boden und wird beim Hüpfen kleiner
    val shadowWidth = 76f * (1f - hop / 60f)
    drawOval(
        Color.Black.copy(alpha = 0.15f),
        Offset(CenterX - shadowWidth / 2f, GroundY - 4f),
        Size(shadowWidth, 8f)
    )

    translate(top = -hop) {
        // Wiegt sich ratlos hin und her, zum Stecker hin etwas mehr
        rotate(sway * 3.5f - (1f - scratching) * 2.5f, pivot = Offset(CenterX, GroundY)) {
            // Nach einem Tipp freut er sich kurz
            drawBuddy(t, scratching, happy = heart != null)
        }
        drawQuestionMark(t, scratching)
    }

    heart?.let { drawBuddyHeart(it) }
}

private fun DrawScope.drawBuddy(t: Float, scratching: Float, happy: Boolean) {
    val outline = Stroke(width = 2.2f)
    val holding = 1f - scratching

    // Hand mit dem Stecker: hängt locker herunter, beim Hinschauen hält er ihn hoch
    val plugHand = lerp(Offset(30f, 103f), Offset(27f, 84f), holding)
    val plugAngle = -12f + sin(2f * PI.toFloat() * t / 1.6f) * 9f
    val plugBottom = plugHand + rotated(Offset(0f, 3f), plugAngle)

    drawCable(plugBottom)

    // Füße
    for (x in listOf(CenterX - 15f, CenterX + 15f)) {
        val topLeft = Offset(x - 11f, GroundY - 13.5f)
        drawOval(FeetColor, topLeft, Size(22f, 11f))
        drawOval(darker(FeetColor), topLeft, Size(22f, 11f), style = outline)
    }

    // Körper mit Glanzlicht
    val bodyTopLeft = Offset(BodyLeft, BodyTop)
    val bodySize = Size(BodyWidth, BodyHeight)
    drawRoundRect(BodyColor, bodyTopLeft, bodySize, CornerRadius(24f))
    drawRoundRect(BodyOutline, bodyTopLeft, bodySize, CornerRadius(24f), style = outline)
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(BodyLeft + 9f, BodyTop + 6f), Size(18f, 6f), CornerRadius(3f))

    // Lautsprecher-Membran als Bauch
    drawCircle(Color.Black.copy(alpha = 0.16f), 14f, Cone)
    drawCircle(Color.White.copy(alpha = 0.45f), 14f, Cone, style = Stroke(width = 2f))
    drawCircle(Color.Black.copy(alpha = 0.28f), 6f, Cone)
    drawCircle(Color.White.copy(alpha = 0.4f), 1.6f, Cone + Offset(-2f, -2f))

    drawFace(t, scratching, happy)

    drawPlugArm(plugHand, plugAngle)
    drawScratchingArm(t, scratching)
}

/** Kabel vom Rücken des Lautsprechers in einer lockeren Schlaufe über den Boden zum Stecker. */
private fun DrawScope.drawCable(plugBottom: Offset) {
    val cable = Path().apply {
        moveTo(BodyLeft + 12f, BodyTop + BodyHeight - 6f)
        cubicTo(42f, GroundY + 3f, 14f, GroundY + 2f, 13f, 126f)
        cubicTo(12f, 116f, plugBottom.x - 6f, plugBottom.y + 16f, plugBottom.x, plugBottom.y)
    }
    drawPath(cable, CableColor, style = Stroke(width = 3.4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFace(t: Float, scratching: Float, happy: Boolean) {
    val line = Stroke(width = 2.2f, cap = StrokeCap.Round)
    // Blick: zum Stecker links unten oder nach oben zur kratzenden Hand
    val look = lerp(Offset(-2.2f, 1.4f), Offset(1.6f, -1.6f), scratching)
    val blinkPhase = t % BlinkSeconds
    val open = if (blinkPhase > BlinkSeconds - 0.16f) 0.12f else 1f

    for (x in listOf(CenterX - 13f, CenterX + 13f)) {
        if (happy) {
            // Lachende Augen als Bögen ^ ^
            val arc = Path().apply {
                moveTo(x - 5f, 67.5f)
                quadraticBezierTo(x, 60f, x + 5f, 67.5f)
            }
            drawPath(arc, FaceColor, style = Stroke(width = 2.8f, cap = StrokeCap.Round))
            continue
        }
        val eye = Offset(x, 66f) + look
        val height = 14f * open
        drawOval(FaceColor, Offset(eye.x - 5.5f, eye.y - height / 2f), Size(11f, height))
        if (open > 0.5f) {
            drawCircle(Color.White, 2.3f, eye + Offset(-1.7f, -2.6f))
            drawCircle(Color.White, 1.1f, eye + Offset(1.9f, 2.4f))
        }
    }

    // Augenbrauen: die linke geht beim Kratzen ratlos hoch
    val raise = if (happy) 0f else scratching
    val leftBrow = Path().apply {
        moveTo(CenterX - 19f, 54f - 2.5f * raise)
        quadraticBezierTo(CenterX - 13f, 49f - 4f * raise, CenterX - 7f, 53f - 2f * raise)
    }
    val rightBrow = Path().apply {
        moveTo(CenterX + 7f, 53f)
        quadraticBezierTo(CenterX + 13f, 52f, CenterX + 19f, 55f)
    }
    drawPath(leftBrow, FaceColor, style = line)
    drawPath(rightBrow, FaceColor, style = line)

    // Rosige Bäckchen
    for (x in listOf(CenterX - 22f, CenterX + 22f)) {
        drawOval(CheekColor.copy(alpha = 0.75f), Offset(x - 5.5f, 74f), Size(11f, 6f))
    }

    // Unsicherer Wellen-Mund, nach einem Tipp ein Lächeln
    val mouth = Path().apply {
        moveTo(CenterX - 6f, 80f)
        if (happy) {
            quadraticBezierTo(CenterX, 87f, CenterX + 6f, 80f)
        } else {
            quadraticBezierTo(CenterX - 3f, 77f, CenterX, 80f)
            quadraticBezierTo(CenterX + 3f, 83f, CenterX + 6f, 80f)
        }
    }
    drawPath(mouth, FaceColor, style = line)
}

/** Linker Arm mit dem Stecker in der Pfote; der Stecker schaukelt leicht. */
private fun DrawScope.drawPlugArm(hand: Offset, plugAngle: Float) {
    drawLimb(listOf(LeftShoulder, hand))

    rotate(plugAngle, pivot = hand) {
        // Die Pfote hält den Stecker unten fest, die Stifte zeigen nach oben
        for (x in listOf(-3.8f, 3.8f)) {
            val pinTopLeft = Offset(hand.x + x - 1.4f, hand.y - 24f)
            drawRoundRect(PinColor, pinTopLeft, Size(2.8f, 9f), CornerRadius(1.4f))
            drawRoundRect(PlugOutline, pinTopLeft, Size(2.8f, 9f), CornerRadius(1.4f), style = Stroke(width = 1f))
        }
        val plugTopLeft = Offset(hand.x - 7.5f, hand.y - 17f)
        drawRoundRect(PlugColor, plugTopLeft, Size(15f, 17f), CornerRadius(4.5f))
        drawRoundRect(PlugOutline, plugTopLeft, Size(15f, 17f), CornerRadius(4.5f), style = Stroke(width = 1.6f))
        drawLine(PlugOutline, Offset(hand.x - 4f, hand.y - 11f), Offset(hand.x + 4f, hand.y - 11f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    }
    drawPaw(hand)
}

/** Rechter Arm: kratzt sich oben am Kopf oder hängt locker herunter. */
private fun DrawScope.drawScratchingArm(t: Float, scratching: Float) {
    val wiggle = sin(2f * PI.toFloat() * t * ScratchesPerSecond) * scratching
    val scratchHand = Offset(116f + wiggle * 2.6f, 41f + cos(2f * PI.toFloat() * t * ScratchesPerSecond) * scratching * 1.2f)
    val restHand = Offset(139f, 106f)
    val hand = lerp(restHand, scratchHand, scratching)
    val elbow = lerp(Offset(132f, 98f), Offset(139f, 65f), scratching)
    drawLimb(listOf(RightShoulder, elbow, hand))
    drawPaw(hand)
}

/** Arm als dicke, runde Linie mit dunklerem Rand — wie bei den Profil-Tieren. */
private fun DrawScope.drawLimb(points: List<Offset>) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, BodyOutline, style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, BodyColor, style = Stroke(width = 8.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPaw(at: Offset) {
    drawCircle(BodyColor, 5.4f, at)
    drawCircle(BodyOutline, 5.4f, at, style = Stroke(width = 1.4f))
}

/** Fragezeichen über dem Kopf: wippt und wackelt, beim Kratzen wird es größer. */
private fun DrawScope.drawQuestionMark(t: Float, scratching: Float) {
    val bob = sin(2f * PI.toFloat() * t / 1.3f) * 2f
    val tilt = sin(2f * PI.toFloat() * t / 2.6f) * 12f
    val grow = 0.8f + 0.25f * scratching
    val center = QuestionMark + Offset(0f, bob)

    rotate(tilt, pivot = center) {
        scale(grow, pivot = center) {
            val hook = Path().apply {
                moveTo(center.x - 6.5f, center.y - 6f)
                cubicTo(center.x - 6.5f, center.y - 14f, center.x + 6.5f, center.y - 14f, center.x + 6.5f, center.y - 6f)
                cubicTo(center.x + 6.5f, center.y - 1f, center.x, center.y - 0.5f, center.x, center.y + 4.5f)
            }
            drawPath(hook, QuestionOutline, style = Stroke(width = 7.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(hook, QuestionColor, style = Stroke(width = 4.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val dot = center + Offset(0f, 11.5f)
            drawCircle(QuestionOutline, 3.9f, dot)
            drawCircle(QuestionColor, 2.6f, dot)
        }
    }
}

/** Herz, das nach einem Tipp über dem Kopf aufsteigt und verblasst. */
private fun DrawScope.drawBuddyHeart(progress: Float) {
    val x = CenterX + 18f
    val y = BodyTop - 14f - 28f * progress
    val r = 6f + 4f * progress
    val heart = Path().apply {
        moveTo(x, y + r)
        cubicTo(x - r * 1.6f, y + r * 0.1f, x - r * 0.9f, y - r * 1.1f, x, y - r * 0.35f)
        cubicTo(x + r * 0.9f, y - r * 1.1f, x + r * 1.6f, y + r * 0.1f, x, y + r)
        close()
    }
    drawPath(heart, HeartColor.copy(alpha = 1f - progress * progress))
}

private fun rotated(offset: Offset, degrees: Float): Offset {
    val rad = degrees * PI.toFloat() / 180f
    return Offset(offset.x * cos(rad) - offset.y * sin(rad), offset.x * sin(rad) + offset.y * cos(rad))
}
