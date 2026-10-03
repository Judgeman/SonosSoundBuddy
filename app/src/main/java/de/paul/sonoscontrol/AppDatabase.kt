package de.paul.sonoscontrol

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Pro Sonos-Player: darf er auf dem Homescreen gewählt werden und welches Icon bekommt er. */
@Entity(tableName = "speaker_config")
data class SpeakerConfig(
    @PrimaryKey val playerId: String,
    val name: String,
    val enabled: Boolean,
    val iconKey: String,
    /** Obergrenze für die Lautstärke-Leiste auf dem Homescreen (0–100). */
    @ColumnInfo(defaultValue = "100") val maxVolume: Int = 100,
    /** Seit dem letzten Besuch der Settings neu gefunden — wird dort als „Neu" markiert. */
    @ColumnInfo(defaultValue = "0") val isNew: Boolean = false
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

    // Wer einen Speaker bearbeitet, hat ihn gesehen → „Neu"-Markierung fällt weg
    @Query("UPDATE speaker_config SET enabled = :enabled, isNew = 0 WHERE playerId = :playerId")
    suspend fun setEnabled(playerId: String, enabled: Boolean)

    @Query("UPDATE speaker_config SET iconKey = :iconKey, isNew = 0 WHERE playerId = :playerId")
    suspend fun setIcon(playerId: String, iconKey: String)

    @Query("UPDATE speaker_config SET maxVolume = :maxVolume, isNew = 0 WHERE playerId = :playerId")
    suspend fun setMaxVolume(playerId: String, maxVolume: Int)

    @Query("UPDATE speaker_config SET isNew = 0 WHERE isNew = 1")
    suspend fun clearNewFlags()

    @Query("DELETE FROM speaker_config WHERE playerId = :playerId")
    suspend fun delete(playerId: String)
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
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speakerConfigDao(): SpeakerConfigDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speaker_config ADD COLUMN maxVolume INTEGER NOT NULL DEFAULT 100")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speaker_config ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sound_buddy.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
