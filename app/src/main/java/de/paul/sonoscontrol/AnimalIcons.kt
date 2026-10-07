package de.paul.sonoscontrol

import androidx.compose.ui.graphics.vector.ImageVector

/*
 * Selbst gezeichnete, mehrfarbige Tiergesichter (24×24-Viewport) für die
 * Kinder-Profile — im selben Stil wie Einhorn und Pikachu in CharacterIcons.kt.
 */

private const val DARK = 0xFF2B2B2B
private const val WHITE = 0xFFFFFFFF
private const val BLUSH = 0xFFF8A5C2

/** Dunkles Auge mit kleinem Glanzpunkt. */
private fun ImageVector.Builder.eye(cx: Float, cy: Float, r: Float = 0.9f) {
    fill(DARK) { circle(cx, cy, r) }
    fill(WHITE) { circle(cx - r * 0.35f, cy - r * 0.35f, r * 0.38f) }
}

private fun ImageVector.Builder.eyes(leftX: Float, rightX: Float, y: Float, r: Float = 0.9f) {
    eye(leftX, y, r)
    eye(rightX, y, r)
}

private fun ImageVector.Builder.cheeks(leftX: Float, rightX: Float, y: Float, r: Float = 0.9f) {
    fill(BLUSH) { circle(leftX, y, r) }
    fill(BLUSH) { circle(rightX, y, r) }
}

/** Kleiner „w"-Mund unter der Nase. */
private fun ImageVector.Builder.catMouth(y: Float, color: Long = DARK) {
    line(color, 0.45f) {
        moveTo(10.8f, y)
        quadTo(11.4f, y + 0.8f, 12f, y + 0.2f)
        quadTo(12.6f, y + 0.8f, 13.2f, y)
    }
}

val CatIcon: ImageVector by lazy {
    drawnIcon("Cat") {
        val fur = 0xFFFFA94D
        val stripe = 0xFFE07B1A
        // Spitze Ohren mit rosa Innenseite
        fill(fur) { moveTo(4.8f, 11.0f); lineTo(5.4f, 3.0f); lineTo(10.6f, 7.0f); close() }
        fill(fur) { moveTo(19.2f, 11.0f); lineTo(18.6f, 3.0f); lineTo(13.4f, 7.0f); close() }
        fill(0xFFFFC1D6) { moveTo(6.2f, 9.0f); lineTo(6.5f, 5.0f); lineTo(9.2f, 7.2f); close() }
        fill(0xFFFFC1D6) { moveTo(17.8f, 9.0f); lineTo(17.5f, 5.0f); lineTo(14.8f, 7.2f); close() }

        fill(fur) { ellipse(12f, 13.8f, 7.6f, 6.6f) }
        // Streifen auf der Stirn
        line(stripe, 0.6f) { moveTo(12f, 7.6f); lineTo(12f, 9.4f) }
        line(stripe, 0.6f) { moveTo(10.2f, 7.9f); lineTo(10.7f, 9.3f) }
        line(stripe, 0.6f) { moveTo(13.8f, 7.9f); lineTo(13.3f, 9.3f) }

        fill(0xFFFFF3E0) { ellipse(10.7f, 16.6f, 1.9f, 1.4f) }
        fill(0xFFFFF3E0) { ellipse(13.3f, 16.6f, 1.9f, 1.4f) }
        eyes(9.0f, 15.0f, 12.6f, 1.0f)
        fill(0xFFF06292) { moveTo(11.1f, 15.0f); lineTo(12.9f, 15.0f); lineTo(12f, 16.0f); close() }
        catMouth(16.1f)

        // Schnurrhaare
        line(DARK, 0.3f) { moveTo(8.4f, 16.2f); lineTo(4.4f, 15.4f) }
        line(DARK, 0.3f) { moveTo(8.4f, 17.0f); lineTo(4.6f, 17.8f) }
        line(DARK, 0.3f) { moveTo(15.6f, 16.2f); lineTo(19.6f, 15.4f) }
        line(DARK, 0.3f) { moveTo(15.6f, 17.0f); lineTo(19.4f, 17.8f) }
    }
}

val DogIcon: ImageVector by lazy {
    drawnIcon("Dog") {
        val fur = 0xFFD9A86C
        val ear = 0xFF8D5A3B
        fill(fur) { ellipse(12f, 13.2f, 6.6f, 7.0f) }
        // Fleck um ein Auge
        fill(0xFFB07A45) { ellipse(14.9f, 11.6f, 2.0f, 2.1f) }
        // Schlappohren
        fill(ear) { ellipse(5.4f, 11.6f, 2.3f, 4.6f) }
        fill(ear) { ellipse(18.6f, 11.6f, 2.3f, 4.6f) }

        fill(0xFFF5E1C0) { ellipse(12f, 16.4f, 3.8f, 2.9f) }
        // Zunge
        fill(0xFFF06292) { ellipse(12f, 18.6f, 1.0f, 1.2f) }
        line(DARK, 0.45f) {
            moveTo(12f, 15.8f); lineTo(12f, 17.0f)
            moveTo(10.4f, 16.6f); quadTo(12f, 18.4f, 13.6f, 16.6f)
        }
        fill(DARK) { ellipse(12f, 15.0f, 1.4f, 1.0f) }
        eyes(9.3f, 14.7f, 11.8f, 0.95f)
    }
}

val BearIcon: ImageVector by lazy {
    drawnIcon("Bear") {
        val fur = 0xFF9C6B4E
        val light = 0xFFE3C3A3
        fill(fur) { circle(6.2f, 7.4f, 2.7f) }
        fill(fur) { circle(17.8f, 7.4f, 2.7f) }
        fill(light) { circle(6.2f, 7.4f, 1.4f) }
        fill(light) { circle(17.8f, 7.4f, 1.4f) }

        fill(fur) { ellipse(12f, 13.6f, 7.4f, 6.8f) }
        fill(light) { ellipse(12f, 16.4f, 3.4f, 2.6f) }
        fill(DARK) { ellipse(12f, 15.2f, 1.4f, 1.0f) }
        line(DARK, 0.45f) { moveTo(12f, 16.0f); lineTo(12f, 16.8f) }
        catMouth(16.8f)
        eyes(9.0f, 15.0f, 12.4f)
        cheeks(7.0f, 17.0f, 15.4f)
    }
}

val PandaIcon: ImageVector by lazy {
    drawnIcon("Panda") {
        fill(DARK) { circle(6.0f, 7.2f, 2.6f) }
        fill(DARK) { circle(18.0f, 7.2f, 2.6f) }
        fill(WHITE) { ellipse(12f, 13.6f, 7.6f, 6.8f) }

        // Augenflecken
        fill(DARK) { ellipse(8.8f, 12.6f, 2.0f, 2.5f) }
        fill(DARK) { ellipse(15.2f, 12.6f, 2.0f, 2.5f) }
        fill(WHITE) { circle(9.0f, 12.2f, 0.85f) }
        fill(WHITE) { circle(15.0f, 12.2f, 0.85f) }
        fill(DARK) { circle(9.1f, 12.4f, 0.45f) }
        fill(DARK) { circle(14.9f, 12.4f, 0.45f) }

        fill(DARK) { ellipse(12f, 15.6f, 1.3f, 0.85f) }
        catMouth(16.5f)
        cheeks(6.6f, 17.4f, 16.0f)
    }
}

val FoxIcon: ImageVector by lazy {
    drawnIcon("Fox") {
        val fur = 0xFFFF7A33
        val dark = 0xFF5D3A1A
        fill(fur) { moveTo(4.4f, 11.0f); lineTo(4.6f, 2.6f); lineTo(10.2f, 7.0f); close() }
        fill(fur) { moveTo(19.6f, 11.0f); lineTo(19.4f, 2.6f); lineTo(13.8f, 7.0f); close() }
        fill(dark) { moveTo(5.5f, 8.8f); lineTo(5.6f, 4.8f); lineTo(8.6f, 7.1f); close() }
        fill(dark) { moveTo(18.5f, 8.8f); lineTo(18.4f, 4.8f); lineTo(15.4f, 7.1f); close() }

        // Kopf läuft unten spitz zur Schnauze zu
        fill(fur) { ellipse(12f, 12.4f, 7.2f, 5.4f) }
        fill(fur) { moveTo(4.9f, 13.0f); lineTo(12f, 20.8f); lineTo(19.1f, 13.0f); close() }
        // Weiße Wangen
        fill(0xFFFFF8F0) {
            moveTo(4.9f, 13.2f)
            quadTo(9.2f, 12.4f, 12f, 15.6f)
            quadTo(14.8f, 12.4f, 19.1f, 13.2f)
            lineTo(12f, 20.8f)
            close()
        }
        fill(DARK) { circle(12f, 20.0f, 0.95f) }
        eyes(9.2f, 14.8f, 11.6f, 0.9f)
    }
}

val FrogIcon: ImageVector by lazy {
    drawnIcon("Frog") {
        val green = 0xFF66BB6A
        fill(green) { circle(7.4f, 8.6f, 3.3f) }
        fill(green) { circle(16.6f, 8.6f, 3.3f) }
        fill(green) { ellipse(12f, 15.0f, 8.6f, 5.9f) }

        fill(WHITE) { circle(7.4f, 8.6f, 2.3f) }
        fill(WHITE) { circle(16.6f, 8.6f, 2.3f) }
        eye(7.8f, 8.9f, 1.15f)
        eye(16.2f, 8.9f, 1.15f)

        fill(0xFF2E7D32) { circle(11.1f, 13.0f, 0.3f) }
        fill(0xFF2E7D32) { circle(12.9f, 13.0f, 0.3f) }
        line(DARK, 0.6f) { moveTo(6.8f, 15.2f); quadTo(12f, 19.8f, 17.2f, 15.2f) }
        cheeks(5.6f, 18.4f, 16.6f, 1.0f)
    }
}

val LionIcon: ImageVector by lazy {
    drawnIcon("Lion") {
        val mane = 0xFFE07B24
        val face = 0xFFFFCC4D
        // Zottelige Mähne aus vielen Kreisen
        fill(mane) {
            circle(12f, 12.8f, 8.2f)
            circle(12f, 4.4f, 2.6f)
            circle(17.0f, 6.0f, 2.6f)
            circle(20.0f, 10.2f, 2.6f)
            circle(20.0f, 15.4f, 2.6f)
            circle(17.0f, 19.6f, 2.6f)
            circle(12f, 21.2f, 2.6f)
            circle(7.0f, 19.6f, 2.6f)
            circle(4.0f, 15.4f, 2.6f)
            circle(4.0f, 10.2f, 2.6f)
            circle(7.0f, 6.0f, 2.6f)
        }
        fill(face) { circle(7.6f, 7.8f, 1.6f) }
        fill(face) { circle(16.4f, 7.8f, 1.6f) }
        fill(face) { circle(12f, 13.2f, 6.0f) }

        fill(0xFFFFF1C1) { ellipse(10.9f, 16.0f, 1.7f, 1.3f) }
        fill(0xFFFFF1C1) { ellipse(13.1f, 16.0f, 1.7f, 1.3f) }
        fill(0xFF6D3B1A) { moveTo(10.8f, 14.4f); lineTo(13.2f, 14.4f); lineTo(12f, 15.7f); close() }
        eyes(9.6f, 14.4f, 12.0f, 0.85f)
    }
}

val PigIcon: ImageVector by lazy {
    drawnIcon("Pig") {
        val skin = 0xFFF8BBD0
        val dark = 0xFFF06292
        fill(dark) { moveTo(4.8f, 9.6f); lineTo(5.4f, 3.6f); lineTo(10.0f, 6.8f); close() }
        fill(dark) { moveTo(19.2f, 9.6f); lineTo(18.6f, 3.6f); lineTo(14.0f, 6.8f); close() }
        fill(skin) { ellipse(12f, 13.6f, 7.8f, 6.9f) }

        fill(dark) { ellipse(12f, 15.4f, 3.1f, 2.2f) }
        fill(0xFF880E4F) { ellipse(11.0f, 15.4f, 0.5f, 0.8f) }
        fill(0xFF880E4F) { ellipse(13.0f, 15.4f, 0.5f, 0.8f) }
        eyes(8.9f, 15.1f, 11.6f, 0.9f)
        cheeks(6.6f, 17.4f, 15.6f, 1.0f)
    }
}

val MouseIcon: ImageVector by lazy {
    drawnIcon("Mouse") {
        val fur = 0xFFB0B0B8
        fill(fur) { circle(5.6f, 7.4f, 3.8f) }
        fill(fur) { circle(18.4f, 7.4f, 3.8f) }
        fill(0xFFF8BBD0) { circle(5.6f, 7.4f, 2.4f) }
        fill(0xFFF8BBD0) { circle(18.4f, 7.4f, 2.4f) }

        fill(fur) { ellipse(12f, 14.4f, 6.6f, 6.2f) }
        // Hasenzähnchen
        fill(WHITE) { moveTo(11.3f, 17.6f); lineTo(12.7f, 17.6f); lineTo(12.7f, 19.0f); lineTo(11.3f, 19.0f); close() }
        line(DARK, 0.3f) { moveTo(12f, 17.6f); lineTo(12f, 19.0f) }
        catMouth(16.9f)
        fill(0xFFF06292) { circle(12f, 16.4f, 0.95f) }
        eyes(9.6f, 14.4f, 13.2f, 0.9f)

        line(DARK, 0.3f) { moveTo(9.8f, 16.6f); lineTo(6.0f, 15.8f) }
        line(DARK, 0.3f) { moveTo(9.8f, 17.4f); lineTo(6.2f, 18.2f) }
        line(DARK, 0.3f) { moveTo(14.2f, 16.6f); lineTo(18.0f, 15.8f) }
        line(DARK, 0.3f) { moveTo(14.2f, 17.4f); lineTo(17.8f, 18.2f) }
    }
}

val BunnyIcon: ImageVector by lazy {
    drawnIcon("Bunny") {
        val outline = 0xFFE0E0E0
        val pink = 0xFFF8BBD0
        fill(outline) { ellipse(9.0f, 6.0f, 2.1f, 5.4f) }
        fill(outline) { ellipse(15.0f, 6.0f, 2.1f, 5.4f) }
        fill(WHITE) { ellipse(9.0f, 6.0f, 1.75f, 5.05f) }
        fill(WHITE) { ellipse(15.0f, 6.0f, 1.75f, 5.05f) }
        fill(pink) { ellipse(9.0f, 6.4f, 0.85f, 3.8f) }
        fill(pink) { ellipse(15.0f, 6.4f, 0.85f, 3.8f) }

        fill(outline) { ellipse(12f, 15.0f, 6.9f, 6.2f) }
        fill(WHITE) { ellipse(12f, 15.0f, 6.5f, 5.8f) }
        eyes(9.6f, 14.4f, 13.8f, 0.85f)
        fill(0xFFF06292) { ellipse(12f, 15.9f, 0.85f, 0.6f) }
        catMouth(16.5f)
        cheeks(7.6f, 16.4f, 16.0f)
    }
}

val OwlIcon: ImageVector by lazy {
    drawnIcon("Owl") {
        val feather = 0xFF8D6E63
        val light = 0xFFFFE0B2
        fill(feather) { moveTo(4.4f, 9.4f); lineTo(4.8f, 3.4f); lineTo(9.2f, 6.4f); close() }
        fill(feather) { moveTo(19.6f, 9.4f); lineTo(19.2f, 3.4f); lineTo(14.8f, 6.4f); close() }
        fill(feather) { ellipse(12f, 13.4f, 7.8f, 7.8f) }

        fill(light) { circle(8.7f, 11.4f, 3.3f) }
        fill(light) { circle(15.3f, 11.4f, 3.3f) }
        eyes(8.7f, 15.3f, 11.4f, 1.8f)

        fill(0xFFFFA726) { moveTo(10.9f, 13.4f); lineTo(13.1f, 13.4f); lineTo(12f, 15.6f); close() }
        // Bauchfedern
        line(light, 0.4f) { moveTo(9.4f, 17.4f); quadTo(10.2f, 18.3f, 11.0f, 17.4f) }
        line(light, 0.4f) { moveTo(13.0f, 17.4f); quadTo(13.8f, 18.3f, 14.6f, 17.4f) }
        line(light, 0.4f) { moveTo(11.2f, 19.0f); quadTo(12f, 19.9f, 12.8f, 19.0f) }
    }
}

val PenguinIcon: ImageVector by lazy {
    drawnIcon("Penguin") {
        fill(0xFF37474F) { ellipse(12f, 12.8f, 7.8f, 8.2f) }
        fill(WHITE) {
            circle(9.5f, 12.6f, 3.3f)
            circle(14.5f, 12.6f, 3.3f)
        }
        fill(WHITE) { ellipse(12f, 16.0f, 4.8f, 4.0f) }
        eyes(9.6f, 14.4f, 12.2f, 0.9f)
        fill(0xFFFFA000) { moveTo(10.5f, 14.4f); lineTo(13.5f, 14.4f); lineTo(12f, 16.6f); close() }
        cheeks(7.6f, 16.4f, 15.4f, 0.85f)
    }
}

val MonkeyIcon: ImageVector by lazy {
    drawnIcon("Monkey") {
        val fur = 0xFF8D6E63
        val light = 0xFFFFDDB8
        fill(fur) { circle(4.6f, 12.4f, 2.5f) }
        fill(fur) { circle(19.4f, 12.4f, 2.5f) }
        fill(0xFFFFCCBC) { circle(4.6f, 12.4f, 1.3f) }
        fill(0xFFFFCCBC) { circle(19.4f, 12.4f, 1.3f) }
        fill(fur) { circle(12f, 12.4f, 7.2f) }

        fill(light) {
            circle(9.8f, 11.4f, 2.7f)
            circle(14.2f, 11.4f, 2.7f)
        }
        fill(light) { ellipse(12f, 15.4f, 4.6f, 3.5f) }
        eyes(9.8f, 14.2f, 11.4f, 0.9f)
        fill(0xFF5D4037) { circle(11.3f, 13.9f, 0.32f) }
        fill(0xFF5D4037) { circle(12.7f, 13.9f, 0.32f) }
        line(DARK, 0.5f) { moveTo(9.6f, 15.4f); quadTo(12f, 18.0f, 14.4f, 15.4f) }
    }
}

val KoalaIcon: ImageVector by lazy {
    drawnIcon("Koala") {
        val fur = 0xFF90A4AE
        fill(fur) { circle(5.0f, 8.4f, 3.7f) }
        fill(fur) { circle(19.0f, 8.4f, 3.7f) }
        fill(0xFFECEFF1) { circle(5.0f, 8.4f, 2.2f) }
        fill(0xFFECEFF1) { circle(19.0f, 8.4f, 2.2f) }

        fill(fur) { ellipse(12f, 13.8f, 7.0f, 6.8f) }
        eyes(8.8f, 15.2f, 12.2f, 0.85f)
        fill(0xFF37474F) { ellipse(12f, 14.6f, 1.7f, 2.3f) }
        line(DARK, 0.45f) { moveTo(11.0f, 17.6f); quadTo(12f, 18.4f, 13.0f, 17.6f) }
        cheeks(7.4f, 16.6f, 15.6f)
    }
}

val ChickIcon: ImageVector by lazy {
    drawnIcon("Chick") {
        val yellow = 0xFFFFE082
        // Haarbüschel
        line(0xFFFFB300, 0.6f) {
            moveTo(12f, 5.4f); quadTo(10.8f, 3.4f, 12.4f, 2.6f)
            moveTo(12f, 5.4f); quadTo(13.6f, 3.6f, 14.8f, 4.2f)
        }
        // Flügel
        fill(0xFFFFCA28) { ellipse(4.6f, 15.0f, 1.7f, 2.9f) }
        fill(0xFFFFCA28) { ellipse(19.4f, 15.0f, 1.7f, 2.9f) }
        fill(yellow) { circle(12f, 13.2f, 8.0f) }

        eyes(9.4f, 14.6f, 11.6f, 1.0f)
        fill(0xFFFF9800) { moveTo(10.6f, 13.6f); lineTo(13.4f, 13.6f); lineTo(12f, 15.8f); close() }
        cheeks(7.6f, 16.4f, 15.0f)
    }
}

val LadybugIcon: ImageVector by lazy {
    drawnIcon("Ladybug") {
        // Fühler
        line(DARK, 0.45f) { moveTo(10.6f, 4.6f); quadTo(9.6f, 2.6f, 8.0f, 2.2f) }
        line(DARK, 0.45f) { moveTo(13.4f, 4.6f); quadTo(14.4f, 2.6f, 16.0f, 2.2f) }
        fill(DARK) { circle(8.0f, 2.2f, 0.65f) }
        fill(DARK) { circle(16.0f, 2.2f, 0.65f) }
        fill(DARK) { ellipse(12f, 6.4f, 4.0f, 2.8f) }
        fill(WHITE) { circle(10.5f, 5.5f, 0.6f) }
        fill(WHITE) { circle(13.5f, 5.5f, 0.6f) }

        fill(0xFFE53935) { circle(12f, 14.4f, 7.6f) }
        line(DARK, 0.5f) { moveTo(12f, 6.8f); lineTo(12f, 22.0f) }
        fill(DARK) {
            circle(8.8f, 11.6f, 1.3f)
            circle(15.2f, 11.6f, 1.3f)
            circle(7.9f, 15.8f, 1.4f)
            circle(16.1f, 15.8f, 1.4f)
            circle(10.2f, 19.2f, 1.1f)
            circle(13.8f, 19.2f, 1.1f)
        }
    }
}

/** Fröhliche Kuh: weißer Kopf mit schwarzen Flecken, Hörnchen und rosa Schnauze. */
val CowIcon: ImageVector by lazy {
    drawnIcon("Cow") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Hörner
        fill(0xFFF3D9A4) { moveTo(6.2f, 7.6f); quadTo(3.6f, 6.4f, 3.8f, 3.2f); quadTo(6.2f, 4.6f, 8.4f, 6.4f); close() }
        fill(0xFFF3D9A4) { moveTo(17.8f, 7.6f); quadTo(20.4f, 6.4f, 20.2f, 3.2f); quadTo(17.8f, 4.6f, 15.6f, 6.4f); close() }

        // Ohren
        fill(white, outline = 0xFFD7CCC8, outlineWidth = 0.35f) { ellipse(3.6f, 10.0f, 2.6f, 1.4f) }
        fill(white, outline = 0xFFD7CCC8, outlineWidth = 0.35f) { ellipse(20.4f, 10.0f, 2.6f, 1.4f) }
        fill(0xFFF8BBD0) { ellipse(3.4f, 10.0f, 1.5f, 0.7f) }
        fill(0xFFF8BBD0) { ellipse(20.6f, 10.0f, 1.5f, 0.7f) }

        // Kopf mit Flecken
        fill(white, outline = 0xFFD7CCC8, outlineWidth = 0.4f) { ellipse(12.0f, 11.6f, 6.4f, 6.6f) }
        fill(dark) { moveTo(6.3f, 9.8f); quadTo(6.8f, 6.2f, 10.0f, 5.3f); quadTo(10.6f, 7.6f, 9.4f, 9.0f); quadTo(7.8f, 8.8f, 6.3f, 9.8f); close() }
        fill(dark) { ellipse(16.0f, 7.9f, 1.5f, 1.1f) }

        // Haarbüschel
        fill(0xFF6D4C41) { moveTo(10.6f, 5.6f); quadTo(11.2f, 3.6f, 12.0f, 5.0f); quadTo(12.8f, 3.6f, 13.4f, 5.6f); close() }

        // Augen und Bäckchen
        fill(dark) { circle(9.3f, 11.2f, 1.15f) }
        fill(dark) { circle(14.7f, 11.2f, 1.15f) }
        fill(white) { circle(8.92f, 10.78f, 0.44f) }
        fill(white) { circle(14.32f, 10.78f, 0.44f) }
        fill(0xFFF48FB1) { circle(7.2f, 13.4f, 1.0f) }
        fill(0xFFF48FB1) { circle(16.8f, 13.4f, 1.0f) }

        // Schnauze mit Nasenlöchern und Lächeln
        fill(0xFFF8A5C2) { ellipse(12.0f, 17.4f, 5.0f, 3.6f) }
        fill(0xFFC2185B) { ellipse(10.0f, 16.6f, 0.75f, 0.55f) }
        fill(0xFFC2185B) { ellipse(14.0f, 16.6f, 0.75f, 0.55f) }
        line(0xFFAD1457, 0.5f) { moveTo(10.4f, 18.8f); quadTo(12.0f, 20.0f, 13.6f, 18.8f) }
    }
}

/** Wuscheliger weißer Hund mit hellbraunen Kippohren, Flecken um die Augen, weißer Blesse und Halsband. */
val FluffyDogIcon: ImageVector by lazy {
    drawnIcon("FluffyDog") {
        val tan = 0xFFC98B4F
        val earTip = 0xFF9C6236
        val outline = 0xFFD7CCC8

        // Kippohren: hochgestellt, die Spitzen nach vorn umgeklappt
        fill(tan) { moveTo(5.8f, 10.8f); lineTo(4.1f, 5.9f); quadTo(5.4f, 3.0f, 8.4f, 4.0f); lineTo(10.2f, 7.2f); close() }
        fill(tan) { moveTo(18.2f, 10.8f); lineTo(19.9f, 5.9f); quadTo(18.6f, 3.0f, 15.6f, 4.0f); lineTo(13.8f, 7.2f); close() }
        fill(earTip) { moveTo(4.1f, 5.9f); quadTo(5.4f, 3.0f, 8.4f, 4.0f); quadTo(7.6f, 6.6f, 5.6f, 8.6f); quadTo(4.6f, 7.4f, 4.1f, 5.9f); close() }
        fill(earTip) { moveTo(19.9f, 5.9f); quadTo(18.6f, 3.0f, 15.6f, 4.0f); quadTo(16.4f, 6.6f, 18.4f, 8.6f); quadTo(19.4f, 7.4f, 19.9f, 5.9f); close() }

        // Kopf mit wuscheligem Fell an Wangen und Kinn: erst die Kontur um alles, dann Weiß darüber
        fill(outline) {
            ellipse(12f, 13.4f, 7.3f, 7.1f)
            circle(5.5f, 15.0f, 1.8f)
            circle(6.3f, 17.4f, 1.9f)
            circle(8.3f, 19.3f, 1.9f)
            circle(12f, 20.3f, 1.9f)
            circle(15.7f, 19.3f, 1.9f)
            circle(17.7f, 17.4f, 1.9f)
            circle(18.5f, 15.0f, 1.8f)
        }
        fill(WHITE) { ellipse(12f, 13.4f, 6.9f, 6.7f) }

        // Hellbraune Flecken um die Augen — rechts größer und bis in die Wange —, dazwischen die weiße Blesse
        fill(tan) {
            moveTo(5.6f, 10.6f)
            arcTo(6.9f, 6.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 10.7f, y1 = 6.82f)
            quadTo(10.5f, 9.4f, 10.8f, 11.4f)
            quadTo(10.9f, 13.6f, 9.0f, 13.6f)
            quadTo(6.8f, 13.6f, 5.6f, 10.6f)
            close()
        }
        fill(tan) {
            moveTo(13.3f, 6.82f)
            arcTo(6.9f, 6.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18.6f, y1 = 15.4f)
            quadTo(16.4f, 15.6f, 14.9f, 14.3f)
            quadTo(13.1f, 13.2f, 13.2f, 11.2f)
            quadTo(13.5f, 9.4f, 13.3f, 6.82f)
            close()
        }

        // Wangenfell liegt über den Flecken
        fill(WHITE) {
            circle(5.5f, 15.0f, 1.4f)
            circle(6.3f, 17.4f, 1.5f)
            circle(8.3f, 19.3f, 1.5f)
            circle(12f, 20.3f, 1.5f)
            circle(15.7f, 19.3f, 1.5f)
            circle(17.7f, 17.4f, 1.5f)
            circle(18.5f, 15.0f, 1.4f)
        }

        // Schnauze
        fill(0xFFF4EEE8) { ellipse(12f, 16.6f, 3.3f, 2.5f) }
        eyes(9.2f, 14.8f, 12.0f, 0.95f)
        fill(DARK) { ellipse(12f, 15.2f, 1.5f, 1.05f) }
        fill(WHITE) { ellipse(11.5f, 14.85f, 0.45f, 0.28f) }
        line(DARK, 0.45f) {
            moveTo(12f, 16.1f); lineTo(12f, 16.9f)
            moveTo(10.5f, 16.7f); quadTo(12f, 18.2f, 13.5f, 16.7f)
        }
        cheeks(7.4f, 16.6f, 15.8f, 0.85f)

        // Graues Halsband mit Ring
        line(0xFF55606B, 1.0f) { moveTo(8.4f, 19.6f); quadTo(12f, 21.8f, 15.6f, 19.6f) }
        line(0xFFA7B4BE, 0.4f) { circle(12f, 21.8f, 0.7f) }
    }
}
