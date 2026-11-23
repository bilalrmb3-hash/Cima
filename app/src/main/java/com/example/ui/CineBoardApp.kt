package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.R
import com.example.data.firestore.LibraryRepository
import com.example.data.firestore.WatchStatus
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CinemetaRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.screens.BoardScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.theme.CineBoardTheme
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.LibraryMessageType
import com.example.ui.viewmodel.LibraryViewModel
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

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
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
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                    onToggleLanguage = { settingsViewModel.toggleLanguage() }
                )
            } else {
                key(user.uid) {
                    AuthenticatedMainContent(
                        user = user,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMainContent(
    user: FirebaseUser,
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
        key = "catalog_${user.uid}",
        factory = viewModelFactory {
            initializer { CatalogViewModel(cinemetaRepository) }
        }
    )

    val libraryViewModel: LibraryViewModel = viewModel(
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

    val boardState by catalogViewModel.boardState.collectAsStateWithLifecycle()
    val discoverFilter by catalogViewModel.discoverFilter.collectAsStateWithLifecycle()
    val discoverState by catalogViewModel.discoverState.collectAsStateWithLifecycle()
    val detailsStates by catalogViewModel.detailsStates.collectAsStateWithLifecycle()

    val libraryState by libraryViewModel.libraryState.collectAsStateWithLifecycle()
    val progressMap by libraryViewModel.progressMap.collectAsStateWithLifecycle()
    val calendarState by libraryViewModel.calendarState.collectAsStateWithLifecycle()

    val libraryEntries = (libraryState as? UiState.Success)?.data.orEmpty()
    val libraryMap = remember(libraryEntries) { libraryEntries.associateBy { it.id } }
    val continueWatching = remember(libraryEntries) {
        libraryEntries.filter { it.watchStatus == WatchStatus.WATCHING }
    }

    LaunchedEffect(libraryViewModel, strings) {
        libraryViewModel.events.collect { event ->
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
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = strings.settingsTitle
                                    )
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
                            onRetry = { catalogViewModel.loadBoard() },
                            onOpenDetails = { type, id ->
                                navController.navigate(Routes.detail(type, id))
                            },
                            onQuickAdd = { item ->
                                libraryViewModel.saveMediaItem(item, WatchStatus.WATCHLIST)
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
                            libraryState = libraryState,
                            onOpenDetails = { type, id ->
                                navController.navigate(Routes.detail(type, id))
                            },
                            onUpdateStatus = { entry, status ->
                                libraryViewModel.updateEntryStatus(entry, status)
                            },
                            onRemoveEntry = { mediaId ->
                                libraryViewModel.removeFromLibrary(mediaId)
                            },
                            onExploreClick = { navigateToTab(Routes.DISCOVER) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    composable(Routes.CALENDAR) {
                        CalendarScreen(
                            calendarState = calendarState,
                            onRetry = { libraryViewModel.refreshCalendar() },
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
                            onBack = { navController.popBackStack() },
                            onRetry = { catalogViewModel.loadDetails(type, id, forceRefresh = true) },
                            onUpdateStatus = { details, status, inLib ->
                                libraryViewModel.updateMediaStatus(details, status, inLib)
                            },
                            onRemoveFromLibrary = { mediaId ->
                                libraryViewModel.removeFromLibrary(mediaId)
                            },
                            onToggleEpisode = { details, episode ->
                                libraryViewModel.toggleEpisodeWatched(details, episode)
                            },
                            onMarkSeasonWatched = { details, season, mark ->
                                libraryViewModel.markSeasonWatched(details, season, mark)
                            },
                            onShowMessage = { msg ->
                                // Launch snackbar via scope
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
            onLanguageChange = { settingsViewModel.setLanguage(it) },
            onThemeModeChange = { settingsViewModel.setThemeMode(it) },
            onDynamicColorChange = { settingsViewModel.setDynamicColor(it) },
            onSignOut = {
                showSettingsSheet = false
                signOutUser(credentialManager = credentialManager, scope = scope)
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
