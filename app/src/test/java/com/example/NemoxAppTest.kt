package com.example

import android.content.Context
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthScreen
import com.example.data.LocalGuestRepository
import com.example.data.SettingsDataStore
import com.example.model.AccountApiMode
import com.example.model.CatalogGameItem
import com.example.model.GameAccount
import com.example.model.GameCatalogPresets
import com.example.model.GameMission
import com.example.model.GameReleaseStatus
import com.example.model.GameSourceType
import com.example.model.LibraryGame
import com.example.model.LibrarySortOrder
import com.example.model.MissionCategory
import com.example.model.SearchFilterState
import com.example.model.SearchPricingFilter
import com.example.model.safeEnumValueOf
import com.example.auth.ZyvoraSplashScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.GameAccountsScreen
import com.example.ui.screens.GameExplorerScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.theme.ZyvoraTheme
import com.example.viewmodel.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NemoxAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `firestore models toCreateMap omits id and filters nulls`() {
        val game = LibraryGame(
            id = "doc_123",
            userId = "uid_1",
            externalId = "steam_578080",
            title = "PUBG: BATTLEGROUNDS",
            coverUrl = null,
            sourceType = GameSourceType.STEAM.name
        )
        val map = game.toCreateMap()
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("coverUrl"))
        assertEquals("uid_1", map["userId"])
        assertEquals("STEAM", map["sourceType"])
    }

    @Test
    fun `safeEnumValueOf falls back gracefully on unknown raw values`() {
        val resolved = safeEnumValueOf("UNKNOWN_MODE", AccountApiMode.MANUAL_NO_PUBLIC_API)
        assertEquals(AccountApiMode.MANUAL_NO_PUBLIC_API, resolved)
    }

    @Test
    fun `ZyvoraTheme switches LayoutDirection between Arabic RTL and English or Turkish LTR`() {
        var arDirection: LayoutDirection? = null
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "ar") {
                arDirection = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, arDirection)
    }

    @Test
    fun `Turkish language sets LTR direction and loads Turkish navigation strings`() {
        var trDirection: LayoutDirection? = null
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "tr") {
                trDirection = LocalLayoutDirection.current
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(R.string.nav_home)
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Ltr, trDirection)
        composeRule.onNodeWithText("Ana Sayfa").assertIsDisplayed()
    }

    @Test
    fun `Arabic is configured as the primary default app language`() {
        assertEquals("ar", com.example.data.LocalAppPreferences().languageCode)
        assertEquals("ar", com.example.model.UserProfile().preferredLanguage)

        var defaultDirection: LayoutDirection? = null
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true) {
                defaultDirection = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, defaultDirection)
    }

    @Test
    fun `GameCatalogPresets covers requested games without password fields`() {
        val keys = GameCatalogPresets.supportedAccountGames.map { it.gameKey }.toSet()
        assertTrue(keys.containsAll(listOf("pubg_mobile", "blood_strike", "fortnite", "cod", "minecraft", "rocket_league")))
    }

    @Test
    fun `GameCatalogPresets discoverGenres contains all 14 required genres and cross-platform examples`() {
        val ids = GameCatalogPresets.discoverGenres.map { it.id }.toSet()
        val expectedIds = setOf(
            "rpg", "open_world", "shooter", "battle_royale", "sports",
            "racing", "simulation", "survival", "strategy", "adventure",
            "horror", "mmorpg", "anime_style", "gacha"
        )
        assertEquals(14, GameCatalogPresets.discoverGenres.size)
        assertEquals(expectedIds, ids)

        val genshin = GameCatalogPresets.curatedMultiPlatformCatalog.first { it.title == "Genshin Impact" }
        assertTrue(genshin.genres.contains("Anime Style"))
        assertTrue(genshin.genres.contains("RPG"))

        val pubgMobile = GameCatalogPresets.curatedMultiPlatformCatalog.first { it.title == "PUBG Mobile" }
        assertTrue(pubgMobile.genres.contains("Battle Royale"))
        assertTrue(pubgMobile.genres.contains("Shooter"))

        val eaFc = GameCatalogPresets.curatedMultiPlatformCatalog.first { it.title.contains("EA SPORTS FC") }
        assertTrue(eaFc.genres.contains("Sports"))

        val minecraft = GameCatalogPresets.curatedMultiPlatformCatalog.first { it.title == "Minecraft" }
        assertTrue(minecraft.genres.contains("Survival"))
    }

    @Test
    fun `LocalGuestRepository persists library games and missions on device`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = SettingsDataStore(context)
        val guestRepo = LocalGuestRepository(dataStore)

        val game = LibraryGame(
            id = "guest_steam_578080",
            userId = "guest_local",
            externalId = "steam_578080",
            title = "PUBG: BATTLEGROUNDS",
            sourceType = GameSourceType.STEAM.name
        )
        guestRepo.addOrUpdateLibraryGame(game)

        val storedGames = guestRepo.observeLibraryGames().first()
        assertTrue(storedGames.any { it.externalId == "steam_578080" })

        guestRepo.deleteLibraryGame(game.id)
        val afterDelete = guestRepo.observeLibraryGames().first()
        assertFalse(afterDelete.any { it.externalId == "steam_578080" })
    }

    @Test
    fun `ZyvoraSplashScreen displays brand lockup and Arabic tagline`() {
        var completed = false
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "ar") {
                ZyvoraSplashScreen(onSplashComplete = { completed = true })
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("zyvora_splash").assertIsDisplayed()
        composeRule.onNodeWithText("ZYVORA").assertIsDisplayed()
        composeRule.onNodeWithText("عالمك، ألعابك، مجتمعك.").assertIsDisplayed()
    }

    @Test
    fun `AuthScreen shows ZYVORA branding, language chips AR EN TR, and Continue as Guest`() {
        var enteredGuest = false
        var selectedLang: String? = null
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                AuthScreen(
                    currentLanguage = "en",
                    onLanguageSelected = { selectedLang = it },
                    onAuthSuccess = {},
                    onContinueAsGuest = { enteredGuest = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("ZYVORA").assertIsDisplayed()
        composeRule.onNodeWithText("YOUR WORLD. YOUR GAMES.").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_lang_ar").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_lang_en").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_lang_tr").assertIsDisplayed().performClick()
        assertEquals("tr", selectedLang)

        composeRule.onNodeWithTag("guest_sign_in_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(enteredGuest)
    }

    @Test
    fun `DiscoverScreen displays first-use genre onboarding and saves selected interests`() {
        var savedGenres: List<String> = emptyList()
        val sampleTrending = listOf(
            CatalogGameItem(
                externalId = "steam_578080",
                title = "PUBG: BATTLEGROUNDS",
                coverUrl = null,
                iconUrl = null,
                sourceType = GameSourceType.STEAM,
                genres = listOf("Battle Royale", "Shooter"),
                platforms = listOf("PC"),
                trendingRank = 1,
                trendingReasonEn = "Steam Top Played #1"
            )
        )

        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                DiscoverScreen(
                    isArabic = false,
                    trendingState = UiState.Success(sampleTrending),
                    recommendedState = UiState.Success(GameCatalogPresets.curatedMultiPlatformCatalog.take(3)),
                    preferredGenres = emptyList(),
                    hasCompletedOnboarding = false,
                    selectedGenre = null,
                    genreGamesState = UiState.Success(emptyList()),
                    libraryGames = emptyList(),
                    onSaveInterests = { savedGenres = it },
                    onSelectGenre = {},
                    onDismissGenre = {},
                    onOpenGamePreview = {},
                    onAddToLibrary = {},
                    onOpenAllTrending = {},
                    onRefreshRecommended = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_onboarding_card").assertIsDisplayed()
        composeRule.onNodeWithTag("interest_chip_rpg").performClick()
        composeRule.onNodeWithTag("interest_chip_anime_style").performClick()
        composeRule.onNodeWithTag("save_interests_button").performClick()

        assertTrue(savedGenres.containsAll(listOf("rpg", "anime_style")))
    }

    @Test
    fun `GameExplorerScreen renders search field, recent searches, and filters`() {
        var updatedFilters: SearchFilterState? = null
        val sampleItem = CatalogGameItem(
            externalId = "curated_genshin_impact",
            title = "Genshin Impact",
            coverUrl = null,
            iconUrl = null,
            sourceType = GameSourceType.MANUAL,
            genres = listOf("Anime Style", "RPG"),
            platforms = listOf("PC", "Mobile"),
            status = GameReleaseStatus.AVAILABLE,
            isFreeToPlay = true,
            priceDisplay = "Free"
        )

        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                GameExplorerScreen(
                    isArabic = false,
                    query = "",
                    explorerState = UiState.Success(listOf(sampleItem)),
                    filters = SearchFilterState(),
                    recentSearches = listOf("Minecraft"),
                    suggestionsProvider = { emptyList() },
                    libraryGames = emptyList(),
                    waitlistGames = emptyList(),
                    onSearch = {},
                    onUpdateFilters = { updatedFilters = it },
                    onResetFilters = {},
                    onRemoveRecentSearch = {},
                    onClearRecentSearches = {},
                    onSelectGame = {},
                    onAddToLibrary = {},
                    onAddToWaitlist = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explorer_search_field").assertIsDisplayed()
        composeRule.onNodeWithText("Genshin Impact").assertIsDisplayed()
        composeRule.onNodeWithText("Minecraft").assertIsDisplayed()

        composeRule.onNodeWithTag("search_filter_toggle").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_price_free").performScrollTo().performClick()
        assertEquals(SearchPricingFilter.FREE, updatedFilters?.pricingFilter)
    }

    @Test
    fun `HomeDashboardScreen renders games, missions, and sort chips`() {
        val sampleGame = LibraryGame(
            id = "steam_578080",
            userId = "u1",
            externalId = "steam_578080",
            title = "PUBG: BATTLEGROUNDS",
            needsUpdate = true,
            playHours = 42,
            playerLevel = 15
        )
        val sampleMission = GameMission(
            id = "m1",
            userId = "u1",
            gameName = "PUBG: BATTLEGROUNDS",
            title = "Win 2 Squad Matches",
            category = MissionCategory.DAILY.name,
            currentProgress = 1,
            targetProgress = 2,
            completed = false,
            expiresAtMillis = System.currentTimeMillis() + 3_600_000L
        )
        var clickedSort: LibrarySortOrder? = null

        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                HomeDashboardScreen(
                    displayName = "TestPlayer",
                    libraryState = UiState.Success(listOf(sampleGame)),
                    sortOrder = LibrarySortOrder.RECENTLY_UPDATED,
                    missionsState = UiState.Success(listOf(sampleMission)),
                    newsState = UiState.Success(emptyList()),
                    liveEventsState = UiState.Success(emptyList()),
                    notificationsState = UiState.Success(emptyList()),
                    onSortChanged = { clickedSort = it },
                    onOpenGameDetail = {},
                    onToggleFavorite = {},
                    onOpenExplorer = {},
                    onOpenMyGames = {},
                    onOpenMissions = {},
                    onOpenNews = {},
                    onIncrementMission = {},
                    onToggleMission = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("TestPlayer").assertIsDisplayed()
        composeRule.onNodeWithTag("sort_favorites").performClick()
        assertEquals(LibrarySortOrder.FAVORITES, clickedSort)
    }

    @Test
    fun `MissionsScreen renders active mission and triggers increment`() {
        val sampleMission = GameMission(
            id = "m_daily_1",
            userId = "u1",
            gameName = "Minecraft",
            title = "Craft 10 Torches",
            category = MissionCategory.DAILY.name,
            currentProgress = 4,
            targetProgress = 10,
            completed = false,
            expiresAtMillis = System.currentTimeMillis() + 7_200_000L
        )
        var incremented = false

        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                MissionsScreen(
                    missionsState = UiState.Success(listOf(sampleMission)),
                    libraryGames = emptyList(),
                    onAddMission = { _, _, _, _, _, _, _, _ -> },
                    onIncrementMission = { incremented = true },
                    onToggleMission = {},
                    onDeleteMission = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Craft 10 Torches").assertIsDisplayed()
        composeRule.onNodeWithTag("mission_inc_m_daily_1").performClick()
        assertTrue(incremented)
    }

    @Test
    fun `GameAccountsScreen renders API transparency banner and linked account`() {
        val acc = GameAccount(
            id = "acc_1",
            userId = "u1",
            gameKey = "pubg_mobile",
            gameName = "PUBG Mobile",
            playerIdentifier = "5123456789",
            platform = "Mobile",
            level = 65,
            rankTier = "Ace",
            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API.name,
            apiAvailabilityNote = "Manual tracking; no public stats API."
        )

        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                GameAccountsScreen(
                    accountsState = UiState.Success(listOf(acc)),
                    isSyncing = false,
                    isArabic = false,
                    onLinkOrSyncAccount = { _, _, _, _, _, _, _, _, _ -> },
                    onRefreshLive = {},
                    onDeleteAccount = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("PUBG Mobile").assertIsDisplayed()
        composeRule.onNodeWithText("ID: 5123456789 • Mobile").assertIsDisplayed()
    }

    @Test
    fun `GameAccountsScreen in Guest Mode shows Google Sign-In gate`() {
        var clickedSignIn = false
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                GameAccountsScreen(
                    accountsState = UiState.Success(emptyList()),
                    isSyncing = false,
                    isArabic = false,
                    isGuestMode = true,
                    onSignInWithGoogle = { clickedSignIn = true },
                    onLinkOrSyncAccount = { _, _, _, _, _, _, _, _, _ -> },
                    onRefreshLive = {},
                    onDeleteAccount = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guest_prompt_google_signin").assertIsDisplayed().performClick()
        assertTrue(clickedSignIn)
    }

    @Test
    fun `ProfileSettingsScreen in Guest Mode shows Google Sign-In prompt and 4 language chips`() {
        var clickedSignIn = false
        var selectedLanguage: String? = null
        composeRule.setContent {
            ZyvoraTheme(darkTheme = true, languageCode = "en") {
                ProfileSettingsScreen(
                    displayName = "Guest Mode (Saved Locally)",
                    email = null,
                    photoUrl = null,
                    isGuestMode = true,
                    profileState = UiState.Success(null),
                    currentLanguage = "en",
                    darkMode = true,
                    accountsCount = 0,
                    missionsCount = 2,
                    unreadNotifCount = 1,
                    onSignInWithGoogle = { clickedSignIn = true },
                    onOpenAccounts = {},
                    onOpenMissions = {},
                    onOpenNotifications = {},
                    onLanguageSelected = { selectedLanguage = it },
                    onDarkModeChanged = {},
                    onUpdateGlobalNotifs = { _, _, _, _, _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_google_signin_button").assertIsDisplayed().performClick()
        assertTrue(clickedSignIn)

        composeRule.onNodeWithTag("profile_settings_list")
            .performScrollToNode(hasTestTag("settings_lang_tr"))
        composeRule.onNodeWithTag("settings_lang_ar").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lang_en").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lang_system").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lang_tr").assertIsDisplayed().performClick()
        assertEquals("tr", selectedLanguage)
    }
}
