package de.paul.sonoscontrol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AppSettings(
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val passwordRequired: Boolean = false,
    val lastSelectedPlayerId: String? = null
) {
    val hasPassword: Boolean get() = passwordHash != null && passwordSalt != null

    /** Settings dürfen nur nach Passwort-Eingabe geöffnet werden. */
    val isLocked: Boolean get() = hasPassword && passwordRequired
}

class SettingsRepository(database: AppDatabase) {

    private val speakerDao = database.speakerConfigDao()
    private val settingDao = database.appSettingDao()

    val speakerConfigs: Flow<List<SpeakerConfig>> = speakerDao.observeAll()

    val settings: Flow<AppSettings> = settingDao.observeAll().map { rows ->
        val values = rows.associate { it.key to it.value }
        AppSettings(
            passwordHash = values[KEY_PASSWORD_HASH],
            passwordSalt = values[KEY_PASSWORD_SALT],
            passwordRequired = values[KEY_PASSWORD_REQUIRED] == true.toString(),
            lastSelectedPlayerId = values[KEY_LAST_SELECTED_PLAYER]
        )
    }

    /**
     * Übernimmt die aktuell im Haushalt gefundenen Player in die Datenbank.
     * Beim allerersten Abgleich werden alle Speaker freigegeben, später neu
     * hinzukommende Speaker sind erst nach Freigabe in den Settings sichtbar.
     */
    suspend fun syncPlayers(players: List<SonosPlayer>) {
        val existing = speakerDao.getAll().associateBy { it.playerId }
        val isFirstSync = existing.isEmpty()
        val configs = players.map { player ->
            existing[player.id]?.copy(name = player.name)
                ?: SpeakerConfig(
                    playerId = player.id,
                    name = player.name,
                    enabled = isFirstSync,
                    iconKey = SpeakerIcon.guessFor(player.name).name
                )
        }
        speakerDao.upsertAll(configs)
    }

    suspend fun setSpeakerEnabled(playerId: String, enabled: Boolean) =
        speakerDao.setEnabled(playerId, enabled)

    suspend fun setSpeakerIcon(playerId: String, icon: SpeakerIcon) =
        speakerDao.setIcon(playerId, icon.name)

    suspend fun setLastSelectedPlayer(playerId: String) =
        settingDao.put(AppSetting(KEY_LAST_SELECTED_PLAYER, playerId))

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
    }
}
