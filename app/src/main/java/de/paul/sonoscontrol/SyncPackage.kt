package de.paul.sonoscontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Was beim Abgleich zwischen zwei Tablets übernommen werden kann. Das sendende
 * Tablet packt alles ein, das empfangende sucht sich aus, was es übernimmt.
 */
enum class SyncScope(val label: String, val description: String) {
    PROFILES("Kinder-Profile", "Namen, Icons, Kategorien und Musik samt eigenen Bildern"),
    SPEAKER_SETTINGS("Speaker-Einstellungen", "Icons und maximale Lautstärke"),
    SPEAKER_SELECTION("Speaker-Freigabe", "Welche Speaker auf dem Homescreen auswählbar sind"),
    PASSWORD("Passwort", "Passwortschutz der Einstellungen");

    companion object {
        /** Vorauswahl: was auf jedem Tablet gleich sein soll. Freigabe und Passwort sind oft je Tablet verschieden. */
        val defaults: Set<SyncScope> = setOf(PROFILES, SPEAKER_SETTINGS)

        fun fromKeys(keys: String?): Set<SyncScope>? =
            keys?.split(',')?.mapNotNull { key -> entries.firstOrNull { it.name == key } }?.toSet()

        fun toKeys(scopes: Set<SyncScope>): String = scopes.joinToString(",") { it.name }
    }
}

/**
 * Stand eines Tablets zum Übertragen. Bereiche, die nicht mitgeschickt werden,
 * sind null. Eigene Bilder und gespeicherte Cover stehen als [SyncPackage.IMAGE_PREFIX]-Verweis
 * auf eine Datei im Paket drin statt als Pfad auf dem sendenden Tablet.
 */
@Serializable
data class SyncSnapshot(
    val formatVersion: Int = SyncPackage.FORMAT_VERSION,
    val createdAtMillis: Long,
    val deviceName: String,
    val profiles: List<SyncProfile>? = null,
    val speakers: List<SyncSpeaker>? = null,
    val password: SyncPassword? = null
) {
    /** Bereiche, die in diesem Stand enthalten sind. */
    val availableScopes: Set<SyncScope>
        get() = buildSet {
            if (profiles != null) add(SyncScope.PROFILES)
            if (speakers != null) {
                add(SyncScope.SPEAKER_SETTINGS)
                add(SyncScope.SPEAKER_SELECTION)
            }
            if (password != null) add(SyncScope.PASSWORD)
        }
}

@Serializable
data class SyncProfile(
    val syncId: String,
    val name: String,
    val iconKey: String,
    val categories: List<SyncCategory>
)

@Serializable
data class SyncCategory(
    val name: String,
    /** [CustomImage]-Schlüssel; eigene Fotos als `file:` + Bild-Verweis. */
    val imageKey: String? = null,
    val playOrder: String,
    val items: List<SyncItem>
)

@Serializable
data class SyncItem(
    val source: String,
    val sonosId: String,
    val name: String,
    val description: String? = null,
    /** Wie [MusicItem.imageUrl]: mehrere Adressen, gespeicherte Cover als Bild-Verweis. */
    val imageUrl: String? = null,
    val type: String,
    val customImageKey: String? = null,
    val trackCount: Int? = null
)

@Serializable
data class SyncSpeaker(
    val playerId: String,
    val name: String,
    val iconKey: String,
    val maxVolume: Int,
    val enabled: Boolean
)

@Serializable
data class SyncPassword(
    val hash: String,
    val salt: String,
    val required: Boolean
)

/** Ein Stand samt Bildern, wie er als ZIP-Datei oder übers WLAN reist. */
class SyncPackage(val snapshot: SyncSnapshot, val images: Map<String, ByteArray>) {

    fun writeTo(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(SNAPSHOT_ENTRY))
            zip.write(json.encodeToString(SyncSnapshot.serializer(), snapshot).toByteArray())
            zip.closeEntry()
            images.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(IMAGE_DIRECTORY + name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun toByteArray(): ByteArray = ByteArrayOutputStream().also(::writeTo).toByteArray()

    companion object {
        const val FORMAT_VERSION = 1
        /** Kennzeichnet in Bild-Schlüsseln und Cover-Adressen ein Bild aus dem Paket. */
        const val IMAGE_PREFIX = "sync-image:"
        private const val SNAPSHOT_ENTRY = "soundbuddy.json"
        private const val IMAGE_DIRECTORY = "images/"
        private const val MAX_ENTRIES = 5_000
        private const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

        private val json = Json { ignoreUnknownKeys = true }

        /** Liest ein Paket. Wirft [IOException] mit einer verständlichen Meldung, wenn es keins ist. */
        fun read(input: InputStream): SyncPackage {
            var snapshot: SyncSnapshot? = null
            val images = mutableMapOf<String, ByteArray>()
            var entries = 0
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) throw IOException("Die Datei enthält zu viele Einträge.")
                    if (entry.isDirectory) continue
                    val bytes = readLimited(zip)
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) throw IOException("Die Datei ist zu groß.")
                    when {
                        entry.name == SNAPSHOT_ENTRY -> snapshot = try {
                            json.decodeFromString(SyncSnapshot.serializer(), bytes.decodeToString())
                        } catch (e: Exception) {
                            throw IOException("Die Daten in der Datei sind beschädigt.", e)
                        }
                        // Nur der Name zählt, Pfade aus dem Paket landen nie im Dateisystem
                        entry.name.startsWith(IMAGE_DIRECTORY) -> images[entry.name.removePrefix(IMAGE_DIRECTORY)] = bytes
                    }
                }
            }
            val result = snapshot ?: throw IOException("Das ist keine SoundBuddy-Datei.")
            if (result.formatVersion > FORMAT_VERSION) {
                throw IOException("Die Daten stammen von einer neueren SoundBuddy-Version. Bitte zuerst die App aktualisieren.")
            }
            return SyncPackage(result, images)
        }

        fun fromByteArray(bytes: ByteArray): SyncPackage = read(bytes.inputStream())

        private fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var size = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                size += read
                if (size > MAX_ENTRY_BYTES) throw IOException("Die Datei enthält ein zu großes Bild.")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}

/**
 * Welches lokale Profil zu welchem Profil aus dem Paket gehört. Erst über die [ChildProfile.syncId],
 * sonst über den Namen — so finden auch Profile zusammen, die vor dem ersten Abgleich
 * auf beiden Tablets von Hand angelegt wurden. Profile ohne Gegenstück werden gelöscht.
 */
data class ProfileMatch(
    val pairs: List<Pair<SyncProfile, ChildProfile?>>,
    val toDelete: List<ChildProfile>
)

fun matchProfiles(local: List<ChildProfile>, incoming: List<SyncProfile>): ProfileMatch {
    val unmatched = local.toMutableList()
    val incomingIds = incoming.map { it.syncId }.toSet()
    // Zuerst alle Treffer über die Kennung, damit ein Namens-Treffer keinem davon das Profil wegnimmt
    val bySyncId = incoming.map { profile ->
        unmatched.firstOrNull { it.syncId == profile.syncId }?.also { unmatched.remove(it) }
    }
    val pairs = incoming.mapIndexed { index, profile ->
        val match = bySyncId[index] ?: unmatched.firstOrNull {
            it.syncId !in incomingIds && it.name.trim().equals(profile.name.trim(), ignoreCase = true)
        }?.also { unmatched.remove(it) }
        profile to match
    }
    return ProfileMatch(pairs, unmatched)
}
