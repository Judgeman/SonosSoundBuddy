package de.paul.sonoscontrol

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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QuestionMark
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Großes Popup mit der Musikauswahl des Profils: pro Kategorie eine Überschrift
 * und darunter große Cover-Kacheln. Ein Tipp spielt sofort auf dem gewählten Speaker.
 */
@Composable
fun MusicPickerDialog(
    profile: ProfileWithMusic,
    onPlay: (MusicItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    ProfileIconBadge(profile.profile.icon, size = 64.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Musik für ${profile.profile.name}",
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
                    profile.playableCategories.forEach { category ->
                        item(key = "header-${category.category.id}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                category.category.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(category.items, key = { "item-${it.id}" }) { item ->
                            MusicTile(item = item, onClick = { onPlay(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTile(item: MusicItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        MusicCover(item.imageUrl, item.musicType)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
