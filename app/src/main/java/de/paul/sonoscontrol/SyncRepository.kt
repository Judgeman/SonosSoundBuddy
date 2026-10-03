package de.paul.sonoscontrol

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Packt Profile, Speaker-Einstellungen und Passwort für ein anderes Tablet ein
 * und übernimmt umgekehrt einen Stand von dort.
 *
 * Was übernommen wird, ersetzt den Stand auf diesem Tablet. Nur, was je Tablet
 * verschieden sein soll, bleibt: welche Profile hier aktiv sind und welcher
 * Speaker und welches Profil zuletzt gewählt waren.
 */
class SyncRepository(
    context: Context,
    private val database: AppDatabase,
    private val imageStore: CustomImageStore
) {
    private val resolver = context.contentResolver
    private val speakerDao = database.speakerConfigDao()
    private val settingDao = database.appSettingDao()
    private val profileDao = database.profileDao()

    /** Name des Tablets, wie er in den Android-Einstellungen steht — so erkennt man es auf den anderen. */
    val deviceName: String =
        Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    // --- Einpacken ---------------------------------------------------------

    /** Der komplette Stand dieses Tablets; das Passwort nur mit [includePassword]. */
    suspend fun createPackage(includePassword: Boolean): SyncPackage {
        val images = ImageCollector()
        val categoriesByProfile = profileDao.getCategories().groupBy { it.profileId }
        val itemsByCategory = profileDao.getItems().groupBy { it.categoryId }
        val profiles = profileDao.getProfiles().map { profile ->
            SyncProfile(
                syncId = profile.syncId,
                name = profile.name,
                iconKey = profile.iconKey,
                categories = categoriesByProfile[profile.id].orEmpty().map { category ->
                    SyncCategory(
                        name = category.name,
                        imageKey = images.exportKey(category.imageKey),
                        playOrder = category.playOrder,
                        items = itemsByCategory[category.id].orEmpty().map { item ->
                            SyncItem(
                                source = item.source,
                                sonosId = item.sonosId,
                                name = item.name,
                                description = item.description,
                                imageUrl = images.exportUrls(item.imageUrl),
                                type = item.type,
                                customImageKey = images.exportKey(item.customImageKey),
                                trackCount = item.trackCount
                            )
                        }
                    )
                }
            )
        }
        val speakers = speakerDao.getAll().sortedBy { it.name.lowercase() }.map {
            SyncSpeaker(it.playerId, it.name, it.iconKey, it.maxVolume, it.enabled)
        }
        val password = if (includePassword) readPassword() else null
        val snapshot = SyncSnapshot(
            createdAtMillis = System.currentTimeMillis(),
            deviceName = deviceName,
            profiles = profiles,
            speakers = speakers,
            password = password
        )
        return SyncPackage(snapshot, images.images)
    }

    private suspend fun readPassword(): SyncPassword? {
        val hash = settingDao.get(SettingsRepository.KEY_PASSWORD_HASH) ?: return null
        val salt = settingDao.get(SettingsRepository.KEY_PASSWORD_SALT) ?: return null
        val required = settingDao.get(SettingsRepository.KEY_PASSWORD_REQUIRED) == true.toString()
        return SyncPassword(hash, salt, required)
    }

    /** Sammelt die gespeicherten Bilder ein und ersetzt ihre Pfade durch Verweise ins Paket. */
    private inner class ImageCollector {
        val images = linkedMapOf<String, ByteArray>()
        private val refs = mutableMapOf<String, String>()

        suspend fun ref(path: String): String? {
            refs[path]?.let { return it }
            val bytes = imageStore.read(path) ?: return null
            val name = "${images.size}.jpg"
            images[name] = bytes
            return (SyncPackage.IMAGE_PREFIX + name).also { refs[path] = it }
        }

        /** Eigenes Foto → Verweis; fehlt die Datei, gilt das Bild als nicht gewählt. */
        suspend fun exportKey(key: String?): String? {
            val image = CustomImage.fromKey(key) as? CustomImage.File ?: return key
            return ref(image.path)?.let { CustomImage.File(it).key }
        }

        /** Gespeicherte Cover → Verweis, Adressen im Netz bleiben, wie sie sind. */
        suspend fun exportUrls(imageUrl: String?): String? =
            joinImageUrls(imageUrlCandidates(imageUrl).map { if (it.startsWith("/")) ref(it) else it })
    }

    // --- Dateien -----------------------------------------------------------

    suspend fun writePackage(uri: Uri, syncPackage: SyncPackage) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Die Datei konnte nicht angelegt werden.")
        output.use { syncPackage.writeTo(it) }
    }

    suspend fun readPackage(uri: Uri): SyncPackage = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Die Datei konnte nicht geöffnet werden.")
        input.use { SyncPackage.read(it) }
    }

    // --- Übernehmen --------------------------------------------------------

    /** Bereiche, die beim letzten Übernehmen gewählt waren — als Vorauswahl fürs nächste Mal. */
    suspend fun lastScopes(): Set<SyncScope> =
        SyncScope.fromKeys(settingDao.get(KEY_LAST_SCOPES)) ?: SyncScope.defaults

    /** Übernimmt die gewählten [scopes] aus [syncPackage]. */
    suspend fun apply(syncPackage: SyncPackage, scopes: Set<SyncScope>) {
        val snapshot = syncPackage.snapshot
        val applied = scopes intersect snapshot.availableScopes
        settingDao.put(AppSetting(KEY_LAST_SCOPES, SyncScope.toKeys(scopes)))
        if (applied.isEmpty()) return

        // Bilder vor der Transaktion ablegen, damit die Datenbank nicht auf Dateien wartet
        val profiles = if (SyncScope.PROFILES in applied) {
            ImageReceiver(syncPackage.images).let { images -> snapshot.profiles.orEmpty().map { images.localize(it) } }
        } else {
            null
        }

        try {
            database.withTransaction {
                profiles?.let { replaceProfiles(it) }
                snapshot.speakers?.let { applySpeakers(it, applied) }
                if (SyncScope.PASSWORD in applied) snapshot.password?.let { applyPassword(it) }
            }
        } finally {
            // Bilder ersetzter Profile – und bei einem Fehler die schon abgelegten neuen
            imageStore.deleteUnreferenced(profileDao.referencedImagePaths())
        }
    }

    private suspend fun replaceProfiles(incoming: List<SyncProfile>) {
        val match = matchProfiles(profileDao.getProfiles(), incoming)
        match.toDelete.forEach { profile ->
            profileDao.deleteItemsOfProfile(profile.id)
            profileDao.deleteCategoriesOfProfile(profile.id)
            profileDao.deleteProfileRow(profile.id)
        }
        match.pairs.forEach { (source, local) ->
            // Vorhandene Profile behalten ihre Id: daran hängen „aktiv" und „zuletzt gewählt"
            val profileId = if (local != null) {
                profileDao.updateSyncedProfile(local.id, source.name, source.iconKey, source.syncId)
                profileDao.deleteItemsOfProfile(local.id)
                profileDao.deleteCategoriesOfProfile(local.id)
                local.id
            } else {
                profileDao.insertProfile(
                    ChildProfile(name = source.name, iconKey = source.iconKey, enabled = true, syncId = source.syncId)
                )
            }
            source.categories.forEachIndexed { categoryIndex, category ->
                val categoryId = profileDao.insertCategory(
                    MusicCategory(
                        profileId = profileId,
                        name = category.name,
                        position = categoryIndex,
                        imageKey = category.imageKey,
                        playOrder = category.playOrder
                    )
                )
                category.items.forEachIndexed { itemIndex, item ->
                    profileDao.insertItem(
                        MusicItem(
                            categoryId = categoryId,
                            source = item.source,
                            sonosId = item.sonosId,
                            name = item.name,
                            description = item.description,
                            imageUrl = item.imageUrl,
                            type = item.type,
                            position = itemIndex,
                            customImageKey = item.customImageKey,
                            trackCount = item.trackCount
                        )
                    )
                }
            }
        }
    }

    /**
     * Speaker werden nur ergänzt, nie gelöscht — jedes Tablet gleicht seine Liste
     * ohnehin selbst mit Sonos ab. Speaker, die hier noch fehlen, kommen ohne
     * übernommene Freigabe gesperrt und als „Neu" markiert dazu, wie bei der Suche.
     */
    private suspend fun applySpeakers(speakers: List<SyncSpeaker>, scopes: Set<SyncScope>) {
        val settings = SyncScope.SPEAKER_SETTINGS in scopes
        val selection = SyncScope.SPEAKER_SELECTION in scopes
        if (!settings && !selection) return
        val existing = speakerDao.getAll().associateBy { it.playerId }
        var added = false
        speakers.forEach { speaker ->
            val maxVolume = speaker.maxVolume.coerceIn(0, 100)
            if (speaker.playerId in existing) {
                if (settings) speakerDao.setIconAndMaxVolume(speaker.playerId, speaker.iconKey, maxVolume)
                if (selection) speakerDao.setEnabled(speaker.playerId, speaker.enabled)
            } else {
                speakerDao.insert(
                    SpeakerConfig(
                        playerId = speaker.playerId,
                        name = speaker.name,
                        enabled = selection && speaker.enabled,
                        iconKey = if (settings) speaker.iconKey else SpeakerIcon.guessFor(speaker.name).name,
                        maxVolume = if (settings) maxVolume else 100,
                        isNew = !selection
                    )
                )
                added = true
            }
        }
        // Sonst würde der erste eigene Abgleich alle Speaker freigeben
        if (added) settingDao.put(AppSetting(SettingsRepository.KEY_SPEAKERS_SYNCED, true.toString()))
    }

    private suspend fun applyPassword(password: SyncPassword) {
        settingDao.put(AppSetting(SettingsRepository.KEY_PASSWORD_SALT, password.salt))
        settingDao.put(AppSetting(SettingsRepository.KEY_PASSWORD_HASH, password.hash))
        settingDao.put(AppSetting(SettingsRepository.KEY_PASSWORD_REQUIRED, password.required.toString()))
    }

    /** Legt die Bilder aus dem Paket ab und setzt ihre Pfade auf diesem Tablet ein. */
    private inner class ImageReceiver(private val images: Map<String, ByteArray>) {
        private val paths = mutableMapOf<String, String?>()

        private suspend fun path(ref: String): String? {
            if (ref in paths) return paths[ref]
            val bytes = images[ref.removePrefix(SyncPackage.IMAGE_PREFIX)]
            val path = bytes?.let { imageStore.storeReceived(it, "synced-${UUID.randomUUID().toString().take(8)}") }
            paths[ref] = path
            return path
        }

        private suspend fun localKey(key: String?): String? {
            val image = CustomImage.fromKey(key) as? CustomImage.File ?: return key
            // Pfade vom anderen Tablet gibt es hier nicht
            if (!image.path.startsWith(SyncPackage.IMAGE_PREFIX)) return null
            return path(image.path)?.let { CustomImage.File(it).key }
        }

        private suspend fun localUrls(imageUrl: String?): String? =
            joinImageUrls(
                imageUrlCandidates(imageUrl).map {
                    when {
                        it.startsWith(SyncPackage.IMAGE_PREFIX) -> path(it)
                        it.startsWith("/") -> null
                        else -> it
                    }
                }
            )

        suspend fun localize(profile: SyncProfile): SyncProfile = profile.copy(
            categories = profile.categories.map { category ->
                category.copy(
                    imageKey = localKey(category.imageKey),
                    items = category.items.map { item ->
                        item.copy(imageUrl = localUrls(item.imageUrl), customImageKey = localKey(item.customImageKey))
                    }
                )
            }
        )
    }

    companion object {
        private const val KEY_LAST_SCOPES = "sync_last_scopes"
    }
}
