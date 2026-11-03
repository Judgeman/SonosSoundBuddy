package de.paul.sonoscontrol

import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AppSettings(
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val passwordRequired: Boolean = false,
    val lastSelectedPlayerId: String? = null,
    val lastSelectedProfileId: Long? = null,
    /** Pro Speaker (playerId) die Kategorie, aus der dort zuletzt etwas gestartet wurde — für das Tier am Cover. */
    val lastStartedCategories: Map<String, Long> = emptyMap(),
    /** „Musik hinzufügen“ setzt neue Einträge an den Anfang statt ans Ende der Kategorie. */
    val addMusicAtStart: Boolean = false
) {
    val hasPassword: Boolean get() = passwordHash != null && passwordSalt != null

    /** Settings dürfen nur nach Passwort-Eingabe geöffnet werden. */
    val isLocked: Boolean get() = hasPassword && passwordRequired
}

class SettingsRepository(private val database: AppDatabase, private val imageStore: CustomImageStore) {

    private val speakerDao = database.speakerConfigDao()
    private val settingDao = database.appSettingDao()
    private val profileDao = database.profileDao()
    private val playedDao = database.playedMusicDao()
    private val playLogDao = database.playLogDao()

    val speakerConfigs: Flow<List<SpeakerConfig>> = speakerDao.observeAll()

    /** Je Profil ([ChildProfile.syncId]) die [MusicItem.playedKey] der Musik, die es schon gespielt hat. */
    val playedMusic: Flow<Map<String, Set<String>>> = playedDao.observePlayed().map { rows ->
        rows.groupBy({ it.profileSyncId }, { it.musicKey }).mapValues { (_, keys) -> keys.toSet() }
    }

    /** Was auf diesem Tablet zuletzt aus der Musikauswahl gespielt wurde, neueste zuerst. */
    val playLog: Flow<List<PlayLogEntry>> = playLogDao.observeAll()

    val settings: Flow<AppSettings> = settingDao.observeAll().map { rows ->
        val values = rows.associate { it.key to it.value }
        AppSettings(
            passwordHash = values[KEY_PASSWORD_HASH],
            passwordSalt = values[KEY_PASSWORD_SALT],
            passwordRequired = values[KEY_PASSWORD_REQUIRED] == true.toString(),
            lastSelectedPlayerId = values[KEY_LAST_SELECTED_PLAYER],
            lastSelectedProfileId = values[KEY_LAST_SELECTED_PROFILE]?.toLongOrNull(),
            lastStartedCategories = values.entries
                .filter { it.key.startsWith(KEY_LAST_CATEGORY_PREFIX) }
                .mapNotNull { (key, value) -> value.toLongOrNull()?.let { key.removePrefix(KEY_LAST_CATEGORY_PREFIX) to it } }
                .toMap(),
            addMusicAtStart = values[KEY_ADD_MUSIC_AT_START] == true.toString()
        )
    }

    /** Die zentrale Musikauswahl: alle Kategorien mit Einträgen und zugeordneten Profilen, fertig sortiert. */
    val categories: Flow<List<CategoryWithMusic>> = combine(
        profileDao.observeCategories(),
        profileDao.observeItems(),
        profileDao.observeAssignments()
    ) { categories, items, assignments ->
        val itemsByCategory = items.groupBy { it.categoryId }
        val profilesByCategory = assignments.groupBy({ it.categoryId }, { it.profileId })
        categories.map { category ->
            CategoryWithMusic(
                category = category,
                items = category.sortItems(itemsByCategory[category.id].orEmpty()),
                profileIds = profilesByCategory[category.id].orEmpty().toSet()
            )
        }
    }

    /** Alle Profile mit den Kategorien, die sie sehen, in der Reihenfolge der Musikauswahl. */
    val profiles: Flow<List<ProfileWithMusic>> = combine(profileDao.observeProfiles(), categories) { profiles, categories ->
        profiles.map { profile ->
            ProfileWithMusic(
                profile = profile,
                categories = categories.filter { profile.id in it.profileIds }
            )
        }
    }

    /**
     * Übernimmt die aktuell im Haushalt gefundenen Player in die Datenbank.
     * Beim allerersten Abgleich werden alle Speaker freigegeben, später neu
     * hinzukommende Speaker sind erst nach Freigabe in den Settings sichtbar
     * und werden dort als „Neu" markiert.
     *
     * @return Anzahl der neu hinzugekommenen Speaker
     */
    suspend fun syncPlayers(players: List<SonosPlayer>): Int {
        val existing = speakerDao.getAll().associateBy { it.playerId }
        // Nicht nur an der leeren Tabelle festmachen: Sind alle Speaker gelöscht worden,
        // sollen neu gefundene trotzdem nicht automatisch freigegeben werden.
        val isFirstSync = existing.isEmpty() && settingDao.get(KEY_SPEAKERS_SYNCED) == null
        var added = 0
        val configs = players.map { player ->
            existing[player.id]?.copy(name = player.name)
                ?: SpeakerConfig(
                    playerId = player.id,
                    name = player.name,
                    enabled = isFirstSync,
                    iconKey = SpeakerIcon.guessFor(player.name).name,
                    isNew = !isFirstSync
                ).also { added++ }
        }
        speakerDao.upsertAll(configs)
        if (configs.isNotEmpty()) settingDao.put(AppSetting(KEY_SPEAKERS_SYNCED, true.toString()))
        return added
    }

    suspend fun deleteSpeaker(playerId: String) = speakerDao.delete(playerId)

    suspend fun clearNewFlags() = speakerDao.clearNewFlags()

    suspend fun setSpeakerEnabled(playerId: String, enabled: Boolean) =
        speakerDao.setEnabled(playerId, enabled)

    suspend fun setSpeakerIcon(playerId: String, icon: SpeakerIcon) =
        speakerDao.setIcon(playerId, icon.name)

    suspend fun setSpeakerMaxVolume(playerId: String, maxVolume: Int) =
        speakerDao.setMaxVolume(playerId, maxVolume.coerceIn(0, 100))

    suspend fun setLastSelectedPlayer(playerId: String) =
        settingDao.put(AppSetting(KEY_LAST_SELECTED_PLAYER, playerId))

    suspend fun setLastSelectedProfile(profileId: Long) =
        settingDao.put(AppSetting(KEY_LAST_SELECTED_PROFILE, profileId.toString()))

    suspend fun setLastStartedCategory(playerId: String, categoryId: Long) =
        settingDao.put(AppSetting(KEY_LAST_CATEGORY_PREFIX + playerId, categoryId.toString()))

    suspend fun setAddMusicAtStart(atStart: Boolean) =
        settingDao.put(AppSetting(KEY_ADD_MUSIC_AT_START, atStart.toString()))

    /**
     * Merkt sich, dass das Profil [item] gespielt hat — für die Neu-Markierung und für „Zuletzt
     * gespielt“. true = es war für das Profil bis eben neu.
     */
    suspend fun markPlayed(profileSyncId: String, item: MusicItem): Boolean {
        val now = System.currentTimeMillis()
        playLogDao.insert(PlayLogEntry(profileSyncId = profileSyncId, musicKey = item.playedKey, playedAt = now))
        playLogDao.trim(keep = PLAY_LOG_SIZE)
        return setPlayed(listOf(profileSyncId), listOf(item), played = true, onlyChanges = true)
    }

    /**
     * Markiert [items] für die Profile als gespielt bzw. wieder als neu. Auch „wieder neu“
     * bleibt als Eintrag stehen, damit die anderen Tablets davon erfahren. true = es wurde etwas geschrieben.
     *
     * Mit [onlyChanges] wird nur geschrieben, was sich hier ändert (Abspielen). Ohne — wenn die Eltern
     * etwas umstellen — bekommt jeder Eintrag einen neuen Zeitpunkt: Sonst würde ein Abspielen auf einem
     * anderen Tablet, von dem dieses noch nichts weiß, die Entscheidung der Eltern beim Abgleich überstimmen.
     */
    suspend fun setPlayed(
        profileSyncIds: Collection<String>,
        items: Collection<MusicItem>,
        played: Boolean,
        onlyChanges: Boolean = false
    ): Boolean =
        database.withTransaction {
            val current = playedDao.getAll().associateBy { it.entryKey }
            val now = System.currentTimeMillis()
            val keys = items.map { it.playedKey }.distinct()
            val changes = profileSyncIds.distinct().flatMap { profile ->
                keys.mapNotNull { key ->
                    val entry = current[profile to key]
                    // Ohne Eintrag ist die Musik neu
                    if (onlyChanges && (entry?.played ?: false) == played) return@mapNotNull null
                    // Geht die Uhr hier nach, muss die Änderung trotzdem neuer sein als der Stand, den man gesehen hat
                    PlayedMusic(profile, key, played, maxOf(now, (entry?.changedAt ?: 0L) + 1))
                }
            }
            if (changes.isNotEmpty()) playedDao.upsertAll(changes)
            changes.isNotEmpty()
        }

    // --- Profile und Musikauswahl ---------------------------------------

    /** Legt ein neues, direkt aktives Profil an und gibt seine Id zurück. */
    suspend fun createProfile(name: String, icon: ProfileIcon): Long =
        profileDao.insertProfile(ChildProfile(name = name.trim(), iconKey = icon.name, enabled = true))

    suspend fun setProfileName(profileId: Long, name: String) = profileDao.setProfileName(profileId, name.trim())

    suspend fun setProfileIcon(profileId: Long, icon: ProfileIcon) = profileDao.setProfileIcon(profileId, icon.name)

    suspend fun setProfileEnabled(profileId: Long, enabled: Boolean) =
        profileDao.setProfileEnabled(profileId, enabled)

    /** Löscht nur das Profil — die Kategorien bleiben in der Musikauswahl für die anderen. */
    suspend fun deleteProfile(profileId: Long) =
        database.withTransaction {
            profileDao.deleteAssignmentsOfProfile(profileId)
            profileDao.deleteProfileRow(profileId)
        }

    /**
     * Legt eine Kategorie in der Musikauswahl an. Sichtbar ist sie für [profileIds],
     * bei null für alle Profile.
     */
    suspend fun createCategory(name: String, profileIds: Collection<Long>? = null): Long =
        database.withTransaction {
            val id = profileDao.insertCategory(
                MusicCategory(
                    name = name.trim(),
                    position = profileDao.nextCategoryPosition(),
                    coverAnimation = CoverAnimation.suggestedFor(name).name
                )
            )
            val visibleFor = profileIds ?: profileDao.getProfileIds()
            profileDao.insertAssignments(visibleFor.map { ProfileCategory(profileId = it, categoryId = id) })
            id
        }

    /**
     * Legt eine Kategorie an, die ihre Musik aus der Sonos-Playlist [playlist] bezieht — jedes Album
     * darin wird ein Eintrag (siehe [updatePlaylistAlbums]). Name wie die Playlist, sichtbar für alle Profile.
     */
    suspend fun createPlaylistCategory(playlist: CatalogEntry): Long =
        database.withTransaction {
            val id = profileDao.insertCategory(
                MusicCategory(
                    name = playlist.name.trim(),
                    position = profileDao.nextCategoryPosition(),
                    coverAnimation = CoverAnimation.suggestedFor(playlist.name).name,
                    playlistId = playlist.sonosId,
                    playlistName = playlist.name
                )
            )
            profileDao.insertAssignments(profileDao.getProfileIds().map { ProfileCategory(profileId = it, categoryId = id) })
            id
        }

    suspend fun getCategory(categoryId: Long): MusicCategory? = profileDao.getCategory(categoryId)

    /** Siehe [ProfileDao.restorePlaylistLinks] — beim App-Start. */
    suspend fun restorePlaylistLinks(): Int = database.withTransaction { profileDao.restorePlaylistLinks() }

    /** Merkt sich die Sonos-Playlist der Kategorie, z. B. nach dem Wechsel oder wenn Sonos die Id neu vergeben hat. */
    suspend fun setCategoryPlaylist(categoryId: Long, playlistId: String, playlistName: String) =
        profileDao.setCategoryPlaylist(categoryId, playlistId, playlistName)

    /**
     * Gleicht die Einträge einer Playlist-Kategorie mit den Alben der Playlist ab: Neue Alben kommen
     * dazu (und sind damit für die Kinder neu), verschwundene werden entfernt, die übrigen behalten
     * ihre eigenen Bilder und ihren Zeitpunkt. Die Reihenfolge ist die der Playlist — so sortiert die
     * Kategorie bei „Wie in der Playlist“. Wiedererkannt wird ein Album am Namen ([albumKey]); der Name
     * eines vorhandenen Eintrags bleibt, damit „schon gespielt“ daran hängen bleibt.
     * true = es hat sich etwas geändert.
     */
    suspend fun updatePlaylistAlbums(categoryId: Long, playlistId: String, albums: List<PlaylistAlbum>): Boolean {
        val coversToStore = mutableListOf<Pair<Long, String>>()
        val imagesToDelete = mutableListOf<String>()
        val changed = database.withTransaction {
            if (profileDao.getCategory(categoryId) == null) return@withTransaction false
            val existing = profileDao.getItemsOfCategory(categoryId)
            val wanted = albums.associateBy { albumKey(it.name) }
            // Je Album ein Eintrag; was nicht (mehr) in der Playlist steckt, kommt heraus
            val kept = mutableMapOf<String, MusicItem>()
            val removed = existing.filter { item ->
                val key = albumKey(item.name)
                val keep = item.musicSource == MusicSource.PLAYLIST_ALBUM && key in wanted && key !in kept
                if (keep) kept[key] = item
                !keep
            }
            removed.forEach { item ->
                profileDao.deleteItem(item.id)
                item.customImageKey?.let { imagesToDelete += it }
            }
            var modified = removed.isNotEmpty()
            val orderedIds = albums.map { album ->
                val imageUrl = joinImageUrls(album.imageUrls)
                val item = kept[albumKey(album.name)]
                if (item == null) {
                    modified = true
                    val id = profileDao.insertItem(
                        MusicItem(
                            categoryId = categoryId,
                            source = MusicSource.PLAYLIST_ALBUM.name,
                            sonosId = playlistId,
                            name = album.name,
                            description = album.description,
                            imageUrl = imageUrl,
                            type = MusicType.ALBUM.name,
                            position = 0,
                            trackCount = album.trackCount
                        )
                    )
                    imageUrl?.let { coversToStore += id to it }
                    id
                } else {
                    if (item.description != album.description || item.trackCount != album.trackCount) {
                        modified = true
                        profileDao.setItemDetails(item.id, album.description, album.trackCount)
                    }
                    // Cover nachtragen, solange noch keins auf dem Tablet gespeichert ist
                    val hasStoredCover = imageUrlCandidates(item.imageUrl).any { it.startsWith("/") }
                    if (!hasStoredCover && imageUrl != null) coversToStore += item.id to imageUrl
                    item.id
                }
            }
            // Nur schreiben, was sich verschoben hat — sonst lädt jede Abfrage die Auswahl neu
            val positions = existing.associate { it.id to it.position }
            orderedIds.forEachIndexed { index, id ->
                if (positions[id] != index) {
                    modified = true
                    profileDao.setItemPosition(id, index)
                }
            }
            modified
        }
        // Danach, damit die Auswahl nicht auf die Downloads wartet
        coversToStore.forEach { (id, url) ->
            if (!storeCover(id, url)) {
                val item = profileDao.getItem(id)
                if (item != null && item.imageUrl != url && imageUrlCandidates(item.imageUrl).none { it.startsWith("/") }) {
                    profileDao.setItemImageUrl(id, url)
                }
            }
        }
        imagesToDelete.forEach { imageStore.delete(it) }
        return changed
    }

    /** Legt fest, ob das Profil die Kategorie sieht. */
    suspend fun setCategoryVisible(categoryId: Long, profileId: Long, visible: Boolean) {
        if (visible) {
            profileDao.insertAssignments(listOf(ProfileCategory(profileId = profileId, categoryId = categoryId)))
        } else {
            profileDao.deleteAssignment(profileId, categoryId)
        }
    }

    suspend fun setCategoryName(categoryId: Long, name: String) = profileDao.setCategoryName(categoryId, name.trim())

    suspend fun deleteCategory(categoryId: Long) {
        val images = listOfNotNull(profileDao.getCategoryImage(categoryId)) +
            profileDao.getItemImagesOfCategory(categoryId)
        database.withTransaction {
            profileDao.deleteItemsOfCategory(categoryId)
            profileDao.deleteAssignmentsOfCategory(categoryId)
            profileDao.deleteCategoryRow(categoryId)
        }
        images.forEach { imageStore.delete(it) }
    }

    /** Setzt das Kategorie-Bild; ein vorher hochgeladenes eigenes Bild wird gelöscht. */
    suspend fun setCategoryImage(categoryId: Long, image: CustomImage) {
        val previous = profileDao.getCategoryImage(categoryId)
        profileDao.setCategoryImage(categoryId, image.key)
        if (previous != image.key) imageStore.delete(previous)
    }

    /** Kopiert ein Bild vom Tablet in die App und setzt es als Kategorie-Bild. */
    suspend fun importCategoryImage(categoryId: Long, uri: Uri) =
        setCategoryImage(categoryId, CustomImage.File(imageStore.import(uri, "category-$categoryId")))

    /** Setzt das eigene Bild eines Musik-Eintrags; ein vorher hochgeladenes wird gelöscht. */
    suspend fun setItemImage(itemId: Long, image: CustomImage) {
        val previous = profileDao.getItemCustomImage(itemId)
        profileDao.setItemCustomImage(itemId, image.key)
        if (previous != image.key) imageStore.delete(previous)
    }

    suspend fun importItemImage(itemId: Long, uri: Uri) =
        setItemImage(itemId, CustomImage.File(imageStore.import(uri, "item-$itemId")))

    suspend fun setCategoryPlayOrder(categoryId: Long, playOrder: PlayOrder) =
        profileDao.setCategoryPlayOrder(categoryId, playOrder.name)

    suspend fun setCategoryItemSort(categoryId: Long, sort: ItemSort, descending: Boolean) =
        profileDao.setCategoryItemSort(categoryId, sort.name, descending)

    suspend fun setCategoryCoverAnimation(categoryId: Long, animation: CoverAnimation) =
        profileDao.setCategoryCoverAnimation(categoryId, animation.name)

    /** Schreibt die manuelle Reihenfolge der Musik einer Kategorie neu (nach Verschieben). */
    // In einer Transaktion, damit die Liste nicht mit halb geschriebener Reihenfolge neu erscheint
    suspend fun reorderItems(orderedIds: List<Long>) = database.withTransaction {
        orderedIds.forEachIndexed { index, id -> profileDao.setItemPosition(id, index) }
    }

    /** Schreibt die Reihenfolge der Kategorien neu (nach Verschieben). */
    suspend fun reorderCategories(orderedIds: List<Long>) = database.withTransaction {
        orderedIds.forEachIndexed { index, id -> profileDao.setCategoryPosition(id, index) }
    }

    /**
     * Nimmt einen Katalog-Eintrag in die Kategorie auf — ans Ende oder, mit [insertAt],
     * an diese Stelle der manuellen Reihenfolge (0 = ganz oben).
     */
    suspend fun addMusic(categoryId: Long, entry: CatalogEntry, insertAt: Int? = null) {
        val id = database.withTransaction {
            val newId = profileDao.insertItem(
                MusicItem(
                    categoryId = categoryId,
                    source = entry.source.name,
                    sonosId = entry.sonosId,
                    name = entry.name,
                    description = entry.description,
                    imageUrl = entry.imageUrl,
                    type = entry.type.name,
                    position = profileDao.nextItemPosition(categoryId),
                    trackCount = entry.trackCount
                )
            )
            if (insertAt != null) {
                val others = profileDao.getItemIdsOfCategory(categoryId).filterNot { it == newId }
                val ordered = others.toMutableList().apply { add(insertAt.coerceIn(0, others.size), newId) }
                ordered.forEachIndexed { index, itemId -> profileDao.setItemPosition(itemId, index) }
            }
            newId
        }
        // Danach, damit das Hinzufügen nicht auf den Download wartet
        storeCover(id, entry.imageUrl)
    }

    /**
     * Speichert das erste ladbare Cover aus [imageUrl] auf dem Tablet und setzt es
     * vor die übrigen Adressen. Cover von Musikdiensten sind teils nur einen Tag
     * gültig (Apple Music) oder hängen an der IP des Speakers — gespeichert bleibt es.
     */
    private suspend fun storeCover(itemId: Long, imageUrl: String?, fileName: String = "cover-item-$itemId"): Boolean {
        val candidates = imageUrlCandidates(imageUrl).filterNot { it.startsWith("/") || isExpiredUrl(it) }
        val path = candidates.firstNotNullOfOrNull { imageStore.cacheCover(it, fileName) } ?: return false
        // Signierte Adressen laufen ab — die braucht später niemand mehr
        profileDao.setItemImageUrl(itemId, joinImageUrls(listOf(path) + candidates.filterNot(::isSignedUrl)))
        return true
    }

    suspend fun removeMusic(categoryId: Long, entry: CatalogEntry) {
        val images = profileDao.getItemImagesFor(categoryId, entry.source.name, entry.sonosId)
        profileDao.deleteItemFromCategory(categoryId, entry.source.name, entry.sonosId)
        images.forEach { imageStore.delete(it) }
    }

    suspend fun removeMusicItem(itemId: Long) {
        val image = profileDao.getItemCustomImage(itemId)
        profileDao.deleteItem(itemId)
        imageStore.delete(image)
    }

    /**
     * Übernimmt die Cover aus dem aktuellen Katalog in die gespeicherte Auswahl —
     * so bekommen auch früher ohne Cover hinzugefügte Einträge ihr Bild. Ebenso die
     * Titel-Anzahl der Sonos-Playlisten.
     */
    suspend fun refreshMusicImages(entries: List<CatalogEntry>) {
        entries.forEach { entry ->
            // Playlisten werden in der Sonos-App weiter bearbeitet
            if (entry.trackCount != null) {
                profileDao.setTrackCount(entry.source.name, entry.sonosId, entry.name, entry.trackCount, entry.description)
            }
            val url = entry.imageUrl ?: return@forEach
            profileDao.getItemsWithoutStoredCover(entry.source.name, entry.sonosId, entry.name).forEach { item ->
                if (!storeCover(item.id, url) && item.imageUrl != url) profileDao.setItemImageUrl(item.id, url)
            }
        }
    }

    /**
     * Cover aus der laufenden Wiedergabe übernehmen. Die Adressen dort sind frisch —
     * anders als die bei Sonos gespeicherten, die bei Apple Music nach einem Tag ablaufen.
     *
     * - [containerUrl] (Cover der Playlist bzw. des Albums) ist das richtige Bild für
     *   Playlisten und Alben. Es ersetzt ein vorher gespeichertes Cover, denn der
     *   Speaker liefert für Apple-Music-Playlisten nur das Cover eines Titels daraus.
     * - [trackUrl] nur, wenn der Eintrag noch gar kein gespeichertes Cover hat.
     */
    suspend fun storeCoverFromPlayback(itemId: Long, containerUrl: String?, trackUrl: String?) {
        val item = profileDao.getItem(itemId) ?: return
        val candidates = imageUrlCandidates(item.imageUrl)
        val stored = candidates.filter { it.startsWith("/") }
        val hasContainerCover = stored.any { CONTAINER_COVER_MARK in it }
        val others = candidates.filterNot { it.startsWith("/") }
        // Ein Album aus einer Playlist läuft aus der Warteschlange — deren Container ist nicht das Album,
        // das Cover des Titels dagegen schon
        val containerIsItem = item.musicType != MusicType.TRACK && item.musicSource != MusicSource.PLAYLIST_ALBUM
        if (containerUrl != null && !hasContainerCover && containerIsItem) {
            val name = "cover-item-$itemId$CONTAINER_COVER_MARK"
            if (storeCover(itemId, joinImageUrls(listOf(containerUrl) + others), name)) return
        }
        if (stored.isEmpty() && trackUrl != null) storeCover(itemId, joinImageUrls(listOf(trackUrl) + others))
    }

    /**
     * Cover eines Katalog-Eintrags vom Tablet; ist eine noch gültige, aber bald
     * ablaufende Adresse dabei ([freshSignedUrl]) und noch nichts gespeichert, wird sie gesichert.
     */
    suspend fun catalogCover(entry: CatalogEntry, freshSignedUrl: String?): String? {
        val key = "${entry.source.name}:${entry.sonosId}:${entry.name}"
        return imageStore.catalogCover(key)
            ?: freshSignedUrl?.let { imageStore.storeCatalogCover(key, it) }
    }

    /** Gespeicherte Bilder löschen, auf die kein Eintrag und keine Kategorie mehr verweist. */
    suspend fun deleteUnusedImages() = imageStore.deleteUnreferenced(profileDao.referencedImagePaths())

    /** Speichert ein neues Passwort und aktiviert dabei direkt den Passwortschutz. */
    suspend fun setPassword(password: String) {
        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
        settingDao.put(AppSetting(KEY_PASSWORD_SALT, salt))
        settingDao.put(AppSetting(KEY_PASSWORD_HASH, hash))
        settingDao.put(AppSetting(KEY_PASSWORD_REQUIRED, true.toString()))
    }

    suspend fun removePassword() =
        settingDao.delete(listOf(KEY_PASSWORD_HASH, KEY_PASSWORD_SALT, KEY_PASSWORD_REQUIRED))

    suspend fun setPasswordRequired(required: Boolean) =
        settingDao.put(AppSetting(KEY_PASSWORD_REQUIRED, required.toString()))

    suspend fun verifyPassword(password: String): Boolean {
        val salt = settingDao.get(KEY_PASSWORD_SALT) ?: return false
        val hash = settingDao.get(KEY_PASSWORD_HASH) ?: return false
        return withContext(Dispatchers.Default) { PasswordHasher.verify(password, salt, hash) }
    }

    companion object {
        internal const val KEY_PASSWORD_HASH = "password_hash"
        internal const val KEY_PASSWORD_SALT = "password_salt"
        internal const val KEY_PASSWORD_REQUIRED = "password_required"
        private const val KEY_LAST_SELECTED_PLAYER = "last_selected_player"
        private const val KEY_LAST_SELECTED_PROFILE = "last_selected_profile"
        /** Plus playerId — je Tablet, wird nicht abgeglichen. */
        private const val KEY_LAST_CATEGORY_PREFIX = "last_category_"
        private const val KEY_ADD_MUSIC_AT_START = "add_music_at_start"
        /** Im Dateinamen: das Cover stammt von der Playlist/dem Album selbst, nicht von einem Titel. */
        private const val CONTAINER_COVER_MARK = "-container"
        internal const val KEY_SPEAKERS_SYNCED = "speakers_synced"
        /** So viele Abspielvorgänge merkt sich das Tablet für „Zuletzt gespielt“. */
        private const val PLAY_LOG_SIZE = 50
    }
}

/**
 * Stellt die Verbindung von Playlist-Kategorien zu ihrer Sonos-Playlist wieder her, die beim Abgleich
 * verloren gegangen ist: Eine App-Version ohne Playlist-Kategorien übernimmt deren Alben als
 * normale Kategorie und lässt die Playlist weg — auch, wenn sie den Stand später weitergibt.
 * Erkennbar ist so eine Kategorie daran, dass alle ihre Einträge Alben aus einer Playlist sind.
 * Die Id der Playlist tragen die Einträge selbst (die des zuletzt hinzugefügten, falls Sonos sie
 * inzwischen neu vergeben hat); den Namen trägt der nächste Abgleich mit der Playlist nach.
 * Gibt zurück, wie viele Kategorien wieder verbunden wurden.
 */
internal suspend fun ProfileDao.restorePlaylistLinks(): Int {
    val itemsByCategory = getItems().groupBy { it.categoryId }
    var restored = 0
    getCategories().filter { it.playlistId == null }.forEach { category ->
        val items = itemsByCategory[category.id].orEmpty()
        if (items.isEmpty() || items.any { it.musicSource != MusicSource.PLAYLIST_ALBUM }) return@forEach
        val playlistId = items.maxWith(compareBy<MusicItem>({ it.addedAt }, { it.id })).sonosId
        setCategoryPlaylist(category.id, playlistId, null)
        restored++
    }
    return restored
}

/** Pfade aller gespeicherten Bilder, auf die Kategorien und Einträge verweisen. */
internal suspend fun ProfileDao.referencedImagePaths(): Set<String> {
    val keys = getAllCategoryImages() + getAllItemCustomImages()
    val files = keys.mapNotNull { (CustomImage.fromKey(it) as? CustomImage.File)?.path }
    val covers = getAllItemImageUrls().flatMap(::imageUrlCandidates).filter { it.startsWith("/") }
    return (files + covers).toSet()
}
