package de.paul.sonoscontrol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/*
 * Buntes Funkeln für Musik, die ein Kind noch nie gespielt hat — so gezeichnet,
 * dass es auch Kinder verstehen, die noch nicht lesen können.
 * Koordinaten im 24×24-Viewport, wie in AnimalIcons.kt; ohne Tint zeichnen.
 */

/** Farbe für „neu“ in Texten und Knöpfen der Einstellungen. */
val NewMusicColor = Color(0xFFE91E63)

/** Funkelstern mit vier Zacken und nach innen gewölbten Seiten — je kleiner [pinch], desto schlanker. */
private fun PathBuilder.sparkle(cx: Float, cy: Float, r: Float, pinch: Float = 0.2f) {
    val k = r * pinch
    moveTo(cx, cy - r)
    quadTo(cx + k, cy - k, cx + r, cy)
    quadTo(cx + k, cy + k, cx, cy + r)
    quadTo(cx - k, cy + k, cx - r, cy)
    quadTo(cx - k, cy - k, cx, cy - r)
    close()
}

/** Großer goldener Funkelstern mit fröhlichem Gesicht, dazu pinke und türkise Funkel und bunte Pünktchen. */
val NewMusicIcon: ImageVector by lazy {
    drawnIcon("NewMusic") {
        // Kleine Funkel und Pünktchen rundherum
        fill(0xFFFF4FA3, outline = 0xFFD81B60, outlineWidth = 0.4f) { sparkle(19.2f, 4.8f, 4.0f) }
        fill(0xFF4DD0E1, outline = 0xFF0097A7, outlineWidth = 0.4f) { sparkle(20.2f, 19.6f, 3.0f) }
        fill(0xFFB388FF) { circle(3.4f, 4.6f, 1.1f) }
        fill(0xFF69F0AE) { circle(3.8f, 20.4f, 0.9f) }
        fill(0xFFFF9100) { circle(15.4f, 1.6f, 0.7f) }

        // Der große Stern, oben links ein Glanz
        fill(0xFFFFD54F, outline = 0xFFFF8F00, outlineWidth = 0.6f) { sparkle(10.5f, 13f, 9.4f) }
        fill(0xFFFFF9C4) { circle(7.9f, 10.0f, 0.7f) }
        fill(0xFFFFF9C4) { circle(9.1f, 9.3f, 0.35f) }

        // Gesicht
        fill(0xFF5D4037) { circle(8.6f, 12.2f, 0.75f) }
        fill(0xFF5D4037) { circle(12.4f, 12.2f, 0.75f) }
        fill(0xFFFFFFFF) { circle(8.35f, 11.95f, 0.28f) }
        fill(0xFFFFFFFF) { circle(12.15f, 11.95f, 0.28f) }
        fill(0xFFFF8A80) { circle(7.3f, 14.2f, 0.7f) }
        fill(0xFFFF8A80) { circle(13.7f, 14.2f, 0.7f) }
        line(0xFF5D4037, 0.55f) { moveTo(9.0f, 14.6f); quadTo(10.5f, 16.1f, 12.0f, 14.6f) }
    }
}
