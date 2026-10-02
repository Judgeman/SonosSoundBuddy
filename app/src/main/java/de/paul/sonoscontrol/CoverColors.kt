package de.paul.sonoscontrol

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aus dem Cover abgeleitete Farben für den Homescreen-Hintergrund (à la Apple Music). */
data class CoverColors(
    val gradientTop: Color,
    val gradientBottom: Color,
    /** Helle Akzentfarbe für Play-Knopf, Fortschrittsbalken und Status-Badge. */
    val accent: Color
) {
    companion object {
        fun from(palette: Palette): CoverColors? {
            val base = (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)
                ?.let { Color(it.rgb) } ?: return null
            val deep = (palette.darkMutedSwatch ?: palette.darkVibrantSwatch)?.let { Color(it.rgb) } ?: base
            val light = (palette.lightVibrantSwatch ?: palette.lightMutedSwatch)?.let { Color(it.rgb) }
                ?: lerp(base, Color.White, 0.5f)

            // Hintergrund dunkel genug halten, damit weiße Schrift immer lesbar ist
            return CoverColors(
                gradientTop = darkenTo(base, maxLuminance = 0.22f),
                gradientBottom = darkenTo(deep, maxLuminance = 0.06f),
                accent = lightenTo(light, minLuminance = 0.55f)
            )
        }

        private fun darkenTo(color: Color, maxLuminance: Float): Color {
            var result = color
            var step = 0
            while (result.luminance() > maxLuminance && step < 20) {
                result = lerp(result, Color.Black, 0.1f)
                step++
            }
            return result
        }

        private fun lightenTo(color: Color, minLuminance: Float): Color {
            var result = color
            var step = 0
            while (result.luminance() < minLuminance && step < 20) {
                result = lerp(result, Color.White, 0.1f)
                step++
            }
            return result
        }
    }
}

/**
 * Lädt das Cover (klein, aus Coils Cache) und berechnet daraus die Farben.
 * Beim Track-Wechsel bleiben die alten Farben stehen, bis die neuen da sind.
 */
@Composable
fun rememberCoverColors(imageUrl: String?): CoverColors? {
    val context = LocalContext.current
    var colors by remember { mutableStateOf<CoverColors?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            colors = null
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .allowHardware(false) // Palette braucht Zugriff auf die Pixel
            .build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        colors = withContext(Dispatchers.Default) {
            CoverColors.from(Palette.from(bitmap).maximumColorCount(16).generate())
        }
    }
    return colors
}

/**
 * Tauscht für den Homescreen das Farbschema gegen eins aus den Cover-Farben
 * (dunkler Verlauf, weiße Schrift) und blendet sanft zwischen Covern über.
 * Ohne Cover-Farben bleibt das normale App-Theme aktiv.
 */
@Composable
fun CoverTheme(colors: CoverColors?, content: @Composable (background: Brush) -> Unit) {
    val base = MaterialTheme.colorScheme
    val active = colors != null
    val animation = tween<Color>(durationMillis = 900)

    @Composable
    fun animated(cover: Color?, default: Color): Color =
        animateColorAsState(cover ?: default, animation, label = "coverColor").value

    val top = animated(colors?.gradientTop, base.background)
    val bottom = animated(colors?.gradientBottom, base.background)
    val accent = animated(colors?.accent, base.primary)
    val onAccent = animated(colors?.gradientBottom, base.onPrimary)
    val content1 = animated(if (active) Color.White else null, base.onSurface)
    val content2 = animated(if (active) Color.White.copy(alpha = 0.72f) else null, base.onSurfaceVariant)
    val glass = animated(if (active) Color.White.copy(alpha = 0.16f) else null, base.primaryContainer)
    val onGlass = animated(if (active) Color.White else null, base.onPrimaryContainer)
    val tonal = animated(if (active) Color.White.copy(alpha = 0.14f) else null, base.secondaryContainer)
    val onTonal = animated(if (active) Color.White else null, base.onSecondaryContainer)
    val menu = animated(colors?.let { lerp(it.gradientTop, Color.Black, 0.3f) }, base.surfaceContainer)

    StatusBarIcons(light = !active && !isSystemInDarkTheme())

    MaterialTheme(
        colorScheme = base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = glass,
            onPrimaryContainer = onGlass,
            secondaryContainer = tonal,
            onSecondaryContainer = onTonal,
            background = top,
            onBackground = content1,
            surface = top,
            onSurface = content1,
            onSurfaceVariant = content2,
            surfaceContainer = menu
        )
    ) {
        content(Brush.verticalGradient(listOf(top, bottom)))
    }
}

/** Dunkle Statusleisten-Icons auf hellem Hintergrund, helle auf dem Cover-Verlauf. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = light
    }
}
