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

    /**
     * Der komplette Stand dieses Tablets; das Passwort nur mit [includePassword].
     * [householdId]: der Sonos-Haushalt, an dem das Tablet angemeldet ist.
     */
    suspend fun createPackage(includePassword: Boolean, householdId: String?): SyncPackage {
        val images = ImageCollector()
        val localProfiles = profileDao.getProfiles()
        val syncIdOf = localProfiles.associate { it.id to it.syncId }
        val profilesByCategory = profileDao.getAssignments().groupBy({ it.categoryId }, { it.profileId })
        val itemsByCategory = profileDao.getItems().groupBy { it.categoryId }
        val profiles = localProfiles.map { SyncProfile(syncId = it.syncId, name = it.name, iconKey = it.iconKey) }
        val categories = profileDao.getCategories().map { category ->
            SyncCategory(
                name = category.name,
                imageKey = images.exportKey(category.imageKey),
                playOrder = category.playOrder,
                itemSort = category.itemSort,
                itemSortDescending = category.itemSortDescending,
                coverAnimation = category.coverAnimation,
                items = itemsByCategory[category.id].orEmpty().map { item ->
                    SyncItem(
                        source = item.source,
                        sonosId = item.sonosId,
                        name = item.name,
                        description = item.description,
                        imageUrl = images.exportUrls(item.imageUrl),
                        type = item.type,
                        customImageKey = images.exportKey(item.customImageKey),
                        trackCount = item.trackCount,
                        addedAt = item.addedAt
                    )
                },
                profileSyncIds = profilesByCategory[category.id].orEmpty().mapNotNull { syncIdOf[it] }.sorted()
            )
        }
        val speakers = speakerDao.getAll().sortedBy { it.name.lowercase() }.map {
            SyncSpeaker(it.playerId, it.name, it.iconKey, it.maxVolume, it.enabled)
        }
        val password = if (includePassword) readPassword() else null
        val snapshot = SyncSnapshot(
            createdAtMillis = System.currentTimeMillis(),
            deviceName = deviceName,
            householdId = householdId,
            profiles = profiles,
            categories = categories,
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
            val name = sha256Hex(bytes) + ".jpg"
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

    /**
     * Übernimmt die gewählten [scopes] aus [syncPackage]. Mit [rememberScopes] werden
     * sie die Vorauswahl fürs nächste Mal (nicht beim automatischen Übernehmen).
     */
    suspend fun apply(syncPackage: SyncPackage, scopes: Set<SyncScope>, rememberScopes: Boolean = true) {
        val snapshot = syncPackage.snapshot
        val applied = scopes intersect snapshot.availableScopes
        if (rememberScopes) settingDao.put(AppSetting(KEY_LAST_SCOPES, SyncScope.toKeys(scopes)))
        if (applied.isEmpty()) return

        // Bilder vor der Transaktion ablegen, damit die Datenbank nicht auf Dateien wartet
        val categories = if (SyncScope.PROFILES in applied) {
            ImageReceiver(syncPackage.images).let { images -> snapshot.categories.orEmpty().map { images.localize(it) } }
        } else {
            null
        }

        try {
            database.withTransaction {
                categories?.let { replaceLibrary(snapshot.profiles.orEmpty(), it) }
                snapshot.speakers?.let { applySpeakers(it, applied) }
                if (SyncScope.PASSWORD in applied) snapshot.password?.let { applyPassword(it) }
            }
        } finally {
            // Bilder der ersetzten Musikauswahl – und bei einem Fehler die schon abgelegten neuen
            imageStore.deleteUnreferenced(profileDao.referencedImagePaths())
        }
    }

    /**
     * Ersetzt Profile und die zentrale Musikauswahl. Profile werden zugeordnet und behalten
     * ihre Id (daran hängen „aktiv" und „zuletzt gewählt"); die Kategorien samt Musik und
     * Zuordnungen werden komplett neu angelegt.
     */
    private suspend fun replaceLibrary(incoming: List<SyncProfile>, categories: List<SyncCategory>) {
        val match = matchProfiles(profileDao.getProfiles(), incoming)
        match.toDelete.forEach { profileDao.deleteProfileRow(it.id) }
        val profileIds = match.pairs.associate { (source, local) ->
            val id = if (local != null) {
                profileDao.updateSyncedProfile(local.id, source.name, source.iconKey, source.syncId)
                local.id
            } else {
                profileDao.insertProfile(
                    ChildProfile(name = source.name, iconKey = source.iconKey, enabled = true, syncId = source.syncId)
                )
            }
            source.syncId to id
        }

        profileDao.deleteAllAssignments()
        profileDao.deleteAllItems()
        profileDao.deleteAllCategories()
        categories.forEachIndexed { categoryIndex, category ->
            val categoryId = profileDao.insertCategory(
                MusicCategory(
                    name = category.name,
                    position = categoryIndex,
                    imageKey = category.imageKey,
                    playOrder = category.playOrder,
                    itemSort = category.itemSort,
                    itemSortDescending = category.itemSortDescending,
                    coverAnimation = category.coverAnimation
                        ?: CoverAnimation.suggestedFor(category.name, category.imageKey).name
                )
            )
            profileDao.insertAssignments(
                category.profileSyncIds.mapNotNull { profileIds[it] }.map { ProfileCategory(it, categoryId) }
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
                        trackCount = item.trackCount,
                        addedAt = item.addedAt
                    )
                )
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
            val name = ref.removePrefix(SyncPackage.IMAGE_PREFIX)
            // Schon einmal übernommen (Abgleich über die Cloud) → die Datei weiterverwenden
            val hash = imageHash(name)
            val path = hash?.let { imageStore.findReceived(it) }
                ?: images[name]?.let { imageStore.storeReceived(it, hash ?: UUID.randomUUID().toString().take(8)) }
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

        suspend fun localize(category: SyncCategory): SyncCategory = category.copy(
            imageKey = localKey(category.imageKey),
            items = category.items.map { item ->
                item.copy(imageUrl = localUrls(item.imageUrl), customImageKey = localKey(item.customImageKey))
            }
        )
    }

    /** Liegt das Bild [name] aus einem Paket schon auf diesem Tablet? Dann muss es nicht geladen werden. */
    suspend fun hasImage(name: String): Boolean = imageHash(name)?.let { imageStore.findReceived(it) } != null

    /** Prüfsumme des Stands ohne Zeitpunkt — gleich, solange sich nichts geändert hat. */
    fun fingerprint(syncPackage: SyncPackage): String {
        val json = syncJson.encodeToString(SyncSnapshot.serializer(), syncPackage.snapshot.copy(createdAtMillis = 0))
        return sha256Hex(json.toByteArray())
    }

    // --- Einstellungen für den Abgleich über die Cloud ---------------------

    suspend fun cloudSettings(): CloudSettings = CloudSettings(
        role = CloudRole.fromKey(settingDao.get(KEY_CLOUD_ROLE)),
        autoApply = settingDao.get(KEY_CLOUD_AUTO_APPLY) != false.toString(),
        scopes = SyncScope.fromKeys(settingDao.get(KEY_CLOUD_SCOPES)) ?: SyncScope.defaults
    )

    suspend fun saveCloudSettings(settings: CloudSettings) {
        settingDao.put(AppSetting(KEY_CLOUD_ROLE, settings.role.name))
        settingDao.put(AppSetting(KEY_CLOUD_AUTO_APPLY, settings.autoApply.toString()))
        settingDao.put(AppSetting(KEY_CLOUD_SCOPES, SyncScope.toKeys(settings.scopes)))
    }

    /** Version aus der Cloud, die dieses Tablet schon übernommen oder abgelehnt hat — je Haushalt. */
    suspend fun seenCloudVersion(household: String): String? = settingDao.get(KEY_CLOUD_SEEN_PREFIX + household)

    suspend fun setSeenCloudVersion(household: String, version: String) =
        settingDao.put(AppSetting(KEY_CLOUD_SEEN_PREFIX + household, version))

    /** Prüfsumme des zuletzt hochgeladenen Stands — je Haushalt. */
    suspend fun uploadedFingerprint(household: String): String? = settingDao.get(KEY_CLOUD_UPLOADED_PREFIX + household)

    suspend fun setUploadedFingerprint(household: String, fingerprint: String?) {
        if (fingerprint == null) {
            settingDao.delete(listOf(KEY_CLOUD_UPLOADED_PREFIX + household))
        } else {
            settingDao.put(AppSetting(KEY_CLOUD_UPLOADED_PREFIX + household, fingerprint))
        }
    }

    companion object {
        private const val KEY_LAST_SCOPES = "sync_last_scopes"
        private const val KEY_CLOUD_ROLE = "cloud_sync_role"
        private const val KEY_CLOUD_AUTO_APPLY = "cloud_sync_auto_apply"
        private const val KEY_CLOUD_SCOPES = "cloud_sync_scopes"
        private const val KEY_CLOUD_SEEN_PREFIX = "cloud_sync_seen:"
        private const val KEY_CLOUD_UPLOADED_PREFIX = "cloud_sync_uploaded:"

        private val HASH_NAME = Regex("^([0-9a-f]{64})\\.jpg$")

        /** SHA-256 aus einem Bildnamen wie `<hash>.jpg`, sonst null. */
        fun imageHash(name: String): String? = HASH_NAME.find(name)?.groupValues?.get(1)
    }
}
