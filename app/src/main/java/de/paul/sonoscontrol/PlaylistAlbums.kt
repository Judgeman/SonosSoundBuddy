package de.paul.sonoscontrol

import java.util.Locale

/**
 * Kategorien aus einer Sonos-Playlist: Die Kategorie hängt an genau einer Sonos-Playlist
 * ([MusicCategory.playlistId]), und jedes Album darin ist ein eigener Eintrag
 * ([MusicSource.PLAYLIST_ALBUM]). Gedacht für Hörbücher: Neue Alben kommen nur noch in der
 * Sonos-App in die Playlist, die App übernimmt sie von selbst.
 *
 * Abspielen lässt sich so ein Album nur über den Speaker im Heimnetz — die Control API kennt
 * nur ganze Playlisten. Die App lädt die Playlist in die Warteschlange und nimmt alles
 * heraus, was nicht zum Album gehört ([LocalSonosClient.playAlbumFromPlaylist]).
 */

/** Ein Titel einer Sonos-Playlist, so wie der Speaker im Heimnetz oder die Sonos-Cloud ihn nennt. */
data class PlaylistTrackInfo(
    val title: String?,
    val album: String?,
    val artist: String? = null,
    /** Cover-Adressen des Titels (bei Alben dasselbe wie das des Albums), beste zuerst. */
    val imageUrls: List<String> = emptyList()
)

/** Ein Album einer Sonos-Playlist: alle Titel mit diesem Album-Namen, in der Reihenfolge der Playlist. */
data class PlaylistAlbum(
    /** So, wie Sonos ihn beim ersten Titel nennt. */
    val name: String,
    /** Künstler der Titel, die häufigsten zuerst. */
    val artists: List<String>,
    val trackCount: Int,
    val imageUrls: List<String>
) {
    /** Beschreibung des Eintrags, z. B. „Bibi Blocksberg · 12 Titel“. */
    val description: String
        get() = listOfNotNull(summarizeArtists(artists), if (trackCount == 1) "1 Titel" else "$trackCount Titel")
            .joinToString(" · ")
}

/**
 * Woran die App ein Album wiedererkennt: der Album-Name ohne Unterschied in Groß-/Kleinschreibung
 * und Leerzeichen — Speaker und Cloud schreiben ihn nicht immer exakt gleich. Titel ohne Album
 * stehen für sich, unter ihrem eigenen Namen.
 */
fun albumKey(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

/** Name des Albums, zu dem ein Titel gehört — ohne Album der Titel selbst; null = weder noch. */
fun PlaylistTrackInfo.albumName(): String? =
    album?.trim()?.takeIf { it.isNotEmpty() } ?: title?.trim()?.takeIf { it.isNotEmpty() }

/** Fasst die Titel einer Playlist zu Alben zusammen, in der Reihenfolge ihres ersten Titels. */
fun groupAlbums(tracks: List<PlaylistTrackInfo>): List<PlaylistAlbum> {
    val groups = LinkedHashMap<String, MutableList<PlaylistTrackInfo>>()
    val names = mutableMapOf<String, String>()
    tracks.forEach { track ->
        val name = track.albumName() ?: return@forEach
        val key = albumKey(name)
        names.getOrPut(key) { name }
        groups.getOrPut(key) { mutableListOf() } += track
    }
    return groups.map { (key, albumTracks) ->
        PlaylistAlbum(
            name = names.getValue(key),
            artists = rankArtists(albumTracks.mapNotNull { it.artist }),
            trackCount = albumTracks.size,
            // Die ersten Titel genügen — ein Album hat ein Cover
            imageUrls = albumTracks.take(2).flatMap { it.imageUrls }.distinct()
        )
    }
}

/**
 * Was aus einer Warteschlange mit [queueLength] Titeln heraus muss, damit nur die Plätze [keep]
 * (1-basiert, wie bei Sonos) bleiben: zusammenhängende Bereiche als (erster Platz, Anzahl), von
 * hinten nach vorne — so verschieben sich die noch zu löschenden Plätze nicht.
 */
fun rangesToRemove(keep: Set<Int>, queueLength: Int): List<Pair<Int, Int>> {
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start: Int? = null
    for (position in 1..queueLength + 1) {
        val remove = position <= queueLength && position !in keep
        if (remove && start == null) start = position
        if (!remove && start != null) {
            ranges += start to position - start
            start = null
        }
    }
    return ranges.reversed()
}

private val WHITESPACE = Regex("\\s+")
