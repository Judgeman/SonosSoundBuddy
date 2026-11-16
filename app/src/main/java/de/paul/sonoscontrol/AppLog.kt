package de.paul.sonoscontrol

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Wie wichtig ein Eintrag im Protokoll ist — danach filtert die Seite „Protokoll“. */
enum class LogLevel(val label: String) {
    /** Was die App gerade tut, zum Nachvollziehen. */
    DETAIL("Detail"),
    INFO("Info"),

    /** Etwas hat nicht geklappt, ohne dass eine Meldung auf dem Startbildschirm stand. */
    WARNING("Problem"),

    /** Eine Fehlermeldung stand auf dem Startbildschirm, oder die App ist abgestürzt. */
    ERROR("Fehler"),

    /** Ein Problem ist wieder weg: ein neuer Versuch hat geklappt, ein Ersatzweg, oder die Meldung verschwand. */
    RESOLVED("Erledigt")
}

@Serializable
data class LogEntry(
    val id: Long,
    /** Wann es (zum ersten Mal) passiert ist, in Millisekunden seit 1970. */
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    /** Zusätzliches für die Fehlersuche, z. B. die Ausnahme mit den Stellen im Code. */
    val detail: String? = null,
    /** So oft kurz hintereinander — gleiche Einträge werden zusammengefasst. */
    val count: Int = 1,
    /** Wann es zuletzt passiert ist, falls mehrfach. */
    val lastTime: Long = time
)

/**
 * Das Protokoll der App, in den Einstellungen unter „Protokoll“ zu sehen: Fehler, die auf dem
 * Startbildschirm standen, Probleme im Hintergrund und wie sie wieder weggingen (neuer Versuch,
 * Ersatzweg). Die Kinder können nicht vorlesen, was auf dem Tablet stand — hier steht es.
 *
 * Die Einträge liegen in einer Datei im App-Speicher (eine Zeile JSON je Eintrag) und überstehen
 * damit einen Neustart und einen Absturz. Alles geht zusätzlich ins Logcat.
 */
object AppLog {
    private const val TAG = "AppLog"
    private const val FILE_NAME = "protokoll.jsonl"

    /**
     * So viele Einträge bleiben höchstens stehen, je Art getrennt ([bucket]): Routine (Detail, Info),
     * Probleme, Wichtiges (Fehler, Erledigt). So drängt eine Flut von Problemen — etwa eine Nacht
     * ohne WLAN — die Fehler vom Tag nicht aus dem Protokoll, und die Routine weder noch.
     */
    private val LIMITS = intArrayOf(600, 500, 400)

    /** Index der Fehler und des Erledigten in [LIMITS]. */
    private const val IMPORTANT = 2
    private val MAX_ENTRIES = LIMITS.sum()

    /** Erst aufräumen, wenn so viele zu viel sind — nicht bei jedem Eintrag. */
    private const val TRIM_SLACK = 50

    /**
     * Wiederholt sich ein Eintrag so bald und liegt so wenig dazwischen, zählt der frühere hoch, statt
     * dass ein neuer dazukommt — sonst füllte z. B. jede gescheiterte Abfrage (alle 2 Sekunden) das Protokoll.
     * Fehler und Erledigtes nur direkt hintereinander: Dort soll jedes Mal mit seiner Uhrzeit dastehen.
     */
    private const val FOLD_WINDOW_MS = 2 * 60 * 1000L
    private const val FOLD_LOOK_BACK = 12
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_MESSAGE_LENGTH = 2_000
    private const val MAX_DETAIL_LENGTH = 6_000

    /** So viele Stellen aus dem eigenen Code nennt ein Eintrag zu einer Ausnahme. */
    private const val OWN_FRAMES = 4

    private val json = Json { ignoreUnknownKeys = true }

    /** Schützt [all], [counts] und [nextId]. */
    private val lock = Any()
    private var all = ArrayList<LogEntry>()

    /** Einträge je Art, siehe [bucket]. */
    private var counts = IntArray(LIMITS.size)

    /** Fortlaufend, beginnend mit der Startzeit — so bleiben Ids über Neustarts hinweg eindeutig. */
    private var nextId = System.currentTimeMillis()

    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Alle Einträge, der älteste zuerst. */
    val entries: StateFlow<List<LogEntry>> = state

    /** Schützt die Datei: Geschrieben wird im Hintergrund, bei einem Absturz aber sofort. */
    private val fileLock = Any()

    @Volatile
    private var file: File? = null

    /** Zeilen in der Datei — mehr als Einträge, weil Wiederholungen und Aufgeräumtes dort noch stehen. */
    private var linesInFile = 0
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AppLog").apply { isDaemon = true }
    }
    private var crashHandlerInstalled = false

    /** Einmal beim Start der App: lädt das Protokoll vom letzten Mal und fängt Abstürze ab. */
    fun init(context: Context) {
        start(context.applicationContext.filesDir)
        installCrashHandler()
    }

    /** Das Protokoll liegt in [directory]. Was dort schon steht, kommt vor die bisherigen Einträge. */
    internal fun start(directory: File) {
        if (file != null) return
        val target = File(directory, FILE_NAME)
        file = target
        writer.execute { load(target) }
    }

    fun d(tag: String, message: String, error: Throwable? = null) = add(LogLevel.DETAIL, tag, message, error)

    fun i(tag: String, message: String) = add(LogLevel.INFO, tag, message, null)

    /** Etwas hat nicht geklappt, aber auf dem Startbildschirm stand keine Meldung. */
    fun w(tag: String, message: String, error: Throwable? = null) = add(LogLevel.WARNING, tag, message, error)

    /** Eine Fehlermeldung stand auf dem Startbildschirm. */
    fun e(tag: String, message: String, error: Throwable? = null) = add(LogLevel.ERROR, tag, message, error)

    /** Ein Problem ist wieder weg — [message] nennt, welches und wodurch. */
    fun resolved(tag: String, message: String) = add(LogLevel.RESOLVED, tag, message, null)

    /** Leert das Protokoll, auch die Datei. */
    fun clear() {
        synchronized(lock) {
            replaceAll(emptyList())
            state.value = emptyList()
        }
        writer.execute { rewrite(emptyList()) }
    }

    /** Wartet, bis alles in der Datei steht. */
    internal fun flush() {
        runCatching { writer.submit(Runnable {}).get(5, TimeUnit.SECONDS) }
    }

    /** Das Protokoll als Text, der neueste Eintrag zuerst — zum Teilen. */
    fun asText(entries: List<LogEntry>, formatTime: (Long) -> String): String =
        entries.asReversed().joinToString("\n") { entry ->
            buildString {
                append(formatTime(entry.time)).append("  ").append(entry.level.label.uppercase())
                append("  [").append(entry.tag).append("]  ").append(entry.message)
                if (entry.count > 1) append("  (×${entry.count}, zuletzt ${formatTime(entry.lastTime)})")
                entry.detail?.let { append("\n    ").append(it.replace("\n", "\n    ")) }
            }
        }

    private fun add(level: LogLevel, tag: String, message: String, error: Throwable?) {
        when (level) {
            LogLevel.DETAIL -> Log.d(tag, message, error)
            LogLevel.INFO, LogLevel.RESOLVED -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message, error)
            LogLevel.ERROR -> Log.e(tag, message, error)
        }
        val entry = record(level, tag, message, error?.let(::describe))
        writer.execute { append(entry) }
    }

    /** Nimmt den Eintrag ins Protokoll auf; ein gleicher von eben zählt stattdessen hoch ([FOLD_WINDOW_MS]). */
    private fun record(level: LogLevel, tag: String, message: String, detail: String?): LogEntry =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val text = message.take(MAX_MESSAGE_LENGTH)
            val more = detail?.take(MAX_DETAIL_LENGTH)
            val lookBack = if (bucket(level) == IMPORTANT) 1 else FOLD_LOOK_BACK
            val index = (all.lastIndex downTo maxOf(0, all.size - lookBack)).firstOrNull { i ->
                val earlier = all[i]
                earlier.level == level && earlier.tag == tag && earlier.message == text && earlier.detail == more &&
                    now - earlier.lastTime <= FOLD_WINDOW_MS
            }
            val entry = if (index != null) {
                all[index].let { it.copy(count = it.count + 1, lastTime = now) }.also { all[index] = it }
            } else {
                LogEntry(id = nextId++, time = now, level = level, tag = tag, message = text, detail = more).also {
                    all.add(it)
                    val kind = bucket(level)
                    if (++counts[kind] > LIMITS[kind] + TRIM_SLACK) replaceAll(trimmed(all))
                }
            }
            state.value = all.toList()
            entry
        }

    /** Zu welcher Art ein Eintrag beim Aufräumen zählt — Index in [LIMITS]. */
    private fun bucket(level: LogLevel): Int = when (level) {
        LogLevel.DETAIL, LogLevel.INFO -> 0
        LogLevel.WARNING -> 1
        LogLevel.ERROR, LogLevel.RESOLVED -> IMPORTANT
    }

    /** Nur mit [lock]. */
    private fun replaceAll(entries: List<LogEntry>) {
        all = ArrayList(entries)
        counts = IntArray(LIMITS.size).also { byKind -> entries.forEach { byKind[bucket(it.level)]++ } }
    }

    /** Von jeder Art die ältesten weg, bis sie wieder in ihre Grenze passt. */
    private fun trimmed(entries: List<LogEntry>): List<LogEntry> {
        val surplus = IntArray(LIMITS.size)
        entries.forEach { surplus[bucket(it.level)]++ }
        for (kind in LIMITS.indices) surplus[kind] -= LIMITS[kind]
        if (surplus.none { it > 0 }) return entries
        return entries.filter { entry ->
            val kind = bucket(entry.level)
            if (surplus[kind] > 0) {
                surplus[kind]--
                false
            } else {
                true
            }
        }
    }

    /** Die Ausnahme mit ihren Ursachen und den Stellen im eigenen Code — ohne den ganzen Stacktrace. */
    private fun describe(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            if (depth > 0) append("\nUrsache: ")
            append(current.javaClass.simpleName)
            current.message?.let { append(": ").append(it) }
            current.stackTrace
                .filter { it.className.startsWith("de.paul.sonoscontrol") }
                .take(OWN_FRAMES)
                .forEach { append("\n  bei ").append(it.className.substringAfterLast('.')).append('.').append(it.methodName).append(" (").append(it.fileName).append(':').append(it.lineNumber).append(')') }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
    }

    // --- Datei -------------------------------------------------------------

    /** Liest das Protokoll vom letzten Mal. Läuft im Hintergrund, vor allen Schreibaufträgen. */
    private fun load(target: File) {
        val lines = synchronized(fileLock) {
            runCatching { if (target.exists()) target.readLines() else emptyList() }
                .onFailure { Log.w(TAG, "Protokoll nicht gelesen", it) }
                .getOrDefault(emptyList())
        }
        // Dieselbe Id noch einmal heißt: Der Eintrag hat sich wiederholt — die spätere Zeile gilt
        val byId = LinkedHashMap<Long, LogEntry>()
        for (line in lines) {
            if (line.isBlank()) continue
            runCatching { json.decodeFromString(LogEntry.serializer(), line) }.getOrNull()?.let { byId[it.id] = it }
        }
        val oldest = System.currentTimeMillis() - MAX_AGE_MS
        // Nach Id, also in der Reihenfolge des Entstehens: Eine Zeile kann in der Datei hinter jüngeren
        // stehen, wenn sie erst nach dem Neuschreiben der Datei angehängt wurde
        val loaded = byId.values.filter { it.lastTime >= oldest }.sortedBy { it.id }
        val merged = synchronized(lock) {
            // Was seit dem Start schon dazukam, steht danach — und ggf. noch nicht in der Datei
            val loadedIds = loaded.mapTo(HashSet()) { it.id }
            replaceAll(trimmed(loaded + all.filterNot { it.id in loadedIds }))
            state.value = all.toList()
            all.toList()
        }
        linesInFile = lines.size
        if (lines.size != loaded.size) rewrite(merged)
    }

    private fun append(entry: LogEntry) {
        val target = file ?: return
        synchronized(fileLock) {
            runCatching { target.appendText(json.encodeToString(entry) + "\n") }
                .onFailure { Log.w(TAG, "Protokoll nicht geschrieben", it) }
            linesInFile++
        }
        // Die Datei wächst mit jeder Wiederholung und behält Aufgeräumtes — ab und zu neu schreiben
        if (linesInFile > MAX_ENTRIES * 2) rewrite(synchronized(lock) { all.toList() })
    }

    private fun rewrite(entries: List<LogEntry>) {
        val target = file ?: return
        synchronized(fileLock) {
            runCatching {
                val temp = File(target.parentFile, "$FILE_NAME.tmp")
                temp.writeText(entries.joinToString("") { json.encodeToString(it) + "\n" })
                if (!temp.renameTo(target)) {
                    target.writeText(temp.readText())
                    temp.delete()
                }
                linesInFile = entries.size
            }.onFailure { Log.w(TAG, "Protokoll nicht neu geschrieben", it) }
        }
    }

    // --- Abstürze ----------------------------------------------------------

    /**
     * Ein Absturz steht sonst nirgends, wo ihn jemand nachlesen kann — für die Kinder ist die App
     * einfach weg. Deshalb sofort in die Datei, noch bevor Android die App beendet.
     */
    private fun installCrashHandler() {
        synchronized(this) {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val entry = record(
                    LogLevel.ERROR,
                    TAG,
                    "Die App ist abgestürzt: ${error.javaClass.simpleName}: ${error.message.orEmpty()} (Thread ${thread.name})",
                    Log.getStackTraceString(error)
                )
                append(entry)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
