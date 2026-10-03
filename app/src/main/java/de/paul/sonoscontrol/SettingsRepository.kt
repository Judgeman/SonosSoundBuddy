package de.paul.sonoscontrol

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

class SettingsRepository(database: AppDatabase) {

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

    suspend fun deleteProfile(profileId: Long) = profileDao.deleteProfile(profileId)

    suspend fun createCategory(profileId: Long, name: String): Long =
        profileDao.insertCategory(
            MusicCategory(
                profileId = profileId,
                name = name.trim(),
                position = profileDao.nextCategoryPosition(profileId)
            )
        )

    suspend fun setCategoryName(categoryId: Long, name: String) = profileDao.setCategoryName(categoryId, name.trim())

    suspend fun deleteCategory(categoryId: Long) = profileDao.deleteCategory(categoryId)

    /** Schreibt die Reihenfolge der Kategorien eines Profils neu (nach Verschieben). */
    suspend fun reorderCategories(orderedIds: List<Long>) =
        orderedIds.forEachIndexed { index, id -> profileDao.setCategoryPosition(id, index) }

    suspend fun addMusic(categoryId: Long, entry: CatalogEntry) {
        profileDao.insertItem(
            MusicItem(
                categoryId = categoryId,
                source = entry.source.name,
                sonosId = entry.sonosId,
                name = entry.name,
                description = entry.description,
                imageUrl = entry.imageUrl,
                type = entry.type.name,
                position = profileDao.nextItemPosition(categoryId)
            )
        )
    }

    suspend fun removeMusic(categoryId: Long, entry: CatalogEntry) =
        profileDao.deleteItemFromCategory(categoryId, entry.source.name, entry.sonosId)

    suspend fun removeMusicItem(itemId: Long) = profileDao.deleteItem(itemId)

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
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_PASSWORD_REQUIRED = "password_required"
        private const val KEY_LAST_SELECTED_PLAYER = "last_selected_player"
        private const val KEY_LAST_SELECTED_PROFILE = "last_selected_profile"
        private const val KEY_SPEAKERS_SYNCED = "speakers_synced"
    }
}
