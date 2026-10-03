package de.paul.sonoscontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class Household(
    val id: String,
    val name: String? = null
)

@Serializable
data class HouseholdsResponse(
    val households: List<Household> = emptyList()
)

@Serializable
data class SonosPlayer(
    val id: String,
    val name: String,
    val icon: String? = null,
    val capabilities: List<String>? = null
)

@Serializable
data class SonosGroup(
    val id: String,
    val name: String,
    val coordinatorId: String,
    val playbackState: String? = null,
    val playerIds: List<String> = emptyList()
)

/** Antwort von GET /households/{householdId}/groups — enthält Groups UND Players. */
@Serializable
data class GroupsResponse(
    val groups: List<SonosGroup> = emptyList(),
    val players: List<SonosPlayer> = emptyList()
)

/** Antwort von GET /groups/{groupId}/playback */
@Serializable
data class PlaybackStatus(
    val playbackState: String? = null,
    val positionMillis: Long = 0,
    val availablePlaybackActions: PlaybackActions? = null
) {
    val isPlaying: Boolean get() = playbackState == STATE_PLAYING
    val isBuffering: Boolean get() = playbackState == STATE_BUFFERING
    val isPaused: Boolean get() = playbackState == STATE_PAUSED

    companion object {
        const val STATE_PLAYING = "PLAYBACK_STATE_PLAYING"
        const val STATE_BUFFERING = "PLAYBACK_STATE_BUFFERING"
        const val STATE_PAUSED = "PLAYBACK_STATE_PAUSED"
    }
}

/** Welche Steuerbefehle die aktuelle Quelle erlaubt (z. B. kein Skip bei Radio). */
@Serializable
data class PlaybackActions(
    val canSkip: Boolean = true,
    val canSkipBack: Boolean = true,
    val canPlay: Boolean = true,
    val canPause: Boolean = true
)

/** Antwort von GET /groups/{groupId}/playbackMetadata */
@Serializable
data class PlaybackMetadata(
    val container: MetadataContainer? = null,
    val currentItem: MetadataItem? = null,
    val streamInfo: String? = null
) {
    /**
     * Cover des aktuellen Titels. Je nach Quelle liefert Sonos es als `imageUrl`
     * oder nur in der `images`-Liste, am Track oder nur am Container (Album/Sender).
     */
    val coverUrl: String?
        get() = currentItem?.track?.let { it.imageUrl.orNullIfBlank() ?: it.images.largestUrl() }
            ?: container?.let { it.imageUrl.orNullIfBlank() ?: it.images.largestUrl() }
}

@Serializable
data class MetadataImage(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

internal fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

internal fun List<MetadataImage>.largestUrl(): String? =
    filter { !it.url.isNullOrBlank() }
        .maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        ?.url

@Serializable
data class MetadataContainer(
    val name: String? = null,
    val type: String? = null,
    val imageUrl: String? = null,
    val images: List<MetadataImage> = emptyList()
)

@Serializable
data class MetadataItem(
    val track: Track? = null
)

@Serializable
data class Track(
    val name: String? = null,
    val imageUrl: String? = null,
    val images: List<MetadataImage> = emptyList(),
    val durationMillis: Long? = null,
    val artist: NamedItem? = null,
    val album: NamedItem? = null
)

@Serializable
data class NamedItem(
    val name: String? = null
)

/** Antwort von GET /players/{playerId}/playerVolume */
@Serializable
data class PlayerVolume(
    val volume: Int = 0,
    val muted: Boolean = false,
    val fixed: Boolean = false
)

/** Eintrag aus GET /households/{householdId}/favorites (Liste `items`) */
@Serializable
data class SonosFavorite(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val images: List<MetadataImage> = emptyList(),
    val service: FavoriteService? = null,
    val resource: FavoriteResource? = null,
    /** Cover, das [findImageUrl] irgendwo im Favoriten gefunden hat (nicht Teil des JSON). */
    @Transient val foundImageUrl: String? = null
) {
    val coverUrl: String?
        get() = (imageUrl.orNullIfBlank() ?: images.largestUrl())?.let(::normalizeImageUrl) ?: foundImageUrl
}

/**
 * Sucht in einem Objekt der Sonos-API ein Cover — je nach Musikdienst steckt es
 * direkt am Favoriten, an der `resource` oder in einem verschachtelten Objekt,
 * als `imageUrl`, `images`-Liste oder `albumArtUri`. Näher liegende Felder
 * gewinnen. Das Logo des Musikdienstes (`service`) wird bewusst übergangen.
 */
fun findImageUrl(element: JsonObject): String? {
    var level = listOf(element)
    repeat(4) {
        level.forEach { obj -> imageUrlIn(obj)?.let { return it } }
        level = level.flatMap { obj ->
            obj.filterKeys { it != "service" }.values.flatMap { value ->
                when (value) {
                    is JsonObject -> listOf(value)
                    is JsonArray -> value.filterIsInstance<JsonObject>()
                    else -> emptyList()
                }
            }
        }
        if (level.isEmpty()) return null
    }
    return null
}

private fun imageUrlIn(obj: JsonObject): String? {
    IMAGE_URL_KEYS.forEach { key ->
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::normalizeImageUrl)?.let { return it }
    }
    val images = obj["images"] as? JsonArray ?: return null
    return images.filterIsInstance<JsonObject>()
        .mapNotNull { image ->
            val url = (image["url"] as? JsonPrimitive)?.contentOrNull?.let(::normalizeImageUrl) ?: return@mapNotNull null
            val width = (image["width"] as? JsonPrimitive)?.intOrNull ?: 0
            val height = (image["height"] as? JsonPrimitive)?.intOrNull ?: 0
            url to width * height
        }
        .maxByOrNull { it.second }
        ?.first
}

private val IMAGE_URL_KEYS = listOf("imageUrl", "albumArtUri", "albumArtURI", "artUrl")

/**
 * Macht aus dem, was Sonos als Bild-URL liefert, eine ladbare Adresse:
 * protokoll-relative URLs bekommen https, Platzhalter für die Größe
 * (z. B. Apple Music `{w}x{h}`) werden ersetzt. Relative Pfade wie
 * `/getaa?…` lassen sich ohne Adresse des Speakers nicht laden → null.
 */
fun normalizeImageUrl(raw: String): String? {
    var url = raw.trim()
    if (url.isEmpty()) return null
    if (url.startsWith("//")) url = "https:$url"
    url = url.replace("{w}", COVER_SIZE).replace("{h}", COVER_SIZE)
        .replace("%7Bw%7D", COVER_SIZE).replace("%7Bh%7D", COVER_SIZE)
    return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private const val COVER_SIZE = "600"

@Serializable
data class FavoriteService(
    val name: String? = null
)

@Serializable
data class FavoriteResource(
    /** z. B. TRACK, ALBUM, PLAYLIST, PROGRAM (Radio), ARTIST */
    val type: String? = null
)

/** Antwort von GET /households/{householdId}/playlists */
@Serializable
data class PlaylistsResponse(
    val playlists: List<SonosPlaylist> = emptyList()
)

@Serializable
data class SonosPlaylist(
    val id: String,
    val name: String = "",
    val trackCount: Int? = null
)

/** Antwort von POST /households/{householdId}/playlists/getPlaylist */
@Serializable
data class PlaylistDetails(
    val name: String? = null,
    val tracks: List<PlaylistTrack> = emptyList()
)

@Serializable
data class PlaylistTrack(
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null
)
