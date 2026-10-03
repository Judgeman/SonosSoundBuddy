package de.paul.sonoscontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.resume

/** Ein anderes Tablet im WLAN, das gerade seine Daten freigibt. */
class NearbyTablet(val name: String, internal val service: NsdServiceInfo)

/** Was beim Freigeben passiert ist, für die Anzeige auf dem sendenden Tablet. */
sealed interface ShareEvent {
    data class Sent(val receiverName: String) : ShareEvent
    data class WrongCode(val receiverName: String, val attemptsLeft: Int) : ShareEvent
}

/**
 * Überträgt den Stand eines Tablets direkt im WLAN auf ein anderes — ohne Cloud.
 *
 * Das sendende Tablet macht sich per mDNS (`_soundbuddy._tcp`) sichtbar und wartet
 * auf einem freien Port. Das empfangende findet es, schickt den vierstelligen Code,
 * der auf dem sendenden angezeigt wird, und bekommt dafür das [SyncPackage].
 * Nach [MAX_WRONG_CODES] falschen Codes hört das sendende Tablet auf.
 *
 * Die Daten gehen unverschlüsselt durchs Heimnetz; der Code verhindert nur,
 * dass sich ein anderes Gerät im WLAN unbemerkt bedient.
 */
class LocalTransfer(context: Context) {

    private val nsdManager = context.getSystemService(NsdManager::class.java)

    // --- Senden ------------------------------------------------------------

    /**
     * Gibt [payload] frei, bis die Coroutine abgebrochen wird oder zu viele falsche
     * Codes kamen (dann [TransferException]). Mehrere Tablets können nacheinander abholen.
     */
    suspend fun share(name: String, code: String, payload: ByteArray, onEvent: (ShareEvent) -> Unit) =
        withContext(Dispatchers.IO) {
            ServerSocket(0).use { server ->
                // accept() reagiert nicht auf Abbruch — deshalb kurz warten und dann nachsehen
                server.soTimeout = ACCEPT_POLL_MS
                val registration = register(name, server.localPort)
                var wrongCodes = 0
                try {
                    while (true) {
                        ensureActive()
                        val socket = try {
                            server.accept()
                        } catch (e: SocketTimeoutException) {
                            continue
                        }
                        when (val result = serve(socket, code, payload)) {
                            is ServeResult.Sent -> onEvent(ShareEvent.Sent(result.receiverName))
                            is ServeResult.WrongCode -> {
                                wrongCodes++
                                val left = MAX_WRONG_CODES - wrongCodes
                                onEvent(ShareEvent.WrongCode(result.receiverName, left))
                                if (left <= 0) {
                                    throw TransferException("Zu viele falsche Codes – die Freigabe wurde beendet.")
                                }
                            }
                            ServeResult.Failed -> Unit
                        }
                    }
                } finally {
                    runCatching { nsdManager.unregisterService(registration) }
                }
            }
        }

    private sealed interface ServeResult {
        data class Sent(val receiverName: String) : ServeResult
        data class WrongCode(val receiverName: String) : ServeResult
        data object Failed : ServeResult
    }

    private fun serve(socket: Socket, code: String, payload: ByteArray): ServeResult = try {
        socket.use {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            if (input.readUTF() != MAGIC) return ServeResult.Failed
            val version = input.readInt()
            val receivedCode = input.readUTF()
            val receiverName = input.readUTF().take(100)
            when {
                version != PROTOCOL_VERSION -> {
                    output.writeInt(STATUS_INCOMPATIBLE)
                    output.flush()
                    ServeResult.Failed
                }
                receivedCode != code -> {
                    output.writeInt(STATUS_WRONG_CODE)
                    output.flush()
                    ServeResult.WrongCode(receiverName)
                }
                else -> {
                    output.writeInt(STATUS_OK)
                    output.writeInt(payload.size)
                    output.write(payload)
                    output.flush()
                    // Erst wenn das andere Tablet alles gelesen hat, zählt es als übertragen
                    if (input.readInt() == STATUS_OK) ServeResult.Sent(receiverName) else ServeResult.Failed
                }
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Übertragung abgebrochen", e)
        ServeResult.Failed
    }

    private suspend fun register(name: String, port: Int): NsdManager.RegistrationListener =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(this)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(TransferException("Das Tablet konnte sich im WLAN nicht anmelden ($errorCode)."))
                        )
                    }
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            val info = NsdServiceInfo().apply {
                serviceName = name
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            continuation.invokeOnCancellation { runCatching { nsdManager.unregisterService(listener) } }
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }

    // --- Empfangen ---------------------------------------------------------

    /** Tablets, die gerade freigeben. Die Suche läuft, solange der Flow gesammelt wird. */
    fun discover(): Flow<List<NearbyTablet>> = callbackFlow {
        val found = linkedMapOf<String, NearbyTablet>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(TransferException("Die Suche im WLAN ließ sich nicht starten ($errorCode)."))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found[serviceInfo.serviceName] = NearbyTablet(serviceInfo.serviceName, serviceInfo)
                trySend(found.values.toList())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                trySend(found.values.toList())
            }
        }
        trySend(emptyList())
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsdManager.stopServiceDiscovery(listener) } }
    }

    /**
     * Holt den Stand von [tablet] ab. Wirft [WrongCodeException] bei falschem Code
     * und [TransferException] mit verständlicher Meldung bei anderen Problemen.
     */
    suspend fun receive(tablet: NearbyTablet, code: String, ownName: String): ByteArray {
        val address = resolve(tablet.service)
            ?: throw TransferException("${tablet.name} ist nicht erreichbar. Ist die Freigabe dort noch offen?")
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(address, SOCKET_TIMEOUT_MS)
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    val output = DataOutputStream(socket.getOutputStream().buffered())
                    val input = DataInputStream(socket.getInputStream().buffered())
                    output.writeUTF(MAGIC)
                    output.writeInt(PROTOCOL_VERSION)
                    output.writeUTF(code)
                    output.writeUTF(ownName)
                    output.flush()
                    when (input.readInt()) {
                        STATUS_OK -> Unit
                        STATUS_WRONG_CODE -> throw WrongCodeException()
                        STATUS_INCOMPATIBLE -> throw TransferException(
                            "Die SoundBuddy-Versionen passen nicht zusammen. Bitte auf beiden Tablets aktualisieren."
                        )
                        else -> throw TransferException("${tablet.name} hat unerwartet geantwortet.")
                    }
                    val size = input.readInt()
                    if (size !in 0..MAX_PAYLOAD_BYTES) throw TransferException("${tablet.name} hat zu viele Daten geschickt.")
                    val payload = ByteArray(size)
                    input.readFully(payload)
                    output.writeInt(STATUS_OK)
                    output.flush()
                    payload
                }
            } catch (e: TransferException) {
                throw e
            } catch (e: IOException) {
                // Technische Fehler (Timeout, Verbindung zurückgesetzt …) verständlich melden
                Log.w(TAG, "Empfang von ${tablet.name} fehlgeschlagen", e)
                throw TransferException("Die Verbindung zu ${tablet.name} ist abgebrochen.")
            }
        }
    }

    /** Adresse und Port eines gefundenen Tablets. */
    private suspend fun resolve(service: NsdServiceInfo): InetSocketAddress? = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "${serviceInfo.serviceName} nicht aufgelöst ($errorCode)")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = serviceInfo.host
                    if (continuation.isActive) {
                        continuation.resume(host?.let { InetSocketAddress(it, serviceInfo.port) })
                    }
                }
            })
        }
    }

    /** Fehler mit einer Meldung, die so angezeigt werden kann. */
    open class TransferException(message: String) : IOException(message)

    class WrongCodeException : TransferException("Der Code stimmt nicht.")

    companion object {
        private const val TAG = "SoundBuddySync"
        private const val SERVICE_TYPE = "_soundbuddy._tcp."
        private const val MAGIC = "SoundBuddySync"
        private const val PROTOCOL_VERSION = 1
        private const val STATUS_OK = 0
        private const val STATUS_WRONG_CODE = 1
        private const val STATUS_INCOMPATIBLE = 2
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val ACCEPT_POLL_MS = 500
        private const val RESOLVE_TIMEOUT_MS = 8_000L
        private const val MAX_PAYLOAD_BYTES = 300 * 1024 * 1024
        const val MAX_WRONG_CODES = 5
    }
}
