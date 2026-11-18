package com.example

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.example.model.AccountApiMode
import com.example.model.GameAccount
import com.example.model.GameCatalogPresets
import com.example.model.GameMission
import com.example.model.GameSourceType
import com.example.model.LibraryGame
import com.example.model.LibrarySortOrder
import com.example.model.MissionCategory
import com.example.model.safeEnumValueOf
import com.example.ui.screens.GameAccountsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.theme.NEMOxTheme
import com.example.viewmodel.UiState
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
    fun `NEMOxTheme switches LayoutDirection between Arabic RTL and English LTR`() {
        var capturedDirection: LayoutDirection? = null
        composeRule.setContent {
            NEMOxTheme(darkTheme = true, languageCode = "ar") {
                capturedDirection = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, capturedDirection)
    }

    @Test
    fun `GameCatalogPresets covers requested games without password fields`() {
        val keys = GameCatalogPresets.supportedAccountGames.map { it.gameKey }.toSet()
        assertTrue(keys.containsAll(listOf("pubg_mobile", "blood_strike", "fortnite", "cod", "minecraft", "rocket_league")))
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
            NEMOxTheme(darkTheme = true, languageCode = "en") {
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
            NEMOxTheme(darkTheme = true, languageCode = "en") {
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
            NEMOxTheme(darkTheme = true, languageCode = "en") {
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
}
