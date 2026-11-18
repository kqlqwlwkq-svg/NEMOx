package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.ui.components.ZyvoraBrandLockup
import com.example.ui.navigation.NemoxRoute
import com.example.viewmodel.NemoxViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.Locale
import kotlinx.coroutines.launch

private data class BottomTabItem(
    val route: NemoxRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    viewModel: NemoxViewModel,
    currentLanguage: String,
    darkMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val authUser = Firebase.auth.currentUser
    val isArabic = currentLanguage == "ar" ||
        (currentLanguage == "system" && Locale.getDefault().language == "ar")
    val isTurkish = currentLanguage == "tr" ||
        (currentLanguage == "system" && Locale.getDefault().language == "tr")
    val displayName = if (viewModel.isGuestMode) {
        stringResource(R.string.guest_mode_label)
    } else {
        authUser?.displayName ?: when {
            isArabic -> "لاعب"
            isTurkish -> "Oyuncu"
            else -> "Gamer"
        }
    }
    val email = if (viewModel.isGuestMode) null else authUser?.email
    val photoUrl = if (viewModel.isGuestMode) null else authUser?.photoUrl?.toString()

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryGames.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val accountsState by viewModel.gameAccounts.collectAsStateWithLifecycle()
    val missionsState by viewModel.missions.collectAsStateWithLifecycle()
    val waitlistState by viewModel.waitlist.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notifications.collectAsStateWithLifecycle()
    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val selectedNewsFilter by viewModel.selectedNewsGameFilter.collectAsStateWithLifecycle()
    val officialNewsOnly by viewModel.officialNewsOnly.collectAsStateWithLifecycle()
    val upcomingState by viewModel.upcomingState.collectAsStateWithLifecycle()
    val liveEventsState by viewModel.liveEventsState.collectAsStateWithLifecycle()
    val trendingState by viewModel.trendingState.collectAsStateWithLifecycle()
    val recommendedState by viewModel.recommendedState.collectAsStateWithLifecycle()
    val preferredGenres by viewModel.preferredGenres.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by viewModel.hasCompletedGenreOnboarding.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val genreGamesState by viewModel.genreGamesState.collectAsStateWithLifecycle()
    val explorerQuery by viewModel.explorerQuery.collectAsStateWithLifecycle()
    val explorerState by viewModel.explorerState.collectAsStateWithLifecycle()
    val searchFilters by viewModel.searchFilters.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val selectedPreview by viewModel.selectedCatalogPreview.collectAsStateWithLifecycle()
    val selectedGameNews by viewModel.selectedGameNews.collectAsStateWithLifecycle()
    val isSyncingAccount by viewModel.isSyncingAccount.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val libraryGames = (libraryState as? UiState.Success)?.data.orEmpty()
    val waitlistGames = (waitlistState as? UiState.Success)?.data.orEmpty()
    val missionsList = (missionsState as? UiState.Success)?.data.orEmpty()
    val accountsList = (accountsState as? UiState.Success)?.data.orEmpty()
    val unreadNotifCount = (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0

    // Exact 5 primary tabs: 1. Home, 2. Discover, 3. Search, 4. My Games, 5. Profile
    val tabs = remember {
        listOf(
            BottomTabItem(NemoxRoute.Home, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "tab_home"),
            BottomTabItem(NemoxRoute.Discover, R.string.nav_discover, Icons.Rounded.Explore, Icons.Outlined.Explore, "tab_discover"),
            BottomTabItem(NemoxRoute.Search, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search, "tab_search"),
            BottomTabItem(NemoxRoute.MyGames, R.string.nav_my_games, Icons.Rounded.SportsEsports, Icons.Outlined.SportsEsports, "tab_my_games"),
            BottomTabItem(NemoxRoute.Profile, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "tab_profile")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val dest = backStackEntry?.destination
    val isTopTab = dest?.let { d ->
        d.hasRoute<NemoxRoute.Home>() ||
            d.hasRoute<NemoxRoute.Discover>() ||
            d.hasRoute<NemoxRoute.Search>() ||
            d.hasRoute<NemoxRoute.MyGames>() ||
            d.hasRoute<NemoxRoute.Profile>()
    } ?: true

    val navigateToTab: (NemoxRoute) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val triggerGoogleSignIn: () -> Unit = {
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onAuthError = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            scope = scope
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        if (!isTopTab) {
                            IconButton(
                                onClick = { navController.navigateUp() },
                                modifier = Modifier.testTag("top_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    title = {
                        ZyvoraBrandLockup(
                            markSize = 30.dp,
                            modifier = Modifier.testTag("zyvora_topbar_brand")
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(NemoxRoute.News()) },
                            modifier = Modifier.testTag("top_news_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Newspaper,
                                contentDescription = stringResource(R.string.nav_news),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = { navigateToTab(NemoxRoute.Search) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.nav_search),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = { navController.navigate(NemoxRoute.Notifications) },
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifCount > 0) {
                                        Badge { Text(unreadNotifCount.coerceAtMost(99).toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = stringResource(R.string.notifications_title)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    val navBarColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is NemoxRoute.Home -> dest?.hasRoute<NemoxRoute.Home>() == true
                                is NemoxRoute.Discover -> dest?.hasRoute<NemoxRoute.Discover>() == true
                                is NemoxRoute.Search -> dest?.hasRoute<NemoxRoute.Search>() == true
                                is NemoxRoute.MyGames -> dest?.hasRoute<NemoxRoute.MyGames>() == true
                                is NemoxRoute.Profile -> dest?.hasRoute<NemoxRoute.Profile>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                colors = navBarColors,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    val navRailColors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is NemoxRoute.Home -> dest?.hasRoute<NemoxRoute.Home>() == true
                                is NemoxRoute.Discover -> dest?.hasRoute<NemoxRoute.Discover>() == true
                                is NemoxRoute.Search -> dest?.hasRoute<NemoxRoute.Search>() == true
                                is NemoxRoute.MyGames -> dest?.hasRoute<NemoxRoute.MyGames>() == true
                                is NemoxRoute.Profile -> dest?.hasRoute<NemoxRoute.Profile>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                colors = navRailColors,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_${tab.testTag}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = NemoxRoute.Home,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 960.dp)
                    ) {
                    composable<NemoxRoute.Home> {
                        HomeDashboardScreen(
                            displayName = displayName,
                            libraryState = libraryState,
                            sortOrder = sortOrder,
                            missionsState = missionsState,
                            newsState = newsState,
                            liveEventsState = liveEventsState,
                            notificationsState = notificationsState,
                            trendingState = trendingState,
                            isArabic = isArabic,
                            onSortChanged = viewModel::setSortOrder,
                            onOpenGameDetail = { id -> navController.navigate(NemoxRoute.GameDetail(id)) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onOpenExplorer = { navigateToTab(NemoxRoute.Search) },
                            onOpenMyGames = { navigateToTab(NemoxRoute.MyGames) },
                            onOpenMissions = { navController.navigate(NemoxRoute.Missions) },
                            onOpenNews = { navController.navigate(NemoxRoute.News()) },
                            onOpenUpcoming = { navController.navigate(NemoxRoute.Upcoming) },
                            onOpenTrending = { navController.navigate(NemoxRoute.Trending) },
                            onOpenDiscover = { navigateToTab(NemoxRoute.Discover) },
                            onOpenAccounts = { navController.navigate(NemoxRoute.Accounts) },
                            onOpenCatalogPreview = viewModel::loadCatalogPreview,
                            onIncrementMission = viewModel::incrementMission,
                            onToggleMission = viewModel::toggleMissionCompleted
                        )
                    }

                    composable<NemoxRoute.Discover> {
                        DiscoverScreen(
                            isArabic = isArabic,
                            trendingState = trendingState,
                            recommendedState = recommendedState,
                            preferredGenres = preferredGenres,
                            hasCompletedOnboarding = hasCompletedOnboarding,
                            selectedGenre = selectedGenre,
                            genreGamesState = genreGamesState,
                            libraryGames = libraryGames,
                            onSaveInterests = viewModel::savePreferredGenres,
                            onSelectGenre = viewModel::selectGenre,
                            onDismissGenre = viewModel::clearSelectedGenre,
                            onOpenGamePreview = viewModel::loadCatalogPreview,
                            onAddToLibrary = viewModel::addCatalogGameToLibrary,
                            onOpenAllTrending = { navController.navigate(NemoxRoute.Trending) },
                            onRefreshRecommended = { viewModel.refreshRecommended() }
                        )
                    }

                    composable<NemoxRoute.Search> {
                        GameExplorerScreen(
                            isArabic = isArabic,
                            query = explorerQuery,
                            explorerState = explorerState,
                            filters = searchFilters,
                            recentSearches = recentSearches,
                            suggestionsProvider = viewModel::getSearchSuggestions,
                            libraryGames = libraryGames,
                            waitlistGames = waitlistGames,
                            onSearch = { q -> viewModel.searchExplorer(q, recordHistory = true) },
                            onUpdateFilters = viewModel::updateSearchFilters,
                            onResetFilters = viewModel::resetSearchFilters,
                            onRemoveRecentSearch = viewModel::removeRecentSearch,
                            onClearRecentSearches = viewModel::clearRecentSearches,
                            onSelectGame = viewModel::loadCatalogPreview,
                            onAddToLibrary = viewModel::addCatalogGameToLibrary,
                            onAddToWaitlist = viewModel::addToWaitlist
                        )
                    }

                    composable<NemoxRoute.Trending> {
                        TrendingScreen(
                            isArabic = isArabic,
                            trendingState = trendingState,
                            libraryGames = libraryGames,
                            onRefresh = { viewModel.refreshTrending(forceRefresh = true) },
                            onOpenGamePreview = viewModel::loadCatalogPreview,
                            onAddToLibrary = viewModel::addCatalogGameToLibrary
                        )
                    }

                    composable<NemoxRoute.MyGames> {
                        MyGamesScreen(
                            libraryState = libraryState,
                            sortOrder = sortOrder,
                            onSortChanged = viewModel::setSortOrder,
                            onOpenGameDetail = { id -> navController.navigate(NemoxRoute.GameDetail(id)) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onOpenExplorer = { navigateToTab(NemoxRoute.Search) },
                            onAddManualGame = viewModel::addManualGameToLibrary
                        )
                    }

                    composable<NemoxRoute.News> { entry ->
                        val args = entry.toRoute<NemoxRoute.News>()
                        LaunchedEffect(args.filterGameTitle) {
                            if (args.filterGameTitle != null) {
                                viewModel.setNewsFilter(args.filterGameTitle)
                            }
                        }
                        NewsCenterScreen(
                            newsState = newsState,
                            libraryGames = libraryGames,
                            selectedGameFilter = selectedNewsFilter,
                            officialOnly = officialNewsOnly,
                            onSelectGameFilter = viewModel::setNewsFilter,
                            onToggleOfficialOnly = viewModel::setOfficialNewsOnly,
                            onRefresh = viewModel::refreshNews
                        )
                    }

                    composable<NemoxRoute.Upcoming> {
                        UpcomingGamesScreen(
                            upcomingState = upcomingState,
                            waitlistState = waitlistState,
                            onOpenExplorerForUpcoming = { query ->
                                viewModel.searchExplorer(query, recordHistory = false)
                                navigateToTab(NemoxRoute.Search)
                            },
                            onAddToWaitlist = viewModel::addToWaitlist,
                            onToggleWaitlistAlerts = viewModel::toggleWaitlistAlert,
                            onRemoveFromWaitlist = viewModel::removeFromWaitlist,
                            onRefresh = viewModel::refreshUpcoming
                        )
                    }

                    composable<NemoxRoute.Profile> {
                        ProfileSettingsScreen(
                            displayName = displayName,
                            email = email,
                            photoUrl = photoUrl,
                            isGuestMode = viewModel.isGuestMode,
                            profileState = profileState,
                            currentLanguage = currentLanguage,
                            darkMode = darkMode,
                            accountsCount = accountsList.size,
                            missionsCount = missionsList.count { !it.completed },
                            unreadNotifCount = unreadNotifCount,
                            onSignInWithGoogle = triggerGoogleSignIn,
                            onOpenAccounts = { navController.navigate(NemoxRoute.Accounts) },
                            onOpenMissions = { navController.navigate(NemoxRoute.Missions) },
                            onOpenNotifications = { navController.navigate(NemoxRoute.Notifications) },
                            onOpenNews = { navController.navigate(NemoxRoute.News()) },
                            onOpenUpcoming = { navController.navigate(NemoxRoute.Upcoming) },
                            onOpenTrending = { navController.navigate(NemoxRoute.Trending) },
                            onLanguageSelected = viewModel::setLanguage,
                            onDarkModeChanged = viewModel::setDarkMode,
                            onUpdateGlobalNotifs = viewModel::updateGlobalNotificationPrefs,
                            onSignOut = {
                                if (viewModel.isGuestMode) {
                                    viewModel.exitGuestMode()
                                } else {
                                    signOut(credentialManager, scope)
                                }
                            }
                        )
                    }

                    composable<NemoxRoute.Explorer> {
                        GameExplorerScreen(
                            isArabic = isArabic,
                            query = explorerQuery,
                            explorerState = explorerState,
                            filters = searchFilters,
                            recentSearches = recentSearches,
                            suggestionsProvider = viewModel::getSearchSuggestions,
                            libraryGames = libraryGames,
                            waitlistGames = waitlistGames,
                            onSearch = { q -> viewModel.searchExplorer(q, recordHistory = true) },
                            onUpdateFilters = viewModel::updateSearchFilters,
                            onResetFilters = viewModel::resetSearchFilters,
                            onRemoveRecentSearch = viewModel::removeRecentSearch,
                            onClearRecentSearches = viewModel::clearRecentSearches,
                            onSelectGame = viewModel::loadCatalogPreview,
                            onAddToLibrary = viewModel::addCatalogGameToLibrary,
                            onAddToWaitlist = viewModel::addToWaitlist
                        )
                    }

                    composable<NemoxRoute.GameDetail> { entry ->
                        val route = entry.toRoute<NemoxRoute.GameDetail>()
                        val game = libraryGames.firstOrNull { it.id == route.gameDocId }
                        GameDetailScreen(
                            game = game,
                            gameNews = selectedGameNews,
                            missions = missionsList,
                            onLoadNews = viewModel::loadGameDetailNews,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleNeedsUpdate = viewModel::toggleNeedsUpdate,
                            onUpdateStatsAndSeason = viewModel::updateGameStatsAndSeason,
                            onUpdateNotificationPrefs = viewModel::updateGameNotificationPrefs,
                            onAddMission = viewModel::addMission,
                            onIncrementMission = viewModel::incrementMission,
                            onToggleMission = viewModel::toggleMissionCompleted,
                            onDeleteMission = viewModel::deleteMission,
                            onRemoveGame = viewModel::removeGameFromLibrary,
                            onBack = { navController.navigateUp() }
                        )
                    }

                    composable<NemoxRoute.Missions> {
                        MissionsScreen(
                            missionsState = missionsState,
                            libraryGames = libraryGames,
                            onAddMission = viewModel::addMission,
                            onIncrementMission = viewModel::incrementMission,
                            onToggleMission = viewModel::toggleMissionCompleted,
                            onDeleteMission = viewModel::deleteMission
                        )
                    }

                    composable<NemoxRoute.Accounts> {
                        GameAccountsScreen(
                            accountsState = accountsState,
                            isSyncing = isSyncingAccount,
                            isArabic = isArabic,
                            isGuestMode = viewModel.isGuestMode,
                            onSignInWithGoogle = triggerGoogleSignIn,
                            onLinkOrSyncAccount = viewModel::linkOrSyncAccount,
                            onRefreshLive = viewModel::refreshAccountLive,
                            onDeleteAccount = viewModel::deleteAccount
                        )
                    }

                    composable<NemoxRoute.Notifications> {
                        NotificationsScreen(
                            notificationsState = notificationsState,
                            onScanNow = viewModel::scanAlertsNow,
                            onMarkRead = viewModel::markNotificationRead,
                            onMarkAllRead = viewModel::markAllNotificationsRead,
                            onDelete = viewModel::deleteNotification
                        )
                    }
                    }
                }
            }
        }

        selectedPreview?.let { previewItem ->
            CatalogGamePreviewSheet(
                selectedPreview = previewItem,
                selectedGameNews = selectedGameNews,
                libraryGames = libraryGames,
                waitlistGames = waitlistGames,
                isArabic = isArabic,
                onDismiss = viewModel::dismissCatalogPreview,
                onAddToLibrary = viewModel::addCatalogGameToLibrary,
                onAddToWaitlist = viewModel::addToWaitlist
            )
        }
    }
}
