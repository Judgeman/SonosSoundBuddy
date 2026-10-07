package de.paul.sonoscontrol

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Ein Stand, der zum Übernehmen bereitliegt — aus einer Datei, von einem anderen Tablet oder aus der Cloud. */
data class PendingImport(
    val syncPackage: SyncPackage,
    /** Woher er kommt, z. B. der Name des anderen Tablets. */
    val origin: String,
    val preselected: Set<SyncScope>,
    /** Stand aus der Cloud: wird nach dem Übernehmen als gesehen gemerkt. */
    val cloud: CloudDownload? = null
)

/** Dieses Tablet gibt seine Daten im WLAN frei. */
data class ShareState(
    val code: String,
    /** Die Daten sind gepackt, der Code kann eingegeben werden. */
    val ready: Boolean = false,
    /** Was bisher passiert ist, neuestes zuletzt. */
    val events: List<String> = emptyList()
)

class SyncViewModel(
    private val repository: SyncRepository,
    private val transfer: LocalTransfer,
    private val cloudSync: CloudSync
) : ViewModel() {

    val deviceName: String get() = repository.deviceName

    /** Text für den Ladekreis, solange etwas läuft, das nicht unterbrochen werden soll. */
    var busy: String? by mutableStateOf(null)
        private set

    /** Ergebnis oder Fehler, als Snackbar angezeigt. */
    var message: String? by mutableStateOf(null)
        private set

    var pendingImport: PendingImport? by mutableStateOf(null)
        private set

    /** null = dieses Tablet gibt gerade nicht frei. */
    var share: ShareState? by mutableStateOf(null)
        private set

    /** Gefundene Tablets; null = es wird gerade nicht gesucht. */
    var nearby: List<NearbyTablet>? by mutableStateOf(null)
        private set

    /** Tablet, für das gerade der Code eingegeben wird. */
    var codePromptFor: NearbyTablet? by mutableStateOf(null)
        private set

    var codeWrong: Boolean by mutableStateOf(false)
        private set

    var cloudSettings: CloudSettings by mutableStateOf(CloudSettings())
        private set

    /** Letztes Ergebnis des Abgleichs über die Cloud, z. B. „Hochgeladen um 18:20“. */
    var cloudStatus: String? by mutableStateOf(null)
        private set

    /** Gerade wird hochgeladen, nachgesehen oder übernommen. */
    var cloudBusy: Boolean by mutableStateOf(false)
        private set

    /** Neuer Stand in der Cloud, der noch übernommen werden kann (beim Nachfragen oder während die Einstellungen offen sind). */
    var cloudUpdate: CloudUpdate? by mutableStateOf(null)
        private set

    private var shareJob: Job? = null
    private var searchJob: Job? = null
    private var receiveJob: Job? = null
    private var cloudLoopJob: Job? = null
    private val cloudMutex = Mutex()
    private var settingsOpen = false
    private var isForeground = false

    init {
        viewModelScope.launch { cloudSettings = repository.cloudSettings() }
    }

    fun consumeMessage() {
        message = null
    }

    // --- Datei -------------------------------------------------------------

    /** Vorschlag für den Dateinamen beim Exportieren. */
    fun exportFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        val device = deviceName.replace(Regex("[^A-Za-z0-9äöüÄÖÜß_-]+"), "-").trim('-').ifEmpty { "Tablet" }
        return "SoundBuddy-$device-$date.zip"
    }

    fun exportToFile(uri: Uri, includePassword: Boolean) = runBusy("Daten werden exportiert …") {
        repository.writePackage(uri, repository.createPackage(includePassword, householdOrNull()))
        message = "Export gespeichert. Auf dem anderen Tablet unter „Als Datei“ auf „Importieren“ tippen."
    }

    fun importFromFile(uri: Uri) = runBusy("Datei wird gelesen …") {
        val syncPackage = repository.readPackage(uri)
        offerImport(syncPackage, origin = syncPackage.snapshot.deviceName)
    }

    // --- Übernehmen --------------------------------------------------------

    private suspend fun offerImport(syncPackage: SyncPackage, origin: String) {
        // Speaker und Musik eines anderen Haushalts gibt es hier nicht — solche Stände nie übernehmen
        val source = syncPackage.snapshot.householdId
        val own = householdOrNull()
        if (source != null && own != null && source != own) {
            message = "Die Daten von „$origin“ gehören zu einem anderen Sonos-Haushalt und werden nicht übernommen."
            return
        }
        val available = syncPackage.snapshot.availableScopes
        pendingImport = PendingImport(syncPackage, origin, repository.lastScopes() intersect available)
    }

    /** Haushalt, an dem dieses Tablet angemeldet ist — null ohne Anmeldung oder Netz. */
    private suspend fun householdOrNull(): String? = try {
        cloudSync.household()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun applyImport(scopes: Set<SyncScope>) {
        val pending = pendingImport ?: return
        pendingImport = null
        runBusy("Daten werden übernommen …") {
            val cloud = pending.cloud
            if (cloud != null) {
                cloudMutex.withLock {
                    repository.apply(pending.syncPackage, scopes)
                    cloudSync.markSeen(cloud.household, cloud.version)
                }
                cloudUpdate = null
                cloudStatus = "Stand vom ${formatTime(pending.syncPackage.snapshot.createdAtMillis)} übernommen"
            } else {
                repository.apply(pending.syncPackage, scopes)
            }
            message = "Übernommen: " + SyncScope.entries.filter { it in scopes }.joinToString { it.label }
        }
    }

    fun dismissImport() {
        pendingImport = null
    }

    // --- WLAN: freigeben ---------------------------------------------------

    fun startSharing() {
        if (share != null) return
        stopSearching()
        val code = Random.nextInt(0, 10_000).toString().padStart(4, '0')
        share = ShareState(code)
        shareJob = viewModelScope.launch {
            val job = coroutineContext.job
            try {
                // Das Passwort geht nur an Tablets mit dem richtigen Code; dort wählt man, ob es übernommen wird
                val payload = repository.createPackage(includePassword = true, householdOrNull()).let { syncPackage ->
                    withContext(Dispatchers.Default) { syncPackage.toByteArray() }
                }
                share = share?.copy(ready = true)
                transfer.share(deviceName, code, payload) { event ->
                    val text = when (event) {
                        is ShareEvent.Sent -> "An „${event.receiverName}“ übertragen"
                        is ShareEvent.WrongCode ->
                            "„${event.receiverName}“ hat einen falschen Code eingegeben (noch ${event.attemptsLeft} Versuche)"
                    }
                    // Kommt aus dem IO-Thread → auf dem Main-Thread eintragen
                    viewModelScope.launch {
                        if (shareJob === job) share = share?.let { it.copy(events = it.events + text) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Freigabe beendet", e)
                message = e.message ?: "Die Freigabe wurde beendet."
            } finally {
                // Nach „Beenden“ kann schon eine neue Freigabe laufen — die bleibt
                if (shareJob === job) {
                    shareJob = null
                    share = null
                }
            }
        }
    }

    fun stopSharing() {
        shareJob?.cancel()
        shareJob = null
        share = null
    }

    // --- WLAN: holen -------------------------------------------------------

    fun startSearching() {
        if (nearby != null) return
        stopSharing()
        nearby = emptyList()
        searchJob = viewModelScope.launch {
            try {
                transfer.discover().collect { nearby = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Die Suche im WLAN ist fehlgeschlagen."
                nearby = null
            }
        }
    }

    fun stopSearching() {
        searchJob?.cancel()
        searchJob = null
        nearby = null
    }

    fun selectTablet(tablet: NearbyTablet) {
        codeWrong = false
        codePromptFor = tablet
    }

    fun dismissCodePrompt() {
        codePromptFor = null
        codeWrong = false
    }

    fun submitCode(code: String) {
        val tablet = codePromptFor ?: return
        if (receiveJob?.isActive == true) return
        receiveJob = viewModelScope.launch {
            busy = "Daten werden von „${tablet.name}“ geholt …"
            try {
                val payload = transfer.receive(tablet, code.trim(), deviceName)
                val syncPackage = withContext(Dispatchers.Default) { SyncPackage.fromByteArray(payload) }
                codePromptFor = null
                codeWrong = false
                stopSearching()
                offerImport(syncPackage, origin = tablet.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalTransfer.WrongCodeException) {
                codeWrong = true
            } catch (e: Exception) {
                Log.w(TAG, "Empfang fehlgeschlagen", e)
                codePromptFor = null
                message = e.message ?: "Die Daten konnten nicht geholt werden."
            } finally {
                busy = null
            }
        }
    }

    // --- Cloud --------------------------------------------------------------

    fun updateCloudSettings(settings: CloudSettings) {
        val previous = cloudSettings
        cloudSettings = settings
        viewModelScope.launch {
            repository.saveCloudSettings(settings)
            if (settings.role != previous.role) {
                cloudUpdate = null
                cloudStatus = null
                if (isForeground) startCloudLoop(firstIsUserAction = true)
            }
        }
    }

    /** „Jetzt hochladen“ bzw. „Jetzt nachsehen“. */
    fun syncNow() {
        viewModelScope.launch { runCloudCycle(userAction = true) }
    }

    /** Neuen Stand aus der Cloud ansehen und auswählen, was übernommen wird. */
    fun reviewCloudUpdate() {
        val update = cloudUpdate ?: return
        runBusy("Stand wird aus der Cloud geholt …") {
            val download = cloudMutex.withLock { cloudSync.download(update.household) }
            val snapshot = download.syncPackage.snapshot
            pendingImport = PendingImport(
                syncPackage = download.syncPackage,
                origin = snapshot.deviceName,
                preselected = cloudSettings.scopes intersect snapshot.availableScopes,
                cloud = download
            )
        }
    }

    /** Neuen Stand nicht übernehmen; erst ein neuerer meldet sich wieder. */
    fun ignoreCloudUpdate() {
        val update = cloudUpdate ?: return
        cloudUpdate = null
        viewModelScope.launch { cloudSync.markSeen(update.household, update.state.version) }
    }

    fun deleteFromCloud() = runBusy("Daten werden aus der Cloud gelöscht …") {
        cloudMutex.withLock { cloudSync.deleteFromCloud() }
        cloudStatus = "Die Daten dieses Sonos-Haushalts wurden aus der Cloud gelöscht."
        message = cloudStatus
    }

    /** Solange die App sichtbar ist, regelmäßig abgleichen. */
    fun onForegroundChanged(foreground: Boolean) {
        isForeground = foreground
        cloudLoopJob?.cancel()
        cloudLoopJob = null
        if (!foreground) return
        // Schon das Laden gehört zum Job: Geht das Display gleich wieder aus, startet kein Abgleich mehr
        cloudLoopJob = viewModelScope.launch {
            // Erst die gespeicherte Rolle laden, sonst läuft der erste Abgleich mit der Vorgabe „Aus“
            cloudSettings = repository.cloudSettings()
            runCloudLoop(firstIsUserAction = false)
        }
    }

    private fun startCloudLoop(firstIsUserAction: Boolean) {
        cloudLoopJob?.cancel()
        cloudLoopJob = viewModelScope.launch { runCloudLoop(firstIsUserAction) }
    }

    private suspend fun runCloudLoop(firstIsUserAction: Boolean) {
        var userAction = firstIsUserAction
        while (true) {
            runCloudCycle(userAction)
            userAction = false
            // Das Haupt-Tablet lädt nur nach Änderungen hoch, also beim Schließen der Einstellungen
            if (cloudSettings.role != CloudRole.FOLLOWER) break
            delay(CLOUD_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Die Einstellungen wurden geöffnet oder geschlossen. Solange sie offen sind,
     * wird nichts automatisch übernommen; beim Schließen lädt das Haupt-Tablet hoch.
     */
    fun onSettingsOpenChanged(open: Boolean) {
        if (open == settingsOpen) return
        settingsOpen = open
        if (!open && cloudSettings.role != CloudRole.OFF) {
            viewModelScope.launch { runCloudCycle(userAction = false) }
        }
    }

    /** Hinweis für die Settings-Hauptseite, wenn ein neuer Stand wartet. */
    val cloudNotice: String?
        get() = cloudUpdate?.let { "Neuer Stand von „${it.state.deviceName}“ vom ${formatTime(it.state.updatedAt)}" }

    private suspend fun runCloudCycle(userAction: Boolean) {
        val role = cloudSettings.role
        if (role == CloudRole.OFF) return
        if (!cloudMutex.tryLock()) return
        cloudBusy = true
        try {
            when (role) {
                CloudRole.SOURCE -> {
                    val state = cloudSync.upload()
                    cloudStatus = when {
                        state != null -> "Hochgeladen am ${formatTime(state.updatedAt)}"
                        userAction -> "Keine Änderungen seit dem letzten Hochladen"
                        else -> cloudStatus
                    }
                }
                CloudRole.FOLLOWER -> {
                    val update = cloudSync.check()
                    cloudUpdate = update
                    when {
                        update == null -> if (userAction) cloudStatus = "Kein neuer Stand in der Cloud"
                        cloudSettings.autoApply && !settingsOpen -> applyAutomatically(update)
                        else -> Unit
                    }
                }
                CloudRole.OFF -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            cloudStatus = "Für den Abgleich bei Sonos anmelden."
        } catch (e: Exception) {
            Log.w(TAG, "Abgleich über die Cloud fehlgeschlagen", e)
            cloudStatus = e.message ?: "Der Abgleich über die Cloud ist fehlgeschlagen."
            if (userAction) message = cloudStatus
        } finally {
            cloudBusy = false
            cloudMutex.unlock()
        }
    }

    private suspend fun applyAutomatically(update: CloudUpdate) {
        val download = cloudSync.download(update.household)
        repository.apply(download.syncPackage, cloudSettings.scopes, rememberScopes = false)
        cloudSync.markSeen(download.household, download.version)
        cloudUpdate = null
        cloudStatus = "Stand von „${update.state.deviceName}“ vom ${formatTime(update.state.updatedAt)} übernommen"
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(millis))

    /** Freigabe und Suche beenden, z. B. beim Verlassen der Seite. */
    fun stopNetwork() {
        stopSharing()
        stopSearching()
    }

    private fun runBusy(text: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = text
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, text, e)
                message = e.message ?: "Das hat nicht geklappt."
            } finally {
                busy = null
            }
        }
    }

    companion object {
        private const val TAG = "SoundBuddySync"
        private const val CLOUD_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}

class SyncViewModelFactory(
    private val repository: SyncRepository,
    private val transfer: LocalTransfer,
    private val cloudSync: CloudSync
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncViewModel(repository, transfer, cloudSync) as T
}
