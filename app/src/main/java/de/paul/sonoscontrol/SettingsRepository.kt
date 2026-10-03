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
    val lastSelectedProfileId: Long? = null
) {
    val hasPassword: Boolean get() = passwordHash != null && passwordSalt != null

    /** Settings dürfen nur nach Passwort-Eingabe geöffnet werden. */
    val isLocked: Boolean get() = hasPassword && passwordRequired
}

class SettingsRepository(private val database: AppDatabase, private val imageStore: CustomImageStore) {

    private val speakerDao = database.speakerConfigDao()
    private val settingDao = database.appSettingDao()
    private val profileDao = database.profileDao()

    val speakerConfigs: Flow<List<SpeakerConfig>> = speakerDao.observeAll()

    val settings: Flow<AppSettings> = settingDao.observeAll().map { rows ->
        val values = rows.associate { it.key to it.value }
        AppSettings(
            passwordHash = values[KEY_PASSWORD_HASH],
            passwordSalt = values[KEY_PASSWORD_SALT],
            passwordRequired = values[KEY_PASSWORD_REQUIRED] == true.toString(),
            lastSelectedPlayerId = values[KEY_LAST_SELECTED_PLAYER],
            lastSelectedProfileId = values[KEY_LAST_SELECTED_PROFILE]?.toLongOrNull()
        )
    }

    /** Alle Profile mit ihren Kategorien und Einträgen, fertig sortiert. */
    val profiles: Flow<List<ProfileWithMusic>> = combine(
        profileDao.observeProfiles(),
        profileDao.observeCategories(),
        profileDao.observeItems()
    ) { profiles, categories, items ->
        val itemsByCategory = items.groupBy { it.categoryId }
        val categoriesByProfile = categories.groupBy { it.profileId }
        profiles.map { profile ->
            ProfileWithMusic(
                profile = profile,
                categories = categoriesByProfile[profile.id].orEmpty().map { category ->
                    CategoryWithMusic(category, itemsByCategory[category.id].orEmpty())
                }
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

    // --- Profile und Musikauswahl ---------------------------------------

    /** Legt ein neues, direkt aktives Profil an und gibt seine Id zurück. */
    suspend fun createProfile(name: String, icon: ProfileIcon): Long =
        profileDao.insertProfile(ChildProfile(name = name.trim(), iconKey = icon.name, enabled = true))

    suspend fun setProfileName(profileId: Long, name: String) = profileDao.setProfileName(profileId, name.trim())

    suspend fun setProfileIcon(profileId: Long, icon: ProfileIcon) = profileDao.setProfileIcon(profileId, icon.name)

    suspend fun setProfileEnabled(profileId: Long, enabled: Boolean) =
        profileDao.setProfileEnabled(profileId, enabled)

    suspend fun deleteProfile(profileId: Long) {
        val images = profileDao.getCategoryImagesOfProfile(profileId) + profileDao.getItemImagesOfProfile(profileId)
        // Ohne Foreign Keys: Kategorien und Einträge werden von Hand mitgelöscht
        database.withTransaction {
            profileDao.deleteItemsOfProfile(profileId)
            profileDao.deleteCategoriesOfProfile(profileId)
            profileDao.deleteProfileRow(profileId)
        }
        images.forEach { imageStore.delete(it) }
    }

    suspend fun createCategory(profileId: Long, name: String): Long =
        profileDao.insertCategory(
            MusicCategory(
                profileId = profileId,
                name = name.trim(),
                position = profileDao.nextCategoryPosition(profileId)
            )
        )

    suspend fun setCategoryName(categoryId: Long, name: String) = profileDao.setCategoryName(categoryId, name.trim())

    suspend fun deleteCategory(categoryId: Long) {
        val images = listOfNotNull(profileDao.getCategoryImage(categoryId)) +
            profileDao.getItemImagesOfCategory(categoryId)
        database.withTransaction {
            profileDao.deleteItemsOfCategory(categoryId)
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

    /** Schreibt die Reihenfolge der Kategorien eines Profils neu (nach Verschieben). */
    suspend fun reorderCategories(orderedIds: List<Long>) =
        orderedIds.forEachIndexed { index, id -> profileDao.setCategoryPosition(id, index) }

    suspend fun addMusic(categoryId: Long, entry: CatalogEntry) {
        val id = profileDao.insertItem(
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
        if (containerUrl != null && !hasContainerCover && item.musicType != MusicType.TRACK) {
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
        /** Im Dateinamen: das Cover stammt von der Playlist/dem Album selbst, nicht von einem Titel. */
        private const val CONTAINER_COVER_MARK = "-container"
        internal const val KEY_SPEAKERS_SYNCED = "speakers_synced"
    }
}

/** Pfade aller gespeicherten Bilder, auf die Kategorien und Einträge verweisen. */
internal suspend fun ProfileDao.referencedImagePaths(): Set<String> {
    val keys = getAllCategoryImages() + getAllItemCustomImages()
    val files = keys.mapNotNull { (CustomImage.fromKey(it) as? CustomImage.File)?.path }
    val covers = getAllItemImageUrls().flatMap(::imageUrlCandidates).filter { it.startsWith("/") }
    return (files + covers).toSet()
}
