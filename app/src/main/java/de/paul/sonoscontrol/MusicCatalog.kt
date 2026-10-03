package de.paul.sonoscontrol

/**
 * Woher ein Musik-Eintrag kommt. Die Sonos Control API kann nur Sonos-Favoriten
 * und Sonos-Playlisten abspielen — beides zusammen ist der Katalog, aus dem die
 * Musikauswahl der Profile zusammengestellt wird. Der Enum-Name steht in der Datenbank.
 */
enum class MusicSource(val label: String) {
    FAVORITE("Sonos-Favorit"),
    PLAYLIST("Sonos-Playlist");

    companion object {
        fun fromKey(key: String?): MusicSource = entries.firstOrNull { it.name == key } ?: FAVORITE
    }
}

/** Art des Inhalts, für Filter und Beschriftung. Der Enum-Name steht in der Datenbank. */
enum class MusicType(val label: String, val pluralLabel: String) {
    TRACK("Song", "Songs"),
    PLAYLIST("Playlist", "Playlisten"),
    ALBUM("Album", "Alben"),
    RADIO("Radio", "Radio"),
    OTHER("Sonstiges", "Sonstiges");

    companion object {
        fun fromKey(key: String?): MusicType = entries.firstOrNull { it.name == key } ?: OTHER

        /** Ordnet den `resource.type` eines Sonos-Favoriten zu. */
        fun fromFavoriteType(type: String?): MusicType = when (type?.uppercase()) {
            "TRACK", "EPISODE" -> TRACK
            "PLAYLIST" -> PLAYLIST
            "ALBUM" -> ALBUM
            "PROGRAM", "STATION", "STREAM", "RADIO" -> RADIO
            else -> OTHER
        }
    }
}

/** Ein abspielbarer Eintrag im Sonos-Katalog (Favorit oder Playlist). */
data class CatalogEntry(
    val source: MusicSource,
    val sonosId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val type: MusicType
) {
    val key: String get() = "${source.name}:$sonosId"

    companion object {
        fun from(favorite: SonosFavorite) = CatalogEntry(
            source = MusicSource.FAVORITE,
            sonosId = favorite.id,
            name = favorite.name,
            description = favorite.description?.takeIf { it.isNotBlank() } ?: favorite.service?.name,
            imageUrl = favorite.coverUrl,
            type = MusicType.fromFavoriteType(favorite.resource?.type)
        )

        fun from(playlist: SonosPlaylist) = CatalogEntry(
            source = MusicSource.PLAYLIST,
            sonosId = playlist.id,
            name = playlist.name,
            description = playlist.trackCount?.let { if (it == 1) "1 Titel" else "$it Titel" },
            imageUrl = null,
            type = MusicType.PLAYLIST
        )
    }
}

val MusicItem.catalogKey: String get() = "$source:$sonosId"

/** Kategorie eines Profils samt ihrer Musik. */
data class CategoryWithMusic(
    val category: MusicCategory,
    val items: List<MusicItem>
)

/** Profil samt Kategorien, so wie Home- und Settings-Screen es brauchen. */
data class ProfileWithMusic(
    val profile: ChildProfile,
    val categories: List<CategoryWithMusic>
) {
    val itemCount: Int get() = categories.sumOf { it.items.size }

    /** Kategorien, in denen etwas zum Abspielen steckt — nur die sehen die Kinder. */
    val playableCategories: List<CategoryWithMusic> get() = categories.filter { it.items.isNotEmpty() }
}
