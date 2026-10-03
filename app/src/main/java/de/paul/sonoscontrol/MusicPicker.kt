package de.paul.sonoscontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Profil-Auswahl neben dem Speaker-Dropdown: zeigt nur das Icon des gewählten
 * Profils. Gibt es nur ein aktives Profil, ist es fest gewählt (ohne Pfeil).
 */
@Composable
fun ProfileDropdown(
    profiles: List<ProfileWithMusic>,
    selectedProfile: ProfileWithMusic?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val canChoose = profiles.size > 1

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = canChoose) { onExpandedChange(true) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = 14.dp,
                    end = if (canChoose) 4.dp else 14.dp,
                    top = 14.dp,
                    bottom = 14.dp
                )
            ) {
                if (selectedProfile != null) {
                    ProfileIconBadge(selectedProfile.profile.icon, size = 56.dp)
                } else {
                    NoProfileBadge(size = 56.dp)
                }
                if (canChoose) {
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = "Profil auswählen",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            profiles.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.profile.name, style = MaterialTheme.typography.titleLarge) },
                    leadingIcon = { ProfileIconBadge(entry.profile.icon, size = 56.dp) },
                    trailingIcon = {
                        if (entry.profile.id == selectedProfile?.profile?.id) {
                            Icon(Icons.Rounded.Check, contentDescription = "Ausgewählt")
                        }
                    },
                    onClick = {
                        onExpandedChange(false)
                        onSelectProfile(entry.profile.id)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** Platzhalter, solange bei mehreren Profilen noch keins gewählt ist. */
@Composable
private fun NoProfileBadge(size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), CircleShape)
    ) {
        Icon(
            Icons.Rounded.QuestionMark,
            contentDescription = "Kein Profil gewählt",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Großer Knopf ganz unten auf dem Homescreen, der die Musikauswahl des
 * gewählten Profils öffnet. Ohne gewähltes Profil öffnet er die Profil-Auswahl.
 */
@Composable
fun MusicChooserButton(
    selectedProfile: ProfileWithMusic?,
    isStartingMusic: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMusic = selectedProfile?.playableCategories?.isNotEmpty() == true
    Button(
        onClick = onClick,
        enabled = selectedProfile == null || hasMusic,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        if (selectedProfile != null) {
            ProfileIconBadge(selectedProfile.profile.icon, size = 52.dp)
        } else {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = when {
                selectedProfile == null -> "Wer hört Musik? Tippe hier!"
                !hasMusic -> "Noch keine Musik ausgesucht"
                else -> "Musik aussuchen"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (isStartingMusic) {
            Spacer(modifier = Modifier.width(16.dp))
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Großes Popup mit der Musikauswahl des Profils. Bei mehreren Kategorien
 * kommen zuerst die Kategorien als große Bild-Kacheln, ein Tipp öffnet deren
 * Musik. Ein Tipp auf die Musik spielt sie auf dem gewählten Speaker —
 * bei „Kinder entscheiden" wird vorher gefragt: der Reihe nach oder durcheinander.
 * [onPlay] bekommt die Wahl der Kinder (oder null = Einstellung der Kategorie).
 */
@Composable
fun MusicPickerDialog(
    profile: ProfileWithMusic,
    onPlay: (MusicItem, Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = profile.playableCategories
    var openCategoryId by remember { mutableStateOf(categories.singleOrNull()?.category?.id) }
    var askOrderFor by remember { mutableStateOf<MusicItem?>(null) }
    val openCategory = categories.firstOrNull { it.category.id == openCategoryId }
    val canGoBack = openCategory != null && categories.size > 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Zurück-Taste: erst die Frage schließen, dann zurück zu den Kategorien, dann Popup zu
        BackHandler(enabled = askOrderFor != null || canGoBack) {
            if (askOrderFor != null) askOrderFor = null else openCategoryId = null
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                ) {
                    if (canGoBack) {
                        FilledTonalIconButton(onClick = { openCategoryId = null }, modifier = Modifier.size(64.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Zurück",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    if (openCategory != null) {
                        CategoryImageView(openCategory, size = 64.dp)
                    } else {
                        ProfileIconBadge(profile.profile.icon, size = 64.dp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        openCategory?.category?.name ?: "Musik für ${profile.profile.name}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Schließen", modifier = Modifier.size(40.dp))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    if (openCategory == null) {
                        items(categories, key = { "category-${it.category.id}" }) { category ->
                            PickerTile(
                                title = category.category.name,
                                onClick = { openCategoryId = category.category.id }
                            ) { CategoryImageView(category) }
                        }
                    } else {
                        items(openCategory.items, key = { "item-${it.id}" }) { item ->
                            PickerTile(
                                title = item.name,
                                onClick = {
                                    val ask = openCategory.category.playOrderMode == PlayOrder.CHILD_CHOICE &&
                                        item.musicType.hasMultipleTracks
                                    if (ask) askOrderFor = item else onPlay(item, null)
                                }
                            ) { MusicItemImage(item) }
                        }
                    }
                }
            }
        }

        askOrderFor?.let { item ->
            PlayOrderQuestion(
                item = item,
                onChoose = { shuffle ->
                    askOrderFor = null
                    onPlay(item, shuffle)
                },
                onDismiss = { askOrderFor = null }
            )
        }
    }
}

@Composable
private fun PickerTile(title: String, onClick: () -> Unit, image: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        image()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** „Wie soll es laufen?" — zwei große Knöpfe mit Bildern, damit auch Kinder ohne Lesen wählen können. */
@Composable
private fun PlayOrderQuestion(item: MusicItem, onChoose: (shuffle: Boolean) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), tonalElevation = 6.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                MusicItemImage(item, size = 120.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OrderChoiceButton(
                        icon = Icons.Rounded.FormatListNumbered,
                        label = "Der Reihe nach",
                        onClick = { onChoose(false) },
                        modifier = Modifier.weight(1f)
                    )
                    OrderChoiceButton(
                        icon = Icons.Rounded.Shuffle,
                        label = "Durcheinander",
                        onClick = { onChoose(true) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderChoiceButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.height(150.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
