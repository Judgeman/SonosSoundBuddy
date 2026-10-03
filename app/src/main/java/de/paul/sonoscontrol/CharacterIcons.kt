package de.paul.sonoscontrol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * Selbst gezeichnete, mehrfarbige Figuren-Icons und das Platzhalter-Cover (24×24-Viewport), die es in den
 * Material Symbols nicht gibt. Sie werden ohne Tint gezeichnet, siehe SpeakerIcon.multicolor.
 */

internal fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

internal fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * rx, dy1 = 0f)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * rx, dy1 = 0f)
    close()
}

internal fun ImageVector.Builder.fill(color: Long, block: PathBuilder.() -> Unit) =
    path(fill = SolidColor(Color(color)), pathBuilder = block)

/** Gefüllte Fläche mit dünner Kontur, damit Weiß sich vom hellen Badge abhebt. */
internal fun ImageVector.Builder.fill(
    color: Long,
    outline: Long,
    outlineWidth: Float,
    block: PathBuilder.() -> Unit
) = path(
    fill = SolidColor(Color(color)),
    stroke = SolidColor(Color(outline)),
    strokeLineWidth = outlineWidth,
    pathBuilder = block
)

internal fun ImageVector.Builder.line(color: Long, width: Float, block: PathBuilder.() -> Unit) =
    path(
        stroke = SolidColor(Color(color)),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    )

internal fun drawnIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

/** Fröhliches Einhorn von vorne: weißer Kopf, goldenes Horn, Regenbogen-Mähne. */
val UnicornIcon: ImageVector by lazy {
    drawnIcon("Unicorn") {
        val white = 0xFFFFFFFF
        val outline = 0xFFD9CCEF
        val dark = 0xFF4A3B5C

        // Ohren
        fill(white) { moveTo(6.4f, 10.2f); lineTo(5.6f, 5.4f); lineTo(9.6f, 8.0f); close() }
        fill(white) { moveTo(17.6f, 10.2f); lineTo(18.4f, 5.4f); lineTo(14.4f, 8.0f); close() }
        fill(0xFFF8BBD0) { moveTo(6.9f, 9.0f); lineTo(6.5f, 6.6f); lineTo(8.6f, 8.0f); close() }
        fill(0xFFF8BBD0) { moveTo(17.1f, 9.0f); lineTo(17.5f, 6.6f); lineTo(15.4f, 8.0f); close() }

        // Kopf und Schnauze
        path(
            fill = SolidColor(Color(white)),
            stroke = SolidColor(Color(outline)),
            strokeLineWidth = 0.4f
        ) { ellipse(12f, 14.6f, 6.2f, 6.8f) }
        fill(0xFFF3E5F5) { ellipse(12f, 18.4f, 3.4f, 2.4f) }
        fill(0xFFCE93D8) { ellipse(10.8f, 17.7f, 0.42f, 0.3f) }
        fill(0xFFCE93D8) { ellipse(13.2f, 17.7f, 0.42f, 0.3f) }
        line(dark, 0.45f) { moveTo(10.9f, 19.2f); quadTo(12f, 20.0f, 13.1f, 19.2f) }

        // Regenbogen-Mähne über der Stirn
        fill(0xFFFF7EB3) { circle(9.0f, 8.4f, 2.0f) }
        fill(0xFFFFB74D) { circle(15.1f, 8.4f, 2.0f) }
        fill(0xFFBA68C8) { circle(6.6f, 10.4f, 1.7f) }
        fill(0xFF81D4FA) { circle(17.4f, 10.4f, 1.7f) }
        fill(0xFF9CCC65) { circle(12.0f, 8.0f, 1.9f) }

        // Horn mit Rillen
        fill(0xFFFFC83D) { moveTo(10.7f, 8.4f); lineTo(12f, 1.4f); lineTo(13.3f, 8.4f); close() }
        line(0xFFE6A100, 0.35f) { moveTo(11.1f, 6.6f); lineTo(12.8f, 5.9f) }
        line(0xFFE6A100, 0.35f) { moveTo(11.5f, 4.5f); lineTo(12.5f, 4.1f) }

        // Geschlossene, lachende Augen und Bäckchen
        line(dark, 0.6f) { moveTo(8.1f, 13.2f); quadTo(9.3f, 12.0f, 10.5f, 13.2f) }
        line(dark, 0.6f) { moveTo(13.5f, 13.2f); quadTo(14.7f, 12.0f, 15.9f, 13.2f) }
        fill(0xFFF8BBD0) { circle(7.7f, 15.2f, 1.0f) }
        fill(0xFFF8BBD0) { circle(16.3f, 15.2f, 1.0f) }
    }
}

/** Pikachu-Gesicht: gelber Kopf, Ohren mit schwarzen Spitzen, rote Bäckchen. */
val PikachuIcon: ImageVector by lazy {
    drawnIcon("Pikachu") {
        val yellow = 0xFFFFD93B
        val black = 0xFF2B2B2B

        // Lange Ohren mit schwarzen Spitzen
        fill(yellow) { moveTo(5.6f, 11.0f); lineTo(1.6f, 1.6f); lineTo(10.0f, 8.2f); close() }
        fill(black) { moveTo(1.6f, 1.6f); lineTo(3.2f, 5.4f); lineTo(5.0f, 4.0f); close() }
        fill(yellow) { moveTo(18.4f, 11.0f); lineTo(22.4f, 1.6f); lineTo(14.0f, 8.2f); close() }
        fill(black) { moveTo(22.4f, 1.6f); lineTo(20.8f, 5.4f); lineTo(19.0f, 4.0f); close() }

        // Kopf
        fill(yellow) { ellipse(12f, 14.6f, 8.2f, 6.9f) }

        // Augen mit Glanzpunkt
        fill(black) { circle(8.6f, 13.2f, 1.35f) }
        fill(black) { circle(15.4f, 13.2f, 1.35f) }
        fill(0xFFFFFFFF) { circle(8.2f, 12.7f, 0.5f) }
        fill(0xFFFFFFFF) { circle(15.0f, 12.7f, 0.5f) }

        // Nase und Mund
        fill(black) { ellipse(12f, 14.9f, 0.4f, 0.28f) }
        line(black, 0.45f) {
            moveTo(10.5f, 16.0f)
            quadTo(11.25f, 16.8f, 12f, 16.0f)
            quadTo(12.75f, 16.8f, 13.5f, 16.0f)
        }

        // Rote Bäckchen
        fill(0xFFE8443A) { circle(6.2f, 16.3f, 1.55f) }
        fill(0xFFE8443A) { circle(17.8f, 16.3f, 1.55f) }
    }
}

/** Buntes Platzhalter-Cover: Regenbogen, Wölkchen, Glitzer und lachende Noten (ohne Hintergrund). */
val DefaultCoverArt: ImageVector by lazy {
    drawnIcon("DefaultCover") {
        val white = 0xFFFFFFFF
        val dark = 0xFF3B2A4D

        // Regenbogen mit zwei Wölkchen
        line(0xFFFF5A5F, 1.3f) { moveTo(3.0f, 13.0f); curveTo(3.0f, 1.03f, 21.0f, 1.03f, 21.0f, 13.0f) }
        line(0xFFFFA62B, 1.3f) { moveTo(4.3f, 13.0f); curveTo(4.3f, 2.76f, 19.7f, 2.76f, 19.7f, 13.0f) }
        line(0xFFFFE14D, 1.3f) { moveTo(5.6f, 13.0f); curveTo(5.6f, 4.49f, 18.4f, 4.49f, 18.4f, 13.0f) }
        line(0xFF6EDB6A, 1.3f) { moveTo(6.9f, 13.0f); curveTo(6.9f, 6.22f, 17.1f, 6.22f, 17.1f, 13.0f) }
        line(0xFF4FC3F7, 1.3f) { moveTo(8.2f, 13.0f); curveTo(8.2f, 7.95f, 15.8f, 7.95f, 15.8f, 13.0f) }
        line(0xFF9C7BFF, 1.3f) { moveTo(9.5f, 13.0f); curveTo(9.5f, 9.68f, 14.5f, 9.68f, 14.5f, 13.0f) }
        fill(white) { circle(1.9f, 13.3f, 1.4f) }
        fill(white) { circle(4.5f, 13.3f, 1.4f) }
        fill(white) { circle(3.2f, 12.5f, 1.7f) }
        fill(white) { ellipse(3.2f, 13.9f, 2.6f, 0.9f) }
        fill(white) { circle(19.5f, 13.3f, 1.4f) }
        fill(white) { circle(22.1f, 13.3f, 1.4f) }
        fill(white) { circle(20.8f, 12.5f, 1.7f) }
        fill(white) { ellipse(20.8f, 13.9f, 2.6f, 0.9f) }

        // Glitzersterne
        fill(white) { moveTo(4.0f, 2.6f); quadTo(4.39f, 3.61f, 5.4f, 4.0f); quadTo(4.39f, 4.39f, 4.0f, 5.4f); quadTo(3.61f, 4.39f, 2.6f, 4.0f); quadTo(3.61f, 3.61f, 4.0f, 2.6f); close() }
        fill(0xFFFFF59D) { moveTo(20.4f, 4.2f); quadTo(20.68f, 4.92f, 21.4f, 5.2f); quadTo(20.68f, 5.48f, 20.4f, 6.2f); quadTo(20.12f, 5.48f, 19.4f, 5.2f); quadTo(20.12f, 4.92f, 20.4f, 4.2f); close() }
        fill(white) { moveTo(20.8f, 16.4f); quadTo(21.14f, 17.26f, 22.0f, 17.6f); quadTo(21.14f, 17.94f, 20.8f, 18.8f); quadTo(20.46f, 17.94f, 19.6f, 17.6f); quadTo(20.46f, 17.26f, 20.8f, 16.4f); close() }
        fill(0xFFFFF59D) { moveTo(3.6f, 19.5f); quadTo(3.85f, 20.15f, 4.5f, 20.4f); quadTo(3.85f, 20.65f, 3.6f, 21.3f); quadTo(3.35f, 20.65f, 2.7f, 20.4f); quadTo(3.35f, 20.15f, 3.6f, 19.5f); close() }

        // Kleine bunte Noten
        fill(0xFFFFE14D) { ellipse(6.2f, 18.4f, 0.85f, 0.68f) }
        line(0xFFFFE14D, 0.41f) { moveTo(6.92f, 18.22f); lineTo(6.92f, 15.34f); quadTo(7.82f, 16.06f, 8.18f, 16.78f) }
        fill(0xFF6EDB6A) { ellipse(19.0f, 22.0f, 0.76f, 0.6f) }
        line(0xFF6EDB6A, 0.36f) { moveTo(19.64f, 21.84f); lineTo(19.64f, 19.28f); quadTo(20.44f, 19.92f, 20.76f, 20.56f) }

        // Große Doppelnote mit lachenden Gesichtern
        fill(0xFFFF4F9A) { moveTo(10.2f, 19.6f); lineTo(10.2f, 11.8f); lineTo(17.6f, 10.2f); lineTo(17.6f, 18.0f); lineTo(16.6f, 18.0f); lineTo(16.6f, 12.6f); lineTo(11.2f, 13.8f); lineTo(11.2f, 19.6f); close() }
        fill(0xFFFF4F9A) { ellipse(8.8f, 19.8f, 2.3f, 1.9f) }
        fill(0xFFFF4F9A) { ellipse(15.2f, 18.2f, 2.3f, 1.9f) }
        line(dark, 0.35f) { moveTo(7.85f, 19.4f); quadTo(8.2f, 18.85f, 8.55f, 19.4f) }
        line(dark, 0.35f) { moveTo(14.25f, 17.8f); quadTo(14.6f, 17.25f, 14.95f, 17.8f) }
        line(dark, 0.35f) { moveTo(9.05f, 19.4f); quadTo(9.4f, 18.85f, 9.75f, 19.4f) }
        line(dark, 0.35f) { moveTo(15.45f, 17.8f); quadTo(15.8f, 17.25f, 16.15f, 17.8f) }
        line(dark, 0.35f) { moveTo(8.3f, 20.1f); quadTo(8.8f, 20.7f, 9.3f, 20.1f) }
        line(dark, 0.35f) { moveTo(14.7f, 18.5f); quadTo(15.2f, 19.1f, 15.7f, 18.5f) }
        fill(0xFFFFB3D1) { circle(7.4f, 20.1f, 0.35f) }
        fill(0xFFFFB3D1) { circle(10.2f, 20.1f, 0.35f) }
        fill(0xFFFFB3D1) { circle(13.8f, 18.5f, 0.35f) }
        fill(0xFFFFB3D1) { circle(16.6f, 18.5f, 0.35f) }
    }
}
