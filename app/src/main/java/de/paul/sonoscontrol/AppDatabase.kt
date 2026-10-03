package de.paul.sonoscontrol

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

/** Kinder-Profil: eigenes Icon und eigene Musikauswahl. [enabled] = auf diesem Tablet auswählbar. */
@Entity(tableName = "child_profile")
data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val enabled: Boolean
)

val ChildProfile.icon: ProfileIcon get() = ProfileIcon.fromKey(iconKey)

/** Kategorie in der Musikauswahl eines Profils (z. B. „Hörspiele", „Zum Einschlafen"). */
@Entity(tableName = "music_category")
data class MusicCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val position: Int,
    /** [CustomImage]-Schlüssel, null = Standard-Icon. */
    val imageKey: String? = null,
    /** [PlayOrder]-Name. */
    val playOrder: String = PlayOrder.ORDERED.name
)

/**
 * Ein Eintrag aus dem Sonos-Katalog in einer Kategorie. Name und Cover werden
 * beim Hinzufügen kopiert, damit die Auswahl ohne Netz-Abfrage angezeigt werden kann.
 */
@Entity(tableName = "music_item")
data class MusicItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** [MusicSource]-Name: Sonos-Favorit oder Sonos-Playlist. */
    val source: String,
    /** Id des Favoriten bzw. der Playlist bei Sonos. */
    val sonosId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    /** [MusicType]-Name: Song, Album, Playlist, Radio, ... */
    val type: String,
    val position: Int,
    /** Selbst gewähltes Bild ([CustomImage]-Schlüssel) statt des Covers von Sonos. */
    val customImageKey: String? = null
)

val MusicItem.musicSource: MusicSource get() = MusicSource.fromKey(source)
val MusicItem.musicType: MusicType get() = MusicType.fromKey(type)

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

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM child_profile ORDER BY name COLLATE NOCASE, id")
    abstract fun observeProfiles(): Flow<List<ChildProfile>>

    @Query("SELECT * FROM music_category ORDER BY position, id")
    abstract fun observeCategories(): Flow<List<MusicCategory>>

    @Query("SELECT * FROM music_item ORDER BY position, id")
    abstract fun observeItems(): Flow<List<MusicItem>>

    @Insert
    abstract suspend fun insertProfile(profile: ChildProfile): Long

    @Query("UPDATE child_profile SET name = :name WHERE id = :id")
    abstract suspend fun setProfileName(id: Long, name: String)

    @Query("UPDATE child_profile SET iconKey = :iconKey WHERE id = :id")
    abstract suspend fun setProfileIcon(id: Long, iconKey: String)

    @Query("UPDATE child_profile SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setProfileEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_category WHERE profileId = :profileId")
    abstract suspend fun nextCategoryPosition(profileId: Long): Int

    @Insert
    abstract suspend fun insertCategory(category: MusicCategory): Long

    @Query("UPDATE music_category SET name = :name WHERE id = :id")
    abstract suspend fun setCategoryName(id: Long, name: String)

    @Query("UPDATE music_category SET imageKey = :imageKey WHERE id = :id")
    abstract suspend fun setCategoryImage(id: Long, imageKey: String?)

    @Query("UPDATE music_category SET playOrder = :playOrder WHERE id = :id")
    abstract suspend fun setCategoryPlayOrder(id: Long, playOrder: String)

    @Query("SELECT imageKey FROM music_category WHERE id = :id")
    abstract suspend fun getCategoryImage(id: Long): String?

    @Query("SELECT imageKey FROM music_category WHERE profileId = :profileId AND imageKey IS NOT NULL")
    abstract suspend fun getCategoryImagesOfProfile(profileId: Long): List<String>

    @Query("UPDATE music_item SET customImageKey = :imageKey WHERE id = :id")
    abstract suspend fun setItemCustomImage(id: Long, imageKey: String?)

    @Query("SELECT customImageKey FROM music_item WHERE id = :id")
    abstract suspend fun getItemCustomImage(id: Long): String?

    @Query("SELECT customImageKey FROM music_item WHERE categoryId = :categoryId AND customImageKey IS NOT NULL")
    abstract suspend fun getItemImagesOfCategory(categoryId: Long): List<String>

    @Query(
        "SELECT customImageKey FROM music_item WHERE customImageKey IS NOT NULL AND categoryId IN " +
            "(SELECT id FROM music_category WHERE profileId = :profileId)"
    )
    abstract suspend fun getItemImagesOfProfile(profileId: Long): List<String>

    @Query(
        "SELECT customImageKey FROM music_item WHERE categoryId = :categoryId AND source = :source " +
            "AND sonosId = :sonosId AND customImageKey IS NOT NULL"
    )
    abstract suspend fun getItemImagesFor(categoryId: Long, source: String, sonosId: String): List<String>

    @Query("UPDATE music_category SET position = :position WHERE id = :id")
    abstract suspend fun setCategoryPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_item WHERE categoryId = :categoryId")
    abstract suspend fun nextItemPosition(categoryId: Long): Int

    @Insert
    abstract suspend fun insertItem(item: MusicItem): Long

    /** Cover eines Eintrags nachtragen, z. B. wenn es erst später bekannt wird. */
    @Query(
        "UPDATE music_item SET imageUrl = :imageUrl " +
            "WHERE source = :source AND sonosId = :sonosId AND name = :name AND imageUrl IS NOT :imageUrl"
    )
    abstract suspend fun setItemImage(source: String, sonosId: String, name: String, imageUrl: String)

    @Query("UPDATE music_item SET imageUrl = :imageUrl WHERE id = :id AND imageUrl IS NULL")
    abstract suspend fun setMissingItemImage(id: Long, imageUrl: String)

    @Query("DELETE FROM music_item WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)

    @Query("DELETE FROM music_item WHERE categoryId = :categoryId AND source = :source AND sonosId = :sonosId")
    abstract suspend fun deleteItemFromCategory(categoryId: Long, source: String, sonosId: String)

    // Ohne Foreign Keys: Kategorien und Einträge werden von Hand mitgelöscht
    @Transaction
    open suspend fun deleteCategory(id: Long) {
        deleteItemsOfCategory(id)
        deleteCategoryRow(id)
    }

    @Transaction
    open suspend fun deleteProfile(id: Long) {
        deleteItemsOfProfile(id)
        deleteCategoriesOfProfile(id)
        deleteProfileRow(id)
    }

    @Query("DELETE FROM music_item WHERE categoryId = :categoryId")
    abstract suspend fun deleteItemsOfCategory(categoryId: Long)

    @Query("DELETE FROM music_category WHERE id = :id")
    abstract suspend fun deleteCategoryRow(id: Long)

    @Query("DELETE FROM music_item WHERE categoryId IN (SELECT id FROM music_category WHERE profileId = :profileId)")
    abstract suspend fun deleteItemsOfProfile(profileId: Long)

    @Query("DELETE FROM music_category WHERE profileId = :profileId")
    abstract suspend fun deleteCategoriesOfProfile(profileId: Long)

    @Query("DELETE FROM child_profile WHERE id = :id")
    abstract suspend fun deleteProfileRow(id: Long)
}

@Database(
    entities = [SpeakerConfig::class, AppSetting::class, ChildProfile::class, MusicCategory::class, MusicItem::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speakerConfigDao(): SpeakerConfigDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun profileDao(): ProfileDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `child_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `enabled` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `music_category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `music_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, `source` TEXT NOT NULL, `sonosId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `description` TEXT, `imageUrl` TEXT, `type` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
            }
        }

        // Ohne defaultValue in der Entity: Room vergleicht Defaults nur, wenn die Entity einen angibt
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_category ADD COLUMN imageKey TEXT")
                db.execSQL("ALTER TABLE music_category ADD COLUMN playOrder TEXT NOT NULL DEFAULT 'ORDERED'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_item ADD COLUMN customImageKey TEXT")
                // Cover als Kategorie-Bild gibt es nicht mehr → wieder Standard-Icon
                db.execSQL("UPDATE music_category SET imageKey = NULL WHERE imageKey LIKE 'cover:%'")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
