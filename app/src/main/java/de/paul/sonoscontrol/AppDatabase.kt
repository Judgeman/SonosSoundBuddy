package de.paul.sonoscontrol

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

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

/**
 * Kinder-Profil mit eigenem Icon. Welche Kategorien der zentralen Musikauswahl es
 * sieht, steht in [ProfileCategory]. [enabled] = auf diesem Tablet auswählbar.
 */
@Entity(tableName = "child_profile")
data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val enabled: Boolean,
    /**
     * Auf allen Tablets gleiche Kennung — [id] zählt jedes Tablet selbst hoch.
     * Beim Abgleich findet ein Tablet darüber sein Profil wieder.
     */
    val syncId: String = UUID.randomUUID().toString()
)

val ChildProfile.icon: ProfileIcon get() = ProfileIcon.fromKey(iconKey)

/**
 * Kategorie der zentralen Musikauswahl (z. B. „Hörspiele", „Zum Einschlafen").
 * Sie gehört keinem Profil — über [ProfileCategory] wird festgelegt, wer sie sieht.
 */
@Entity(tableName = "music_category")
data class MusicCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Reihenfolge in der Musikauswahl, gilt für alle Profile. */
    val position: Int,
    /** [CustomImage]-Schlüssel, null = Standard-Bild. */
    val imageKey: String? = null,
    /** [PlayOrder]-Name. */
    val playOrder: String = PlayOrder.ORDERED.name,
    /** [ItemSort]-Name: wie die Musik innerhalb der Kategorie sortiert ist. */
    val itemSort: String = ItemSort.MANUAL.name,
    /** Alphabetisch bzw. nach Datum absteigend (Z–A, Neueste zuerst). Bei manueller Sortierung ohne Bedeutung. */
    val itemSortDescending: Boolean = false,
    /** [CoverAnimation]-Name: was das Tier am Cover macht, solange etwas aus der Kategorie läuft. */
    val coverAnimation: String = CoverAnimation.DANCE.name
)

/** Zuordnung: das Profil [profileId] sieht die Kategorie [categoryId]. */
@Entity(tableName = "profile_category", primaryKeys = ["profileId", "categoryId"])
data class ProfileCategory(
    val profileId: Long,
    val categoryId: Long
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
    val customImageKey: String? = null,
    /** Anzahl der Titel laut Sonos, null = unbekannt (nur Sonos-Playlisten liefern sie). */
    val trackCount: Int? = null,
    /** Zeitpunkt des Hinzufügens (Millisekunden), 0 = vor Einführung des Feldes hinzugefügt. */
    val addedAt: Long = System.currentTimeMillis()
)

val MusicItem.musicSource: MusicSource get() = MusicSource.fromKey(source)
val MusicItem.musicType: MusicType get() = MusicType.fromKey(type)

/**
 * Ob ein Kind-Profil eine Musik schon gespielt hat — auf diesem oder einem anderen Tablet.
 * Ohne Eintrag oder mit [played] = false ist sie für das Kind neu. Reist so, wie sie ist,
 * auch zu den anderen Tablets (Datei, WLAN und Cloud).
 */
@Serializable
@Entity(tableName = "played_music", primaryKeys = ["profileSyncId", "musicKey"])
data class PlayedMusic(
    /** [ChildProfile.syncId] — auf allen Tablets gleich, anders als die Id. */
    val profileSyncId: String,
    /** [MusicItem.playedKey]. */
    val musicKey: String,
    /** false = von den Eltern wieder als neu markiert. Bleibt stehen, damit die anderen Tablets davon erfahren. */
    val played: Boolean,
    /** Zeitpunkt der Änderung (Millisekunden), 0 = beim Update auf diese Version eingetragen. */
    val changedAt: Long
)

/**
 * Beim Zusammenführen zweier Tablets gewinnt je Profil und Musik die neuere Änderung, bei
 * gleichem Zeitpunkt „gespielt“. Der Worker rechnet genauso, so landen alle beim selben Stand.
 */
fun PlayedMusic.winsOver(other: PlayedMusic): Boolean =
    changedAt > other.changedAt || (changedAt == other.changedAt && played && !other.played)

val PlayedMusic.entryKey: Pair<String, String> get() = profileSyncId to musicKey

@Dao
interface SpeakerConfigDao {
    @Query("SELECT * FROM speaker_config ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SpeakerConfig>>

    @Query("SELECT * FROM speaker_config")
    suspend fun getAll(): List<SpeakerConfig>

    @Upsert
    suspend fun upsertAll(configs: List<SpeakerConfig>)

    @Insert
    suspend fun insert(config: SpeakerConfig)

    @Query("UPDATE speaker_config SET iconKey = :iconKey, maxVolume = :maxVolume WHERE playerId = :playerId")
    suspend fun setIconAndMaxVolume(playerId: String, iconKey: String, maxVolume: Int)

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
interface ProfileDao {
    @Query("SELECT * FROM child_profile ORDER BY name COLLATE NOCASE, id")
    fun observeProfiles(): Flow<List<ChildProfile>>

    @Query("SELECT * FROM music_category ORDER BY position, id")
    fun observeCategories(): Flow<List<MusicCategory>>

    @Query("SELECT * FROM music_item ORDER BY position, id")
    fun observeItems(): Flow<List<MusicItem>>

    @Query("SELECT * FROM profile_category")
    fun observeAssignments(): Flow<List<ProfileCategory>>

    @Query("SELECT * FROM profile_category")
    suspend fun getAssignments(): List<ProfileCategory>

    // Für das Übernehmen eines Stands von einem anderen Tablet: die Musikauswahl wird ersetzt
    @Query("DELETE FROM profile_category")
    suspend fun deleteAllAssignments()

    @Query("DELETE FROM music_item")
    suspend fun deleteAllItems()

    @Query("DELETE FROM music_category")
    suspend fun deleteAllCategories()

    @Query("SELECT id FROM child_profile")
    suspend fun getProfileIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignments(assignments: List<ProfileCategory>)

    @Query("DELETE FROM profile_category WHERE profileId = :profileId AND categoryId = :categoryId")
    suspend fun deleteAssignment(profileId: Long, categoryId: Long)

    @Query("DELETE FROM profile_category WHERE profileId = :profileId")
    suspend fun deleteAssignmentsOfProfile(profileId: Long)

    @Query("DELETE FROM profile_category WHERE categoryId = :categoryId")
    suspend fun deleteAssignmentsOfCategory(categoryId: Long)

    @Insert
    suspend fun insertProfile(profile: ChildProfile): Long

    // Für Export und Import auf einen Schlag, ohne Flow
    @Query("SELECT * FROM child_profile ORDER BY name COLLATE NOCASE, id")
    suspend fun getProfiles(): List<ChildProfile>

    @Query("SELECT * FROM music_category ORDER BY position, id")
    suspend fun getCategories(): List<MusicCategory>

    @Query("SELECT * FROM music_item ORDER BY position, id")
    suspend fun getItems(): List<MusicItem>

    @Query("UPDATE child_profile SET name = :name, iconKey = :iconKey, syncId = :syncId WHERE id = :id")
    suspend fun updateSyncedProfile(id: Long, name: String, iconKey: String, syncId: String)

    @Query("UPDATE child_profile SET name = :name WHERE id = :id")
    suspend fun setProfileName(id: Long, name: String)

    @Query("UPDATE child_profile SET iconKey = :iconKey WHERE id = :id")
    suspend fun setProfileIcon(id: Long, iconKey: String)

    @Query("UPDATE child_profile SET enabled = :enabled WHERE id = :id")
    suspend fun setProfileEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_category")
    suspend fun nextCategoryPosition(): Int

    @Insert
    suspend fun insertCategory(category: MusicCategory): Long

    @Query("UPDATE music_category SET name = :name WHERE id = :id")
    suspend fun setCategoryName(id: Long, name: String)

    @Query("UPDATE music_category SET imageKey = :imageKey WHERE id = :id")
    suspend fun setCategoryImage(id: Long, imageKey: String?)

    @Query("UPDATE music_category SET playOrder = :playOrder WHERE id = :id")
    suspend fun setCategoryPlayOrder(id: Long, playOrder: String)

    @Query("UPDATE music_category SET itemSort = :itemSort, itemSortDescending = :descending WHERE id = :id")
    suspend fun setCategoryItemSort(id: Long, itemSort: String, descending: Boolean)

    @Query("UPDATE music_category SET coverAnimation = :coverAnimation WHERE id = :id")
    suspend fun setCategoryCoverAnimation(id: Long, coverAnimation: String)

    @Query("SELECT imageKey FROM music_category WHERE id = :id")
    suspend fun getCategoryImage(id: Long): String?

    @Query("UPDATE music_item SET customImageKey = :imageKey WHERE id = :id")
    suspend fun setItemCustomImage(id: Long, imageKey: String?)

    @Query("SELECT customImageKey FROM music_item WHERE id = :id")
    suspend fun getItemCustomImage(id: Long): String?

    @Query("SELECT customImageKey FROM music_item WHERE categoryId = :categoryId AND customImageKey IS NOT NULL")
    suspend fun getItemImagesOfCategory(categoryId: Long): List<String>

    @Query(
        "SELECT customImageKey FROM music_item WHERE categoryId = :categoryId AND source = :source " +
            "AND sonosId = :sonosId AND customImageKey IS NOT NULL"
    )
    suspend fun getItemImagesFor(categoryId: Long, source: String, sonosId: String): List<String>

    @Query("UPDATE music_category SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    @Query("UPDATE music_item SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_item WHERE categoryId = :categoryId")
    suspend fun nextItemPosition(categoryId: Long): Int

    @Query("SELECT id FROM music_item WHERE categoryId = :categoryId ORDER BY position, id")
    suspend fun getItemIdsOfCategory(categoryId: Long): List<Long>

    @Insert
    suspend fun insertItem(item: MusicItem): Long

    /** Einträge zu einem Katalog-Eintrag, deren Cover noch nicht auf dem Tablet gespeichert ist. */
    @Query(
        "SELECT * FROM music_item WHERE source = :source AND sonosId = :sonosId AND name = :name " +
            "AND (imageUrl IS NULL OR imageUrl NOT LIKE '/%')"
    )
    suspend fun getItemsWithoutStoredCover(source: String, sonosId: String, name: String): List<MusicItem>

    @Query("SELECT * FROM music_item WHERE id = :id")
    suspend fun getItem(id: Long): MusicItem?

    @Query("UPDATE music_item SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun setItemImageUrl(id: Long, imageUrl: String?)

    @Query(
        "UPDATE music_item SET trackCount = :trackCount, description = :description " +
            "WHERE source = :source AND sonosId = :sonosId AND name = :name AND trackCount IS NOT :trackCount"
    )
    suspend fun setTrackCount(source: String, sonosId: String, name: String, trackCount: Int, description: String?)

    @Query("SELECT imageKey FROM music_category WHERE imageKey IS NOT NULL")
    suspend fun getAllCategoryImages(): List<String>

    @Query("SELECT customImageKey FROM music_item WHERE customImageKey IS NOT NULL")
    suspend fun getAllItemCustomImages(): List<String>

    @Query("SELECT imageUrl FROM music_item WHERE imageUrl IS NOT NULL")
    suspend fun getAllItemImageUrls(): List<String>

    @Query("DELETE FROM music_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM music_item WHERE categoryId = :categoryId AND source = :source AND sonosId = :sonosId")
    suspend fun deleteItemFromCategory(categoryId: Long, source: String, sonosId: String)

    // Löschen von Kategorien: im Repository in einer Transaktion zusammengefasst
    @Query("DELETE FROM music_item WHERE categoryId = :categoryId")
    suspend fun deleteItemsOfCategory(categoryId: Long)

    @Query("DELETE FROM music_category WHERE id = :id")
    suspend fun deleteCategoryRow(id: Long)

    @Query("DELETE FROM child_profile WHERE id = :id")
    suspend fun deleteProfileRow(id: Long)
}

@Dao
interface PlayedMusicDao {
    @Query("SELECT * FROM played_music WHERE played = 1")
    fun observePlayed(): Flow<List<PlayedMusic>>

    @Query("SELECT * FROM played_music")
    suspend fun getAll(): List<PlayedMusic>

    @Upsert
    suspend fun upsertAll(entries: List<PlayedMusic>)

    @Query("DELETE FROM played_music WHERE profileSyncId = :syncId")
    suspend fun deleteOfProfile(syncId: String)
}

@Database(
    entities = [
        SpeakerConfig::class, AppSetting::class, ChildProfile::class,
        MusicCategory::class, ProfileCategory::class, MusicItem::class, PlayedMusic::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speakerConfigDao(): SpeakerConfigDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun profileDao(): ProfileDao
    abstract fun playedMusicDao(): PlayedMusicDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_item ADD COLUMN trackCount INTEGER")
                // Bei Sonos-Playlisten steht die Anzahl schon in der Beschreibung („3 Titel")
                db.execSQL(
                    "UPDATE music_item SET trackCount = CAST(description AS INTEGER) " +
                        "WHERE source = 'PLAYLIST' AND description LIKE '% Titel'"
                )
            }
        }

        /**
         * Kategorien gehören keinem Profil mehr, sondern der zentralen Musikauswahl.
         * Jede bisherige Kategorie bleibt für ihr Profil sichtbar; die Reihenfolge
         * wird profilweise hintereinander durchnummeriert.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile_category` (`profileId` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `categoryId`))"
                )
                db.execSQL(
                    "INSERT INTO profile_category (profileId, categoryId) " +
                        "SELECT profileId, id FROM music_category WHERE profileId IN (SELECT id FROM child_profile)"
                )
                db.execSQL(
                    "CREATE TABLE `music_category_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `imageKey` TEXT, `playOrder` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO music_category_new (id, name, position, imageKey, playOrder) " +
                        "SELECT id, name, position, imageKey, playOrder FROM music_category"
                )
                // Fensterfunktionen gibt es im SQLite älterer Android-Versionen nicht → hier durchnummerieren
                val ids = mutableListOf<Long>()
                db.query("SELECT id FROM music_category ORDER BY profileId, position, id").use { cursor ->
                    while (cursor.moveToNext()) ids += cursor.getLong(0)
                }
                ids.forEachIndexed { index, id ->
                    db.execSQL("UPDATE music_category_new SET position = ? WHERE id = ?", arrayOf<Any>(index, id))
                }
                db.execSQL("DROP TABLE music_category")
                db.execSQL("ALTER TABLE music_category_new RENAME TO music_category")
            }
        }

        /** Kennung für den Abgleich zwischen Tablets: jedes vorhandene Profil bekommt eine eigene. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profile ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                // randomblob() wird pro Zeile neu ausgewertet → jedes Profil eine eigene Kennung
                db.execSQL("UPDATE child_profile SET syncId = lower(hex(randomblob(16)))")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_category ADD COLUMN itemSort TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE music_category ADD COLUMN itemSortDescending INTEGER NOT NULL DEFAULT 0")
                // Das Datum bisheriger Einträge ist unbekannt — bei 0 entscheidet die Id, also die Reihenfolge des Hinzufügens
                db.execSQL("ALTER TABLE music_item ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Tier am Cover pro Kategorie; Hörbücher & Co. lesen gleich vor — dieselbe Regel wie CoverAnimation.suggestedFor. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_category ADD COLUMN coverAnimation TEXT NOT NULL DEFAULT 'DANCE'")
                db.execSQL(
                    "UPDATE music_category SET coverAnimation = 'READ' WHERE imageKey = 'scene:POP_UP_BOOK' " +
                        "OR name LIKE '%hörb%' OR name LIKE '%hörsp%' OR name LIKE '%geschicht%' OR name LIKE '%märchen%'"
                )
            }
        }

        /**
         * Was ein Profil schon vor dem Update in seiner Musikauswahl hatte, hat das Kind vermutlich
         * gehört — neu ist erst, was danach dazukommt. Der Schlüssel ist derselbe wie [MusicItem.playedKey];
         * mit Zeitpunkt 0 ist jede spätere Änderung neuer, auch „wieder neu“ auf einem anderen Tablet.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `played_music` (`profileSyncId` TEXT NOT NULL, " +
                        "`musicKey` TEXT NOT NULL, `played` INTEGER NOT NULL, `changedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileSyncId`, `musicKey`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO played_music (profileSyncId, musicKey, played, changedAt) " +
                        "SELECT p.syncId, i.source || ':' || i.sonosId || ':' || i.name, 1, 0 FROM music_item i " +
                        "JOIN profile_category pc ON pc.categoryId = i.categoryId " +
                        "JOIN child_profile p ON p.id = pc.profileId"
                )
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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
