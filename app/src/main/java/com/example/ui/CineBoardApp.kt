package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.R
import com.example.data.addons.PlaybackTarget
import com.example.data.addons.StremioAddonRepository
import com.example.data.firestore.LibraryRepository
import com.example.data.firestore.SeriesProgress
import com.example.data.firestore.WatchStatus
import com.example.data.player.LocalPlaybackStore
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CinemetaRepository
import com.example.ui.auth.GoogleSignInPromptDialog
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.screens.AddonsBottomSheet
import com.example.ui.screens.BoardScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.screens.SourcePickerBottomSheet
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.CineBoardTheme
import com.example.ui.viewmodel.CalendarSections
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.LibraryMessageType
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.PlayerSourceViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private object Routes {
    const val BOARD = "board"
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val CALENDAR = "calendar"
    const val DETAIL = "detail/{type}/{id}"
    fun detail(type: String, id: String) = "detail/$type/$id"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun CineBoardApp() {
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SettingsViewModel(UserPreferencesRepository(app.applicationContext))
            }
        }
    )

    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val strings = if (prefs.language == AppLanguage.AR) ArabicStrings else EnglishStrings
    val layoutDirection = if (prefs.language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        CineBoardTheme(
            themeMode = prefs.themeMode,
            dynamicColor = prefs.dynamicColor,
            language = prefs.language
        ) {
            MainContent(
                user = currentUser,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                settingsViewModel = settingsViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    user: FirebaseUser?,
    onAuthSuccess: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()

    val cinemetaRepository = remember { CinemetaRepository() }

    val catalogViewModel: CatalogViewModel = viewModel(
        key = "catalog_main",
        factory = viewModelFactory {
            initializer { CatalogViewModel(cinemetaRepository) }
        }
    )

    val playerSourceViewModel: PlayerSourceViewModel = viewModel(
        key = "player_sources",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                PlayerSourceViewModel(
                    addonRepository = StremioAddonRepository(app.applicationContext),
                    playbackStore = LocalPlaybackStore(app.applicationContext)
                )
            }
        }
    )

    val libraryViewModel: LibraryViewModel? = if (user != null) {
        key(user.uid) {
            viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        LibraryViewModel(
                            libraryRepository = LibraryRepository(db),
                            cinemetaRepository = cinemetaRepository,
                            currentUserId = user.uid
                        )
                    }
                }
            )
        }
    } else {
        null
    }

    val boardState by catalogViewModel.boardState.collectAsStateWithLifecycle()
    val discoverFilter by catalogViewModel.discoverFilter.collectAsStateWithLifecycle()
    val discoverState by catalogViewModel.discoverState.collectAsStateWithLifecycle()
    val detailsStates by catalogViewModel.detailsStates.collectAsStateWithLifecycle()

    val libraryState = if (libraryViewModel != null) {
        libraryViewModel.libraryState.collectAsStateWithLifecycle().value
    } else {
        UiState.Success(emptyList())
    }
    val progressMap: Map<String, SeriesProgress> = if (libraryViewModel != null) {
        libraryViewModel.progressMap.collectAsStateWithLifecycle().value
    } else {
        emptyMap()
    }
    val calendarState = if (libraryViewModel != null) {
        libraryViewModel.calendarState.collectAsStateWithLifecycle().value
    } else {
        UiState.Success(CalendarSections())
    }

    val libraryEntries = (libraryState as? UiState.Success)?.data.orEmpty()
    val libraryMap = remember(libraryEntries) { libraryEntries.associateBy { it.id } }
    val continueWatching = remember(libraryEntries) {
        libraryEntries.filter { it.watchStatus == WatchStatus.WATCHING }
    }

    LaunchedEffect(libraryViewModel, strings) {
        val activeVm = libraryViewModel ?: return@LaunchedEffect
        activeVm.events.collect { event ->
            val msg = when (event.type) {
                LibraryMessageType.ADDED -> strings.addedToLibraryMsg
                LibraryMessageType.STATUS_UPDATED -> strings.updatedStatusMsg
                LibraryMessageType.REMOVED -> strings.removedFromLibraryMsg
                LibraryMessageType.EPISODE_UPDATED -> strings.episodeUpdatedMsg
                LibraryMessageType.SEASON_UPDATED -> strings.seasonUpdatedMsg
                LibraryMessageType.ERROR -> event.detail ?: strings.networkError
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = remember(strings) {
        listOf(
            TopLevelTab(Routes.BOARD, strings.navBoard, Icons.Rounded.Home, Icons.Outlined.Home, "nav_board"),
            TopLevelTab(Routes.DISCOVER, strings.navDiscover, Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_discover"),
            TopLevelTab(Routes.LIBRARY, strings.navLibrary, Icons.Rounded.Bookmark, Icons.Outlined.BookmarkBorder, "nav_library"),
            TopLevelTab(Routes.CALENDAR, strings.navCalendar, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar")
        )
    }
    val isTopLevelRoute = tabs.any { it.route == currentRoute }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAddonsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    val isGuest = user == null

    val installedAddons by playerSourceViewModel.installedAddons.collectAsStateWithLifecycle()
    val addonInstallState by playerSourceViewModel.addonInstallState.collectAsStateWithLifecycle()
    val bookmarks by playerSourceViewModel.bookmarks.collectAsStateWithLifecycle()
    val sourcePickerTarget by playerSourceViewModel.sourcePickerTarget.collectAsStateWithLifecycle()
    val streamFetchState by playerSourceViewModel.streamFetchState.collectAsStateWithLifecycle()
    val activeSession by playerSourceViewModel.activeSession.collectAsStateWithLifecycle()

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && isTopLevelRoute) {
                NavigationRail(
                    header = {
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.padding(top = 8.dp).testTag("rail_settings_button")
                        ) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = strings.settingsTitle)
                        }
                    }
                ) {
                    Spacer(Modifier.weight(1f))
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (isTopLevelRoute) {
                        val title = when (currentRoute) {
                            Routes.DISCOVER -> strings.navDiscover
                            Routes.LIBRARY -> strings.myLibraryTitle
                            Routes.CALENDAR -> strings.calendarTitle
                            else -> strings.appName
                        }
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                TextButton(
                                    onClick = { settingsViewModel.toggleLanguage() },
                                    modifier = Modifier.testTag("topbar_language_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = strings.languageLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.switchLanguageShort)
                                }
                            },
                            actions = {
                                FilledTonalIconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .testTag("topbar_settings_button")
                                ) {
                                    if (user?.photoUrl != null) {
                                        AsyncImage(
                                            model = user.photoUrl,
                                            contentDescription = strings.settingsTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountCircle,
                                            contentDescription = strings.settingsTitle
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && isTopLevelRoute) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.BOARD,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.BOARD) {
                        BoardScreen(
                            boardState = boardState,
                            libraryMap = libraryMap,
                            continueWatching = continueWatching,
                            isGuest = isGuest,
                            onRequestSignIn = { showSignInDialog = true },
                            onRetry = { catalogViewModel.loadBoard() },
                            onOpenDetails = { type, id ->
                                navController.navigate(Routes.detail(type, id))
                            },
                            onQuickAdd = { item ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.saveMediaItem(item, WatchStatus.WATCHLIST)
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    composable(Routes.DISCOVER) {
                        DiscoverScreen(
                            filterState = discoverFilter,
                            discoverState = discoverState,
                            libraryMap = libraryMap,
                            onTypeSelected = { catalogViewModel.setDiscoverType(it) },
                            onGenreSelected = { catalogViewModel.setDiscoverGenre(it) },
                            onSearchQueryChanged = { catalogViewModel.setSearchQuery(it) },
                            onRetry = { catalogViewModel.retryDiscover() },
                            onOpenDetails = { type, id ->
                                navController.navigate(Routes.detail(type, id))
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            isGuest = isGuest,
                            libraryState = libraryState,
                            onSignInClick = { showSignInDialog = true },
                            onOpenDetails = { type, id ->
                                navController.navigate(Routes.detail(type, id))
                            },
                            onUpdateStatus = { entry, status ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.updateEntryStatus(entry, status)
                                }
                            },
                            onRemoveEntry = { mediaId ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.removeFromLibrary(mediaId)
                                }
                            },
                            onExploreClick = { navigateToTab(Routes.DISCOVER) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    composable(Routes.CALENDAR) {
                        CalendarScreen(
                            isGuest = isGuest,
                            calendarState = calendarState,
                            onSignInClick = { showSignInDialog = true },
                            onRetry = { libraryViewModel?.refreshCalendar() },
                            onExploreClick = { navigateToTab(Routes.DISCOVER) },
                            onOpenSeries = { id ->
                                navController.navigate(Routes.detail("series", id))
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("id") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val type = entry.arguments?.getString("type") ?: "movie"
                        val id = entry.arguments?.getString("id") ?: ""
                        val detailKey = "$type:$id"

                        LaunchedEffect(detailKey) {
                            catalogViewModel.loadDetails(type, id)
                        }

                        val detailState = detailsStates[detailKey] ?: UiState.Loading

                        DetailScreen(
                            mediaType = type,
                            mediaId = id,
                            detailsState = detailState,
                            libraryEntry = libraryMap[id],
                            progress = progressMap[id],
                            bookmarks = bookmarks,
                            onBack = { navController.popBackStack() },
                            onRetry = { catalogViewModel.loadDetails(type, id, forceRefresh = true) },
                            onPlayMovie = { details ->
                                playerSourceViewModel.openSourcePicker(
                                    PlaybackTarget(
                                        playbackKey = details.id,
                                        mediaId = details.id,
                                        mediaType = "movie",
                                        streamId = details.id,
                                        title = details.name,
                                        subtitle = details.releaseInfo,
                                        poster = details.poster
                                    )
                                )
                            },
                            onPlayEpisode = { details, episode ->
                                val seasonEps = details.episodesForSeason(episode.season)
                                val idx = seasonEps.indexOfFirst { it.id == episode.id }
                                val nextEp = if (idx >= 0 && idx + 1 < seasonEps.size) {
                                    seasonEps[idx + 1]
                                } else {
                                    val nextSeason = details.seasons.firstOrNull { it > episode.season }
                                    nextSeason?.let { details.episodesForSeason(it).firstOrNull() }
                                }
                                playerSourceViewModel.openSourcePicker(
                                    PlaybackTarget(
                                        playbackKey = episode.id,
                                        mediaId = details.id,
                                        mediaType = "series",
                                        streamId = "${details.id}:${episode.season}:${episode.episode}",
                                        title = details.name,
                                        subtitle = "${strings.episodeShortFormat.format(episode.season, episode.episode)} • ${episode.title}",
                                        poster = details.poster,
                                        nextEpisodeStreamId = nextEp?.let { "${details.id}:${it.season}:${it.episode}" },
                                        nextEpisodePlaybackKey = nextEp?.id,
                                        nextEpisodeTitle = nextEp?.title,
                                        nextEpisodeSubtitle = nextEp?.let {
                                            "${strings.episodeShortFormat.format(it.season, it.episode)} • ${it.title}"
                                        }
                                    )
                                )
                            },
                            onUpdateStatus = { details, status, inLib ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.updateMediaStatus(details, status, inLib)
                                }
                            },
                            onRemoveFromLibrary = { mediaId ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.removeFromLibrary(mediaId)
                                }
                            },
                            onToggleEpisode = { details, episode ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.toggleEpisodeWatched(details, episode)
                                }
                            },
                            onMarkSeasonWatched = { details, season, mark ->
                                val vm = libraryViewModel
                                if (vm == null) {
                                    showSignInDialog = true
                                } else {
                                    vm.markSeasonWatched(details, season, mark)
                                }
                            },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            user = user,
            preferences = prefs,
            activeAddonsCount = installedAddons.count { it.enabled },
            onManageAddons = {
                showSettingsSheet = false
                showAddonsSheet = true
            },
            onLanguageChange = { settingsViewModel.setLanguage(it) },
            onThemeModeChange = { settingsViewModel.setThemeMode(it) },
            onDynamicColorChange = { settingsViewModel.setDynamicColor(it) },
            onSignIn = {
                showSettingsSheet = false
                showSignInDialog = true
            },
            onSignOut = {
                showSettingsSheet = false
                signOutUser(credentialManager = credentialManager, scope = scope)
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showAddonsSheet) {
        AddonsBottomSheet(
            addons = installedAddons,
            installState = addonInstallState,
            onInstallAddon = { playerSourceViewModel.installAddon(it) },
            onResetInstallState = { playerSourceViewModel.resetAddonInstallState() },
            onToggleAddon = { id, enabled -> playerSourceViewModel.toggleAddon(id, enabled) },
            onRemoveAddon = { id -> playerSourceViewModel.removeAddon(id) },
            onDismiss = { showAddonsSheet = false }
        )
    }

    sourcePickerTarget?.let { target ->
        SourcePickerBottomSheet(
            target = target,
            bookmark = bookmarks[target.playbackKey],
            addons = installedAddons,
            streamFetchState = streamFetchState,
            onRefreshStreams = { playerSourceViewModel.refreshStreams() },
            onManageAddons = { showAddonsSheet = true },
            onSelectAddonStream = { stream, subs ->
                playerSourceViewModel.startAddonStream(target, stream, subs)
            },
            onPlayDirectUrl = { url, subUrl, extraSubs ->
                playerSourceViewModel.startDirectUrl(target, url, subUrl, extraSubs)
            },
            onPlayLocalFile = { uri, name, subUri ->
                playerSourceViewModel.startLocalFile(target, uri, name, subUri)
            },
            onDismiss = { playerSourceViewModel.dismissSourcePicker() }
        )
    }

    activeSession?.let { session ->
        VideoPlayerScreen(
            session = session,
            initialBookmark = bookmarks[session.target.playbackKey],
            onBack = { playerSourceViewModel.closePlayer() },
            onSavePosition = { pos, dur ->
                playerSourceViewModel.savePlaybackPosition(
                    target = session.target,
                    sourceTitle = session.stream.addonName,
                    positionMs = pos,
                    durationMs = dur
                )
            },
            onNextEpisode = {
                playerSourceViewModel.switchToNextEpisode(session.target)
            },
            onMarkCompleted = {
                val vm = libraryViewModel
                if (vm == null) {
                    showSignInDialog = true
                } else {
                    val detailKey = "${session.target.mediaType}:${session.target.mediaId}"
                    val details = (detailsStates[detailKey] as? UiState.Success)?.data
                    if (details != null) {
                        if (session.target.mediaType == "movie") {
                            vm.updateMediaStatus(details, WatchStatus.COMPLETED, libraryMap.containsKey(details.id))
                        } else {
                            val ep = details.episodes.firstOrNull { it.id == session.target.playbackKey }
                            if (ep != null) {
                                vm.toggleEpisodeWatched(details, ep)
                            }
                        }
                    }
                }
            }
        )
    }

    if (showSignInDialog && user == null) {
        GoogleSignInPromptDialog(
            onAuthSuccess = {
                showSignInDialog = false
                onAuthSuccess()
            },
            onDismiss = { showSignInDialog = false }
        )
    }
}
