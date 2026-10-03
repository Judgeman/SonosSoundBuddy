package de.paul.sonoscontrol

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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
fun MusicCover(imageUrl: String?, type: MusicType, modifier: Modifier = Modifier, size: Dp? = null) {
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
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val MusicCoverLargeThreshold = 80.dp
