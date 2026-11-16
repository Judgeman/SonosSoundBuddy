package de.paul.sonoscontrol

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Was die Seite „Protokoll“ zeigt. */
enum class LogFilter(val label: String, val description: String, val levels: Set<LogLevel>) {
    ERRORS(
        "Fehler",
        "Fehlermeldungen, die auf dem Startbildschirm standen, Abstürze — und wodurch ein Problem wieder wegging.",
        setOf(LogLevel.ERROR, LogLevel.RESOLVED)
    ),
    PROBLEMS(
        "Auch Probleme",
        "Zusätzlich alles, was im Hintergrund nicht geklappt hat, ohne dass eine Meldung erschien.",
        setOf(LogLevel.ERROR, LogLevel.RESOLVED, LogLevel.WARNING)
    ),
    ALL(
        "Alles",
        "Auch, was die App und die Kinder sonst gemacht haben — zum Nachvollziehen, was vor einem Fehler passiert ist.",
        LogLevel.entries.toSet()
    )
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.GERMAN)
private val DayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val ShareTimeFormat = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss", Locale.GERMAN)

/** Mehr Text passt nicht sicher in eine Nachricht an eine andere App. */
private const val MAX_SHARED_CHARS = 100_000

/** So viele Zeilen zeigt ein Eintrag von seinen Details, bis man ihn antippt. */
private const val COLLAPSED_DETAIL_LINES = 3

private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatTime(millis: Long): String = TimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Heute"
    today.minusDays(1) -> "Gestern"
    else -> DayFormat.format(day)
}

/** Unterseite der Settings: das Protokoll der App, der neueste Eintrag oben. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by AppLog.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ERRORS) }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val shown = remember(entries, filter) { entries.filter { it.level in filter.levels }.asReversed() }
    // Einträge mit einer Überschrift je Tag
    val days = remember(shown) { shown.groupBy { localDate(it.time) }.toList() }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protokoll") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { shareLog(context, shown.asReversed(), filter) }, enabled = shown.isNotEmpty()) {
                        Icon(Icons.Rounded.Share, contentDescription = "Protokoll teilen")
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Protokoll leeren")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "filter") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        filter.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        when {
                            entries.isEmpty() -> "Das Protokoll ist leer."
                            filter == LogFilter.ERRORS -> "Keine Fehler im Protokoll. Unter „Alles“ steht, was die App sonst gemacht hat."
                            else -> "Dazu steht nichts im Protokoll."
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            }
            days.forEach { (day, dayEntries) ->
                item(key = "day-$day") {
                    Text(
                        dayLabel(day, today),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(dayEntries, key = { it.id }) { entry ->
                    LogEntryRow(entry)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Protokoll leeren?") },
            text = { Text("Alle Einträge werden gelöscht, auch die Fehler.") },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.clear()
                    confirmClear = false
                }) {
                    Text("Leeren")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") }
            }
        )
    }
}

/** Ein Eintrag: Uhrzeit, Art und Text; Details (z. B. die Ausnahme) klappen beim Antippen ganz auf. */
@Composable
private fun LogEntryRow(entry: LogEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.detail != null) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(entry.time),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            LevelBadge(entry.level)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                entry.tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        if (entry.count > 1) {
            Text(
                "${entry.count}-mal hintereinander, zuletzt um ${formatTime(entry.lastTime)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entry.detail?.let { detail ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DETAIL_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LevelBadge(level: LogLevel) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (level) {
        LogLevel.ERROR -> colors.error to colors.onError
        LogLevel.RESOLVED -> colors.tertiary to colors.onTertiary
        LogLevel.WARNING -> colors.secondary to colors.onSecondary
        LogLevel.INFO, LogLevel.DETAIL -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    LevelBadge(level.label, background, content)
}

@Composable
private fun LevelBadge(text: String, background: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Einstieg in den Einstellungen, mit der Zahl der Fehler seit gestern um diese Zeit. */
@Composable
fun LogSettingsEntry(onClick: () -> Unit) {
    val entries by AppLog.entries.collectAsState()
    val since = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    val errors = entries.count { it.level == LogLevel.ERROR && it.lastTime >= since }
    ListItem(
        leadingContent = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
        headlineContent = { Text("Protokoll ansehen", style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                when (errors) {
                    0 -> "Keine Fehler in den letzten 24 Stunden"
                    1 -> "1 Fehler in den letzten 24 Stunden"
                    else -> "$errors Fehler in den letzten 24 Stunden"
                },
                color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Gibt [entries] (ältester zuerst) als Text an eine andere App weiter, z. B. zum Verschicken. */
private fun shareLog(context: Context, entries: List<LogEntry>, filter: LogFilter) {
    val header = "SoundBuddy ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
        "Protokoll (${filter.label}), ${entries.size} Einträge, der neueste zuerst\n\n"
    val text = AppLog.asText(entries) { ShareTimeFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    val body = if (text.length > MAX_SHARED_CHARS) {
        text.take(MAX_SHARED_CHARS) + "\n\n… (ältere Einträge abgeschnitten)"
    } else {
        text
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SoundBuddy-Protokoll")
        putExtra(Intent.EXTRA_TEXT, header + body)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Protokoll teilen")) }
        .onFailure { AppLog.w("AppLog", "Protokoll nicht geteilt", it) }
}
