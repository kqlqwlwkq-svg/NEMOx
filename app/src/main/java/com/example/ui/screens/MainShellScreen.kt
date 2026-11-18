package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.Event
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.auth.signOut
import com.example.ui.navigation.NemoxRoute
import com.example.viewmodel.NemoxViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val authUser = Firebase.auth.currentUser
    val displayName = authUser?.displayName ?: "Gamer"
    val email = authUser?.email
    val photoUrl = authUser?.photoUrl?.toString()

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
    val explorerQuery by viewModel.explorerQuery.collectAsStateWithLifecycle()
    val explorerState by viewModel.explorerState.collectAsStateWithLifecycle()
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

    val tabs = remember {
        listOf(
            BottomTabItem(NemoxRoute.Home, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "tab_home"),
            BottomTabItem(NemoxRoute.MyGames, R.string.nav_my_games, Icons.Rounded.SportsEsports, Icons.Outlined.SportsEsports, "tab_my_games"),
            BottomTabItem(NemoxRoute.News(), R.string.nav_news, Icons.Rounded.Newspaper, Icons.Outlined.Newspaper, "tab_news"),
            BottomTabItem(NemoxRoute.Upcoming, R.string.nav_upcoming, Icons.Rounded.Event, Icons.Outlined.Event, "tab_upcoming"),
            BottomTabItem(NemoxRoute.Profile, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "tab_profile")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val dest = backStackEntry?.destination
    val isTopTab = dest?.let { d ->
        d.hasRoute<NemoxRoute.Home>() ||
            d.hasRoute<NemoxRoute.MyGames>() ||
            d.hasRoute<NemoxRoute.News>() ||
            d.hasRoute<NemoxRoute.Upcoming>() ||
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
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_nemox_icon),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(NemoxRoute.Explorer) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.explorer_title),
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
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is NemoxRoute.Home -> dest?.hasRoute<NemoxRoute.Home>() == true
                                is NemoxRoute.MyGames -> dest?.hasRoute<NemoxRoute.MyGames>() == true
                                is NemoxRoute.News -> dest?.hasRoute<NemoxRoute.News>() == true
                                is NemoxRoute.Upcoming -> dest?.hasRoute<NemoxRoute.Upcoming>() == true
                                is NemoxRoute.Profile -> dest?.hasRoute<NemoxRoute.Profile>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
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
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is NemoxRoute.Home -> dest?.hasRoute<NemoxRoute.Home>() == true
                                is NemoxRoute.MyGames -> dest?.hasRoute<NemoxRoute.MyGames>() == true
                                is NemoxRoute.News -> dest?.hasRoute<NemoxRoute.News>() == true
                                is NemoxRoute.Upcoming -> dest?.hasRoute<NemoxRoute.Upcoming>() == true
                                is NemoxRoute.Profile -> dest?.hasRoute<NemoxRoute.Profile>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NemoxRoute.Home,
                    modifier = Modifier.weight(1f)
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
                            onSortChanged = viewModel::setSortOrder,
                            onOpenGameDetail = { id -> navController.navigate(NemoxRoute.GameDetail(id)) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onOpenExplorer = { navController.navigate(NemoxRoute.Explorer) },
                            onOpenMyGames = { navigateToTab(NemoxRoute.MyGames) },
                            onOpenMissions = { navController.navigate(NemoxRoute.Missions) },
                            onOpenNews = { navigateToTab(NemoxRoute.News()) },
                            onIncrementMission = viewModel::incrementMission,
                            onToggleMission = viewModel::toggleMissionCompleted
                        )
                    }

                    composable<NemoxRoute.MyGames> {
                        MyGamesScreen(
                            libraryState = libraryState,
                            sortOrder = sortOrder,
                            onSortChanged = viewModel::setSortOrder,
                            onOpenGameDetail = { id -> navController.navigate(NemoxRoute.GameDetail(id)) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onOpenExplorer = { navController.navigate(NemoxRoute.Explorer) },
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
                                viewModel.searchExplorer(query)
                                navController.navigate(NemoxRoute.Explorer)
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
                            profileState = profileState,
                            currentLanguage = currentLanguage,
                            darkMode = darkMode,
                            accountsCount = accountsList.size,
                            missionsCount = missionsList.count { !it.completed },
                            unreadNotifCount = unreadNotifCount,
                            onOpenAccounts = { navController.navigate(NemoxRoute.Accounts) },
                            onOpenMissions = { navController.navigate(NemoxRoute.Missions) },
                            onOpenNotifications = { navController.navigate(NemoxRoute.Notifications) },
                            onLanguageSelected = viewModel::setLanguage,
                            onDarkModeChanged = viewModel::setDarkMode,
                            onUpdateGlobalNotifs = viewModel::updateGlobalNotificationPrefs,
                            onSignOut = {
                                signOut(credentialManager, scope)
                            }
                        )
                    }

                    composable<NemoxRoute.Explorer> {
                        GameExplorerScreen(
                            query = explorerQuery,
                            explorerState = explorerState,
                            selectedPreview = selectedPreview,
                            selectedGameNews = selectedGameNews,
                            libraryGames = libraryGames,
                            waitlistGames = waitlistGames,
                            onSearch = viewModel::searchExplorer,
                            onSelectGame = viewModel::loadCatalogPreview,
                            onDismissPreview = viewModel::dismissCatalogPreview,
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
                            isArabic = currentLanguage == "ar" ||
                                (currentLanguage == "system" && java.util.Locale.getDefault().language == "ar"),
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
}
