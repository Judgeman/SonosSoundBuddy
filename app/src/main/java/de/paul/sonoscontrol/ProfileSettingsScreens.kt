package de.paul.sonoscontrol

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// --- Abschnitte „Musikauswahl" und „Kinder-Profile" auf der Settings-Hauptseite

/** Einstieg in die zentrale Musikauswahl, aus der sich alle Profile bedienen. */
fun LazyListScope.musicLibrarySection(categories: List<CategoryWithMusic>, onOpenLibrary: () -> Unit) {
    item {
        SectionHeader(
            title = "Musikauswahl",
            description = "Kategorien und Musik werden hier einmal zentral angelegt. " +
                "Pro Kategorie legst du fest, welche Profile sie sehen."
        )
        ListItem(
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
            headlineContent = { Text("Kategorien und Musik", style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(describeMusic(categories)) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenLibrary)
        )
    }
}

/** Liste der Profile mit Schalter „auf diesem Tablet aktiv"; ein Tipp öffnet die Profil-Seite. */
fun LazyListScope.profilesSection(
    profiles: List<ProfileWithMusic>,
    onCreateProfile: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onProfileEnabledChange: (Long, Boolean) -> Unit
) {
    item {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionHeader(
            title = "Kinder-Profile",
            description = "Jedes Profil hat ein eigenes Icon und sieht die Kategorien, die ihm zugewiesen sind. " +
                "Aktive Profile sind auf diesem Tablet auswählbar — ist nur eins aktiv, ist es immer gewählt."
        )
    }
    if (profiles.isEmpty()) {
        item {
            Text(
                "Noch keine Profile angelegt.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
    items(profiles, key = { "profile-${it.profile.id}" }) { entry ->
        val profile = entry.profile
        ListItem(
            leadingContent = { ProfileIconBadge(profile.icon) },
            headlineContent = {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = { Text(describeMusic(entry.categories)) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = { onProfileEnabledChange(profile.id, it) }
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            },
            modifier = Modifier.clickable { onOpenProfile(profile.id) }
        )
    }
    item {
        OutlinedButton(
            onClick = onCreateProfile,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Profil anlegen")
        }
    }
}

private fun describeMusic(categories: List<CategoryWithMusic>): String {
    val items = categories.sumOf { it.items.size }
    val categoryText = if (categories.size == 1) "1 Kategorie" else "${categories.size} Kategorien"
    val itemText = if (items == 1) "1 Eintrag" else "$items Einträge"
    return "$categoryText · $itemText"
}

private fun describeItemCount(category: CategoryWithMusic): String =
    if (category.items.size == 1) "1 Eintrag" else "${category.items.size} Einträge"

// --- Unterseite: ein Profil bearbeiten ------------------------------------

/** Callbacks der Profil-Seite, gebündelt damit die Signatur übersichtlich bleibt. */
class ProfileEditorActions(
    val onBack: () -> Unit,
    val onNameChange: (Long, String) -> Unit,
    val onIconChange: (Long, ProfileIcon) -> Unit,
    val onEnabledChange: (Long, Boolean) -> Unit,
    val onDeleteProfile: (Long) -> Unit,
    /** Kategorie-Id, Profil-Id, sichtbar. */
    val onCategoryVisibleChange: (Long, Long, Boolean) -> Unit,
    val onOpenLibrary: () -> Unit
)

/**
 * Profil-Seite: Name, Icon, aktiv — und welche Kategorien der zentralen
 * Musikauswahl das Profil sieht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profile: ProfileWithMusic,
    categories: List<CategoryWithMusic>,
    actions: ProfileEditorActions
) {
    val id = profile.profile.id
    var showIconPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
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
                ProfileHeader(
                    profile = profile.profile,
                    onIconClick = { showIconPicker = true },
                    onRenameClick = { showRename = true },
                    onEnabledChange = { actions.onEnabledChange(id, it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(
                    title = "Musikauswahl",
                    description = "Eingeschaltete Kategorien erscheinen für ${profile.profile.name} auf dem " +
                        "Startbildschirm unter dem Play-Knopf. Leere Kategorien sehen die Kinder nicht."
                )
            }

            if (categories.isEmpty()) {
                item {
                    Text(
                        "Noch keine Kategorien. Lege in der Musikauswahl z. B. „Lieblingslieder“ oder „Hörspiele“ an.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(categories, key = { "category-${it.category.id}" }) { category ->
                val visible = id in category.profileIds
                ListItem(
                    leadingContent = { CategoryImageView(category, size = 52.dp) },
                    headlineContent = {
                        Text(category.category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(describeItemCount(category)) },
                    trailingContent = {
                        Switch(
                            checked = visible,
                            onCheckedChange = { actions.onCategoryVisibleChange(category.category.id, id, it) }
                        )
                    },
                    modifier = Modifier.clickable {
                        actions.onCategoryVisibleChange(category.category.id, id, !visible)
                    }
                )
            }

            item {
                Button(
                    onClick = actions.onOpenLibrary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Musikauswahl bearbeiten")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = { showDeleteProfile = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Profil löschen")
                }
            }
        }
    }

    if (showIconPicker) {
        ProfileIconPickerDialog(
            profileName = profile.profile.name,
            selected = profile.profile.icon,
            onSelect = {
                actions.onIconChange(id, it)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
    if (showRename) {
        NameDialog(
            title = "Profil umbenennen",
            label = "Name",
            initialValue = profile.profile.name,
            confirmText = "Speichern",
            onConfirm = {
                actions.onNameChange(id, it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
    if (showDeleteProfile) {
        ConfirmDeleteDialog(
            title = "Profil „${profile.profile.name}“ löschen?",
            text = "Das Profil wird gelöscht. Die Kategorien und ihre Musik bleiben in der Musikauswahl " +
                "für die anderen Profile erhalten.",
            onConfirm = {
                showDeleteProfile = false
                actions.onDeleteProfile(id)
            },
            onDismiss = { showDeleteProfile = false }
        )
    }
}

// --- Unterseite: die zentrale Musikauswahl bearbeiten ---------------------

/** Callbacks der Musikauswahl und ihrer Kategorie-Seiten, gebündelt damit die Signatur übersichtlich bleibt. */
class MusicLibraryActions(
    val onBack: () -> Unit,
    val onCreateCategory: (String) -> Unit,
    val onOpenCategory: (Long) -> Unit,
    val onRenameCategory: (Long, String) -> Unit,
    val onDeleteCategory: (Long) -> Unit,
    /** Kategorie-Id, Plätze (−1 = einen nach oben). */
    val onMoveCategory: (Long, Int) -> Unit,
    /** Ids aller Kategorien in der neuen Reihenfolge (nach Drag & Drop). */
    val onReorderCategories: (List<Long>) -> Unit,
    /** Kategorie-Id, Profil-Id, sichtbar. */
    val onCategoryVisibleChange: (Long, Long, Boolean) -> Unit,
    val onAddMusic: (Long) -> Unit,
    val onAddMusicAtStartChange: (Boolean) -> Unit,
    val onRemoveMusicItem: (Long) -> Unit,
    /** Musik, [ChildProfile.syncId] der Profile, true = wieder als neu markieren, false = als gespielt. */
    val onSetMusicNew: (List<MusicItem>, List<String>, Boolean) -> Unit,
    val onCategoryImageChange: (Long, CustomImage) -> Unit,
    val onImportCategoryImage: (Long, Uri) -> Unit,
    val onItemImageChange: (Long, CustomImage) -> Unit,
    val onImportItemImage: (Long, Uri) -> Unit,
    val onPlayOrderChange: (Long, PlayOrder) -> Unit,
    val onItemSortChange: (Long, ItemSort, Boolean) -> Unit,
    val onCoverAnimationChange: (Long, CoverAnimation) -> Unit,
    /** Eintrags-Id, Plätze (−1 = einen nach oben). */
    val onMoveMusicItem: (Long, Int) -> Unit,
    /** Kategorie-Id, Ids ihrer Musik in der neuen Reihenfolge (nach Drag & Drop). */
    val onReorderMusicItems: (Long, List<Long>) -> Unit,
    val onDismissImageError: () -> Unit
)

/**
 * Übersicht der Musikauswahl: pro Kategorie nur Bild, Name und Platz in der Reihenfolge.
 * Musik und Einstellungen einer Kategorie liegen auf ihrer eigenen Seite ([CategoryEditorScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    categories: List<CategoryWithMusic>,
    profiles: List<ChildProfile>,
    imageError: String?,
    actions: MusicLibraryActions
) {
    val openImageDialog = rememberImageChooser(categories, actions)
    val snackbarHostState = remember { SnackbarHostState() }
    ImageErrorSnackbar(imageError, snackbarHostState, actions.onDismissImageError)
    var showNewCategory by remember { mutableStateOf(false) }
    var renameCategory by remember { mutableStateOf<MusicCategory?>(null) }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, categories.map { it.category.id }) { keys ->
        actions.onReorderCategories(keys.map { it as Long })
    }
    val shown = reorder.arrange(categories) { it.category.id }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Musikauswahl") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SectionHeader(
                    title = "Kategorien",
                    description = "Die Kategorien erscheinen auf dem Startbildschirm unter dem Play-Knopf — " +
                        "in dieser Reihenfolge. Tippe auf eine Kategorie, um ihre Musik und Einstellungen zu " +
                        "bearbeiten. Verschieben am Griff oder mit den Pfeilen; langes Drücken auf einen Pfeil " +
                        "setzt die Kategorie ganz nach oben bzw. unten."
                )
            }

            if (categories.isEmpty()) {
                item {
                    Text(
                        "Noch keine Kategorien. Lege z. B. „Lieblingslieder“ oder „Hörspiele“ an.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(shown, key = { _, it -> it.category.id }) { index, category ->
                val id = category.category.id
                CategoryRow(
                    category = category,
                    profiles = profiles,
                    reorder = reorder,
                    canMoveUp = index > 0,
                    canMoveDown = index < shown.lastIndex,
                    onOpen = { actions.onOpenCategory(id) },
                    onRename = { renameCategory = category.category },
                    onImageClick = { openImageDialog("$CATEGORY_TARGET:$id") },
                    onMove = { actions.onMoveCategory(id, it) },
                    onMoveToEnd = { toTop -> actions.onMoveCategory(id, if (toTop) -shown.size else shown.size) },
                    modifier = if (reorder.draggedKey == id) Modifier.draggedItem(reorder, id) else Modifier.animateItem()
                )
            }

            item {
                Button(
                    onClick = { showNewCategory = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kategorie hinzufügen")
                }
            }
        }
    }

    if (showNewCategory) {
        NameDialog(
            title = "Neue Kategorie",
            label = "Name der Kategorie",
            initialValue = "",
            confirmText = "Anlegen",
            onConfirm = {
                actions.onCreateCategory(it)
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false }
        )
    }
    renameCategory?.let { category ->
        RenameCategoryDialog(
            category = category,
            onConfirm = {
                actions.onRenameCategory(category.id, it)
                renameCategory = null
            },
            onDismiss = { renameCategory = null }
        )
    }
}

/** Eine Zeile der Übersicht: Griff, Bild, Name, Umbenennen, Pfeile — ein Tipp öffnet die Kategorie. */
@Composable
private fun CategoryRow(
    category: CategoryWithMusic,
    profiles: List<ChildProfile>,
    reorder: ReorderState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onImageClick: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveToEnd: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val name = category.category.name
    val dragged = reorder.draggedKey == category.category.id
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragged) 8.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            DragHandle(reorder, category.category.id, description = "$name verschieben")
            EditableImage(onClick = onImageClick) { CategoryImageView(category, size = 64.dp) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val seenBy = profiles.filter { it.id in category.profileIds }.map { it.name }
                Text(
                    describeItemCount(category) + " · " +
                        if (seenBy.isEmpty()) "für niemanden sichtbar" else "für ${seenBy.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (seenBy.isEmpty() && profiles.isNotEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Rounded.Edit, contentDescription = "$name umbenennen")
            }
            MoveButtons(
                name = name,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMove = onMove,
                onMoveToEnd = onMoveToEnd
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )
        }
    }
}

// --- Unterseite: eine Kategorie mit ihrer Musik ---------------------------

/** Eine Kategorie: Bild, Name, wer sie sieht, wie sie abspielt — und ihre Musik. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    category: CategoryWithMusic,
    categories: List<CategoryWithMusic>,
    profiles: List<ChildProfile>,
    /** Je Profil ([ChildProfile.syncId]) die [MusicItem.playedKey] der Musik, die es schon gespielt hat. */
    playedMusic: Map<String, Set<String>>,
    addMusicAtStart: Boolean,
    imageError: String?,
    actions: MusicLibraryActions
) {
    val id = category.category.id
    val openImageDialog = rememberImageChooser(categories, actions)
    val snackbarHostState = remember { SnackbarHostState() }
    ImageErrorSnackbar(imageError, snackbarHostState, actions.onDismissImageError)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    // Neu oder gespielt ist Musik nur für die Profile, die die Kategorie sehen
    val viewers = profiles.filter { it.id in category.profileIds }
    val viewerSyncIds = viewers.map { it.syncId }
    val newFor: (MusicItem) -> List<ChildProfile> = { item ->
        viewers.filter { item.playedKey !in playedMusic[it.syncId].orEmpty() }
    }
    var newDialogItemId by remember { mutableStateOf<Long?>(null) }

    val sort = category.category.itemSortMode
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, category.items.map { it.id }) { keys ->
        actions.onReorderMusicItems(id, keys.map { it as Long })
    }
    val items = reorder.arrange(category.items) { it.id }
    val movable = sort == ItemSort.MANUAL && items.size > 1

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(category.category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Weitere Aktionen")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (viewers.isNotEmpty() && category.items.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Alle als neu markieren") },
                                    leadingIcon = {
                                        Icon(NewMusicIcon, contentDescription = null, tint = Color.Unspecified)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        actions.onSetMusicNew(category.items, viewerSyncIds, true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Alle als gespielt markieren") },
                                    leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        actions.onSetMusicNew(category.items, viewerSyncIds, false)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Kategorie löschen") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menuExpanded = false; showDelete = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                CategoryHeader(
                    category = category,
                    onImageClick = { openImageDialog("$CATEGORY_TARGET:$id") },
                    onRenameClick = { showRename = true }
                )
                VisibilitySelector(
                    profiles = profiles,
                    visibleFor = category.profileIds,
                    onVisibleChange = { profileId, visible -> actions.onCategoryVisibleChange(id, profileId, visible) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                PlayOrderSelector(
                    selected = category.category.playOrderMode,
                    onSelect = { actions.onPlayOrderChange(id, it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                CoverAnimationSelector(
                    selected = category.category.coverAnimationMode,
                    onSelect = { actions.onCoverAnimationChange(id, it) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = "Musik", description = null)
                ItemSortSelector(
                    selected = sort,
                    descending = category.category.itemSortDescending,
                    onSelect = { itemSort, descending -> actions.onItemSortChange(id, itemSort, descending) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                AddPositionSelector(
                    atStart = addMusicAtStart,
                    sortedManually = sort == ItemSort.MANUAL,
                    onChange = actions.onAddMusicAtStartChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Button(
                    onClick = { actions.onAddMusic(id) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                ) {
                    Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Musik hinzufügen")
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "Noch keine Musik in dieser Kategorie. Leere Kategorien sehen die Kinder nicht.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                MusicItemRow(
                    item = item,
                    reorder = reorder,
                    movable = movable,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onImageClick = { openImageDialog("$ITEM_TARGET:${item.id}") },
                    onMove = { actions.onMoveMusicItem(item.id, it) },
                    onMoveToEnd = { toTop -> actions.onMoveMusicItem(item.id, if (toTop) -items.size else items.size) },
                    onRemove = { actions.onRemoveMusicItem(item.id) },
                    newFor = newFor(item),
                    onNewClick = { newDialogItemId = item.id }.takeIf { viewers.isNotEmpty() },
                    modifier = if (reorder.draggedKey == item.id) {
                        Modifier.draggedItem(reorder, item.id)
                    } else {
                        Modifier.animateItem()
                    }
                )
            }
        }
    }

    category.items.firstOrNull { it.id == newDialogItemId }?.let { item ->
        NewMusicDialog(
            item = item,
            viewers = viewers,
            newFor = newFor(item),
            onNewChange = { profile, new -> actions.onSetMusicNew(listOf(item), listOf(profile.syncId), new) },
            onDismiss = { newDialogItemId = null }
        )
    }
    if (showRename) {
        RenameCategoryDialog(
            category = category.category,
            onConfirm = {
                actions.onRenameCategory(id, it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
    if (showDelete) {
        val seenBy = profiles.filter { it.id in category.profileIds }.map { it.name }
        ConfirmDeleteDialog(
            title = "„${category.category.name}“ löschen?",
            text = "Die Kategorie und ihre ${category.items.size} Einträge werden aus der Musikauswahl entfernt" +
                (if (seenBy.isEmpty()) "." else " — für ${seenBy.joinToString(", ")}.") +
                " Bei Sonos selbst wird nichts gelöscht.",
            onConfirm = {
                showDelete = false
                // Die Seite schließt sich von selbst, sobald die Kategorie weg ist
                actions.onDeleteCategory(id)
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun CategoryHeader(category: CategoryWithMusic, onImageClick: () -> Unit, onRenameClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
        EditableImage(onClick = onImageClick, badgeSize = 28.dp) { CategoryImageView(category, size = 96.dp) }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.category.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                describeItemCount(category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onRenameClick) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Umbenennen")
                }
                TextButton(onClick = onImageClick) { Text("Bild ändern") }
            }
        }
    }
}

/**
 * Ein Musik-Eintrag der Kategorie; bei manueller Sortierung mit Griff und Pfeilen. [newFor]: Profile,
 * für die er neu ist. [onNewClick] öffnet die Neu-Markierung, null = kein Profil sieht die Kategorie.
 */
@Composable
private fun MusicItemRow(
    item: MusicItem,
    reorder: ReorderState,
    movable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onImageClick: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveToEnd: (Boolean) -> Unit,
    onRemove: () -> Unit,
    newFor: List<ChildProfile>,
    onNewClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dragged = reorder.draggedKey == item.id
    Surface(
        color = if (dragged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shadowElevation = if (dragged) 6.dp else 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (movable) 4.dp else 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (movable) DragHandle(reorder, item.id, description = "${item.name} verschieben")
            EditableImage(onClick = onImageClick, badgeSize = 20.dp) {
                MusicItemImage(item, size = 52.dp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.musicType.label, item.description).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (newFor.isNotEmpty()) {
                    Text(
                        "Neu für " + newFor.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = NewMusicColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (movable) {
                MoveButtons(
                    name = item.name,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMove = onMove,
                    onMoveToEnd = onMoveToEnd
                )
            }
            if (onNewClick != null) {
                IconButton(onClick = onNewClick) {
                    // Bunt wie bei den Kindern, solange die Musik für jemanden neu ist
                    if (newFor.isNotEmpty()) {
                        Icon(
                            NewMusicIcon,
                            contentDescription = "Neu-Markierung von ${item.name}",
                            tint = Color.Unspecified
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "Neu-Markierung von ${item.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "${item.name} entfernen")
            }
        }
    }
}

/**
 * Für welche Kinder die Musik neu ist — einzeln umstellbar. Wieder als neu Markiertes trägt in der
 * Musikauswahl des Kindes das bunte Funkeln, bis es die Musik spielt; auf den anderen Tablets ebenso.
 */
@Composable
private fun NewMusicDialog(
    item: MusicItem,
    viewers: List<ChildProfile>,
    newFor: List<ChildProfile>,
    onNewChange: (ChildProfile, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(NewMusicIcon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
        },
        title = { Text("„${item.name}“ ist neu für …", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "Neue Musik funkelt in der Musikauswahl des Kindes, bis es sie zum ersten Mal spielt.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                viewers.forEach { profile ->
                    val isNew = newFor.any { it.id == profile.id }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNewChange(profile, !isNew) }
                            .padding(vertical = 4.dp)
                    ) {
                        ProfileIconBadge(profile.icon, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = isNew, onCheckedChange = { onNewChange(profile, it) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

/** Ob „Musik hinzufügen“ neue Einträge an den Anfang oder ans Ende der Kategorie setzt. */
@Composable
private fun AddPositionSelector(
    atStart: Boolean,
    sortedManually: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Neue Musik einfügen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = atStart,
                onClick = { onChange(true) },
                label = { Text("Am Anfang") },
                leadingIcon = { Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            FilterChip(
                selected = !atStart,
                onClick = { onChange(false) },
                label = { Text("Am Ende") },
                leadingIcon = { Icon(Icons.Rounded.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }
        if (!sortedManually) {
            Text(
                "Wirkt nur bei der Sortierung „${ItemSort.MANUAL.label}“ — sonst ergibt sich der Platz aus der Sortierung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RenameCategoryDialog(category: MusicCategory, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    NameDialog(
        title = "Kategorie umbenennen",
        label = "Name der Kategorie",
        initialValue = category.name,
        confirmText = "Speichern",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun ImageErrorSnackbar(imageError: String?, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    LaunchedEffect(imageError) {
        if (imageError != null) {
            snackbarHostState.showSnackbar(imageError)
            onDismiss()
        }
    }
}

/**
 * Bild-Dialog samt Android-Fotoauswahl für Kategorien und Musik-Einträge.
 * Gibt die Funktion zurück, die den Dialog öffnet, z. B. für "category:3" oder "item:7".
 */
@Composable
private fun rememberImageChooser(categories: List<CategoryWithMusic>, actions: MusicLibraryActions): (String) -> Unit {
    var imageDialogFor by remember { mutableStateOf<String?>(null) }
    var importFor by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = importFor
        importFor = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val targetId = target.substringAfter(':').toLong()
        if (target.startsWith(CATEGORY_TARGET)) {
            actions.onImportCategoryImage(targetId, uri)
        } else {
            actions.onImportItemImage(targetId, uri)
        }
    }
    fun openPhotoPicker(target: String) {
        importFor = target
        imageDialogFor = null
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    imageDialogFor?.let { target ->
        val targetId = target.substringAfter(':').toLong()
        if (target.startsWith(CATEGORY_TARGET)) {
            categories.firstOrNull { it.category.id == targetId }?.let { category ->
                ImageChoiceDialog(
                    title = "Bild für „${category.category.name}“",
                    current = category.category.image,
                    defaultLabel = "Standard-Bild",
                    preview = { CategoryImageView(category, size = 96.dp) },
                    onSelect = { actions.onCategoryImageChange(targetId, it) },
                    onUploadClick = { openPhotoPicker(target) },
                    onDismiss = { imageDialogFor = null }
                )
            }
        } else {
            categories.flatMap { it.items }.firstOrNull { it.id == targetId }?.let { item ->
                ImageChoiceDialog(
                    title = "Bild für „${item.name}“",
                    current = item.customImage,
                    defaultLabel = "Cover von Sonos",
                    preview = { MusicItemImage(item, size = 96.dp) },
                    onSelect = { actions.onItemImageChange(targetId, it) },
                    onUploadClick = { openPhotoPicker(target) },
                    onDismiss = { imageDialogFor = null }
                )
            }
        }
    }

    return remember { { target: String -> imageDialogFor = target } }
}

@Composable
private fun ProfileHeader(
    profile: ChildProfile,
    onIconClick: () -> Unit,
    onRenameClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileIconBadge(
                icon = profile.icon,
                size = 96.dp,
                modifier = Modifier.clickable(onClick = onIconClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    TextButton(onClick = onRenameClick) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Umbenennen")
                    }
                    TextButton(onClick = onIconClick) { Text("Icon ändern") }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auf diesem Tablet aktiv", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Nur aktive Profile lassen sich auf dem Startbildschirm auswählen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = profile.enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/** Welche Profile die Kategorie sehen: ein Chip pro Profil zum An- und Abwählen. */
@Composable
private fun VisibilitySelector(
    profiles: List<ChildProfile>,
    visibleFor: Set<Long>,
    onVisibleChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Sichtbar für",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (profiles.isEmpty()) {
            Text(
                "Noch keine Profile angelegt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                profiles.forEach { profile ->
                    val visible = profile.id in visibleFor
                    FilterChip(
                        selected = visible,
                        onClick = { onVisibleChange(profile.id, !visible) },
                        label = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { ProfileIconBadge(profile.icon, size = 24.dp) }
                    )
                }
            }
            if (profiles.none { it.id in visibleFor }) {
                Text(
                    "Kein Profil ausgewählt — die Kategorie sieht gerade niemand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Abspielreihenfolge der Kategorie: der Reihe nach, zufällig oder die Kinder entscheiden lassen. */
@Composable
private fun PlayOrderSelector(selected: PlayOrder, onSelect: (PlayOrder) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Abspielen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            PlayOrder.entries.forEach { order ->
                FilterChip(
                    selected = order == selected,
                    onClick = { onSelect(order) },
                    label = { Text(order.label) },
                    leadingIcon = {
                        Icon(
                            order.icon,
                            contentDescription = null,
                            // Enten und Würfel sind bunt, das Kinder-Symbol folgt der Chip-Farbe
                            tint = if (order == PlayOrder.CHILD_CHOICE) LocalContentColor.current else Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }
        }
        if (selected == PlayOrder.CHILD_CHOICE) {
            Text(
                "Nach dem Antippen fragt die App mit zwei großen Knöpfen: der Reihe nach oder durcheinander. " +
                    "Bei Songs und Radio wird nicht gefragt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Was das Tier am Cover auf dem Startbildschirm macht, solange etwas aus der Kategorie läuft. */
@Composable
private fun CoverAnimationSelector(selected: CoverAnimation, onSelect: (CoverAnimation) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Tier am Cover",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverAnimation.entries.forEach { animation ->
                FilterChip(
                    selected = animation == selected,
                    onClick = { onSelect(animation) },
                    label = { Text(animation.label) },
                    leadingIcon = { Icon(animation.icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }
        }
    }
}

private val CoverAnimation.icon: ImageVector
    get() = when (this) {
        CoverAnimation.DANCE -> Icons.Rounded.MusicNote
        CoverAnimation.READ -> Icons.Rounded.AutoStories
    }

/**
 * Sortierung der Musik in der Kategorie — so sehen die Kinder sie auch auf dem Startbildschirm.
 * Alphabetisch und nach Datum lassen sich auf- oder absteigend sortieren.
 */
@Composable
private fun ItemSortSelector(
    selected: ItemSort,
    descending: Boolean,
    onSelect: (ItemSort, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Sortierung",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ItemSort.entries.forEach { sort ->
                FilterChip(
                    selected = sort == selected,
                    onClick = { onSelect(sort, descending) },
                    label = { Text(sort.label) }
                )
            }
            if (selected != ItemSort.MANUAL) {
                AssistChip(
                    onClick = { onSelect(selected, !descending) },
                    label = { Text(if (descending) selected.descendingLabel else selected.ascendingLabel) },
                    leadingIcon = {
                        Icon(
                            if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
        if (selected == ItemSort.MANUAL) {
            Text(
                "Am Griff ziehen oder mit den Pfeilen verschieben — langes Drücken auf einen Pfeil " +
                    "setzt den Eintrag ganz nach oben bzw. unten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

val PlayOrder.icon: ImageVector
    get() = when (this) {
        PlayOrder.ORDERED -> InOrderIcon
        PlayOrder.SHUFFLE -> ShuffleIcon
        PlayOrder.CHILD_CHOICE -> Icons.Rounded.ChildCare
    }

private const val CATEGORY_TARGET = "category"
private const val ITEM_TARGET = "item"

/** Bild mit kleinem Stift-Symbol: zeigt, dass ein Tipp das Bild ändert. */
@Composable
private fun EditableImage(onClick: () -> Unit, badgeSize: Dp = 24.dp, image: @Composable () -> Unit) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        image()
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(badgeSize)
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Bild ändern", modifier = Modifier.padding(4.dp))
        }
    }
}

/**
 * Bild wählen — für Kategorien und Musik-Einträge: ein eigenes Foto vom Tablet,
 * eins der Icons oder Tiere, oder zurück zum Standard ([defaultLabel]).
 */
@Composable
private fun ImageChoiceDialog(
    title: String,
    current: CustomImage,
    defaultLabel: String,
    preview: @Composable () -> Unit,
    onSelect: (CustomImage) -> Unit,
    onUploadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                fullWidthItem {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        preview()
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Button(onClick = onUploadClick) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Eigenes Bild")
                            }
                            TextButton(onClick = { onSelect(CustomImage.Default) }, enabled = current != CustomImage.Default) {
                                Text(defaultLabel)
                            }
                        }
                    }
                }

                fullWidthItem { DialogSectionTitle("Bilder") }
                items(CategoryIcon.entries) { icon ->
                    val image = CustomImage.Scene(icon)
                    SelectableImage(selected = current == image, onClick = { onSelect(image) }) {
                        CustomImageView(image)
                    }
                }

                fullWidthItem { DialogSectionTitle("Icons") }
                // Einhorn und Pikachu stehen schon unter „Tiere“
                items(SpeakerIcon.entries.filterNot { it.multicolor }) { icon ->
                    val image = CustomImage.Icon(icon)
                    SelectableImage(selected = current == image, onClick = { onSelect(image) }) {
                        CustomImageView(image)
                    }
                }

                fullWidthItem { DialogSectionTitle("Tiere") }
                items(ProfileIcon.entries) { icon ->
                    val image = CustomImage.Animal(icon)
                    SelectableImage(selected = current == image, onClick = { onSelect(image) }) {
                        CustomImageView(image)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

private fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SelectableImage(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        content()
    }
}

// --- Unterseite: Musik aus dem Sonos-Katalog auswählen -------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCatalogScreen(
    category: CategoryWithMusic,
    /** Alle Kategorien der Musikauswahl — zeigt, wo ein Eintrag schon steckt. */
    categories: List<CategoryWithMusic>,
    state: CatalogState,
    playlistPreview: PlaylistPreview?,
    /** Wohin neue Einträge kommen — nur bei manueller Sortierung, sonst null. */
    insertAtStart: Boolean?,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onToggleEntry: (CatalogEntry) -> Unit,
    onShowPlaylist: (CatalogEntry) -> Unit,
    onDismissPlaylist: () -> Unit
) {
    var detailsFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<MusicType?>(null) }
    var onlyUnassigned by remember { mutableStateOf(false) }
    val selectedKeys = category.items.map { it.catalogKey }.toSet()
    val assignments = remember(categories) { categoryNamesByKey(categories) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Musik hinzufügen")
                        Text(
                            listOfNotNull(
                                "zu „${category.category.name}“",
                                "${category.items.size} ausgewählt",
                                insertAtStart?.let { if (it) "neue an den Anfang" else "neue ans Ende" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Fertig")
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = state !is CatalogState.Loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Neu laden")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is CatalogState.Loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) { CircularProgressIndicator() }

                is CatalogState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onReload) { Text("Nochmal versuchen") }
                }

                is CatalogState.Loaded -> CatalogList(
                    entries = state.entries,
                    query = query,
                    onQueryChange = { query = it },
                    typeFilter = typeFilter,
                    onTypeFilterChange = { typeFilter = it },
                    onlyUnassigned = onlyUnassigned,
                    onOnlyUnassignedChange = { onlyUnassigned = it },
                    assignments = assignments,
                    selectedKeys = selectedKeys,
                    onToggleEntry = onToggleEntry,
                    onShowPlaylist = onShowPlaylist,
                    onShowDetails = { detailsFor = it }
                )
            }
        }
    }

    playlistPreview?.let { PlaylistPreviewDialog(it, onDismissPlaylist) }
    detailsFor?.let {
        CatalogEntryDetailsDialog(it, assignments[it.key].orEmpty(), onDismiss = { detailsFor = null })
    }
}

/** Zu jedem Katalog-Eintrag die Namen der Kategorien, in denen er steckt — in deren Reihenfolge. */
private fun categoryNamesByKey(categories: List<CategoryWithMusic>): Map<String, List<String>> =
    categories
        .flatMap { category -> category.items.map { it.catalogKey to category.category.name } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.distinct() }

@Composable
private fun CatalogList(
    entries: List<CatalogEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: MusicType?,
    onTypeFilterChange: (MusicType?) -> Unit,
    onlyUnassigned: Boolean,
    onOnlyUnassignedChange: (Boolean) -> Unit,
    assignments: Map<String, List<String>>,
    selectedKeys: Set<String>,
    onToggleEntry: (CatalogEntry) -> Unit,
    onShowPlaylist: (CatalogEntry) -> Unit,
    onShowDetails: (CatalogEntry) -> Unit
) {
    // Was bei „Ohne Kategorie" gerade angetippt wurde, bleibt stehen — sonst verschwände
    // es sofort und ein versehentlicher Tipp ließe sich nicht mehr zurücknehmen
    var touchedKeys by remember { mutableStateOf(emptySet<String>()) }
    val availableTypes = MusicType.entries.filter { type -> entries.any { it.type == type } }
    val unassignedCount = entries.count { it.key !in assignments }
    val search = query.trim()
    val visible = entries.filter { entry ->
        (typeFilter == null || entry.type == typeFilter) &&
            (!onlyUnassigned || entry.key !in assignments || entry.key in touchedKeys) &&
            (search.isEmpty() || (listOfNotNull(entry.name, entry.description) + entry.artists)
                .any { it.contains(search, ignoreCase = true) })
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                "Hier stehen deine Sonos-Favoriten und Sonos-Playlisten. Fehlt etwas? Füge es in der " +
                    "Sonos-App zu den Favoriten hinzu (z. B. einen einzelnen Song, ein Album oder eine " +
                    "Spotify-Playlist) und lade die Liste oben rechts neu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Suchen") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Suche leeren")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (entries.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = onlyUnassigned,
                        onClick = {
                            touchedKeys = emptySet()
                            onOnlyUnassignedChange(!onlyUnassigned)
                        },
                        label = { Text("Ohne Kategorie ($unassignedCount)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    if (availableTypes.size > 1) {
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { onTypeFilterChange(null) },
                            label = { Text("Alle") }
                        )
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = typeFilter == type,
                                onClick = { onTypeFilterChange(if (typeFilter == type) null else type) },
                                label = { Text(type.pluralLabel) },
                                leadingIcon = {
                                    Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "Keine Sonos-Favoriten oder -Playlisten gefunden.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    if (onlyUnassigned && unassignedCount == 0) {
                        "Alles steckt schon in einer Kategorie."
                    } else {
                        "Nichts gefunden."
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(visible, key = { it.key }) { entry ->
            val selected = entry.key in selectedKeys
            // Passt die Suche auf einen Künstler, steht er vorne — auch wenn er sonst unter „u. a." fiele.
            // Nennt Sonos den Künstler schon als Name oder Beschreibung, nicht doppelt zeigen.
            val artists = if (search.isEmpty()) {
                entry.artists
            } else {
                entry.artists.sortedByDescending { it.contains(search, ignoreCase = true) }
            }
            val artist = summarizeArtists(artists)?.takeUnless { artist ->
                listOfNotNull(entry.name, entry.description).any { it.trim().equals(artist, ignoreCase = true) }
            }
            ListItem(
                leadingContent = { MusicCover(entry.imageUrl, entry.type, size = 56.dp) },
                headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(
                            listOfNotNull(entry.type.label, artist, entry.description, entry.source.label)
                                .joinToString(" · "),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        CategoryAssignment(assignments[entry.key].orEmpty())
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Zeigt, was Sonos zu dem Eintrag liefert (Fehlersuche bei Covern)
                        IconButton(onClick = { onShowDetails(entry) }) {
                            Icon(Icons.Rounded.Info, contentDescription = "Details")
                        }
                        if (entry.source == MusicSource.PLAYLIST) {
                            IconButton(onClick = { onShowPlaylist(entry) }) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Titel ansehen")
                            }
                        }
                        Icon(
                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                            contentDescription = if (selected) "Ausgewählt" else "Hinzufügen",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                modifier = Modifier.clickable {
                    touchedKeys = touchedKeys + entry.key
                    onToggleEntry(entry)
                }
            )
        }
    }
}

/** In welchen Kategorien ein Katalog-Eintrag steckt — oder ein auffälliges Schild, wenn in keiner. */
@Composable
private fun CategoryAssignment(categoryNames: List<String>) {
    if (categoryNames.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Rounded.NewReleases, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(NOT_ASSIGNED, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        Text(
            "In: ${categoryNames.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private const val NOT_ASSIGNED = "Noch in keiner Kategorie"

/** Was Sonos zu einem Katalog-Eintrag liefert — Künstler, Cover-URL, ob sie lädt, Rohdaten. */
@Composable
private fun CatalogEntryDetailsDialog(entry: CatalogEntry, categoryNames: List<String>, onDismiss: () -> Unit) {
    val candidates = imageUrlCandidates(entry.imageUrl)
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailLine("Künstler", entry.artists.joinToString(", ").ifEmpty { "— (Sonos nennt keinen)" })
                DetailLine("Kategorien", categoryNames.joinToString(", ").ifEmpty { NOT_ASSIGNED })
                DetailLine("Quelle", entry.source.label)
                DetailLine("Sonos-Id", entry.sonosId)
                DetailLine("Art", entry.type.label)
                DetailLine("Cover von", entry.coverOrigin ?: "—")
                if (candidates.isEmpty()) DetailLine("Cover-URL", "— (Sonos liefert keins)")
                // Jede bekannte Adresse einzeln laden, damit man sieht, welche klappt
                candidates.forEachIndexed { index, url -> CoverCandidate(index + 1, url) }
                entry.rawData?.let { DetailLine("Daten von Sonos", it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(describeForSupport(entry)))
                copied = true
            }) {
                Text(if (copied) "Kopiert" else "Kopieren")
            }
        }
    )
}

/** Alle Angaben als Text — zum Einfügen in eine Nachricht. */
private fun describeForSupport(entry: CatalogEntry): String = buildString {
    appendLine("Name: ${entry.name}")
    appendLine("Künstler: ${entry.artists.joinToString(", ").ifEmpty { "—" }}")
    appendLine("Quelle: ${entry.source.label}")
    appendLine("Sonos-Id: ${entry.sonosId}")
    appendLine("Art: ${entry.type.label}")
    appendLine("Cover von: ${entry.coverOrigin ?: "—"}")
    imageUrlCandidates(entry.imageUrl).forEachIndexed { index, url -> appendLine("Cover-URL ${index + 1}: $url") }
    entry.rawData?.let { appendLine("Daten von Sonos: $it") }
}

@Composable
private fun CoverCandidate(number: Int, url: String) {
    var result by remember(url) { mutableStateOf("lädt …") }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
        AsyncImage(
            model = if (url.startsWith("/")) java.io.File(url) else url,
            contentDescription = null,
            onSuccess = { result = "geladen" },
            onError = { result = "Fehler: ${it.result.throwable.message ?: it.result.throwable.javaClass.simpleName}" },
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            DetailLine("Cover-URL $number", url)
            DetailLine("Laden", result)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlaylistPreviewDialog(preview: PlaylistPreview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
        title = { Text(preview.entry.name) },
        text = {
            when {
                preview.error != null -> Text(preview.error)
                preview.tracks == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) { CircularProgressIndicator() }
                preview.tracks.isEmpty() -> Text("Die Playlist ist leer.")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(preview.tracks) { index, track ->
                        Row(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "${index + 1}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp)
                            )
                            Column {
                                Text(track.name ?: "Unbekannter Titel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                listOfNotNull(track.artist, track.album).takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        it.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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

// --- Dialoge ----------------------------------------------------------------

@Composable
fun NameDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    val canConfirm = value.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(value) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = canConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Löschen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun ProfileIconPickerDialog(
    profileName: String,
    selected: ProfileIcon,
    onSelect: (ProfileIcon) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon für $profileName") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 460.dp)
            ) {
                items(ProfileIcon.entries) { icon ->
                    Card(
                        onClick = { onSelect(icon) },
                        shape = RoundedCornerShape(16.dp),
                        border = if (icon == selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            ProfileIconBadge(icon, size = 56.dp)
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
