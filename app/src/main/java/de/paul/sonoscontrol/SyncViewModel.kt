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
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Ein Stand, der zum Übernehmen bereitliegt — aus einer Datei oder von einem anderen Tablet. */
data class PendingImport(
    val syncPackage: SyncPackage,
    /** Woher er kommt, z. B. der Name des anderen Tablets. */
    val origin: String,
    val preselected: Set<SyncScope>
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
    private val transfer: LocalTransfer
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

    private var shareJob: Job? = null
    private var searchJob: Job? = null
    private var receiveJob: Job? = null

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
        repository.writePackage(uri, repository.createPackage(includePassword))
        message = "Export gespeichert. Auf dem anderen Tablet „Datei importieren“ wählen."
    }

    fun importFromFile(uri: Uri) = runBusy("Datei wird gelesen …") {
        val syncPackage = repository.readPackage(uri)
        offerImport(syncPackage, origin = syncPackage.snapshot.deviceName)
    }

    // --- Übernehmen --------------------------------------------------------

    private suspend fun offerImport(syncPackage: SyncPackage, origin: String) {
        val available = syncPackage.snapshot.availableScopes
        pendingImport = PendingImport(syncPackage, origin, repository.lastScopes() intersect available)
    }

    fun applyImport(scopes: Set<SyncScope>) {
        val pending = pendingImport ?: return
        pendingImport = null
        runBusy("Daten werden übernommen …") {
            repository.apply(pending.syncPackage, scopes)
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
                val payload = repository.createPackage(includePassword = true).let { syncPackage ->
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
    }
}

class SyncViewModelFactory(
    private val repository: SyncRepository,
    private val transfer: LocalTransfer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncViewModel(repository, transfer) as T
}
