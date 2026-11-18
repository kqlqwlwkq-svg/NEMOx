package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GamingRemoteRepository
import com.example.data.NemoxFirestoreRepository
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
import com.example.model.LiveEventItem
import com.example.model.MissionCategory
import com.example.model.NewsArticle
import com.example.model.NotificationType
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.example.model.WaitlistGame
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class NemoxViewModel(
    private val firestoreRepo: NemoxFirestoreRepository,
    private val remoteRepo: GamingRemoteRepository,
    private val settingsDataStore: SettingsDataStore,
    val currentUserId: String
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_UPDATED)
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder.asStateFlow()

    private val _selectedNewsGameFilter = MutableStateFlow<String?>(null)
    val selectedNewsGameFilter: StateFlow<String?> = _selectedNewsGameFilter.asStateFlow()

    private val _officialNewsOnly = MutableStateFlow(false)
    val officialNewsOnly: StateFlow<Boolean> = _officialNewsOnly.asStateFlow()

    private val _newsState = MutableStateFlow<UiState<List<NewsArticle>>>(UiState.Loading)
    val newsState: StateFlow<UiState<List<NewsArticle>>> = _newsState.asStateFlow()

    private val _upcomingState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Loading)
    val upcomingState: StateFlow<UiState<List<CatalogGameItem>>> = _upcomingState.asStateFlow()

    private val _liveEventsState = MutableStateFlow<UiState<List<LiveEventItem>>>(UiState.Loading)
    val liveEventsState: StateFlow<UiState<List<LiveEventItem>>> = _liveEventsState.asStateFlow()

    private val _explorerQuery = MutableStateFlow("")
    val explorerQuery: StateFlow<String> = _explorerQuery.asStateFlow()

    private val _explorerState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Loading)
    val explorerState: StateFlow<UiState<List<CatalogGameItem>>> = _explorerState.asStateFlow()

    private val _selectedCatalogPreview = MutableStateFlow<CatalogGameItem?>(null)
    val selectedCatalogPreview: StateFlow<CatalogGameItem?> = _selectedCatalogPreview.asStateFlow()

    private val _selectedGameNews = MutableStateFlow<List<NewsArticle>>(emptyList())
    val selectedGameNews: StateFlow<List<NewsArticle>> = _selectedGameNews.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    val userProfile: StateFlow<UiState<UserProfile?>> = firestoreRepo.observeUserProfile(currentUserId)
        .toUiStateFlow("Failed to load profile")

    val libraryGames: StateFlow<UiState<List<LibraryGame>>> = combine(
        firestoreRepo.observeLibraryGames(currentUserId),
        _sortOrder
    ) { games, order ->
        when (order) {
            LibrarySortOrder.MOST_PLAYED -> games.sortedByDescending { it.playHours }
            LibrarySortOrder.FAVORITES -> games.sortedWith(
                compareByDescending<LibraryGame> { it.favorite }.thenByDescending { it.lastUpdatedAtMillis }
            )
            LibrarySortOrder.RECENTLY_UPDATED -> games.sortedByDescending { it.lastUpdatedAtMillis }
        }
    }.toUiStateFlow("Failed to load game library")

    val gameAccounts: StateFlow<UiState<List<GameAccount>>> = firestoreRepo.observeGameAccounts(currentUserId)
        .map { list -> list.sortedByDescending { it.lastSyncedAtMillis } }
        .toUiStateFlow("Failed to load game accounts")

    val missions: StateFlow<UiState<List<GameMission>>> = firestoreRepo.observeMissions(currentUserId)
        .map { list ->
            list.sortedWith(
                compareBy<GameMission> { it.completed }.thenBy { it.expiresAtMillis }
            )
        }
        .toUiStateFlow("Failed to load missions")

    val waitlist: StateFlow<UiState<List<WaitlistGame>>> = firestoreRepo.observeWaitlist(currentUserId)
        .map { list -> list.sortedBy { it.releaseTimestampMillis } }
        .toUiStateFlow("Failed to load waitlist")

    val notifications: StateFlow<UiState<List<UserNotification>>> = firestoreRepo.observeNotifications(currentUserId)
        .toUiStateFlow("Failed to load notifications")

    init {
        bootstrapUserProfile()
        refreshNews()
        refreshUpcoming()
        refreshLiveEvents()
        searchExplorer("")
    }

    private fun bootstrapUserProfile() {
        viewModelScope.launch {
            val authUser = Firebase.auth.currentUser
            if (authUser != null && authUser.uid == currentUserId) {
                firestoreRepo.ensureUserProfile(
                    userId = currentUserId,
                    displayName = authUser.displayName ?: "Gamer",
                    email = authUser.email,
                    photoUrl = authUser.photoUrl?.toString(),
                    defaultLang = "system"
                )
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    fun setNewsFilter(gameTitle: String?) {
        _selectedNewsGameFilter.value = gameTitle
    }

    fun setOfficialNewsOnly(only: Boolean) {
        _officialNewsOnly.value = only
    }

    fun refreshNews() {
        viewModelScope.launch {
            _newsState.value = UiState.Loading
            val currentGames = (libraryGames.value as? UiState.Success)?.data.orEmpty()
            val steamApps = currentGames.mapNotNull { g ->
                g.steamAppId?.let { id -> id to g.title }
            }
            val includeMc = currentGames.isEmpty() || currentGames.any {
                it.title.contains("Minecraft", ignoreCase = true) || it.resolvedSourceType == GameSourceType.MOJANG
            }
            val includeFn = currentGames.isEmpty() || currentGames.any {
                it.title.contains("Fortnite", ignoreCase = true) || it.resolvedSourceType == GameSourceType.FORTNITE
            }
            val result = remoteRepo.getAggregatedNews(
                trackedSteamApps = steamApps,
                includeMinecraft = includeMc,
                includeFortnite = includeFn
            )
            _newsState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load gaming news") }
            )
        }
    }

    fun refreshUpcoming() {
        viewModelScope.launch {
            _upcomingState.value = UiState.Loading
            val result = remoteRepo.getUpcomingGames()
            _upcomingState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load upcoming games") }
            )
        }
    }

    fun refreshLiveEvents() {
        viewModelScope.launch {
            _liveEventsState.value = UiState.Loading
            val result = remoteRepo.getLiveGamingEvents()
            _liveEventsState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load live events") }
            )
        }
    }

    fun searchExplorer(query: String) {
        _explorerQuery.value = query
        viewModelScope.launch {
            _explorerState.value = UiState.Loading
            val result = remoteRepo.searchGames(query)
            _explorerState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Search failed") }
            )
        }
    }

    fun loadCatalogPreview(item: CatalogGameItem) {
        _selectedCatalogPreview.value = item
        _selectedGameNews.value = emptyList()
        viewModelScope.launch {
            val appId = item.steamAppId
            if (appId != null) {
                val enriched = remoteRepo.getGameDetailsBySteamId(appId).getOrNull()
                if (enriched != null) {
                    _selectedCatalogPreview.value = enriched
                }
                val news = runCatching { remoteRepo.getNewsForSteamApp(appId, item.title, 8) }
                    .getOrDefault(emptyList())
                _selectedGameNews.value = news
            }
        }
    }

    fun dismissCatalogPreview() {
        _selectedCatalogPreview.value = null
    }

    fun loadGameDetailNews(game: LibraryGame) {
        viewModelScope.launch {
            val appId = game.steamAppId
            val articles = when {
                appId != null -> runCatching { remoteRepo.getNewsForSteamApp(appId, game.title, 8) }
                    .getOrDefault(emptyList())
                game.title.contains("Minecraft", ignoreCase = true) ->
                    runCatching { remoteRepo.getMojangMinecraftNews() }.getOrDefault(emptyList())
                game.title.contains("Fortnite", ignoreCase = true) ->
                    runCatching { remoteRepo.getFortniteOfficialNews() }.getOrDefault(emptyList())
                else -> emptyList()
            }
            _selectedGameNews.value = articles
        }
    }

    fun addCatalogGameToLibrary(item: CatalogGameItem) {
        viewModelScope.launch {
            val libGame = LibraryGame(
                userId = currentUserId,
                externalId = item.externalId,
                title = item.title,
                coverUrl = item.coverUrl,
                iconUrl = item.iconUrl,
                sourceType = item.sourceType.name,
                steamAppId = item.steamAppId,
                platforms = item.platforms,
                genres = item.genres,
                officialUrl = item.officialUrl,
                communityUrl = item.communityUrl,
                lastUpdatedAtMillis = System.currentTimeMillis()
            )
            val res = firestoreRepo.addOrUpdateLibraryGame(currentUserId, libGame)
            _actionMessage.value = if (res.isSuccess) {
                "${item.title} added to library"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to add game"
            }
            refreshNews()
        }
    }

    fun addManualGameToLibrary(
        title: String,
        platform: String,
        genre: String,
        officialUrl: String?,
        seasonName: String?,
        seasonEndsInDays: Int?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val seasonEnd = seasonEndsInDays?.takeIf { it > 0 }?.let { now + it * 86_400_000L }
            val game = LibraryGame(
                userId = currentUserId,
                externalId = "manual_${now}",
                title = title.trim(),
                sourceType = GameSourceType.MANUAL.name,
                platforms = listOf(platform.trim().ifBlank { "Crossplay" }),
                genres = if (genre.isNotBlank()) listOf(genre.trim()) else emptyList(),
                currentSeasonName = seasonName?.takeIf { it.isNotBlank() },
                seasonEndsAtMillis = seasonEnd,
                officialUrl = officialUrl?.takeIf { it.isNotBlank() },
                lastUpdatedAtMillis = now
            )
            val res = firestoreRepo.addOrUpdateLibraryGame(currentUserId, game)
            _actionMessage.value = if (res.isSuccess) "${game.title} added" else "Failed to add game"
        }
    }

    fun toggleFavorite(game: LibraryGame) {
        viewModelScope.launch {
            firestoreRepo.updateLibraryGameFields(
                currentUserId,
                game.id,
                mapOf("favorite" to !game.favorite)
            )
        }
    }

    fun toggleNeedsUpdate(game: LibraryGame) {
        viewModelScope.launch {
            firestoreRepo.updateLibraryGameFields(
                currentUserId,
                game.id,
                mapOf(
                    "needsUpdate" to !game.needsUpdate,
                    "lastUpdatedAtMillis" to System.currentTimeMillis()
                )
            )
        }
    }

    fun updateGameStatsAndSeason(
        game: LibraryGame,
        playHours: Long,
        playerLevel: Long,
        achievementsUnlocked: Long,
        achievementsTotal: Long,
        nextUpdateDateText: String,
        currentSeasonName: String,
        seasonDaysRemaining: Int
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fields = buildMap<String, Any> {
                put("playHours", playHours.coerceAtLeast(0L))
                put("playerLevel", playerLevel.coerceAtLeast(0L))
                put("achievementsUnlocked", achievementsUnlocked.coerceAtLeast(0L))
                put("achievementsTotal", achievementsTotal.coerceAtLeast(0L))
                if (nextUpdateDateText.isNotBlank()) put("nextUpdateDateText", nextUpdateDateText.take(80))
                if (currentSeasonName.isNotBlank()) put("currentSeasonName", currentSeasonName.take(120))
                if (seasonDaysRemaining > 0) {
                    put("seasonEndsAtMillis", now + seasonDaysRemaining * 86_400_000L)
                }
                put("lastUpdatedAtMillis", now)
            }
            val res = firestoreRepo.updateLibraryGameFields(currentUserId, game.id, fields)
            _actionMessage.value = if (res.isSuccess) "Updated ${game.title}" else "Update failed"
        }
    }

    fun updateGameNotificationPrefs(
        game: LibraryGame,
        notifyUpdates: Boolean,
        notifySeasons: Boolean,
        notifyMissions: Boolean,
        notifyNews: Boolean
    ) {
        viewModelScope.launch {
            firestoreRepo.updateLibraryGameFields(
                currentUserId,
                game.id,
                mapOf(
                    "notifyUpdates" to notifyUpdates,
                    "notifySeasons" to notifySeasons,
                    "notifyMissions" to notifyMissions,
                    "notifyNews" to notifyNews
                )
            )
        }
    }

    fun removeGameFromLibrary(gameId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteLibraryGame(currentUserId, gameId)
        }
    }

    // --- GAME ACCOUNTS ---
    fun linkOrSyncAccount(
        gameKey: String,
        customGameName: String,
        playerIdentifier: String,
        platform: String,
        serverRegion: String,
        manualLevel: Long,
        manualRank: String,
        manualStats: String,
        existingId: String = ""
    ) {
        viewModelScope.launch {
            _isSyncingAccount.value = true
            val spec = GameCatalogPresets.supportedAccountGames.firstOrNull { it.gameKey == gameKey }
            val resolvedName = if (gameKey == "custom") customGameName.ifBlank { "Custom Game" } else (spec?.gameName ?: customGameName)

            val syncResult = remoteRepo.syncGameAccount(gameKey, playerIdentifier)
            val synced = syncResult.getOrNull()

            val account = GameAccount(
                id = existingId,
                userId = currentUserId,
                gameKey = gameKey,
                gameName = resolvedName,
                playerIdentifier = synced?.playerIdentifier ?: playerIdentifier.trim(),
                platform = platform.trim().ifBlank { spec?.defaultPlatform ?: "Crossplay" },
                serverRegion = serverRegion.trim().takeIf { it.isNotBlank() },
                level = synced?.level ?: manualLevel.coerceAtLeast(0L),
                rankTier = synced?.rankTier ?: manualRank.trim().takeIf { it.isNotBlank() },
                statsSummary = synced?.statsSummary ?: manualStats.trim().takeIf { it.isNotBlank() },
                achievementsText = synced?.achievementsText,
                apiMode = (synced?.apiMode ?: spec?.apiMode ?: AccountApiMode.MANUAL_NO_PUBLIC_API).name,
                apiAvailabilityNote = synced?.availabilityNote ?: spec?.availabilitySummaryEn,
                avatarUrl = synced?.avatarUrl,
                verifiedPlayerId = synced?.verifiedPlayerId,
                lastSyncedAtMillis = System.currentTimeMillis()
            )

            val saveRes = firestoreRepo.upsertGameAccount(currentUserId, account)
            _isSyncingAccount.value = false

            _actionMessage.value = when {
                syncResult.isFailure && synced == null && (gameKey == "minecraft" || gameKey == "steam") ->
                    "Saved ID, live lookup note: ${syncResult.exceptionOrNull()?.localizedMessage}"
                saveRes.isSuccess -> "$resolvedName account saved"
                else -> saveRes.exceptionOrNull()?.localizedMessage ?: "Failed to save account"
            }
        }
    }

    fun refreshAccountLive(account: GameAccount) {
        linkOrSyncAccount(
            gameKey = account.gameKey,
            customGameName = account.gameName,
            playerIdentifier = account.playerIdentifier,
            platform = account.platform,
            serverRegion = account.serverRegion.orEmpty(),
            manualLevel = account.level,
            manualRank = account.rankTier.orEmpty(),
            manualStats = account.statsSummary.orEmpty(),
            existingId = account.id
        )
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteGameAccount(currentUserId, accountId)
        }
    }

    // --- MISSIONS ---
    fun addMission(
        gameDocId: String?,
        gameName: String,
        title: String,
        description: String?,
        category: MissionCategory,
        targetProgress: Long,
        hoursUntilExpiry: Long,
        rewardText: String?
    ) {
        viewModelScope.launch {
            val expiresAt = System.currentTimeMillis() + hoursUntilExpiry.coerceAtLeast(1L) * 3_600_000L
            val mission = GameMission(
                userId = currentUserId,
                gameDocId = gameDocId,
                gameName = gameName.trim(),
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                category = category.name,
                currentProgress = 0L,
                targetProgress = targetProgress.coerceAtLeast(1L),
                completed = false,
                expiresAtMillis = expiresAt,
                rewardText = rewardText?.trim()?.takeIf { it.isNotBlank() }
            )
            val res = firestoreRepo.createMission(currentUserId, mission)
            _actionMessage.value = if (res.isSuccess) "Mission added" else "Failed to add mission"
        }
    }

    fun incrementMission(mission: GameMission) {
        viewModelScope.launch {
            val next = (mission.currentProgress + 1L).coerceAtMost(mission.targetProgress)
            val done = next >= mission.targetProgress
            firestoreRepo.updateMissionProgress(currentUserId, mission.id, next, done)
        }
    }

    fun toggleMissionCompleted(mission: GameMission) {
        viewModelScope.launch {
            val nextDone = !mission.completed
            val nextProg = if (nextDone) mission.targetProgress else mission.currentProgress.coerceAtMost(mission.targetProgress - 1L).coerceAtLeast(0L)
            firestoreRepo.updateMissionProgress(currentUserId, mission.id, nextProg, nextDone)
        }
    }

    fun deleteMission(missionId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteMission(currentUserId, missionId)
        }
    }

    // --- WAITLIST ---
    fun addToWaitlist(item: CatalogGameItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val releaseMillis = if (item.releaseTimestampMillis > 0L) item.releaseTimestampMillis else now + 30L * 86_400_000L
            val waitlistGame = WaitlistGame(
                userId = currentUserId,
                externalId = item.externalId,
                title = item.title,
                coverUrl = item.coverUrl,
                releaseDateText = item.releaseDateText.ifBlank { "Coming Soon" },
                releaseTimestampMillis = releaseMillis,
                platforms = item.platforms,
                genre = item.genres.firstOrNull(),
                description = item.description.take(550).takeIf { it.isNotBlank() },
                status = item.status.name,
                notifyBeforeRelease = true,
                notifyOnRelease = true,
                steamAppId = item.steamAppId,
                trailerUrl = item.trailerUrl
            )
            val res = firestoreRepo.addToWaitlist(currentUserId, waitlistGame)
            _actionMessage.value = if (res.isSuccess) "${item.title} added to Waitlist" else "Failed to add to Waitlist"
        }
    }

    fun toggleWaitlistAlert(item: WaitlistGame, beforeRelease: Boolean, onRelease: Boolean) {
        viewModelScope.launch {
            firestoreRepo.updateWaitlistNotifications(currentUserId, item.id, beforeRelease, onRelease)
        }
    }

    fun removeFromWaitlist(waitlistId: String) {
        viewModelScope.launch {
            firestoreRepo.removeFromWaitlist(currentUserId, waitlistId)
        }
    }

    // --- NOTIFICATIONS & SETTINGS ---
    fun setLanguage(code: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(code)
            firestoreRepo.updateUserPreferences(currentUserId, mapOf("preferredLanguage" to code))
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDarkMode(enabled)
            firestoreRepo.updateUserPreferences(currentUserId, mapOf("darkMode" to enabled))
        }
    }

    fun updateGlobalNotificationPrefs(
        master: Boolean,
        updates: Boolean,
        seasons: Boolean,
        missions: Boolean,
        upcoming: Boolean,
        news: Boolean
    ) {
        viewModelScope.launch {
            firestoreRepo.updateUserPreferences(
                currentUserId,
                mapOf(
                    "notificationsEnabled" to master,
                    "notifyOnUpdates" to updates,
                    "notifyOnSeasons" to seasons,
                    "notifyOnMissions" to missions,
                    "notifyOnUpcoming" to upcoming,
                    "notifyOnNews" to news
                )
            )
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            firestoreRepo.markNotificationRead(currentUserId, notifId)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val current = (notifications.value as? UiState.Success)?.data.orEmpty()
            val unread = current.filter { !it.read }.map { it.id }
            if (unread.isNotEmpty()) {
                firestoreRepo.markAllNotificationsRead(currentUserId, unread)
            }
        }
    }

    fun deleteNotification(notifId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteNotification(currentUserId, notifId)
        }
    }

    fun scanAlertsNow() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentMissions = (missions.value as? UiState.Success)?.data.orEmpty()
            val currentGames = (libraryGames.value as? UiState.Success)?.data.orEmpty()
            val currentWaitlist = (waitlist.value as? UiState.Success)?.data.orEmpty()
            var createdCount = 0

            for (m in currentMissions) {
                if (!m.completed && m.expiresAtMillis > now && m.expiresAtMillis - now <= 12 * 3_600_000L) {
                    val key = "scan_mission_${m.id}_${m.expiresAtMillis / 3_600_000L}"
                    val res = firestoreRepo.createNotificationIfMissing(
                        currentUserId,
                        key,
                        UserNotification(
                            userId = currentUserId,
                            type = NotificationType.MISSION_EXPIRING.name,
                            title = "Mission Expiring: ${m.gameName}",
                            body = "${m.title} (${m.currentProgress}/${m.targetProgress})",
                            gameName = m.gameName,
                            read = false,
                            createdAtMillis = now
                        )
                    )
                    if (res.getOrDefault(false)) createdCount++
                }
            }

            for (g in currentGames) {
                if (g.needsUpdate && g.notifyUpdates) {
                    val key = "scan_update_${g.id}_${g.lastUpdatedAtMillis / 86_400_000L}"
                    val res = firestoreRepo.createNotificationIfMissing(
                        currentUserId,
                        key,
                        UserNotification(
                            userId = currentUserId,
                            type = NotificationType.UPDATE_RELEASED.name,
                            title = "Update Ready: ${g.title}",
                            body = g.updateNotes?.ifBlank { "A new update is pending for ${g.title}." }
                                ?: "A new update is pending for ${g.title}.",
                            gameName = g.title,
                            read = false,
                            createdAtMillis = now
                        )
                    )
                    if (res.getOrDefault(false)) createdCount++
                }
                val sEnd = g.seasonEndsAtMillis ?: 0L
                if (g.notifySeasons && sEnd > now && sEnd - now <= 72 * 3_600_000L) {
                    val key = "scan_season_${g.id}_${sEnd / 86_400_000L}"
                    val res = firestoreRepo.createNotificationIfMissing(
                        currentUserId,
                        key,
                        UserNotification(
                            userId = currentUserId,
                            type = NotificationType.SEASON_ENDING.name,
                            title = "Season Ending: ${g.title}",
                            body = "${g.currentSeasonName ?: "Current Season"} ends soon.",
                            gameName = g.title,
                            read = false,
                            createdAtMillis = now
                        )
                    )
                    if (res.getOrDefault(false)) createdCount++
                }
            }

            for (w in currentWaitlist) {
                if (w.notifyBeforeRelease && w.releaseTimestampMillis > now && w.releaseTimestampMillis - now <= 7L * 86_400_000L) {
                    val key = "scan_wait_${w.id}"
                    val res = firestoreRepo.createNotificationIfMissing(
                        currentUserId,
                        key,
                        UserNotification(
                            userId = currentUserId,
                            type = NotificationType.WAITLIST_RELEASE.name,
                            title = "Releasing Soon: ${w.title}",
                            body = "Expected release: ${w.releaseDateText}",
                            gameName = w.title,
                            read = false,
                            createdAtMillis = now
                        )
                    )
                    if (res.getOrDefault(false)) createdCount++
                }
            }

            _actionMessage.value = if (createdCount > 0) {
                "$createdCount new alerts added"
            } else {
                "No new urgent alerts found"
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMsg: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("NemoxVM", fallbackMsg, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackMsg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )
}
