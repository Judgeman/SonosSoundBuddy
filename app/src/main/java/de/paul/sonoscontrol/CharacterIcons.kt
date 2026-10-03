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

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * rx, dy1 = 0f)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * rx, dy1 = 0f)
    close()
}

private fun ImageVector.Builder.fill(color: Long, block: PathBuilder.() -> Unit) =
    path(fill = SolidColor(Color(color)), pathBuilder = block)

/** Gefüllte Fläche mit dünner Kontur, damit Weiß sich vom hellen Badge abhebt. */
private fun ImageVector.Builder.fill(
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

private fun ImageVector.Builder.line(color: Long, width: Float, block: PathBuilder.() -> Unit) =
    path(
        stroke = SolidColor(Color(color)),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    )

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

/** Fröhliches Einhorn von vorne: weißer Kopf, goldenes Horn, Regenbogen-Mähne. */
val UnicornIcon: ImageVector by lazy {
    icon("Unicorn") {
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
    icon("Pikachu") {
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

/** Fröhliche Kuh: weißer Kopf mit schwarzen Flecken, Hörnchen und rosa Schnauze. */
val CowIcon: ImageVector by lazy {
    icon("Cow") {
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

/** Rosa Schweinchen mit runder Rüsselnase und Spitzohren. */
val PigIcon: ImageVector by lazy {
    icon("Pig") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Ohren
        fill(0xFFFFAFCC) { moveTo(5.2f, 9.0f); lineTo(4.0f, 3.6f); lineTo(9.4f, 5.8f); close() }
        fill(0xFFFFAFCC) { moveTo(18.8f, 9.0f); lineTo(20.0f, 3.6f); lineTo(14.6f, 5.8f); close() }
        fill(0xFFF06292) { moveTo(5.7f, 7.6f); lineTo(5.0f, 5.0f); lineTo(7.8f, 6.2f); close() }
        fill(0xFFF06292) { moveTo(18.3f, 7.6f); lineTo(19.0f, 5.0f); lineTo(16.2f, 6.2f); close() }

        // Kopf
        fill(0xFFFFAFCC) { ellipse(12.0f, 13.2f, 8.4f, 7.6f) }

        // Augen und Bäckchen
        fill(dark) { circle(8.4f, 10.8f, 1.15f) }
        fill(dark) { circle(15.6f, 10.8f, 1.15f) }
        fill(white) { circle(8.02f, 10.38f, 0.44f) }
        fill(white) { circle(15.22f, 10.38f, 0.44f) }
        fill(0xFFFF80AB) { circle(5.8f, 14.2f, 1.2f) }
        fill(0xFFFF80AB) { circle(18.2f, 14.2f, 1.2f) }

        // Rüssel mit Nasenlöchern
        fill(0xFFFF8FB8, outline = 0xFFEC407A, outlineWidth = 0.4f) { ellipse(12.0f, 14.6f, 3.6f, 2.6f) }
        fill(0xFFC2185B) { ellipse(10.7f, 14.6f, 0.6f, 0.95f) }
        fill(0xFFC2185B) { ellipse(13.3f, 14.6f, 0.6f, 0.95f) }

        // Lächeln
        line(0xFFC2185B, 0.5f) { moveTo(10.2f, 18.6f); quadTo(12.0f, 19.8f, 13.8f, 18.6f) }
    }
}

/** Grüner Frosch mit großen Glubschaugen und breitem Grinsen. */
val FrogIcon: ImageVector by lazy {
    icon("Frog") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Augenhügel und Kopf
        fill(0xFF7CD650) { circle(7.4f, 7.6f, 3.4f) }
        fill(0xFF7CD650) { circle(16.6f, 7.6f, 3.4f) }
        fill(0xFF7CD650) { ellipse(12.0f, 14.4f, 9.6f, 6.8f) }

        // Bauchfarbe unten
        fill(0xFFC5F09E) { ellipse(12.0f, 17.6f, 6.4f, 3.0f) }

        // Glubschaugen
        fill(white) { circle(7.4f, 7.4f, 2.4f) }
        fill(white) { circle(16.6f, 7.4f, 2.4f) }
        fill(dark) { circle(7.8f, 7.6f, 1.3f) }
        fill(dark) { circle(16.2f, 7.6f, 1.3f) }
        fill(white) { circle(7.4f, 7.1f, 0.45f) }
        fill(white) { circle(15.8f, 7.1f, 0.45f) }

        // Nasenlöcher, Grinsen, Bäckchen
        fill(0xFF3E8E2A) { circle(10.8f, 12.4f, 0.4f) }
        fill(0xFF3E8E2A) { circle(13.2f, 12.4f, 0.4f) }
        line(0xFF2E6B1E, 0.6f) { moveTo(6.2f, 14.6f); quadTo(12.0f, 19.4f, 17.8f, 14.6f) }
        fill(0xFFFF8A80) { circle(5.2f, 15.4f, 1.1f) }
        fill(0xFFFF8A80) { circle(18.8f, 15.4f, 1.1f) }
    }
}

/** Kleiner Löwe mit flauschiger orangefarbener Mähne. */
val LionIcon: ImageVector by lazy {
    icon("Lion") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Mähne aus Kreisen rundherum
        fill(0xFFFF9800) { circle(20.2f, 12.6f, 3.0f) }
        fill(0xFFF57C00) { circle(17.82f, 18.42f, 3.0f) }
        fill(0xFFFF9800) { circle(12.0f, 20.8f, 3.0f) }
        fill(0xFFF57C00) { circle(6.18f, 18.42f, 3.0f) }
        fill(0xFFFF9800) { circle(3.8f, 12.6f, 3.0f) }
        fill(0xFFF57C00) { circle(6.18f, 6.78f, 3.0f) }
        fill(0xFFFF9800) { circle(12.0f, 4.4f, 3.0f) }
        fill(0xFFF57C00) { circle(17.82f, 6.78f, 3.0f) }
        fill(0xFFFF9800) { circle(12.0f, 12.6f, 8.6f) }

        // Ohren und Gesicht
        fill(0xFFFFCA28) { circle(7.0f, 6.8f, 1.9f) }
        fill(0xFFFFCA28) { circle(17.0f, 6.8f, 1.9f) }
        fill(0xFFFFB300) { circle(7.0f, 6.8f, 1.0f) }
        fill(0xFFFFB300) { circle(17.0f, 6.8f, 1.0f) }
        fill(0xFFFFCA28) { ellipse(12.0f, 13.0f, 6.4f, 6.0f) }
        fill(0xFFFFF3C4) { ellipse(12.0f, 16.0f, 3.4f, 2.4f) }

        // Augen, Nase, Mund und Bäckchen
        fill(dark) { circle(9.4f, 11.6f, 1.05f) }
        fill(dark) { circle(14.6f, 11.6f, 1.05f) }
        fill(white) { circle(9.02f, 11.18f, 0.4f) }
        fill(white) { circle(14.22f, 11.18f, 0.4f) }
        fill(0xFF6D4C41) { moveTo(10.8f, 14.0f); lineTo(13.2f, 14.0f); lineTo(12.0f, 15.4f); close() }
        line(0xFF6D4C41, 0.45f) { moveTo(12.0f, 15.4f); lineTo(12.0f, 16.2f); quadTo(11.0f, 17.2f, 10.2f, 16.4f) }
        line(0xFF6D4C41, 0.45f) { moveTo(12.0f, 16.2f); quadTo(13.0f, 17.2f, 13.8f, 16.4f) }
        fill(0xFFFF8A65) { circle(7.8f, 14.6f, 0.9f) }
        fill(0xFFFF8A65) { circle(16.2f, 14.6f, 0.9f) }
    }
}

/** Graue Katze mit Schnurrhaaren und rosa Näschen. */
val CatIcon: ImageVector by lazy {
    icon("Cat") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Ohren
        fill(0xFF9E9EB8) { moveTo(4.2f, 12.0f); lineTo(4.4f, 2.8f); lineTo(11.0f, 7.4f); close() }
        fill(0xFF9E9EB8) { moveTo(19.8f, 12.0f); lineTo(19.6f, 2.8f); lineTo(13.0f, 7.4f); close() }
        fill(0xFFF8BBD0) { moveTo(5.4f, 9.6f); lineTo(5.6f, 5.0f); lineTo(8.8f, 7.4f); close() }
        fill(0xFFF8BBD0) { moveTo(18.6f, 9.6f); lineTo(18.4f, 5.0f); lineTo(15.2f, 7.4f); close() }

        // Kopf mit Stirnstreifen
        fill(0xFF9E9EB8) { ellipse(12.0f, 13.8f, 8.2f, 7.0f) }
        line(0xFF7A7A96, 0.6f) { moveTo(12.0f, 7.2f); lineTo(12.0f, 9.0f) }
        line(0xFF7A7A96, 0.6f) { moveTo(10.4f, 7.4f); lineTo(10.8f, 8.8f) }
        line(0xFF7A7A96, 0.6f) { moveTo(13.6f, 7.4f); lineTo(13.2f, 8.8f) }
        fill(0xFFF2F0F7) { ellipse(12.0f, 17.0f, 3.8f, 2.8f) }

        // Augen
        fill(0xFF9CCC65) { ellipse(8.6f, 12.6f, 1.6f, 1.8f) }
        fill(0xFF9CCC65) { ellipse(15.4f, 12.6f, 1.6f, 1.8f) }
        fill(dark) { ellipse(8.6f, 12.7f, 0.75f, 1.4f) }
        fill(dark) { ellipse(15.4f, 12.7f, 0.75f, 1.4f) }
        fill(white) { circle(8.2f, 12.0f, 0.4f) }
        fill(white) { circle(15.0f, 12.0f, 0.4f) }

        // Nase, Mund, Schnurrhaare
        fill(0xFFF06292) { moveTo(11.0f, 15.4f); lineTo(13.0f, 15.4f); lineTo(12.0f, 16.5f); close() }
        line(dark, 0.45f) { moveTo(10.6f, 17.2f); quadTo(11.3f, 18.0f, 12.0f, 16.5f); quadTo(12.7f, 18.0f, 13.4f, 17.2f) }
        line(dark, 0.35f) { moveTo(8.0f, 16.4f); lineTo(3.4f, 15.6f) }
        line(dark, 0.35f) { moveTo(8.0f, 17.4f); lineTo(3.6f, 18.2f) }
        line(dark, 0.35f) { moveTo(16.0f, 16.4f); lineTo(20.6f, 15.6f) }
        line(dark, 0.35f) { moveTo(16.0f, 17.4f); lineTo(20.4f, 18.2f) }
    }
}

/** Brauner Hund mit Schlappohren und herausgestreckter Zunge. */
val DogIcon: ImageVector by lazy {
    icon("Dog") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Kopf
        fill(0xFFE0B07A) { ellipse(12.0f, 12.8f, 7.4f, 7.6f) }

        // Schlappohren
        fill(0xFF8D5A3B) { moveTo(6.6f, 6.4f); quadTo(2.0f, 6.0f, 2.6f, 13.6f); quadTo(3.2f, 15.6f, 5.2f, 14.2f); quadTo(6.4f, 10.6f, 8.2f, 7.4f); close() }
        fill(0xFF8D5A3B) { moveTo(17.4f, 6.4f); quadTo(22.0f, 6.0f, 21.4f, 13.6f); quadTo(20.8f, 15.6f, 18.8f, 14.2f); quadTo(17.6f, 10.6f, 15.8f, 7.4f); close() }

        // Fleck ums Auge und helle Schnauze
        fill(0xFFB9824F) { ellipse(15.0f, 10.8f, 2.4f, 2.2f) }
        fill(0xFFFFE6C7) { ellipse(12.0f, 16.4f, 4.0f, 3.2f) }

        // Augen
        fill(dark) { circle(9.0f, 11.0f, 1.15f) }
        fill(dark) { circle(15.0f, 11.0f, 1.15f) }
        fill(white) { circle(8.62f, 10.58f, 0.44f) }
        fill(white) { circle(14.62f, 10.58f, 0.44f) }

        // Nase, Mund, Zunge
        fill(dark) { ellipse(12.0f, 14.6f, 1.6f, 1.1f) }
        fill(white) { ellipse(11.5f, 14.2f, 0.4f, 0.25f) }
        fill(0xFFFF6F91) { moveTo(11.0f, 17.0f); lineTo(13.0f, 17.0f); lineTo(13.0f, 18.6f); quadTo(12.0f, 20.0f, 11.0f, 18.6f); close() }
        line(dark, 0.5f) { moveTo(12.0f, 15.6f); lineTo(12.0f, 16.6f); quadTo(10.8f, 17.6f, 9.8f, 16.8f) }
        line(dark, 0.5f) { moveTo(12.0f, 16.6f); quadTo(13.2f, 17.6f, 14.2f, 16.8f) }
    }
}

/** Buntes Platzhalter-Cover: Regenbogen, Wölkchen, Glitzer und lachende Noten (ohne Hintergrund). */
val DefaultCoverArt: ImageVector by lazy {
    icon("DefaultCover") {
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
