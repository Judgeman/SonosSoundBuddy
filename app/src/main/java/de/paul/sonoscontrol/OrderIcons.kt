package de.paul.sonoscontrol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path

/*
 * Bilder für die Frage „Der Reihe nach oder durcheinander?" — so gezeichnet,
 * dass auch Kinder sie verstehen, die noch nicht lesen können.
 * Koordinaten im 24×24-Viewport, wie in AnimalIcons.kt.
 */

/** Fläche mit gleichfarbigem, rundem Rand — ergibt abgerundete Ecken (Würfel). */
private fun ImageVector.Builder.rounded(color: Long, block: PathBuilder.() -> Unit) =
    path(
        fill = SolidColor(Color(color)),
        stroke = SolidColor(Color(color)),
        strokeLineWidth = 1.6f,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    )

/** Der Reihe nach: Entenmama mit zwei Küken, die brav hintereinander schwimmen. */
val InOrderIcon: ImageVector by lazy {
    drawnIcon("InOrder") {
        fill(0xFF4FC3F7) { moveTo(0.6f, 16.4f); quadTo(2.2f, 15.4f, 3.8f, 16.4f); quadTo(5.4f, 17.4f, 7.0f, 16.4f); quadTo(8.6f, 15.4f, 10.2f, 16.4f); quadTo(11.8f, 17.4f, 13.4f, 16.4f); quadTo(15.0f, 15.4f, 16.6f, 16.4f); quadTo(18.2f, 17.4f, 19.8f, 16.4f); quadTo(21.4f, 15.4f, 23.4f, 16.4f); lineTo(23.4f, 20.4f); quadTo(12.0f, 21.8f, 0.6f, 20.4f); close() }
        fill(0xFFFFEE58) { moveTo(1.11f, 14.94f); lineTo(0.28f, 13.84f); lineTo(1.66f, 14.39f); close() }
        fill(0xFFFFEE58) { ellipse(2.9f, 15.31f, 2.12f, 1.33f) }
        fill(0xFFFFEE58) { circle(4.69f, 13.2f, 1.15f) }
        fill(0xFFFFEE58) { moveTo(3.87f, 13.93f); lineTo(5.25f, 13.75f); lineTo(4.69f, 14.94f); lineTo(3.5f, 14.85f); close() }
        fill(0xFFFF8F00) { moveTo(5.61f, 13.01f); lineTo(6.63f, 13.43f); lineTo(5.61f, 13.75f); close() }
        fill(0xFFFDD835) { ellipse(2.67f, 15.13f, 1.15f, 0.64f) }
        fill(0xFF2B2B2B) { circle(5.02f, 12.92f, 0.22f) }
        fill(0xFFFFFFFF) { circle(4.95f, 12.85f, 0.08f) }
        fill(0xFFFFEE58) { moveTo(6.91f, 14.94f); lineTo(6.08f, 13.84f); lineTo(7.46f, 14.39f); close() }
        fill(0xFFFFEE58) { ellipse(8.7f, 15.31f, 2.12f, 1.33f) }
        fill(0xFFFFEE58) { circle(10.49f, 13.2f, 1.15f) }
        fill(0xFFFFEE58) { moveTo(9.67f, 13.93f); lineTo(11.05f, 13.75f); lineTo(10.49f, 14.94f); lineTo(9.3f, 14.85f); close() }
        fill(0xFFFF8F00) { moveTo(11.41f, 13.01f); lineTo(12.43f, 13.43f); lineTo(11.41f, 13.75f); close() }
        fill(0xFFFDD835) { ellipse(8.47f, 15.13f, 1.15f, 0.64f) }
        fill(0xFF2B2B2B) { circle(10.82f, 12.92f, 0.22f) }
        fill(0xFFFFFFFF) { circle(10.75f, 12.85f, 0.08f) }
        fill(0xFFFFD54F) { moveTo(13.4f, 13.65f); lineTo(11.93f, 11.68f); lineTo(14.39f, 12.66f); close() }
        fill(0xFFFFD54F) { ellipse(16.6f, 14.3f, 3.77f, 2.38f) }
        fill(0xFFFFD54F) { circle(19.8f, 10.53f, 2.05f) }
        fill(0xFFFFD54F) { moveTo(18.32f, 11.84f); lineTo(20.78f, 11.52f); lineTo(19.8f, 13.65f); lineTo(17.67f, 13.48f); close() }
        fill(0xFFFF8F00) { moveTo(21.44f, 10.2f); lineTo(23.24f, 10.94f); lineTo(21.44f, 11.52f); close() }
        fill(0xFFFFB300) { ellipse(16.19f, 13.98f, 2.05f, 1.15f) }
        fill(0xFF2B2B2B) { circle(20.37f, 10.04f, 0.39f) }
        fill(0xFFFFFFFF) { circle(20.25f, 9.92f, 0.14f) }
        fill(0xFF81D4FA) { moveTo(0.6f, 18.4f); quadTo(2.6f, 17.6f, 4.6f, 18.4f); quadTo(6.6f, 19.2f, 8.6f, 18.4f); quadTo(10.6f, 17.6f, 12.6f, 18.4f); quadTo(14.6f, 19.2f, 16.6f, 18.4f); quadTo(18.6f, 17.6f, 20.6f, 18.4f); quadTo(22.0f, 19.0f, 23.4f, 18.4f); lineTo(23.4f, 20.4f); quadTo(12.0f, 21.8f, 0.6f, 20.4f); close() }
    }
}

/** Durcheinander: zwei bunte, rollende Würfel — Zufall, wie beim Spielen. */
val ShuffleIcon: ImageVector by lazy {
    drawnIcon("Shuffle") {
        line(0xFFB0BEC5, 0.6f) { moveTo(2.2f, 6.2f); quadTo(3.6f, 4.4f, 5.8f, 4.6f) }
        line(0xFFB0BEC5, 0.6f) { moveTo(1.6f, 9.4f); quadTo(2.6f, 8.2f, 4.0f, 8.2f) }
        rounded(0xFFE53935) { moveTo(3.28f, 12.05f); lineTo(11.55f, 9.68f); lineTo(13.92f, 17.95f); lineTo(5.65f, 20.32f); close() }
        fill(0xFFFFFFFF) { circle(5.73f, 13.41f, 0.86f) }
        fill(0xFFFFFFFF) { circle(10.19f, 12.13f, 0.86f) }
        fill(0xFFFFFFFF) { circle(8.6f, 15.0f, 0.86f) }
        fill(0xFFFFFFFF) { circle(7.01f, 17.87f, 0.86f) }
        fill(0xFFFFFFFF) { circle(11.47f, 16.59f, 0.86f) }
        rounded(0xFF1E88E5) { moveTo(14.96f, 3.98f); lineTo(21.82f, 6.76f); lineTo(19.04f, 13.62f); lineTo(12.18f, 10.84f); close() }
        fill(0xFFFFFFFF) { circle(15.9f, 6.2f, 0.74f) }
        fill(0xFFFFFFFF) { circle(17.0f, 8.8f, 0.74f) }
        fill(0xFFFFFFFF) { circle(18.1f, 11.4f, 0.74f) }
        line(0xFFB0BEC5, 0.6f) { moveTo(19.6f, 16.4f); quadTo(21.4f, 17.4f, 22.4f, 19.4f) }
        line(0xFFB0BEC5, 0.6f) { moveTo(16.8f, 18.8f); quadTo(17.8f, 20.0f, 18.0f, 21.6f) }
    }
}
