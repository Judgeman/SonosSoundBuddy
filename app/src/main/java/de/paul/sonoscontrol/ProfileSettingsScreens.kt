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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// --- Abschnitt „Kinder-Profile" auf der Settings-Hauptseite -----------------

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
            description = "Jedes Profil hat ein eigenes Icon und eine eigene Musikauswahl. Aktive Profile " +
                "sind auf diesem Tablet auswählbar — ist nur eins aktiv, ist es immer gewählt."
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
            supportingContent = { Text(describeMusic(entry)) },
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

private fun describeMusic(entry: ProfileWithMusic): String {
    val categories = entry.categories.size
    val items = entry.itemCount
    val categoryText = if (categories == 1) "1 Kategorie" else "$categories Kategorien"
    val itemText = if (items == 1) "1 Eintrag" else "$items Einträge"
    return "$categoryText · $itemText"
}

// --- Unterseite: ein Profil bearbeiten ------------------------------------

/** Callbacks der Profil-Seite, gebündelt damit die Signatur übersichtlich bleibt. */
class ProfileEditorActions(
    val onBack: () -> Unit,
    val onNameChange: (Long, String) -> Unit,
    val onIconChange: (Long, ProfileIcon) -> Unit,
    val onEnabledChange: (Long, Boolean) -> Unit,
    val onDeleteProfile: (Long) -> Unit,
    val onCreateCategory: (Long, String) -> Unit,
    val onRenameCategory: (Long, String) -> Unit,
    val onDeleteCategory: (Long) -> Unit,
    val onMoveCategory: (Long, Int) -> Unit,
    val onAddMusic: (Long) -> Unit,
    val onRemoveMusicItem: (Long) -> Unit,
    val onCategoryImageChange: (Long, CategoryImage) -> Unit,
    val onRandomCategoryCover: (Long) -> Unit,
    val onImportCategoryImage: (Long, Uri) -> Unit,
    val onPlayOrderChange: (Long, PlayOrder) -> Unit,
    val onDismissImageError: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(profile: ProfileWithMusic, imageError: String?, actions: ProfileEditorActions) {
    val id = profile.profile.id
    var imageDialogFor by remember { mutableStateOf<Long?>(null) }
    // Kategorie, für die gerade die Android-Fotoauswahl offen ist
    var importFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val categoryId = importFor
        if (uri != null && categoryId != null) actions.onImportCategoryImage(categoryId, uri)
        importFor = null
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(imageError) {
        if (imageError != null) {
            snackbarHostState.showSnackbar(imageError)
            actions.onDismissImageError()
        }
    }
    var showIconPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showDeleteProfile by remember { mutableStateOf(false) }
    var renameCategory by remember { mutableStateOf<MusicCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryWithMusic?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    description = "Die Kategorien erscheinen auf dem Startbildschirm unter dem Play-Knopf. " +
                        "Leere Kategorien sehen die Kinder nicht."
                )
            }

            if (profile.categories.isEmpty()) {
                item {
                    Text(
                        "Noch keine Kategorien. Lege z. B. „Lieblingslieder“ oder „Hörspiele“ an.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(profile.categories, key = { _, it -> "category-${it.category.id}" }) { index, category ->
                CategoryCard(
                    category = category,
                    canMoveUp = index > 0,
                    canMoveDown = index < profile.categories.lastIndex,
                    onMove = { actions.onMoveCategory(category.category.id, it) },
                    onRename = { renameCategory = category.category },
                    onDelete = { deleteCategory = category },
                    onAddMusic = { actions.onAddMusic(category.category.id) },
                    onRemoveItem = actions.onRemoveMusicItem,
                    onImageClick = { imageDialogFor = category.category.id },
                    onPlayOrderChange = { actions.onPlayOrderChange(category.category.id, it) }
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

    imageDialogFor?.let { categoryId ->
        profile.categories.firstOrNull { it.category.id == categoryId }?.let { category ->
            CategoryImageDialog(
                category = category,
                onSelect = { actions.onCategoryImageChange(categoryId, it) },
                onRandomCover = { actions.onRandomCategoryCover(categoryId) },
                onUploadClick = {
                    importFor = categoryId
                    imageDialogFor = null
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDismiss = { imageDialogFor = null }
            )
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
    if (showNewCategory) {
        NameDialog(
            title = "Neue Kategorie",
            label = "Name der Kategorie",
            initialValue = "",
            confirmText = "Anlegen",
            onConfirm = {
                actions.onCreateCategory(id, it)
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false }
        )
    }
    renameCategory?.let { category ->
        NameDialog(
            title = "Kategorie umbenennen",
            label = "Name der Kategorie",
            initialValue = category.name,
            confirmText = "Speichern",
            onConfirm = {
                actions.onRenameCategory(category.id, it)
                renameCategory = null
            },
            onDismiss = { renameCategory = null }
        )
    }
    deleteCategory?.let { category ->
        ConfirmDeleteDialog(
            title = "„${category.category.name}“ löschen?",
            text = "Die Kategorie und ihre ${category.items.size} Einträge werden aus der Auswahl entfernt. " +
                "Bei Sonos selbst wird nichts gelöscht.",
            onConfirm = {
                actions.onDeleteCategory(category.category.id)
                deleteCategory = null
            },
            onDismiss = { deleteCategory = null }
        )
    }
    if (showDeleteProfile) {
        ConfirmDeleteDialog(
            title = "Profil „${profile.profile.name}“ löschen?",
            text = "Das Profil und seine komplette Musikauswahl werden gelöscht.",
            onConfirm = {
                showDeleteProfile = false
                actions.onDeleteProfile(id)
            },
            onDismiss = { showDeleteProfile = false }
        )
    }
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

@Composable
private fun CategoryCard(
    category: CategoryWithMusic,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddMusic: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onImageClick: () -> Unit,
    onPlayOrderChange: (PlayOrder) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp)
        ) {
            Box(modifier = Modifier.clickable(onClick = onImageClick)) {
                CategoryImageView(category, size = 72.dp)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Bild ändern",
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (category.items.size == 1) "1 Eintrag" else "${category.items.size} Einträge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Kategorie bearbeiten")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text("Nach oben") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove(-1) }
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text("Nach unten") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove(1) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }

        PlayOrderSelector(
            selected = category.category.playOrderMode,
            onSelect = onPlayOrderChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        category.items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp)
            ) {
                MusicCover(item.imageUrl, item.musicType, size = 48.dp)
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
                }
                IconButton(onClick = { onRemoveItem(item.id) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "${item.name} entfernen")
                }
            }
        }

        TextButton(
            onClick = onAddMusic,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 4.dp)
        ) {
            Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Musik hinzufügen")
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
                        Icon(order.icon, contentDescription = null, modifier = Modifier.size(18.dp))
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

val PlayOrder.icon: ImageVector
    get() = when (this) {
        PlayOrder.ORDERED -> Icons.Rounded.FormatListNumbered
        PlayOrder.SHUFFLE -> Icons.Rounded.Shuffle
        PlayOrder.CHILD_CHOICE -> Icons.Rounded.ChildCare
    }

/**
 * Bild der Kategorie wählen: ein Cover aus der Kategorie (auch per Zufall),
 * ein eigenes Foto vom Tablet oder eins der Icons.
 */
@Composable
private fun CategoryImageDialog(
    category: CategoryWithMusic,
    onSelect: (CategoryImage) -> Unit,
    onRandomCover: () -> Unit,
    onUploadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val current = category.category.image
    val covers = category.itemCovers

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bild für „${category.category.name}“") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                fullWidthItem {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryImageView(category, size = 96.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Button(onClick = onRandomCover, enabled = covers.isNotEmpty()) {
                                Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Zufälliges Cover")
                            }
                            OutlinedButton(onClick = onUploadClick) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Eigenes Bild")
                            }
                        }
                    }
                }

                fullWidthItem { DialogSectionTitle("Cover aus der Kategorie") }
                if (covers.isEmpty()) {
                    fullWidthItem {
                        Text(
                            "Noch keine Cover — füge zuerst Musik hinzu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(covers) { url ->
                    SelectableImage(selected = current == CategoryImage.Cover(url), onClick = { onSelect(CategoryImage.Cover(url)) }) {
                        CategoryImageView(CategoryImage.Cover(url), fallbackCover = null)
                    }
                }

                fullWidthItem { DialogSectionTitle("Icons") }
                items(SpeakerIcon.entries) { icon ->
                    SelectableImage(selected = current == CategoryImage.Icon(icon), onClick = { onSelect(CategoryImage.Icon(icon)) }) {
                        CategoryImageView(CategoryImage.Icon(icon), fallbackCover = null)
                    }
                }

                fullWidthItem { DialogSectionTitle("Tiere") }
                items(ProfileIcon.entries) { icon ->
                    SelectableImage(selected = current == CategoryImage.Animal(icon), onClick = { onSelect(CategoryImage.Animal(icon)) }) {
                        CategoryImageView(CategoryImage.Animal(icon), fallbackCover = null)
                    }
                }

                fullWidthItem {
                    TextButton(onClick = { onSelect(CategoryImage.Auto) }, enabled = current != CategoryImage.Auto) {
                        Text("Automatisch (erstes Cover)")
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
    state: CatalogState,
    playlistPreview: PlaylistPreview?,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onToggleEntry: (CatalogEntry) -> Unit,
    onShowPlaylist: (CatalogEntry) -> Unit,
    onDismissPlaylist: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<MusicType?>(null) }
    val selectedKeys = category.items.map { it.catalogKey }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Musik hinzufügen")
                        Text(
                            "zu „${category.category.name}“ · ${category.items.size} ausgewählt",
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
                    selectedKeys = selectedKeys,
                    onToggleEntry = onToggleEntry,
                    onShowPlaylist = onShowPlaylist
                )
            }
        }
    }

    playlistPreview?.let { PlaylistPreviewDialog(it, onDismissPlaylist) }
}

@Composable
private fun CatalogList(
    entries: List<CatalogEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: MusicType?,
    onTypeFilterChange: (MusicType?) -> Unit,
    selectedKeys: Set<String>,
    onToggleEntry: (CatalogEntry) -> Unit,
    onShowPlaylist: (CatalogEntry) -> Unit
) {
    val availableTypes = MusicType.entries.filter { type -> entries.any { it.type == type } }
    val visible = entries.filter { entry ->
        (typeFilter == null || entry.type == typeFilter) &&
            (query.isBlank() || entry.name.contains(query.trim(), ignoreCase = true) ||
                entry.description?.contains(query.trim(), ignoreCase = true) == true)
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
            if (availableTypes.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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
                            leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
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
                Text("Nichts gefunden.", modifier = Modifier.padding(16.dp))
            }
        }

        items(visible, key = { it.key }) { entry ->
            val selected = entry.key in selectedKeys
            ListItem(
                leadingContent = { MusicCover(entry.imageUrl, entry.type, size = 56.dp) },
                headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOfNotNull(entry.type.label, entry.description, entry.source.label).joinToString(" · "),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.clickable { onToggleEntry(entry) }
            )
        }
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
