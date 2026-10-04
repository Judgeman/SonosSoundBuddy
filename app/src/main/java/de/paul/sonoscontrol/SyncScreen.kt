package de.paul.sonoscontrol

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Je nach Speicherort meldet die Dateiauswahl ZIP-Dateien unterschiedlich. */
private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * Unterseite der Settings: den Stand dieses Tablets auf ein anderes übertragen —
 * direkt im WLAN oder als Datei.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel, onBack: () -> Unit) {
    var showExport by remember { mutableStateOf(false) }
    var showCloudDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var includePassword by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportToFile(uri, includePassword)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromFile(uri)
    }

    // Beim Verlassen der Seite nicht weiter im WLAN sichtbar bleiben
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopNetwork() }
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Tablets abgleichen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    "Stell ein Tablet fertig ein und übertrage den Stand auf die anderen. Auf dem " +
                        "empfangenden Tablet suchst du aus, was übernommen wird. Übernommenes ersetzt den " +
                        "Stand dort — nur welche Profile aktiv sind, bleibt je Tablet eigen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                CloudSection(
                    settings = viewModel.cloudSettings,
                    status = viewModel.cloudStatus,
                    busy = viewModel.cloudBusy,
                    notice = viewModel.cloudNotice,
                    onSettingsChange = viewModel::updateCloudSettings,
                    onSyncNow = viewModel::syncNow,
                    onReview = viewModel::reviewCloudUpdate,
                    onIgnore = viewModel::ignoreCloudUpdate,
                    onDelete = { showCloudDelete = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SectionHeader(
                    title = "Im WLAN übertragen",
                    description = "Beide Tablets müssen im selben WLAN sein und SoundBuddy geöffnet haben."
                )
            }
            item {
                val share = viewModel.share
                if (share == null) {
                    OutlinedButton(
                        onClick = viewModel::startSharing,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dieses Tablet freigeben")
                    }
                } else {
                    ShareCard(share = share, deviceName = viewModel.deviceName, onStop = viewModel::stopSharing)
                }
            }
            item {
                if (viewModel.nearby == null) {
                    OutlinedButton(
                        onClick = viewModel::startSearching,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Daten von einem anderen Tablet holen")
                    }
                } else {
                    SearchHeader(onStop = viewModel::stopSearching)
                }
            }
            viewModel.nearby?.let { nearby ->
                if (nearby.isEmpty()) {
                    item {
                        Text(
                            "Noch kein Tablet gefunden. Tippe auf dem anderen Tablet unter „Tablets abgleichen“ " +
                                "auf „Dieses Tablet freigeben“.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(nearby, key = { it.name }) { tablet ->
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.TabletAndroid, contentDescription = null) },
                        headlineContent = { Text(tablet.name, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Tippen, um die Daten zu holen") },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.selectTablet(tablet) }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(
                    title = "Als Datei",
                    description = "Zum Beispiel in Google Drive speichern und auf dem anderen Tablet importieren. " +
                        "Taugt auch als Sicherung."
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    OutlinedButton(onClick = { showExport = true }) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportieren")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(IMPORT_MIME_TYPES) }) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importieren")
                    }
                }
            }
        }
    }

    if (showCloudDelete) {
        AlertDialog(
            onDismissRequest = { showCloudDelete = false },
            icon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            title = { Text("Daten aus der Cloud löschen?") },
            text = {
                Text(
                    "Der Stand dieses Sonos-Haushalts wird samt Bildern aus dem Speicher des Workers gelöscht. " +
                        "Die Daten auf den Tablets bleiben. Ist ein Haupt-Tablet eingerichtet, lädt es beim " +
                        "nächsten Schließen seiner Einstellungen wieder hoch."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloudDelete = false
                    viewModel.deleteFromCloud()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showCloudDelete = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showExport) {
        ExportDialog(
            includePassword = includePassword,
            onIncludePasswordChange = { includePassword = it },
            onConfirm = {
                showExport = false
                exportLauncher.launch(viewModel.exportFileName())
            },
            onDismiss = { showExport = false }
        )
    }

    viewModel.codePromptFor?.let { tablet ->
        CodeDialog(
            tabletName = tablet.name,
            wrongCode = viewModel.codeWrong,
            onSubmit = viewModel::submitCode,
            onDismiss = viewModel::dismissCodePrompt
        )
    }

    viewModel.pendingImport?.let { pending ->
        ImportDialog(pending = pending, onApply = viewModel::applyImport, onDismiss = viewModel::dismissImport)
    }

    viewModel.busy?.let { BusyDialog(it) }
}

@Composable
private fun CloudSection(
    settings: CloudSettings,
    status: String?,
    busy: Boolean,
    notice: String?,
    onSettingsChange: (CloudSettings) -> Unit,
    onSyncNow: () -> Unit,
    onReview: () -> Unit,
    onIgnore: () -> Unit,
    onDelete: () -> Unit
) {
    SectionHeader(
        title = "Automatisch über die Cloud",
        description = "Ein Haupt-Tablet lädt seinen Stand in den Speicher des Cloudflare-Workers, die anderen " +
            "holen ihn ab — auch wenn sie woanders stehen. Alle Tablets müssen beim selben Sonos-Haushalt " +
            "angemeldet sein; andere Haushalte sehen die Daten nicht. Die Daten liegen beim Betreiber des Workers."
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        CloudRole.entries.forEach { role ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = settings.role == role, role = Role.RadioButton) {
                        onSettingsChange(settings.copy(role = role))
                    }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = settings.role == role, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(role.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        role.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (settings.role == CloudRole.FOLLOWER) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatisch übernehmen", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (settings.autoApply) {
                            "Neue Stände werden ohne Nachfrage übernommen — nicht, solange die Einstellungen offen sind."
                        } else {
                            "Neue Stände werden hier und auf der Einstellungs-Seite angezeigt und erst auf Tipp übernommen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoApply,
                    onCheckedChange = { onSettingsChange(settings.copy(autoApply = it)) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (settings.autoApply) "Das wird übernommen:" else "Vorauswahl beim Übernehmen:",
                style = MaterialTheme.typography.titleSmall
            )
            SyncScope.entries.forEach { scope ->
                val checked = scope in settings.scopes
                // Mindestens ein Bereich bleibt gewählt, sonst gäbe es nichts zu übernehmen
                val canToggle = !checked || settings.scopes.size > 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canToggle) {
                            onSettingsChange(settings.copy(scopes = if (checked) settings.scopes - scope else settings.scopes + scope))
                        }
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = canToggle)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(scope.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            scope.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (notice != null && settings.role == CloudRole.FOLLOWER) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(notice, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReview) { Text("Ansehen") }
                        TextButton(onClick = onIgnore) { Text("Ignorieren") }
                    }
                }
            }
        }

        if (settings.role != CloudRole.OFF) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSyncNow, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (settings.role == CloudRole.SOURCE) "Jetzt hochladen" else "Jetzt nachsehen")
                }
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        TextButton(onClick = onDelete) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Daten aus der Cloud löschen")
        }
    }
}

@Composable
private fun ShareCard(share: ShareState, deviceName: String, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            if (!share.ready) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Freigabe wird vorbereitet …")
            } else {
                Text("Code", style = MaterialTheme.typography.labelLarge)
                Text(
                    share.code,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Auf dem anderen Tablet „Daten von einem anderen Tablet holen“ tippen, " +
                        "„$deviceName“ wählen und diesen Code eingeben.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            share.events.forEach { event ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(event, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onStop) { Text("Freigabe beenden") }
        }
    }
}

@Composable
private fun SearchHeader(onStop: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text("Suche Tablets im WLAN …", modifier = Modifier.weight(1f))
        TextButton(onClick = onStop) { Text("Suche beenden") }
    }
}

@Composable
private fun ExportDialog(
    includePassword: Boolean,
    onIncludePasswordChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
        title = { Text("Daten exportieren") },
        text = {
            Column {
                Text(
                    "Die Datei enthält die Kinder-Profile samt Musik und Bildern sowie die Speaker-Einstellungen. " +
                        "Was davon übernommen wird, wählst du beim Importieren."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIncludePasswordChange(!includePassword) }
                ) {
                    Checkbox(checked = includePassword, onCheckedChange = onIncludePasswordChange)
                    Text("Passwort mit exportieren")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Speichern unter …") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun CodeDialog(
    tabletName: String,
    wrongCode: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val canSubmit = code.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.TabletAndroid, contentDescription = null) },
        title = { Text("Daten von „$tabletName“") },
        text = {
            Column {
                Text("Gib den Code ein, der auf „$tabletName“ angezeigt wird.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter(Char::isDigit).take(4) },
                    label = { Text("Code") },
                    singleLine = true,
                    isError = wrongCode,
                    supportingText = if (wrongCode) {
                        { Text("Der Code stimmt nicht") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = canSubmit) { Text("Daten holen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** Auswahl, was aus einem Stand übernommen wird — mit kurzer Übersicht, was drinsteckt. */
@Composable
private fun ImportDialog(pending: PendingImport, onApply: (Set<SyncScope>) -> Unit, onDismiss: () -> Unit) {
    val snapshot = pending.syncPackage.snapshot
    val available = SyncScope.entries.filter { it in snapshot.availableScopes }
    var selected by remember(pending) { mutableStateOf(pending.preselected) }
    val created = remember(snapshot) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.GERMANY)
            .format(Date(snapshot.createdAtMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
        title = { Text("Von „${pending.origin}“ übernehmen") },
        text = {
            Column {
                Text("Stand vom $created", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                available.forEach { scope ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scope.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                describeContent(scope, snapshot),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scope in selected,
                            onCheckedChange = { on -> selected = if (on) selected + scope else selected - scope }
                        )
                    }
                }
                if (SyncScope.PROFILES in selected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Profile, die es auf „${pending.origin}“ nicht gibt, werden auf diesem Tablet gelöscht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selected) }, enabled = selected.isNotEmpty()) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun describeContent(scope: SyncScope, snapshot: SyncSnapshot): String = when (scope) {
    SyncScope.PROFILES -> snapshot.profiles.orEmpty().let { profiles ->
        if (profiles.isEmpty()) "Keine Profile — alle Profile hier würden gelöscht" else profiles.joinToString { it.name }
    }
    SyncScope.SPEAKER_SETTINGS -> "${scope.description} von ${countSpeakers(snapshot)}"
    SyncScope.SPEAKER_SELECTION -> snapshot.speakers.orEmpty().filter { it.enabled }.let { enabled ->
        if (enabled.isEmpty()) "Kein Speaker freigegeben" else "Freigegeben: " + enabled.joinToString { it.name }
    }
    SyncScope.PASSWORD -> if (snapshot.password?.required == true) "Passwort und Schutz der Einstellungen" else "Passwort"
}

private fun countSpeakers(snapshot: SyncSnapshot): String {
    val count = snapshot.speakers.orEmpty().size
    return if (count == 1) "1 Speaker" else "$count Speakern"
}

@Composable
private fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text)
            }
        },
        confirmButton = {}
    )
}
