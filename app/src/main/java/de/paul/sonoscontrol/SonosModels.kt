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
            ?: containerCoverUrl

    /** Cover des Containers (Playlist, Album, Sender) — für eine Playlist passender als das des Titels. */
    val containerCoverUrl: String?
        get() = container?.let { it.imageUrl.orNullIfBlank() ?: it.images.largestUrl() }
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
    @Transient val foundImageUrl: String? = null,
    /** Das JSON, wie Sonos es geschickt hat — für die Detail-Ansicht im Katalog. */
    @Transient val rawJson: String? = null
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
 * protokoll-relative URLs bekommen https, die Platzhalter aus Apple-Music-
 * Vorlagen (`{w}x{h}{c}.{f}` = Breite, Höhe, Zuschnitt, Format) werden ersetzt.
 * Relative Pfade wie `/getaa?…` lassen sich ohne Adresse des Speakers nicht laden → null.
 */
fun normalizeImageUrl(raw: String): String? {
    var url = raw.trim()
    if (url.isEmpty()) return null
    if (url.startsWith("//")) url = "https:$url"
    IMAGE_URL_PLACEHOLDERS.forEach { (placeholder, value) ->
        url = url.replace("{$placeholder}", value).replace("%7B$placeholder%7D", value, ignoreCase = true)
    }
    return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private val IMAGE_URL_PLACEHOLDERS = listOf("w" to "600", "h" to "600", "c" to "bb", "f" to "jpg")

/**
 * Mehrere Cover-Adressen für denselben Eintrag (z. B. vom Speaker und aus der
 * Cloud) werden durch Zeilenumbrüche getrennt in einem Feld gespeichert —
 * lädt die erste nicht, wird die nächste probiert.
 */
fun imageUrlCandidates(imageUrl: String?): List<String> =
    imageUrl?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

fun joinImageUrls(urls: List<String?>): String? =
    urls.filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n").ifEmpty { null }

@Serializable
data class FavoriteService(
    val name: String? = null
)

@Serializable
data class FavoriteResource(
    /** z. B. TRACK, ALBUM, PLAYLIST, PROGRAM (Radio), ARTIST */
    val type: String? = null
)

/**
 * Zeitlich begrenzte (signierte) Adresse, z. B. Apple Music über Amazon S3
 * (`X-Amz-Date` + `X-Amz-Expires`) oder CloudFront (`Expires=<Unix-Zeit>`).
 * Sonos speichert solche Cover-Adressen beim Anlegen eines Favoriten und
 * liefert sie danach unverändert weiter aus — nach Ablauf antwortet der Server
 * mit 400/403. Solche Cover müssen deshalb sofort auf dem Tablet gespeichert werden.
 */
fun isSignedUrl(url: String): Boolean =
    url.contains("X-Amz-Expires=", ignoreCase = true) || Regex("[?&]Expires=\\d+").containsMatchIn(url)

/** Ist eine signierte Adresse schon abgelaufen? Unsignierte gelten nie als abgelaufen. */
fun isExpiredUrl(url: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val query = url.substringAfter('?', "").split('&').associate {
        val value = it.substringAfter('=', "")
        it.substringBefore('=').lowercase() to (runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull() ?: value)
    }
    query["expires"]?.toLongOrNull()?.let { return it * 1000 < nowMillis }
    val date = query["x-amz-date"] ?: return false
    val seconds = query["x-amz-expires"]?.toLongOrNull() ?: return false
    val signedAt = runCatching {
        java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(date)?.time
    }.getOrNull() ?: return false
    return signedAt + seconds * 1000 < nowMillis
}

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
