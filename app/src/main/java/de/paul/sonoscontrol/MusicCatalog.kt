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
    val type: MusicType,
    /** Anzahl der Titel, falls Sonos sie nennt (nur bei Sonos-Playlisten). */
    val trackCount: Int? = null,
    /** Woher das Cover stammt (Sonos-Cloud, Speaker im Heimnetz) — für die Detail-Ansicht. */
    val coverOrigin: String? = null,
    /** Rohdaten von Sonos, zur Fehlersuche in der Detail-Ansicht. */
    val rawData: String? = null
) {
    val key: String get() = "${source.name}:$sonosId"

    companion object {
        fun from(favorite: SonosFavorite) = CatalogEntry(
            source = MusicSource.FAVORITE,
            sonosId = favorite.id,
            name = favorite.name,
            description = favorite.description?.takeIf { it.isNotBlank() } ?: favorite.service?.name,
            imageUrl = favorite.coverUrl,
            type = MusicType.fromFavoriteType(favorite.resource?.type),
            coverOrigin = favorite.coverUrl?.let { "Sonos-Cloud" },
            rawData = favorite.rawJson
        )

        fun from(playlist: SonosPlaylist) = CatalogEntry(
            source = MusicSource.PLAYLIST,
            sonosId = playlist.id,
            name = playlist.name,
            description = playlist.trackCount?.let { if (it == 1) "1 Titel" else "$it Titel" },
            imageUrl = null,
            type = MusicType.PLAYLIST,
            trackCount = playlist.trackCount,
            rawData = playlist.toString()
        )
    }
}

val MusicItem.catalogKey: String get() = "$source:$sonosId"

/** Kategorie der zentralen Musikauswahl samt ihrer Musik und der Profile, die sie sehen. */
data class CategoryWithMusic(
    val category: MusicCategory,
    val items: List<MusicItem>,
    val profileIds: Set<Long> = emptySet()
)

/** Profil samt den Kategorien, die es sieht — so wie Home- und Settings-Screen es brauchen. */
data class ProfileWithMusic(
    val profile: ChildProfile,
    val categories: List<CategoryWithMusic>
) {
    val itemCount: Int get() = categories.sumOf { it.items.size }

    /** Kategorien, in denen etwas zum Abspielen steckt — nur die sehen die Kinder. */
    val playableCategories: List<CategoryWithMusic> get() = categories.filter { it.items.isNotEmpty() }
}

/** Wie die Musik einer Kategorie abgespielt wird. Der Enum-Name steht in der Datenbank. */
enum class PlayOrder(val label: String) {
    ORDERED("Der Reihe nach"),
    SHUFFLE("Zufällig"),
    CHILD_CHOICE("Kinder entscheiden");

    companion object {
        fun fromKey(key: String?): PlayOrder = entries.firstOrNull { it.name == key } ?: ORDERED
    }
}

val MusicCategory.playOrderMode: PlayOrder get() = PlayOrder.fromKey(playOrder)

/** Wie die Musik innerhalb einer Kategorie sortiert ist. Der Enum-Name steht in der Datenbank. */
enum class ItemSort(val label: String, val ascendingLabel: String, val descendingLabel: String) {
    MANUAL("Manuell", "", ""),
    ALPHABETICAL("Alphabetisch", "A–Z", "Z–A"),
    ADDED("Hinzugefügt", "Älteste zuerst", "Neueste zuerst");

    companion object {
        fun fromKey(key: String?): ItemSort = entries.firstOrNull { it.name == key } ?: MANUAL
    }
}

val MusicCategory.itemSortMode: ItemSort get() = ItemSort.fromKey(itemSort)

/** Die Musik einer Kategorie in der dort eingestellten Sortierung; [items] kommt nach Position sortiert. */
fun MusicCategory.sortItems(items: List<MusicItem>): List<MusicItem> {
    val comparator: Comparator<MusicItem> = when (itemSortMode) {
        ItemSort.MANUAL -> return items
        ItemSort.ALPHABETICAL -> compareBy<MusicItem, String>(NaturalOrder) { it.name }.thenBy { it.id }
        // Vor dem Datums-Feld hinzugefügte Einträge haben 0 und sind damit die ältesten, untereinander nach Id
        ItemSort.ADDED -> compareBy<MusicItem> { it.addedAt }.thenBy { it.id }
    }
    return items.sortedWith(if (itemSortDescending) comparator.reversed() else comparator)
}

/**
 * Sortiert Text wie ein Mensch: ohne Groß-/Kleinschreibung, Umlaute an ihrem
 * Platz im Alphabet und Zahlen nach ihrem Wert — „Folge 2" vor „Folge 10".
 */
private object NaturalOrder : Comparator<String> {
    private val collator = java.text.Collator.getInstance(java.util.Locale.GERMAN).apply {
        strength = java.text.Collator.SECONDARY
    }
    private val chunks = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val left = chunks.findAll(a.trim()).map { it.value }.toList()
        val right = chunks.findAll(b.trim()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                compareValuesBy(x, y, { it.trimStart('0').length }, { it.trimStart('0') })
            } else {
                collator.compare(x, y)
            }
            if (result != 0) return result
        }
        return left.size - right.size
    }
}

/** Reihenfolge und Zufall haben nur bei Inhalten mit mehreren Titeln eine Bedeutung. */
val MusicType.hasMultipleTracks: Boolean
    get() = this == MusicType.PLAYLIST || this == MusicType.ALBUM || this == MusicType.OTHER

/**
 * Wie [MusicType.hasMultipleTracks], aber eine Playlist mit nur einem Titel
 * (z. B. genau das eine Lieblingslied) zählt nicht — die läuft immer der Reihe nach.
 */
val MusicItem.hasMultipleTracks: Boolean
    get() = musicType.hasMultipleTracks && trackCount != 1

/**
 * Selbst gewähltes Bild einer Kategorie oder eines Musik-Eintrags: eine bunte
 * Szene, ein Icon, ein Tier oder ein eigenes Foto. In der Datenbank als Text
 * gespeichert, z. B. `scene:POP_UP_BOOK`, `icon:ROCKET`, `animal:FOX` oder
 * `file:/data/…/bild.jpg`.
 * [Default] = nichts gewählt: Kategorien zeigen dann die Tanzparty,
 * Einträge ihr Cover von Sonos.
 */
sealed interface CustomImage {
    data object Default : CustomImage
    data class Scene(val icon: CategoryIcon) : CustomImage
    data class Icon(val icon: SpeakerIcon) : CustomImage
    data class Animal(val icon: ProfileIcon) : CustomImage
    data class File(val path: String) : CustomImage

    val key: String?
        get() = when (this) {
            Default -> null
            is Scene -> "scene:${icon.name}"
            is Icon -> "icon:${icon.name}"
            is Animal -> "animal:${icon.name}"
            is File -> "file:$path"
        }

    companion object {
        fun fromKey(key: String?): CustomImage {
            val kind = key?.substringBefore(':', missingDelimiterValue = "") ?: return Default
            val value = key.substringAfter(':')
            return when (kind) {
                "scene" -> CategoryIcon.entries.firstOrNull { it.name == value }?.let(::Scene) ?: Default
                "icon" -> SpeakerIcon.entries.firstOrNull { it.name == value }?.let(::Icon) ?: Default
                "animal" -> ProfileIcon.entries.firstOrNull { it.name == value }?.let(::Animal) ?: Default
                "file" -> File(value)
                // Früher gab es hier auch Cover (`cover:…`) — die gelten jetzt als nicht gewählt
                else -> Default
            }
        }
    }
}

val MusicCategory.image: CustomImage get() = CustomImage.fromKey(imageKey)

val MusicItem.customImage: CustomImage get() = CustomImage.fromKey(customImageKey)
