package de.paul.sonoscontrol

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

val MusicType.icon: ImageVector
    get() = when (this) {
        MusicType.TRACK -> Icons.Rounded.MusicNote
        MusicType.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
        MusicType.ALBUM -> Icons.Rounded.Album
        MusicType.RADIO -> Icons.Rounded.Radio
        MusicType.OTHER -> Icons.Rounded.LibraryMusic
    }

/**
 * Quadratisches Cover eines Musik-Eintrags. Ohne Bild (z. B. Sonos-Playlisten)
 * oder solange es lädt, steht dort ein Symbol für die Art des Inhalts.
 * Ohne [size] füllt es die verfügbare Breite.
 */
@Composable
fun MusicCover(
    imageUrl: String?,
    type: MusicType,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    /** [imageUrl] ist ein Pfad im App-Speicher (eigenes Kategorie-Bild). */
    isFile: Boolean = false
) {
    val shape = RoundedCornerShape(if (size != null && size < MusicCoverLargeThreshold) 8.dp else 20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxWidth())
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxSize(0.5f)
        )
        if (imageUrl != null) {
            AsyncImage(
                model = if (isFile) File(imageUrl) else imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { Log.w("Cover", "Cover konnte nicht geladen werden: $imageUrl", it.result.throwable) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val MusicCoverLargeThreshold = 80.dp

/** Bild einer Kategorie: Icon, Tier oder eigenes Foto; ohne Auswahl ein Musik-Icon. */
@Composable
fun CategoryImageView(category: CategoryWithMusic, modifier: Modifier = Modifier, size: Dp? = null) {
    CustomImageView(category.category.image, modifier, size, default = CategoryDefaultImage)
}

/** Bild eines Musik-Eintrags: das selbst gewählte, sonst das Cover von Sonos. */
@Composable
fun MusicItemImage(item: MusicItem, modifier: Modifier = Modifier, size: Dp? = null) {
    CustomImageView(item.customImage, modifier, size, default = CustomImage.Default) {
        MusicCover(item.imageUrl, item.musicType, modifier = it)
    }
}

private val CategoryDefaultImage = CustomImage.Icon(SpeakerIcon.MUSIC)

/**
 * Quadratische Anzeige eines [CustomImage]. Ist nichts gewählt, wird [default]
 * gezeigt — oder, wenn das auch [CustomImage.Default] ist, [fallback].
 */
@Composable
fun CustomImageView(
    image: CustomImage,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    default: CustomImage = CustomImage.Default,
    fallback: @Composable (Modifier) -> Unit = { MusicCover(null, MusicType.OTHER, modifier = it) }
) {
    val shape = RoundedCornerShape(if (size != null && size < MusicCoverLargeThreshold) 12.dp else 24.dp)
    val sized = modifier
        .then(if (size != null) Modifier.size(size) else Modifier.fillMaxWidth())
        .aspectRatio(1f)
        .clip(shape)

    when (val shown = if (image == CustomImage.Default) default else image) {
        is CustomImage.Icon -> Box(
            contentAlignment = Alignment.Center,
            modifier = sized.background(shown.icon.color)
        ) {
            Icon(
                imageVector = shown.icon.vector,
                contentDescription = null,
                tint = if (shown.icon.multicolor) Color.Unspecified else Color.White,
                modifier = Modifier.fillMaxSize(if (shown.icon.multicolor) 0.8f else 0.6f)
            )
        }

        is CustomImage.Animal -> Box(
            contentAlignment = Alignment.Center,
            modifier = sized.background(shown.icon.color)
        ) {
            Icon(
                imageVector = shown.icon.vector,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize(0.85f)
            )
        }

        is CustomImage.File -> MusicCover(shown.path, MusicType.OTHER, modifier = sized, isFile = true)
        CustomImage.Default -> fallback(sized)
    }
}
