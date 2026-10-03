package de.paul.sonoscontrol

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * Bunte, selbst gezeichnete Szenen (24×24-Viewport) als Bilder für die
 * Kategorien der Musik-Bibliothek. Anders als die Icons füllen sie die ganze
 * Fläche und liegen auf einem Farbverlauf.
 */

/** Der Enum-Name wird als Schlüssel in der Datenbank gespeichert — Einträge also nicht umbenennen. */
enum class CategoryIcon(
    val label: String,
    private val vectorProvider: () -> ImageVector,
    gradientStart: Color,
    gradientEnd: Color
) {
    DANCE_PARTY("Tanzparty", { DancePartyScene }, Color(0xFFFF7EB3), Color(0xFF7C4DFF)),
    POP_UP_BOOK("Hörbuch", { PopUpBookScene }, Color(0xFF5C6BC0), Color(0xFF26C6DA)),
    LULLABY("Schlaflieder", { LullabyScene }, Color(0xFF283593), Color(0xFF7E57C2));

    val vector: ImageVector get() = vectorProvider()

    val background: Brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))
}

/** Tanzparty: Hase und Bär tanzen unter der Discokugel, Noten fliegen. */
val DancePartyScene: ImageVector by lazy {
    drawnIcon("DanceParty") {
        val white = 0xFFFFFFFF
        val dark = 0xFF2E2A33

        // Discokugel an der Decke
        line(0xFFE0E0E0, 0.3f) { moveTo(12.0f, 0.0f); lineTo(12.0f, 2.0f) }
        fill(0xFFB0BEC5) { circle(12.0f, 4.0f, 2.2f) }
        fill(0xFFE3F2FD) { moveTo(10.2f, 3.0f); lineTo(12.0f, 3.0f); lineTo(12.0f, 4.0f); lineTo(9.8f, 4.0f); close() }
        fill(0xFFE3F2FD) { moveTo(12.0f, 4.0f); lineTo(14.2f, 4.0f); lineTo(13.8f, 5.1f); lineTo(12.0f, 5.1f); close() }
        fill(0xFF90A4AE) { moveTo(12.0f, 1.8f); lineTo(13.6f, 2.6f); lineTo(12.0f, 3.0f); close() }
        fill(0xFFFFF59D) { moveTo(8.6f, 4.8f); quadTo(8.82f, 5.38f, 9.4f, 5.6f); quadTo(8.82f, 5.82f, 8.6f, 6.4f); quadTo(8.38f, 5.82f, 7.8f, 5.6f); quadTo(8.38f, 5.38f, 8.6f, 4.8f); close() }
        fill(white) { moveTo(15.6f, 1.7f); quadTo(15.8f, 2.2f, 16.3f, 2.4f); quadTo(15.8f, 2.6f, 15.6f, 3.1f); quadTo(15.4f, 2.6f, 14.9f, 2.4f); quadTo(15.4f, 2.2f, 15.6f, 1.7f); close() }

        // Bunte Tanzfläche
        fill(0xFFFF5A5F) { moveTo(0.0f, 21.0f); lineTo(6.0f, 21.0f); lineTo(6.0f, 24.0f); lineTo(0.0f, 24.0f); close() }
        fill(0xFFFFE14D) { moveTo(6.0f, 21.0f); lineTo(12.0f, 21.0f); lineTo(12.0f, 24.0f); lineTo(6.0f, 24.0f); close() }
        fill(0xFF4FC3F7) { moveTo(12.0f, 21.0f); lineTo(18.0f, 21.0f); lineTo(18.0f, 24.0f); lineTo(12.0f, 24.0f); close() }
        fill(0xFF6EDB6A) { moveTo(18.0f, 21.0f); lineTo(24.0f, 21.0f); lineTo(24.0f, 24.0f); lineTo(18.0f, 24.0f); close() }

        // Hase: Beine, Körper, Arme in die Luft
        line(white, 1.1f) { moveTo(6.4f, 16.8f); lineTo(4.8f, 20.6f) }
        line(white, 1.1f) { moveTo(7.6f, 17.0f); lineTo(9.0f, 20.4f) }
        fill(white) { moveTo(9.27f, 14.6f); curveTo(9.55f, 16.23f, 8.77f, 17.73f, 7.52f, 17.95f); curveTo(6.27f, 18.18f, 5.02f, 17.03f, 4.73f, 15.4f); curveTo(4.45f, 13.77f, 5.23f, 12.27f, 6.48f, 12.05f); curveTo(7.73f, 11.82f, 8.98f, 12.97f, 9.27f, 14.6f); close() }
        fill(0xFFFCE4EC) { moveTo(8.38f, 15.37f); curveTo(8.55f, 16.35f, 8.12f, 17.25f, 7.41f, 17.37f); curveTo(6.71f, 17.5f, 5.99f, 16.8f, 5.82f, 15.83f); curveTo(5.65f, 14.85f, 6.08f, 13.95f, 6.79f, 13.83f); curveTo(7.49f, 13.7f, 8.21f, 14.4f, 8.38f, 15.37f); close() }
        line(white, 0.9f) { moveTo(5.4f, 13.6f); lineTo(3.4f, 10.8f) }
        line(white, 0.9f) { moveTo(8.6f, 13.4f); lineTo(10.4f, 11.2f) }

        // Hasenkopf mit langen Ohren
        fill(white) { moveTo(6.46f, 6.72f); curveTo(6.9f, 8.09f, 6.88f, 9.32f, 6.4f, 9.47f); curveTo(5.93f, 9.63f, 5.19f, 8.64f, 4.74f, 7.28f); curveTo(4.3f, 5.91f, 4.32f, 4.68f, 4.8f, 4.53f); curveTo(5.27f, 4.37f, 6.01f, 5.36f, 6.46f, 6.72f); close() }
        fill(white) { moveTo(9.47f, 7.05f); curveTo(9.07f, 8.43f, 8.36f, 9.44f, 7.88f, 9.3f); curveTo(7.41f, 9.16f, 7.34f, 7.93f, 7.73f, 6.55f); curveTo(8.13f, 5.17f, 8.84f, 4.16f, 9.32f, 4.3f); curveTo(9.79f, 4.44f, 9.86f, 5.67f, 9.47f, 7.05f); close() }
        fill(0xFFF8BBD0) { moveTo(6.03f, 7.06f); curveTo(6.34f, 8.01f, 6.39f, 8.84f, 6.16f, 8.91f); curveTo(5.92f, 8.99f, 5.48f, 8.28f, 5.17f, 7.34f); curveTo(4.86f, 6.39f, 4.81f, 5.56f, 5.04f, 5.49f); curveTo(5.28f, 5.41f, 5.72f, 6.12f, 6.03f, 7.06f); close() }
        fill(0xFFF8BBD0) { moveTo(9.03f, 7.12f); curveTo(8.76f, 8.08f, 8.34f, 8.8f, 8.1f, 8.73f); curveTo(7.86f, 8.66f, 7.89f, 7.83f, 8.17f, 6.88f); curveTo(8.44f, 5.92f, 8.86f, 5.2f, 9.1f, 5.27f); curveTo(9.34f, 5.34f, 9.31f, 6.17f, 9.03f, 7.12f); close() }
        fill(white) { ellipse(7.0f, 10.6f, 2.4f, 2.2f) }
        line(dark, 0.4f) { moveTo(5.6f, 10.4f); quadTo(6.1f, 9.7f, 6.6f, 10.4f) }
        line(dark, 0.4f) { moveTo(7.4f, 10.4f); quadTo(7.9f, 9.7f, 8.4f, 10.4f) }
        fill(0xFFF48FB1) { ellipse(7.0f, 11.3f, 0.35f, 0.25f) }
        line(dark, 0.3f) { moveTo(6.5f, 11.8f); quadTo(7.0f, 12.3f, 7.5f, 11.8f) }
        fill(0xFFF8BBD0) { circle(5.3f, 11.4f, 0.5f) }
        fill(0xFFF8BBD0) { circle(8.7f, 11.4f, 0.5f) }

        // Bär: Beine, Körper, ein Arm hoch
        line(0xFFC68B59, 1.3f) { moveTo(16.4f, 17.4f); lineTo(15.0f, 20.6f) }
        line(0xFFC68B59, 1.3f) { moveTo(17.8f, 17.4f); lineTo(19.8f, 20.2f) }
        fill(0xFFC68B59) { moveTo(19.54f, 15.74f); curveTo(19.2f, 17.36f, 17.78f, 18.43f, 16.38f, 18.13f); curveTo(14.97f, 17.84f, 14.11f, 16.28f, 14.46f, 14.66f); curveTo(14.8f, 13.04f, 16.22f, 11.97f, 17.62f, 12.27f); curveTo(19.03f, 12.56f, 19.89f, 14.12f, 19.54f, 15.74f); close() }
        fill(0xFFF3D2B0) { moveTo(18.37f, 15.91f); curveTo(18.15f, 16.94f, 17.32f, 17.63f, 16.5f, 17.46f); curveTo(15.69f, 17.29f, 15.21f, 16.31f, 15.43f, 15.29f); curveTo(15.65f, 14.26f, 16.48f, 13.57f, 17.3f, 13.74f); curveTo(18.11f, 13.91f, 18.59f, 14.89f, 18.37f, 15.91f); close() }
        line(0xFFC68B59, 1.1f) { moveTo(19.0f, 13.8f); lineTo(21.2f, 10.6f) }
        line(0xFFC68B59, 1.1f) { moveTo(15.0f, 14.6f); lineTo(13.4f, 16.6f) }

        // Bärenkopf
        fill(0xFFC68B59) { circle(15.2f, 8.0f, 1.1f) }
        fill(0xFFC68B59) { circle(19.4f, 8.6f, 1.1f) }
        fill(0xFFF3D2B0) { circle(15.2f, 8.0f, 0.55f) }
        fill(0xFFF3D2B0) { circle(19.4f, 8.6f, 0.55f) }
        fill(0xFFC68B59) { ellipse(17.4f, 10.4f, 2.6f, 2.4f) }
        fill(0xFFF3D2B0) { ellipse(17.5f, 11.4f, 1.2f, 0.9f) }
        line(dark, 0.4f) { moveTo(15.9f, 10.0f); quadTo(16.4f, 9.3f, 16.9f, 10.0f) }
        line(dark, 0.4f) { moveTo(18.0f, 10.0f); quadTo(18.5f, 9.3f, 19.0f, 10.0f) }
        fill(dark) { ellipse(17.5f, 11.0f, 0.45f, 0.32f) }
        line(dark, 0.3f) { moveTo(17.0f, 11.8f); quadTo(17.5f, 12.3f, 18.0f, 11.8f) }

        // Fliegende Noten
        fill(0xFFFFE14D) { moveTo(3.11f, 5.74f); curveTo(3.22f, 6.03f, 2.99f, 6.38f, 2.59f, 6.53f); curveTo(2.2f, 6.67f, 1.79f, 6.55f, 1.69f, 6.26f); curveTo(1.58f, 5.97f, 1.81f, 5.62f, 2.21f, 5.47f); curveTo(2.6f, 5.33f, 3.01f, 5.45f, 3.11f, 5.74f); close() }
        line(0xFFFFE14D, 0.36f) { moveTo(3.04f, 5.76f); lineTo(3.04f, 3.28f); quadTo(3.84f, 3.92f, 4.16f, 4.56f) }
        fill(0xFFFF4F9A) { moveTo(12.2f, 16.31f); curveTo(12.32f, 16.63f, 12.06f, 17.03f, 11.62f, 17.19f); curveTo(11.17f, 17.35f, 10.72f, 17.22f, 10.6f, 16.89f); curveTo(10.48f, 16.57f, 10.74f, 16.17f, 11.18f, 16.01f); curveTo(11.63f, 15.85f, 12.08f, 15.98f, 12.2f, 16.31f); close() }
        line(0xFFFF4F9A, 0.41f) { moveTo(12.12f, 16.33f); lineTo(12.12f, 13.54f); quadTo(13.02f, 14.26f, 13.38f, 14.98f) }
        fill(0xFF6EDB6A) { moveTo(21.71f, 5.14f); curveTo(21.82f, 5.43f, 21.59f, 5.78f, 21.19f, 5.93f); curveTo(20.8f, 6.07f, 20.39f, 5.95f, 20.29f, 5.66f); curveTo(20.18f, 5.37f, 20.41f, 5.02f, 20.81f, 4.87f); curveTo(21.2f, 4.73f, 21.61f, 4.85f, 21.71f, 5.14f); close() }
        line(0xFF6EDB6A, 0.36f) { moveTo(21.64f, 5.16f); lineTo(21.64f, 2.68f); quadTo(22.44f, 3.32f, 22.76f, 3.96f) }
        fill(0xFFFFF59D) { moveTo(12.2f, 9.8f); quadTo(12.42f, 10.38f, 13.0f, 10.6f); quadTo(12.42f, 10.82f, 12.2f, 11.4f); quadTo(11.98f, 10.82f, 11.4f, 10.6f); quadTo(11.98f, 10.38f, 12.2f, 9.8f); close() }
    }
}

/** Hörbuch: aufgeklapptes Buch, aus dem magisch ein Schloss herausploppt. */
val PopUpBookScene: ImageVector by lazy {
    drawnIcon("PopUpBook") {
        val white = 0xFFFFFFFF

        // Magischer Glitzerbogen hinter dem Schloss
        line(0xFFFFF59D, 0.5f) { moveTo(3.6f, 14.0f); quadTo(2.6f, 4.0f, 12.0f, 2.6f) }
        line(0xFF80DEEA, 0.5f) { moveTo(20.4f, 14.0f); quadTo(21.4f, 4.0f, 12.0f, 2.6f) }
        fill(white) { moveTo(3.4f, 3.3f); quadTo(3.71f, 4.09f, 4.5f, 4.4f); quadTo(3.71f, 4.71f, 3.4f, 5.5f); quadTo(3.09f, 4.71f, 2.3f, 4.4f); quadTo(3.09f, 4.09f, 3.4f, 3.3f); close() }
        fill(0xFFFFF59D) { moveTo(20.6f, 2.9f); quadTo(20.85f, 3.55f, 21.5f, 3.8f); quadTo(20.85f, 4.05f, 20.6f, 4.7f); quadTo(20.35f, 4.05f, 19.7f, 3.8f); quadTo(20.35f, 3.55f, 20.6f, 2.9f); close() }
        fill(white) { moveTo(21.4f, 9.9f); quadTo(21.6f, 10.4f, 22.1f, 10.6f); quadTo(21.6f, 10.8f, 21.4f, 11.3f); quadTo(21.2f, 10.8f, 20.7f, 10.6f); quadTo(21.2f, 10.4f, 21.4f, 9.9f); close() }
        fill(0xFFFFF59D) { moveTo(2.6f, 10.4f); quadTo(2.77f, 10.83f, 3.2f, 11.0f); quadTo(2.77f, 11.17f, 2.6f, 11.6f); quadTo(2.43f, 11.17f, 2.0f, 11.0f); quadTo(2.43f, 10.83f, 2.6f, 10.4f); close() }

        // Seitentürme mit Spitzdächern und Fähnchen
        fill(0xFFFFE6F2) { moveTo(6.0f, 17.0f); lineTo(6.0f, 9.6f); lineTo(8.6f, 9.6f); lineTo(8.6f, 17.0f); close() }
        fill(0xFFFFE6F2) { moveTo(15.4f, 17.0f); lineTo(15.4f, 9.6f); lineTo(18.0f, 9.6f); lineTo(18.0f, 17.0f); close() }
        fill(0xFF7E57C2) { moveTo(5.4f, 9.8f); lineTo(7.3f, 5.6f); lineTo(9.2f, 9.8f); close() }
        fill(0xFF7E57C2) { moveTo(14.8f, 9.8f); lineTo(16.7f, 5.6f); lineTo(18.6f, 9.8f); close() }
        line(0xFF5E35B1, 0.25f) { moveTo(7.3f, 5.6f); lineTo(7.3f, 4.0f) }
        line(0xFF5E35B1, 0.25f) { moveTo(16.7f, 5.6f); lineTo(16.7f, 4.0f) }
        fill(0xFFFFCA28) { moveTo(7.3f, 4.0f); lineTo(8.9f, 4.5f); lineTo(7.3f, 5.0f); close() }
        fill(0xFF4FC3F7) { moveTo(16.7f, 4.0f); lineTo(18.3f, 4.5f); lineTo(16.7f, 5.0f); close() }

        // Hauptturm mit großem Dach
        fill(0xFFFFF0F7) { moveTo(9.0f, 17.0f); lineTo(9.0f, 8.4f); lineTo(15.0f, 8.4f); lineTo(15.0f, 17.0f); close() }
        fill(0xFFFF6FA8) { moveTo(8.4f, 8.6f); lineTo(12.0f, 2.8f); lineTo(15.6f, 8.6f); close() }
        line(0xFFD81B60, 0.25f) { moveTo(12.0f, 2.8f); lineTo(12.0f, 1.0f) }
        fill(0xFFFF5A5F) { moveTo(12.0f, 1.0f); lineTo(14.0f, 1.6f); lineTo(12.0f, 2.2f); close() }

        // Zinnen, Fenster und Tor
        fill(0xFFFFF0F7) { moveTo(9.0f, 8.4f); lineTo(9.0f, 7.6f); lineTo(10.0f, 7.6f); lineTo(10.0f, 8.4f); close() }
        fill(0xFFFFF0F7) { moveTo(14.0f, 8.4f); lineTo(14.0f, 7.6f); lineTo(15.0f, 7.6f); lineTo(15.0f, 8.4f); close() }
        fill(0xFF5C6BC0) { moveTo(11.2f, 11.6f); lineTo(11.2f, 10.4f); quadTo(12.0f, 9.4f, 12.8f, 10.4f); lineTo(12.8f, 11.6f); close() }
        fill(0xFF5C6BC0) { moveTo(6.7f, 12.4f); lineTo(6.7f, 11.4f); quadTo(7.3f, 10.6f, 7.9f, 11.4f); lineTo(7.9f, 12.4f); close() }
        fill(0xFF5C6BC0) { moveTo(16.1f, 12.4f); lineTo(16.1f, 11.4f); quadTo(16.7f, 10.6f, 17.3f, 11.4f); lineTo(17.3f, 12.4f); close() }
        fill(0xFFFFE082) { circle(11.6f, 10.6f, 0.2f) }
        fill(0xFF8D6E63) { moveTo(10.8f, 17.0f); lineTo(10.8f, 14.6f); quadTo(12.0f, 12.8f, 13.2f, 14.6f); lineTo(13.2f, 17.0f); close() }

        // Herzchen über dem Tor
        fill(0xFFFF4F9A) { moveTo(12.0f, 13.0f); quadTo(10.9f, 12.2f, 11.4f, 11.8f); quadTo(11.8f, 11.6f, 12.0f, 12.1f); quadTo(12.2f, 11.6f, 12.6f, 11.8f); quadTo(13.1f, 12.2f, 12.0f, 13.0f); close() }

        // Aufgeklapptes Buch: Einband und zwei Seiten
        fill(0xFFE53935) { moveTo(0.8f, 16.2f); lineTo(12.0f, 18.0f); lineTo(23.2f, 16.2f); lineTo(23.2f, 21.6f); lineTo(12.0f, 23.4f); lineTo(0.8f, 21.6f); close() }
        fill(white) { moveTo(1.6f, 15.4f); quadTo(7.0f, 14.2f, 12.0f, 16.8f); lineTo(12.0f, 22.4f); quadTo(7.0f, 19.8f, 1.6f, 21.0f); close() }
        fill(0xFFFFF8E1) { moveTo(22.4f, 15.4f); quadTo(17.0f, 14.2f, 12.0f, 16.8f); lineTo(12.0f, 22.4f); quadTo(17.0f, 19.8f, 22.4f, 21.0f); close() }
        line(0xFFBCAAA4, 0.3f) { moveTo(3.4f, 17.4f); quadTo(7.0f, 16.6f, 10.4f, 18.2f) }
        line(0xFFBCAAA4, 0.3f) { moveTo(3.4f, 19.0f); quadTo(7.0f, 18.2f, 10.4f, 19.8f) }
        line(0xFFBCAAA4, 0.3f) { moveTo(20.6f, 17.4f); quadTo(17.0f, 16.6f, 13.6f, 18.2f) }
        line(0xFFBCAAA4, 0.3f) { moveTo(20.6f, 19.0f); quadTo(17.0f, 18.2f, 13.6f, 19.8f) }
        line(0xFFE0D6CF, 0.3f) { moveTo(12.0f, 16.8f); lineTo(12.0f, 22.4f) }

        // Funken, die aus dem Buch ploppen
        fill(0xFFFFF59D) { circle(4.6f, 13.4f, 0.4f) }
        fill(0xFF80DEEA) { circle(19.6f, 13.6f, 0.4f) }
        fill(white) { circle(9.6f, 6.2f, 0.3f) }
        fill(0xFFFF8AD8) { circle(14.6f, 5.0f, 0.35f) }
    }
}

/** Schlaflieder: schlafender Mond mit Zipfelmütze, Sterne und ein Schäfchen auf Wolken. */
val LullabyScene: ImageVector by lazy {
    drawnIcon("Lullaby") {
        val white = 0xFFFFFFFF

        // Sterne am Himmel
        fill(0xFFFFF59D) { moveTo(3.4f, 3.0f); quadTo(3.68f, 3.72f, 4.4f, 4.0f); quadTo(3.68f, 4.28f, 3.4f, 5.0f); quadTo(3.12f, 4.28f, 2.4f, 4.0f); quadTo(3.12f, 3.72f, 3.4f, 3.0f); close() }
        fill(white) { moveTo(20.4f, 1.8f); quadTo(20.74f, 2.66f, 21.6f, 3.0f); quadTo(20.74f, 3.34f, 20.4f, 4.2f); quadTo(20.06f, 3.34f, 19.2f, 3.0f); quadTo(20.06f, 2.66f, 20.4f, 1.8f); close() }
        fill(0xFFFFF59D) { moveTo(21.0f, 10.6f); quadTo(21.22f, 11.18f, 21.8f, 11.4f); quadTo(21.22f, 11.62f, 21.0f, 12.2f); quadTo(20.78f, 11.62f, 20.2f, 11.4f); quadTo(20.78f, 11.18f, 21.0f, 10.6f); close() }
        fill(white) { moveTo(2.4f, 12.1f); quadTo(2.6f, 12.6f, 3.1f, 12.8f); quadTo(2.6f, 13.0f, 2.4f, 13.5f); quadTo(2.2f, 13.0f, 1.7f, 12.8f); quadTo(2.2f, 12.6f, 2.4f, 12.1f); close() }
        fill(white) { circle(7.6f, 2.2f, 0.3f) }
        fill(white) { circle(17.0f, 7.6f, 0.3f) }

        // Schlafender Mond mit Zipfelmütze
        fill(0xFFFFE082) { moveTo(14.4f, 4.6f); curveTo(7.0f, 3.4f, 3.8f, 13.6f, 9.6f, 18.0f); curveTo(13.0f, 20.4f, 17.6f, 19.4f, 19.6f, 16.2f); curveTo(13.8f, 17.4f, 10.0f, 10.6f, 14.4f, 4.6f); close() }
        line(0xFF6D4C41, 0.45f) { moveTo(8.4f, 10.8f); quadTo(9.2f, 11.6f, 10.0f, 10.8f) }
        fill(0xFFFFAB91) { circle(8.8f, 13.0f, 0.75f) }
        line(0xFF6D4C41, 0.4f) { moveTo(10.2f, 14.4f); quadTo(11.0f, 15.0f, 11.6f, 14.2f) }
        fill(0xFF7E57C2) { moveTo(8.0f, 7.4f); quadTo(10.4f, 4.2f, 14.4f, 4.6f); quadTo(17.0f, 6.0f, 18.6f, 9.8f); quadTo(14.4f, 6.6f, 11.2f, 7.2f); quadTo(9.4f, 7.8f, 8.0f, 7.4f); close() }
        fill(white) { circle(18.8f, 10.2f, 0.9f) }
        fill(white) { moveTo(12.52f, 6.57f); curveTo(12.63f, 7.0f, 11.59f, 7.63f, 10.19f, 7.98f); curveTo(8.8f, 8.32f, 7.58f, 8.26f, 7.48f, 7.83f); curveTo(7.37f, 7.4f, 8.41f, 6.77f, 9.81f, 6.42f); curveTo(11.2f, 6.08f, 12.42f, 6.14f, 12.52f, 6.57f); close() }

        // Zzz
        line(white, 0.4f) { moveTo(15.0f, 9.8f); lineTo(16.4f, 9.8f); lineTo(15.0f, 11.2f); lineTo(16.4f, 11.2f) }
        line(white, 0.32f) { moveTo(17.2f, 12.6f); lineTo(18.2f, 12.6f); lineTo(17.2f, 13.6f); lineTo(18.2f, 13.6f) }

        // Kuschelwolken unten
        fill(white) { circle(4.0f, 21.0f, 2.6f) }
        fill(white) { circle(8.4f, 20.0f, 3.0f) }
        fill(white) { circle(13.4f, 21.0f, 2.8f) }
        fill(white) { circle(18.4f, 20.2f, 3.0f) }
        fill(white) { circle(22.4f, 21.4f, 2.4f) }
        fill(0xFFE3F2FD) { moveTo(0.0f, 22.0f); lineTo(24.0f, 22.0f); lineTo(24.0f, 24.0f); lineTo(0.0f, 24.0f); close() }

        // Kleines Schäfchen auf der Wolke
        fill(0xFFF5F5F5) { ellipse(18.0f, 17.4f, 1.8f, 1.3f) }
        fill(0xFFF5F5F5) { circle(17.0f, 16.8f, 0.9f) }
        fill(0xFFF5F5F5) { circle(19.0f, 16.8f, 0.9f) }
        fill(0xFF5D4037) { ellipse(19.9f, 17.2f, 0.8f, 0.7f) }
        line(white, 0.25f) { moveTo(19.6f, 17.1f); quadTo(19.85f, 17.35f, 20.1f, 17.1f) }
        line(0xFF5D4037, 0.4f) { moveTo(17.2f, 18.6f); lineTo(17.2f, 19.2f) }
        line(0xFF5D4037, 0.4f) { moveTo(18.8f, 18.6f); lineTo(18.8f, 19.2f) }
    }
}
