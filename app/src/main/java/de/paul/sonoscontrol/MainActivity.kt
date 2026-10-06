package de.paul.sonoscontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private val apiClient by lazy { SonosApiClient.shared(applicationContext) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            apiClient.tokenStore,
            apiClient,
            SettingsRepository(
                AppDatabase.getInstance(applicationContext),
                CustomImageStore(applicationContext)
            ),
            LocalSonosClient(applicationContext)
        )
    }

    private val syncViewModel: SyncViewModel by viewModels {
        val syncRepository = SyncRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext),
            CustomImageStore(applicationContext)
        )
        SyncViewModelFactory(
            syncRepository,
            LocalTransfer(applicationContext),
            CloudSync(syncRepository, CloudSyncClient(apiClient), apiClient)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inhalt bis unter die Statusleiste zeichnen, damit der Cover-Verlauf den ganzen Bildschirm füllt
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            SoundBuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SoundBuddyApp(
                        viewModel = viewModel,
                        syncViewModel = syncViewModel,
                        onLoginClick = { viewModel.authManager.startLogin(this) }
                    )
                }
            }
        }
    }

    // Wiedergabe nur abfragen, solange die App sichtbar ist
    override fun onStart() {
        super.onStart()
        viewModel.onForegroundChanged(true)
        syncViewModel.onForegroundChanged(true)
    }

    override fun onStop() {
        viewModel.onForegroundChanged(false)
        // Im Hintergrund nicht weiter im WLAN freigeben oder suchen, nicht in der Cloud nachsehen
        syncViewModel.stopNetwork()
        syncViewModel.onForegroundChanged(false)
        super.onStop()
    }

    // launchMode="singleTask" -> der Redirect landet hier, nicht in onCreate() einer neuen Instanz
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == SonosConfig.APP_CALLBACK_SCHEME && uri.host == SonosConfig.APP_CALLBACK_HOST) {
            viewModel.handleAuthCallback(uri)
        }
    }
}

@Composable
fun SoundBuddyApp(viewModel: MainViewModel, syncViewModel: SyncViewModel, onLoginClick: () -> Unit) {
    val settingsOpen = viewModel.screen != Screen.Home
    LaunchedEffect(settingsOpen) { syncViewModel.onSettingsOpenChanged(settingsOpen) }

    when (viewModel.screen) {
        Screen.Home -> HomeScreen(
            state = viewModel.uiState,
            speakers = viewModel.selectableSpeakers,
            selectedSpeaker = viewModel.selectedSpeaker,
            nowPlaying = viewModel.nowPlaying,
            maxVolume = viewModel.selectedMaxVolume,
            playbackError = viewModel.playbackError,
            visiblePlaybackError = viewModel.visiblePlaybackError,
            isInForeground = viewModel.isInForeground,
            coverAnimation = viewModel.coverAnimation,
            onDismissPlaybackError = viewModel::dismissPlaybackError,
            onSelectSpeaker = viewModel::selectSpeaker,
            profiles = viewModel.selectableProfiles,
            selectedProfile = viewModel.selectedProfile,
            startingMusic = viewModel.startingMusic,
            onSelectProfile = viewModel::selectProfile,
            onPlayMusic = viewModel::playMusic,
            controls = remember(viewModel) {
                PlaybackControls(
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipToPrevious = viewModel::skipToPrevious,
                    onSkipToNext = viewModel::skipToNext,
                    onVolumeChange = viewModel::setVolume
                )
            },
            onOpenSettings = viewModel::openSettings,
            onLoginClick = onLoginClick,
            onRetryClick = viewModel::loadSpeakers
        )

        Screen.Settings -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            SettingsScreen(
                speakers = viewModel.speakerConfigs,
                settings = viewModel.settings,
                isLoggedIn = viewModel.uiState !is UiState.LoggedOut,
                onBack = viewModel::navigateBack,
                onOpenSpeakers = viewModel::openSpeakers,
                categories = viewModel.categories,
                onOpenLibrary = viewModel::openLibrary,
                profiles = viewModel.profiles,
                onCreateProfile = viewModel::createProfile,
                onOpenProfile = viewModel::openProfile,
                onProfileEnabledChange = viewModel::setProfileEnabled,
                onSavePassword = viewModel::savePassword,
                onRemovePassword = viewModel::removePassword,
                onPasswordRequiredChange = viewModel::setPasswordRequired,
                onOpenSync = viewModel::openSync,
                syncNotice = syncViewModel.cloudNotice,
                onLogout = viewModel::logout
            )
        }

        Screen.Speakers -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            SpeakerSettingsScreen(
                speakers = viewModel.speakerConfigs,
                availablePlayerIds = viewModel.availablePlayerIds,
                isLoggedIn = viewModel.uiState !is UiState.LoggedOut,
                isRefreshingSpeakers = viewModel.isRefreshingSpeakers,
                speakerRefreshMessage = viewModel.speakerRefreshMessage,
                onBack = viewModel::navigateBack,
                onRefreshSpeakers = viewModel::refreshSpeakers,
                onDeleteSpeaker = viewModel::deleteSpeaker,
                onSpeakerEnabledChange = viewModel::setSpeakerEnabled,
                onSpeakerIconChange = viewModel::setSpeakerIcon,
                onSpeakerMaxVolumeChange = viewModel::setSpeakerMaxVolume
            )
        }

        Screen.ProfileEditor -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            viewModel.editingProfile?.let { profile ->
                ProfileEditorScreen(
                    profile = profile,
                    categories = viewModel.categories,
                    actions = remember(viewModel) {
                        ProfileEditorActions(
                            onBack = viewModel::navigateBack,
                            onNameChange = viewModel::setProfileName,
                            onIconChange = viewModel::setProfileIcon,
                            onEnabledChange = viewModel::setProfileEnabled,
                            onDeleteProfile = viewModel::deleteProfile,
                            onCategoryVisibleChange = viewModel::setCategoryVisible,
                            onOpenLibrary = viewModel::openLibrary
                        )
                    }
                )
            }
        }

        Screen.Sync -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            SyncScreen(viewModel = syncViewModel, onBack = viewModel::navigateBack)
        }

        Screen.MusicLibrary -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            MusicLibraryScreen(
                categories = viewModel.categories,
                profiles = viewModel.profiles.map { it.profile },
                imageError = viewModel.imageImportError,
                actions = rememberMusicLibraryActions(viewModel)
            )
        }

        Screen.CategoryEditor -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            viewModel.editingCategory?.let { category ->
                CategoryEditorScreen(
                    category = category,
                    categories = viewModel.categories,
                    profiles = viewModel.profiles.map { it.profile },
                    addMusicAtStart = viewModel.settings.addMusicAtStart,
                    imageError = viewModel.imageImportError,
                    actions = rememberMusicLibraryActions(viewModel)
                )
            }
        }

        Screen.MusicCatalog -> {
            BackHandler(onBack = viewModel::navigateBack)
            StatusBarIcons(light = !isSystemInDarkTheme())
            viewModel.catalogCategory?.let { category ->
                MusicCatalogScreen(
                    category = category,
                    state = viewModel.catalogState,
                    playlistPreview = viewModel.playlistPreview,
                    insertAtStart = viewModel.settings.addMusicAtStart
                        .takeIf { category.category.itemSortMode == ItemSort.MANUAL },
                    onBack = viewModel::navigateBack,
                    onReload = viewModel::loadCatalog,
                    onToggleEntry = viewModel::toggleCatalogEntry,
                    onShowPlaylist = viewModel::showPlaylistPreview,
                    onDismissPlaylist = viewModel::dismissPlaylistPreview
                )
            }
        }
    }

    if (viewModel.showPasswordPrompt) {
        PasswordPromptDialog(
            wrongPassword = viewModel.passwordWrong,
            onSubmit = viewModel::submitPassword,
            onDismiss = viewModel::dismissPasswordPrompt
        )
    }
}

@Composable
private fun rememberMusicLibraryActions(viewModel: MainViewModel): MusicLibraryActions = remember(viewModel) {
    MusicLibraryActions(
        onBack = viewModel::navigateBack,
        onCreateCategory = viewModel::createCategory,
        onOpenCategory = viewModel::openCategory,
        onRenameCategory = viewModel::renameCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onMoveCategory = viewModel::moveCategory,
        onReorderCategories = viewModel::reorderCategories,
        onCategoryVisibleChange = viewModel::setCategoryVisible,
        onAddMusic = viewModel::openCatalog,
        onAddMusicAtStartChange = viewModel::setAddMusicAtStart,
        onRemoveMusicItem = viewModel::removeMusicItem,
        onCategoryImageChange = viewModel::setCategoryImage,
        onImportCategoryImage = viewModel::importCategoryImage,
        onItemImageChange = viewModel::setMusicItemImage,
        onImportItemImage = viewModel::importMusicItemImage,
        onPlayOrderChange = viewModel::setCategoryPlayOrder,
        onItemSortChange = viewModel::setCategoryItemSort,
        onCoverAnimationChange = viewModel::setCategoryCoverAnimation,
        onMoveMusicItem = viewModel::moveMusicItem,
        onReorderMusicItems = viewModel::reorderMusicItems,
        onDismissImageError = viewModel::dismissImageImportError
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A4CE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFFFF8A3D),
    secondaryContainer = Color(0xFFFFE0CC),
    onSecondaryContainer = Color(0xFF5A2600),
    tertiary = Color(0xFF00A88F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF34138F),
    primaryContainer = Color(0xFF4C32B5),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB68A),
    secondaryContainer = Color(0xFF7A3A10),
    onSecondaryContainer = Color(0xFFFFE0CC),
    tertiary = Color(0xFF5FDBC2)
)

@Composable
fun SoundBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
