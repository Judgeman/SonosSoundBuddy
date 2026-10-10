package de.paul.sonoscontrol

/** Rolle dieses Tablets beim Abgleich über die Cloud. */
enum class CloudRole(val label: String, val description: String) {
    OFF("Aus", "Dieses Tablet nimmt nicht am Abgleich über die Cloud teil."),
    SOURCE("Haupt-Tablet", "Lädt seinen Stand hoch, sobald die Einstellungen geschlossen werden."),
    FOLLOWER("Stand übernehmen", "Sieht beim Start und alle paar Minuten nach, ob es einen neuen Stand gibt.");

    companion object {
        fun fromKey(key: String?): CloudRole = entries.firstOrNull { it.name == key } ?: OFF
    }
}

data class CloudSettings(
    val role: CloudRole = CloudRole.OFF,
    /** Neue Stände ohne Nachfrage übernehmen (nur als [CloudRole.FOLLOWER]). */
    val autoApply: Boolean = true,
    /** Was übernommen wird — beim automatischen Übernehmen und als Vorauswahl beim Nachfragen. */
    val scopes: Set<SyncScope> = SyncScope.defaults
)

/** Ein neuer Stand in der Cloud, den dieses Tablet noch nicht übernommen hat. */
data class CloudUpdate(val household: String, val state: CloudState)

/** Ein heruntergeladener Stand, bereit zum Übernehmen. */
data class CloudDownload(val household: String, val version: String, val syncPackage: SyncPackage)

/**
 * Abgleich über den Speicher im Cloudflare-Worker. Alles läuft je Sonos-Haushalt:
 * Der Haushalt kommt bei jedem Vorgang frisch von Sonos, der Worker prüft ihn
 * noch einmal, und gemerkte Versionen gelten nur für ihren Haushalt. Meldet man
 * das Tablet bei einem anderen Konto an, fängt der Abgleich dort von vorn an.
 */
class CloudSync(
    private val repository: SyncRepository,
    private val client: CloudSyncClient,
    private val api: SonosApiClient
) {
    suspend fun household(): String = api.getFirstHouseholdId()

    /**
     * Lädt den Stand dieses Tablets hoch, wenn er sich seit dem letzten Mal geändert hat
     * (oder immer mit [force]). Gibt den neuen Stand in der Cloud zurück, null = unverändert.
     */
    suspend fun upload(force: Boolean = false): CloudState? {
        val household = household()
        // Das Passwort nur als Hash; ob es übernommen wird, entscheidet jedes Tablet selbst
        val syncPackage = repository.createPackage(includePassword = true, householdId = household)
        val fingerprint = repository.fingerprint(syncPackage)
        if (!force && fingerprint == repository.uploadedFingerprint(household)) return null

        val hashes = syncPackage.images.keys.mapNotNull(SyncRepository::imageHash)
        if (hashes.size > MAX_IMAGES) {
            throw CloudSyncException(
                "Zu viele Bilder für den Abgleich (${hashes.size}, höchstens $MAX_IMAGES). " +
                    "Bitte als Datei oder im WLAN übertragen."
            )
        }
        client.missingImages(household, hashes).forEach { hash ->
            val bytes = syncPackage.images["$hash.jpg"] ?: return@forEach
            client.uploadImage(household, hash, bytes)
        }
        val state = client.upload(household, repository.deviceName, syncPackage.snapshot, hashes)
        repository.setUploadedFingerprint(household, fingerprint)
        // Den eigenen Stand muss dieses Tablet nicht übernehmen, falls es später die Rolle wechselt
        repository.setSeenCloudVersion(household, state.version)
        return state
    }

    /** Neuer, noch nicht übernommener Stand in der Cloud — oder null. */
    suspend fun check(): CloudUpdate? {
        val household = household()
        val state = client.state(household) ?: return null
        if (state.version == repository.seenCloudVersion(household)) return null
        return CloudUpdate(household, state)
    }

    /** Holt den Stand samt der Bilder, die auf diesem Tablet noch fehlen. */
    suspend fun download(household: String): CloudDownload {
        val cloud = client.snapshot(household)
            ?: throw CloudSyncException("In der Cloud liegt kein Stand mehr.")
        // Der Worker trennt die Haushalte schon — doppelt hält besser
        val origin = cloud.snapshot.householdId
        if (origin != null && origin != household) {
            throw CloudSyncException("Der Stand in der Cloud gehört zu einem anderen Sonos-Haushalt.")
        }
        val images = cloud.snapshot.imageNames
            .filterNot { repository.hasImage(it) }
            .mapNotNull { name ->
                val hash = SyncRepository.imageHash(name) ?: return@mapNotNull null
                client.image(household, hash)?.let { name to it }
            }
            .toMap()
        return CloudDownload(household, cloud.version, SyncPackage(cloud.snapshot, images))
    }

    suspend fun markSeen(household: String, version: String) = repository.setSeenCloudVersion(household, version)

    /**
     * Gleicht ab, was die Kinder schon gespielt haben. Anders als den Stand lädt das jedes
     * Tablet hoch, egal in welcher Rolle, und jedes holt sich, was die anderen gespielt haben.
     * Hochgeladen wird nur, wenn dieses Tablet etwas kennt, das in der Cloud noch fehlt —
     * meist, weil hier gerade zum ersten Mal etwas gespielt wurde.
     */
    suspend fun syncPlayed() {
        val household = household()
        val cloud = client.played(household)
        val local = repository.mergePlayed(cloud)
        if (cloud.containsAll(local)) return
        // Liefert die Cloud den eigenen Upload noch nicht mit (KV braucht dafür bis zu einer Minute),
        // nicht noch einmal schreiben
        val fingerprint = sha256Hex(local.sorted().joinToString("\n").toByteArray())
        if (fingerprint == repository.uploadedPlayedFingerprint(household)) return
        client.uploadPlayed(household, repository.tabletId(), local)
        repository.setUploadedPlayedFingerprint(household, fingerprint)
    }

    /** Löscht den Stand und die gespielte Musik dieses Haushalts aus der Cloud. */
    suspend fun deleteFromCloud() {
        val household = household()
        client.delete(household)
        repository.setUploadedFingerprint(household, null)
        repository.setUploadedPlayedFingerprint(household, null)
    }

    companion object {
        /** So viele Bilder nimmt der Worker pro Stand an. */
        private const val MAX_IMAGES = 400
    }
}
