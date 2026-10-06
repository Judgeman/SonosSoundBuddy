package de.paul.sonoscontrol

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * Das Tier des gewählten Profils begleitet die Wiedergabe (Figur aus
 * AnimalFigure.kt): Ein kleines läuft auf dem Fortschrittsbalken mit, ein
 * größeres sitzt auf der Ecke des Covers, tanzt zur Musik oder liest aus einem
 * Bilderbuch vor und schläft bei Pause.
 */

/** Was das Tier am Cover macht, solange Musik läuft — pro Kategorie einstellbar. Der Enum-Name steht in der Datenbank. */
enum class CoverAnimation(val label: String) {
    DANCE("Tanzen"),
    READ("Vorlesen");

    companion object {
        fun fromKey(key: String?): CoverAnimation = entries.firstOrNull { it.name == key } ?: DANCE

        /**
         * Vorschlag für eine Kategorie: Hörbücher, Hörspiele, Geschichten und
         * Märchen (am Namen oder am Hörbuch-Bild erkannt) werden vorgelesen.
         * Dieselbe Regel steht in AppDatabase.MIGRATION_10_11.
         */
        fun suggestedFor(name: String, imageKey: String? = null): CoverAnimation =
            if (imageKey == ReadingImageKey || ReadingWords.any { name.contains(it, ignoreCase = true) }) READ else DANCE
    }
}

private const val ReadingImageKey = "scene:POP_UP_BOOK"
private val ReadingWords = listOf("hörb", "hörsp", "geschicht", "märchen")

/** Platz über dem Fortschrittsbalken, in dem das kleine Tier läuft. */
val ProgressWalkerHeight = 56.dp

private const val WalkerScale = 0.65f
private const val WalkerStepsPerSecond = 2.4f

private const val BeatSeconds = 0.6f
private const val ZzzSeconds = 3.6f
private const val NoteSeconds = 2.4f
private const val PageSeconds = 3.5f
private const val PageTurnSeconds = 0.8f
private const val LetterSeconds = 2.8f

private val NoteColors = listOf(Color(0xFFFF8A3D), Color(0xFF00BFA5), Color(0xFFFF4F9A))
private val ZzzColor = Color(0xFFE8EAF6)
private val ZzzOutline = Color(0xFF3F3D56)

private val BookCover = Color(0xFF5C6BC0)
private val BookCoverDark = Color(0xFF3949AB)
private val BookPage = Color(0xFFFFF8E1)
private val BookPageBack = Color(0xFFF1E4C3)
private val BookLine = Color(0xFFBCAAA4)
private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

// Aufgeschlagenes Buch in dp der Grundgröße: halbe Breite (x von der Mitte), oben, unten
private const val BookHalf = 22f
private const val BookTop = 55f
private const val BookBottom = 80f
private const val PageHalf = BookHalf - 1.5f
private const val PageTop = BookTop + 1.5f
private const val PageBottom = BookBottom - 1.5f
private const val PageMiddle = (PageTop + PageBottom) / 2f

/**
 * Das kleine Tier über dem Fortschrittsbalken. Es steht an der Stelle des
 * Fortschritts und läuft, solange die Musik spielt; bei Pause geht es den
 * angefangenen Schritt zu Ende und bleibt stehen. [progress] `null` = Live
 * (Radio): Dann läuft es in der Mitte auf der Stelle.
 *
 * Gedacht für eine Fläche von [ProgressWalkerHeight] direkt über dem Balken;
 * die Füße reichen ein Stück auf den Balken.
 */
@Composable
fun ProgressWalker(icon: ProfileIcon, progress: Float?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val look = animalLook(icon)
    val head = rememberVectorPainter(icon.vector)
    val shownProgress by animateFloatAsState(progress ?: 0.5f, tween(500, easing = LinearEasing), label = "walkerProgress")
    val lean by animateFloatAsState(if (isPlaying) 1f else 0f, tween(400), label = "walkerLean")

    // Gezählte Schritte; bei jeder ganzen Zahl stehen beide Füße am Boden
    var steps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        val until = if (isPlaying) Float.POSITIVE_INFINITY else ceil(steps)
        var last = withFrameNanos { it }
        while (steps < until) {
            withFrameNanos { now ->
                steps = min(until, steps + (now - last) / 1_000_000_000f * WalkerStepsPerSecond)
                last = now
            }
            // Zwei Schritte sind eine ganze Runde — so wird die Zahl nicht beliebig groß
            if (isPlaying && steps >= 2f) steps -= 2f
        }
    }

    BoxWithConstraints(modifier) {
        val width = FigureWidth * WalkerScale
        val height = FigureHeight * WalkerScale
        val travel = maxWidth.value - width
        Canvas(
            modifier = Modifier
                .offset {
                    val x = (shownProgress * maxWidth.value - width / 2f).coerceIn(0f, travel.coerceAtLeast(0f))
                    IntOffset(x.dp.roundToPx(), (ProgressWalkerHeight.value + 4f - height).dp.roundToPx())
                }
                .size(width.dp, height.dp)
                .graphicsLayer {
                    rotationZ = 5f * lean
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            val stepPhase = steps * PI.toFloat()
            drawAnimalFigure(
                look,
                head,
                FigurePose(
                    stepPhase = stepPhase,
                    leftArm = swingingArm(-1, stepPhase),
                    rightArm = swingingArm(1, stepPhase)
                )
            )
        }
    }
}

/**
 * Das Tier auf der unteren rechten Ecke des Covers. Solange Musik läuft, tanzt
 * es mit fliegenden Noten ([CoverAnimation.DANCE]) oder hält ein aufgeschlagenes
 * Bilderbuch zu den Kindern hin, blättert um, und Buchstaben steigen auf
 * ([CoverAnimation.READ]). Bei Pause schläft es mit geschlossenen Augen und
 * „Zzz" (das Buch zugeklappt), beim Laden steht es wach da. Antippen lässt es
 * hüpfen, und ein Herz steigt auf. [modifier] sollte die ganze Cover-Fläche abdecken.
 */
@Composable
fun CoverAnimal(
    icon: ProfileIcon,
    animation: CoverAnimation,
    isPlaying: Boolean,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    val look = animalLook(icon)
    val head = rememberVectorPainter(icon.vector)
    val textMeasurer = rememberTextMeasurer()
    val reading = animation == CoverAnimation.READ
    val active by animateFloatAsState(if (isPlaying) 1f else 0f, tween(500), label = "active")
    val awake by animateFloatAsState(if (isPlaying || isBuffering) 1f else 0f, tween(700), label = "awake")
    val clock = produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    // Vorlese-Zeit läuft nur, solange Musik spielt — so blättert das Buch in der Pause nicht weiter
    var readTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying && reading) {
        if (!(isPlaying && reading)) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                readTime += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    var tappedAt by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier) {
        // Etwa ein Viertel der Cover-Breite, auf kleinen und großen Bildschirmen in Grenzen
        val width = (maxWidth.value * 0.26f).coerceIn(64f, 150f)
        val scale = width / FigureWidth
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (maxWidth.value * 0.04f).dp)
                .size(width.dp, (FigureHeight * scale).dp)
                .graphicsLayer {
                    val t = clock.value
                    val hop = hopHeight(tappedAt?.let { t - it })
                    if (reading) {
                        // Schaukelt beim Vorlesen sanft hin und her
                        translationY = -hop * scale * density
                        rotationZ = sin(2f * PI.toFloat() * t / 5f) * 2f * active
                    } else {
                        // Hüpft auf jeden Schlag und schaukelt hin und her
                        val beat = sin(PI.toFloat() * t / BeatSeconds)
                        translationY = -(abs(beat) * 5f * active + hop) * scale * density
                        rotationZ = beat * 7f * active
                    }
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .pointerInput(Unit) { detectTapGestures { tappedAt = clock.value } }
        ) {
            val t = clock.value
            val asleep = 1f - awake
            if (asleep > 0f) drawZzz(t, asleep)
            if (reading) {
                val open = awake > 0.5f
                drawAnimalFigure(look, head, readerPose(t, asleep, open)) { u ->
                    if (open) drawOpenBook(readTime, u) else drawClosedBook(u)
                }
                if (active > 0f) drawLetters(readTime, active, textMeasurer)
            } else {
                if (active > 0f) drawNotes(t, active)
                drawAnimalFigure(look, head, dancerPose(t, active, asleep))
            }
            heartProgress(tappedAt?.let { t - it })?.let { drawHeart(it) }
        }
    }
}

/** Tanzen: Füße im Takt, Arme abwechselnd hoch, Kopf wippt. Schlafen: Kopf zur Seite, Augen zu, ruhiges Atmen. */
private fun dancerPose(t: Float, dance: Float, asleep: Float): FigurePose {
    val beat = sin(PI.toFloat() * t / BeatSeconds)
    val up = 2.6f
    val left = dance * (0.5f + 0.5f * beat)
    val right = dance * (0.5f - 0.5f * beat)
    return FigurePose(
        stepPhase = PI.toFloat() * t / BeatSeconds,
        stepAmount = 0.6f * dance,
        leftArm = RestArmAngle + (up - RestArmAngle) * left,
        rightArm = RestArmAngle + (up - RestArmAngle) * right,
        leftArmLength = ArmLength + 4f * left,
        rightArmLength = ArmLength + 4f * right,
        headTilt = -beat * 6f * dance + sleepyTilt(t) * asleep,
        eyesClosed = asleep > 0.5f,
        shadow = true
    )
}

/** Vorlesen: hält das Buch an den unteren Ecken, schaut mit leicht wiegendem Kopf hinein. Schlafen wie beim Tanzen. */
private fun readerPose(t: Float, asleep: Float, open: Boolean): FigurePose {
    // Pfoten an den Seiten des Buchs, zugeklappt liegt es schmaler auf dem Bauch
    val edge = if (open) BookHalf - 1f else 11f
    val (leftArm, leftLength) = armTo(-1, -edge, 74f)
    val (rightArm, rightLength) = armTo(1, edge, 74f)
    return FigurePose(
        stepAmount = 0f,
        leftArm = leftArm,
        rightArm = rightArm,
        leftArmLength = leftLength,
        rightArmLength = rightLength,
        headTilt = sin(2f * PI.toFloat() * t / 4f) * 4f * (1f - asleep) + sleepyTilt(t) * asleep,
        eyesClosed = asleep > 0.5f,
        shadow = true
    )
}

/** Kopf im Schlaf zur Seite geneigt, mit ruhigem Atmen. */
private fun sleepyTilt(t: Float) = 12f + 1.5f * sin(2f * PI.toFloat() * t / 4f)

/**
 * Aufgeschlagenes Bilderbuch, den Kindern zugewandt: links ein Bild, rechts
 * Textzeilen. Alle [PageSeconds] blättert eine Seite um, danach ist links ein
 * neues Bild. Koordinaten in dp der Grundgröße ([u] Pixel), x von der Mitte.
 */
private fun DrawScope.drawOpenBook(readTime: Float, u: Float) {
    val cx = size.width / 2f
    fun x(v: Float) = cx + v * u
    fun y(v: Float) = v * u
    val coverSize = Size(2f * BookHalf * u, (BookBottom - BookTop) * u)
    val corner = CornerRadius(2f * u)

    drawRoundRect(BookCover, Offset(x(-BookHalf), y(BookTop)), coverSize, corner)
    drawRoundRect(BookCoverDark, Offset(x(-BookHalf), y(BookTop)), coverSize, corner, style = Stroke(1.2f * u))
    drawRect(BookPage, Offset(x(-PageHalf), y(PageTop)), Size(2f * PageHalf * u, (PageBottom - PageTop) * u))

    val spread = floor(readTime / PageSeconds).toInt()
    val turn = ((readTime - spread * PageSeconds - (PageSeconds - PageTurnSeconds)) / PageTurnSeconds).coerceIn(0f, 1f)

    // Links das Bild der aktuellen Doppelseite, rechts Text
    val pictureSize = 6f * u
    drawPicture(spread, Offset(x(-PageHalf / 2f), y(PageMiddle)), pictureSize, BookPage)
    for (line in 0 until 5) {
        val lineY = y(PageTop + 4f + line * 3.6f)
        val end = if (line == 4) PageHalf * 0.6f else PageHalf - 3f
        drawLine(BookLine, Offset(x(3f), lineY), Offset(x(end), lineY), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
    }
    drawLine(BookLine, Offset(x(0f), y(PageTop)), Offset(x(0f), y(PageBottom)), strokeWidth = 0.8f * u)

    // Umblättern: Die rechte Seite klappt über den Rücken nach links, auf ihrer Rückseite das nächste Bild
    if (turn > 0f && turn < 1f) {
        val edge = PageHalf * cos(PI.toFloat() * turn)
        val lift = sin(PI.toFloat() * turn) * 3f
        val page = Path().apply {
            moveTo(x(0f), y(PageTop))
            lineTo(x(edge), y(PageTop - lift))
            lineTo(x(edge), y(PageBottom - lift))
            lineTo(x(0f), y(PageBottom))
            close()
        }
        val back = turn > 0.5f
        drawPath(page, if (back) BookPageBack else BookPage)
        drawPath(page, BookLine, style = Stroke(0.6f * u))
        if (back) {
            // Mitte der umklappenden Seite; dort wird das Bild auf ihre Breite gestaucht
            val center = Offset(x(edge / 2f), y(PageMiddle - lift / 2f))
            scale(scaleX = -edge / PageHalf, scaleY = 1f, pivot = center) {
                drawPicture(spread + 1, center, pictureSize, BookPageBack)
            }
        }
    }
}

/** Zugeklapptes Buch, das das schlafende Tier auf dem Bauch hält. */
private fun DrawScope.drawClosedBook(u: Float) {
    val cx = size.width / 2f
    val topLeft = Offset(cx - 11f * u, 57f * u)
    val bookSize = Size(22f * u, 20f * u)
    val corner = CornerRadius(2f * u)
    drawRoundRect(BookCover, topLeft, bookSize, corner)
    drawRect(BookCoverDark, topLeft, Size(3f * u, 20f * u))
    drawRoundRect(BookPage, Offset(cx - 5f * u, 61f * u), Size(12f * u, 5f * u), CornerRadius(1f * u))
    drawRoundRect(BookCoverDark, topLeft, bookSize, corner, style = Stroke(1.2f * u))
}

/** Kleine Bilder für die linke Buchseite, der Reihe nach: Sonne, Herz, Stern, Baum, Mond. */
private fun DrawScope.drawPicture(index: Int, c: Offset, s: Float, page: Color) {
    when (index.mod(5)) {
        0 -> {
            for (ray in 0 until 8) {
                val a = ray * PI.toFloat() / 4f
                val dir = Offset(cos(a), sin(a))
                drawLine(Color(0xFFFFB300), c + dir * (0.75f * s), c + dir * (1.05f * s), strokeWidth = 0.18f * s, cap = StrokeCap.Round)
            }
            drawCircle(Color(0xFFFFC107), 0.55f * s, c)
        }
        1 -> drawPath(
            Path().apply {
                moveTo(c.x, c.y + 0.8f * s)
                cubicTo(c.x - 1.3f * s, c.y + 0.05f * s, c.x - 0.7f * s, c.y - 0.9f * s, c.x, c.y - 0.3f * s)
                cubicTo(c.x + 0.7f * s, c.y - 0.9f * s, c.x + 1.3f * s, c.y + 0.05f * s, c.x, c.y + 0.8f * s)
                close()
            },
            Color(0xFFE91E63)
        )
        2 -> drawPath(
            Path().apply {
                for (point in 0 until 10) {
                    val r = if (point % 2 == 0) 1.0f * s else 0.42f * s
                    val a = -PI.toFloat() / 2f + point * PI.toFloat() / 5f
                    val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
                    if (point == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            },
            Color(0xFFFFB300)
        )
        3 -> {
            drawRect(Color(0xFF8D6E63), Offset(c.x - 0.15f * s, c.y + 0.1f * s), Size(0.3f * s, 0.8f * s))
            drawCircle(Color(0xFF66BB6A), 0.65f * s, Offset(c.x, c.y - 0.2f * s))
        }
        else -> {
            drawCircle(Color(0xFFFFD54F), 0.7f * s, c)
            drawCircle(page, 0.6f * s, Offset(c.x + 0.4f * s, c.y - 0.2f * s))
        }
    }
}

/** Bunte Buchstaben, die nacheinander aus dem Buch aufsteigen — bei jedem Aufsteigen ein anderer. */
private fun DrawScope.drawLetters(readTime: Float, amount: Float, textMeasurer: TextMeasurer) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    val count = NoteColors.size
    for (i in 0 until count) {
        val cycle = readTime / LetterSeconds + i / count.toFloat()
        val phase = cycle - floor(cycle)
        val letter = Alphabet[(floor(cycle).toInt() * 7 + i * 11).mod(Alphabet.length)].toString()
        val side = if (i % 2 == 0) -1 else 1
        val layout = textMeasurer.measure(
            letter,
            TextStyle(color = NoteColors[i], fontSize = (11f * u).toSp(), fontWeight = FontWeight.Black)
        )
        // Steigen außen neben dem Kopf auf, damit er sie nicht verdeckt
        val x = cx + side * (BookHalf - 1f + 10f * phase) * u + sin(phase * 2f * PI.toFloat()) * 3f * u
        val y = (BookTop - 2f - 48f * phase) * u
        drawText(
            layout,
            topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
            alpha = sin(PI.toFloat() * phase) * amount
        )
    }
}

/** Drei „Z", die nacheinander vom Kopf schräg nach oben steigen, größer werden und verblassen. */
private fun DrawScope.drawZzz(t: Float, amount: Float) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    for (i in 0 until 3) {
        val phase = (t / ZzzSeconds + i / 3f) % 1f
        val alpha = sin(PI.toFloat() * phase) * amount
        val letter = (6f + 8f * phase) * u
        val x = cx + (16f + 24f * phase + 3f * sin(phase * 6f)) * u
        val y = (6f - 44f * phase) * u
        val z = Path().apply {
            moveTo(x, y)
            lineTo(x + letter, y)
            lineTo(x, y + letter)
            lineTo(x + letter, y + letter)
        }
        drawPath(z, ZzzOutline.copy(alpha = alpha), style = Stroke(4.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(z, ZzzColor.copy(alpha = alpha), style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Bunte Noten, die abwechselnd links und rechts neben dem Tier aufsteigen. */
private fun DrawScope.drawNotes(t: Float, amount: Float) {
    val u = size.width / FigureWidth
    val cx = size.width / 2f
    for (i in NoteColors.indices) {
        val phase = (t / NoteSeconds + i / NoteColors.size.toFloat()) % 1f
        val side = if (i % 2 == 0) -1 else 1
        val alpha = sin(PI.toFloat() * phase) * amount
        val x = cx + side * (30f + 10f * phase) * u + sin(phase * 2f * PI.toFloat()) * 4f * u
        val y = (40f - 50f * phase) * u
        drawNote(Offset(x, y), 10f * u, NoteColors[i].copy(alpha = alpha))
    }
}

/** Achtelnote: Kopf, Hals und Fähnchen; [at] ist die Mitte des Notenkopfs. */
private fun DrawScope.drawNote(at: Offset, size: Float, color: Color) {
    val stemX = at.x + size * 0.32f
    val stemTop = at.y - size * 1.2f
    drawCircle(color, size * 0.38f, at)
    drawLine(color, Offset(stemX, at.y), Offset(stemX, stemTop), strokeWidth = size * 0.16f, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(stemX, stemTop)
        cubicTo(
            stemX + size * 0.45f, stemTop + size * 0.1f,
            stemX + size * 0.6f, stemTop + size * 0.4f,
            stemX + size * 0.5f, stemTop + size * 0.75f
        )
    }
    drawPath(flag, color, style = Stroke(size * 0.16f, cap = StrokeCap.Round))
}
