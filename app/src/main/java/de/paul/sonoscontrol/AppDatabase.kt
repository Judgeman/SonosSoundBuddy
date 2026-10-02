package de.paul.sonoscontrol

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Pro Sonos-Player: darf er auf dem Homescreen gewählt werden und welches Icon bekommt er. */
@Entity(tableName = "speaker_config")
data class SpeakerConfig(
    @PrimaryKey val playerId: String,
    val name: String,
    val enabled: Boolean,
    val iconKey: String
)

val SpeakerConfig.icon: SpeakerIcon get() = SpeakerIcon.fromKey(iconKey)

/** Einfacher Key-Value-Speicher für App-Einstellungen (Passwort, letzter Speaker, ...). */
@Entity(tableName = "app_setting")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface SpeakerConfigDao {
    @Query("SELECT * FROM speaker_config ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SpeakerConfig>>

    @Query("SELECT * FROM speaker_config")
    suspend fun getAll(): List<SpeakerConfig>

    @Upsert
    suspend fun upsertAll(configs: List<SpeakerConfig>)

    @Query("UPDATE speaker_config SET enabled = :enabled WHERE playerId = :playerId")
    suspend fun setEnabled(playerId: String, enabled: Boolean)

    @Query("UPDATE speaker_config SET iconKey = :iconKey WHERE playerId = :playerId")
    suspend fun setIcon(playerId: String, iconKey: String)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_setting")
    fun observeAll(): Flow<List<AppSetting>>

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: AppSetting)

    @Query("DELETE FROM app_setting WHERE `key` IN (:keys)")
    suspend fun delete(keys: List<String>)
}

@Database(
    entities = [SpeakerConfig::class, AppSetting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speakerConfigDao(): SpeakerConfigDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sound_buddy.db"
                ).build().also { instance = it }
            }
    }
}
