package de.paul.sonoscontrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val MIN_PASSWORD_LENGTH = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    speakers: List<SpeakerConfig>,
    availablePlayerIds: Set<String>?,
    settings: AppSettings,
    isLoggedIn: Boolean,
    isRefreshingSpeakers: Boolean,
    speakerRefreshMessage: String?,
    onBack: () -> Unit,
    onRefreshSpeakers: () -> Unit,
    onDeleteSpeaker: (String) -> Unit,
    onSpeakerEnabledChange: (String, Boolean) -> Unit,
    onSpeakerIconChange: (String, SpeakerIcon) -> Unit,
    onSpeakerMaxVolumeChange: (String, Int) -> Unit,
    profiles: List<ProfileWithMusic>,
    onCreateProfile: (String) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onProfileEnabledChange: (Long, Boolean) -> Unit,
    onSavePassword: (String) -> Unit,
    onRemovePassword: () -> Unit,
    onPasswordRequiredChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var iconPickerFor by remember { mutableStateOf<SpeakerConfig?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<SpeakerConfig?>(null) }
    var showNewProfile by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Das ViewModel leert die Meldung vor jedem neuen Aktualisieren, gleiche Texte erscheinen also erneut
    LaunchedEffect(speakerRefreshMessage) {
        speakerRefreshMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
                SectionHeader(
                    title = "Speaker",
                    description = "Lege fest, welche Speaker auf dem Startbildschirm auswählbar sind " +
                        "und wie laut sie höchstens werden dürfen. Tippe auf ein Icon, um es zu ändern."
                )
                if (isLoggedIn) {
                    OutlinedButton(
                        onClick = onRefreshSpeakers,
                        enabled = !isRefreshingSpeakers,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        if (isRefreshingSpeakers) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speaker-Liste aktualisieren")
                    }
                }
            }
            if (speakers.isEmpty()) {
                item {
                    Text(
                        "Noch keine Speaker gefunden.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            // Neu gefundene Speaker oben, damit sie auffallen
            items(speakers.sortedByDescending { it.isNew }, key = { it.playerId }) { speaker ->
                val reachable = availablePlayerIds == null || speaker.playerId in availablePlayerIds
                ListItem(
                    leadingContent = {
                        SpeakerIconBadge(
                            icon = speaker.icon,
                            modifier = Modifier.clickable { iconPickerFor = speaker }
                        )
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                speaker.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (speaker.isNew) {
                                Spacer(modifier = Modifier.width(8.dp))
                                NewBadge()
                            }
                        }
                    },
                    supportingContent = {
                        Column {
                            if (reachable) {
                                Text(speaker.icon.label)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Gerade nicht erreichbar", modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = { deleteConfirmFor = speaker },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Entfernen")
                                    }
                                }
                            }
                            MaxVolumeSlider(
                                maxVolume = speaker.maxVolume,
                                onMaxVolumeChange = { onSpeakerMaxVolumeChange(speaker.playerId, it) }
                            )
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = speaker.enabled,
                            onCheckedChange = { onSpeakerEnabledChange(speaker.playerId, it) }
                        )
                    }
                )
            }

            profilesSection(
                profiles = profiles,
                onCreateProfile = { showNewProfile = true },
                onOpenProfile = onOpenProfile,
                onProfileEnabledChange = onProfileEnabledChange
            )

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(
                    title = "Passwortschutz",
                    description = if (settings.hasPassword) {
                        "Ein Passwort ist hinterlegt."
                    } else {
                        "Es ist noch kein Passwort hinterlegt. Die Einstellungen sind für alle erreichbar."
                    }
                )
                PasswordSection(
                    settings = settings,
                    onSavePassword = onSavePassword,
                    onRemovePassword = onRemovePassword,
                    onPasswordRequiredChange = onPasswordRequiredChange
                )
            }

            if (isLoggedIn) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(title = "Sonos-Konto", description = null)
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("Abmelden")
                    }
                }
            }
        }
    }

    iconPickerFor?.let { speaker ->
        IconPickerDialog(
            speakerName = speaker.name,
            selected = speaker.icon,
            onSelect = { icon ->
                onSpeakerIconChange(speaker.playerId, icon)
                iconPickerFor = null
            },
            onDismiss = { iconPickerFor = null }
        )
    }

    if (showNewProfile) {
        NameDialog(
            title = "Neues Profil",
            label = "Name des Kindes",
            initialValue = "",
            confirmText = "Anlegen",
            onConfirm = {
                showNewProfile = false
                onCreateProfile(it)
            },
            onDismiss = { showNewProfile = false }
        )
    }

    deleteConfirmFor?.let { speaker ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("${speaker.name} entfernen?") },
            text = {
                Text(
                    "Icon und maximale Lautstärke dieses Speakers werden gelöscht. Taucht er später " +
                        "wieder auf, erscheint er als neuer, noch nicht freigegebener Speaker."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSpeaker(speaker.playerId)
                    deleteConfirmFor = null
                }) {
                    Text("Entfernen")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
internal fun NewBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            "Neu",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Maximal-Lautstärke in 5-%-Schritten; gespeichert wird erst beim Loslassen. */
@Composable
private fun MaxVolumeSlider(maxVolume: Int, onMaxVolumeChange: (Int) -> Unit) {
    var value by remember(maxVolume) { mutableFloatStateOf(maxVolume.toFloat()) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Maximale Lautstärke", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${value.roundToInt()} %",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onMaxVolumeChange(value.roundToInt()) },
            valueRange = 5f..100f,
            steps = 18
        )
    }
}

@Composable
internal fun SectionHeader(title: String, description: String?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordSection(
    settings: AppSettings,
    onSavePassword: (String) -> Unit,
    onRemovePassword: () -> Unit,
    onPasswordRequiredChange: (Boolean) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }

    val tooShort = newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH
    val mismatch = repeatPassword.isNotEmpty() && repeatPassword != newPassword
    val canSave = newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == repeatPassword

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Einstellungen nur mit Passwort öffnen",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.isLocked,
                enabled = settings.hasPassword,
                onCheckedChange = onPasswordRequiredChange
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it; confirmation = null },
            label = { Text(if (settings.hasPassword) "Neues Passwort" else "Passwort") },
            singleLine = true,
            isError = tooShort,
            supportingText = if (tooShort) {
                { Text("Mindestens $MIN_PASSWORD_LENGTH Zeichen") }
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = repeatPassword,
            onValueChange = { repeatPassword = it; confirmation = null },
            label = { Text("Passwort wiederholen") },
            singleLine = true,
            isError = mismatch,
            supportingText = if (mismatch) {
                { Text("Die Passwörter stimmen nicht überein") }
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canSave,
                onClick = {
                    onSavePassword(newPassword)
                    newPassword = ""
                    repeatPassword = ""
                    confirmation = "Passwort gespeichert"
                }
            ) {
                Text(if (settings.hasPassword) "Passwort ändern" else "Passwort speichern")
            }
            if (settings.hasPassword) {
                TextButton(onClick = {
                    onRemovePassword()
                    confirmation = "Passwort entfernt"
                }) {
                    Text("Passwort entfernen")
                }
            }
        }
        confirmation?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IconPickerDialog(
    speakerName: String,
    selected: SpeakerIcon,
    onSelect: (SpeakerIcon) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon für $speakerName") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(SpeakerIcon.entries) { icon ->
                    val isSelected = icon == selected
                    Card(
                        onClick = { onSelect(icon) },
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelected) {
                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            SpeakerIconBadge(icon, size = 48.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                icon.label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

/** Abfrage beim Öffnen der Settings, wenn der Passwortschutz aktiv ist. */
@Composable
fun PasswordPromptDialog(
    wrongPassword: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text("Einstellungen geschützt") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                isError = wrongPassword,
                supportingText = if (wrongPassword) {
                    { Text("Falsches Passwort") }
                } else {
                    null
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(password) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text("Öffnen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
