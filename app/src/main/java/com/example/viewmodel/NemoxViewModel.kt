package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GamingRemoteRepository
import com.example.data.LocalAppPreferences
import com.example.data.LocalGuestRepository
import com.example.data.NemoxFirestoreRepository
import com.example.data.SettingsDataStore
import com.example.model.AccountApiMode
import com.example.model.CatalogGameItem
import com.example.model.GameAccount
import com.example.model.GameCatalogPresets
import com.example.model.GameMission
import com.example.model.GameReleaseStatus
import com.example.model.GameSourceType
import com.example.model.GenreSpec
import com.example.model.LibraryGame
import com.example.model.LibrarySortOrder
import com.example.model.LiveEventItem
import com.example.model.MissionCategory
import com.example.model.NewsArticle
import com.example.model.NotificationType
import com.example.model.SearchFilterState
import com.example.model.SearchPricingFilter
import com.example.model.SearchReleaseFilter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class NemoxViewModel(
    val isGuestMode: Boolean,
    private val firestoreRepo: NemoxFirestoreRepository?,
    private val guestRepo: LocalGuestRepository,
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

    private val _trendingState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Loading)
    val trendingState: StateFlow<UiState<List<CatalogGameItem>>> = _trendingState.asStateFlow()

    private val _recommendedState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Loading)
    val recommendedState: StateFlow<UiState<List<CatalogGameItem>>> = _recommendedState.asStateFlow()

    private val _selectedGenre = MutableStateFlow<GenreSpec?>(null)
    val selectedGenre: StateFlow<GenreSpec?> = _selectedGenre.asStateFlow()

    private val _genreGamesState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Success(emptyList()))
    val genreGamesState: StateFlow<UiState<List<CatalogGameItem>>> = _genreGamesState.asStateFlow()

    private val _explorerQuery = MutableStateFlow("")
    val explorerQuery: StateFlow<String> = _explorerQuery.asStateFlow()

    private val _rawSearchState = MutableStateFlow<UiState<List<CatalogGameItem>>>(UiState.Loading)

    private val _searchFilters = MutableStateFlow(SearchFilterState())
    val searchFilters: StateFlow<SearchFilterState> = _searchFilters.asStateFlow()

    val explorerState: StateFlow<UiState<List<CatalogGameItem>>> = combine(
        _rawSearchState,
        _searchFilters
    ) { raw, filters ->
        when (raw) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> raw
            is UiState.Success -> UiState.Success(applySearchFilters(raw.data, filters))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedCatalogPreview = MutableStateFlow<CatalogGameItem?>(null)
    val selectedCatalogPreview: StateFlow<CatalogGameItem?> = _selectedCatalogPreview.asStateFlow()

    private val _selectedGameNews = MutableStateFlow<List<NewsArticle>>(emptyList())
    val selectedGameNews: StateFlow<List<NewsArticle>> = _selectedGameNews.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    val localPreferences: StateFlow<LocalAppPreferences> = settingsDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAppPreferences())

    val preferredGenres: StateFlow<List<String>> = settingsDataStore.preferencesFlow
        .map { it.preferredGenres }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hasCompletedGenreOnboarding: StateFlow<Boolean> = settingsDataStore.preferencesFlow
        .map { it.hasCompletedGenreOnboarding }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val recentSearches: StateFlow<List<String>> = settingsDataStore.preferencesFlow
        .map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userProfile: StateFlow<UiState<UserProfile?>> = if (isGuestMode || firestoreRepo == null) {
        flowOf<UserProfile?>(null).toUiStateFlow("Guest profile")
    } else {
        firestoreRepo.observeUserProfile(currentUserId).toUiStateFlow("Failed to load profile")
    }

    val libraryGames: StateFlow<UiState<List<LibraryGame>>> = combine(
        if (isGuestMode || firestoreRepo == null) guestRepo.observeLibraryGames()
        else firestoreRepo.observeLibraryGames(currentUserId),
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

    val gameAccounts: StateFlow<UiState<List<GameAccount>>> = if (isGuestMode || firestoreRepo == null) {
        flowOf(emptyList<GameAccount>()).toUiStateFlow("Game accounts")
    } else {
        firestoreRepo.observeGameAccounts(currentUserId)
            .map { list -> list.sortedByDescending { it.lastSyncedAtMillis } }
            .toUiStateFlow("Failed to load game accounts")
    }

    val missions: StateFlow<UiState<List<GameMission>>> = (
        if (isGuestMode || firestoreRepo == null) guestRepo.observeMissions()
        else firestoreRepo.observeMissions(currentUserId)
        ).map { list ->
            list.sortedWith(
                compareBy<GameMission> { it.completed }.thenBy { it.expiresAtMillis }
            )
        }.toUiStateFlow("Failed to load missions")

    val waitlist: StateFlow<UiState<List<WaitlistGame>>> = (
        if (isGuestMode || firestoreRepo == null) guestRepo.observeWaitlist()
        else firestoreRepo.observeWaitlist(currentUserId)
        ).map { list -> list.sortedBy { it.releaseTimestampMillis } }
        .toUiStateFlow("Failed to load waitlist")

    val notifications: StateFlow<UiState<List<UserNotification>>> = (
        if (isGuestMode || firestoreRepo == null) guestRepo.observeNotifications()
        else firestoreRepo.observeNotifications(currentUserId)
        ).toUiStateFlow("Failed to load notifications")

    init {
        if (!isGuestMode) {
            bootstrapUserProfile()
        }
        refreshNews()
        refreshUpcoming()
        refreshLiveEvents()
        refreshTrending(forceRefresh = false)
        viewModelScope.launch {
            val prefs = settingsDataStore.preferencesFlow.first()
            refreshRecommended(prefs.preferredGenres)
        }
        searchExplorer("", recordHistory = false)
    }

    private fun isArabicLocale(): Boolean {
        val code = localPreferences.value.languageCode
        return code == "ar" || (code == "system" && java.util.Locale.getDefault().language == "ar")
    }

    private fun bootstrapUserProfile() {
        val repo = firestoreRepo ?: return
        viewModelScope.launch {
            val authUser = Firebase.auth.currentUser
            if (authUser != null && authUser.uid == currentUserId) {
                val lang = settingsDataStore.preferencesFlow.first().languageCode
                repo.ensureUserProfile(
                    userId = currentUserId,
                    displayName = authUser.displayName ?: if (lang == "ar") "لاعب" else "Gamer",
                    email = authUser.email,
                    photoUrl = authUser.photoUrl?.toString(),
                    defaultLang = lang
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

    fun exitGuestMode() {
        viewModelScope.launch {
            settingsDataStore.setGuestMode(false)
        }
    }

    // --- DISCOVER, GENRES, TRENDING & RECOMMENDED ---
    fun refreshTrending(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _trendingState.value = UiState.Loading
            val res = remoteRepo.getTrendingGames(forceRefresh = forceRefresh)
            _trendingState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load trending games") }
            )
        }
    }

    fun savePreferredGenres(genreIds: List<String>) {
        viewModelScope.launch {
            settingsDataStore.setPreferredGenres(genreIds, onboardingDone = true)
            refreshRecommended(genreIds)
        }
    }

    fun refreshRecommended(genreIds: List<String> = preferredGenres.value) {
        viewModelScope.launch {
            _recommendedState.value = UiState.Loading
            val res = remoteRepo.getRecommendedGames(genreIds)
            _recommendedState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load recommendations") }
            )
        }
    }

    fun selectGenre(genre: GenreSpec) {
        _selectedGenre.value = genre
        viewModelScope.launch {
            _genreGamesState.value = UiState.Loading
            val res = remoteRepo.getGamesByGenre(genre)
            _genreGamesState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to load genre games") }
            )
        }
    }

    fun clearSelectedGenre() {
        _selectedGenre.value = null
    }

    // --- SEARCH & FILTERS ---
    fun updateSearchFilters(filters: SearchFilterState) {
        _searchFilters.value = filters
    }

    fun resetSearchFilters() {
        _searchFilters.value = SearchFilterState()
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            settingsDataStore.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            settingsDataStore.clearRecentSearches()
        }
    }

    fun getSearchSuggestions(input: String): List<String> {
        val clean = input.trim()
        if (clean.isBlank()) return emptyList()
        val recent = recentSearches.value.filter { it.contains(clean, ignoreCase = true) }
        val curated = GameCatalogPresets.curatedMultiPlatformCatalog
            .map { it.title }
            .filter { it.contains(clean, ignoreCase = true) }
        val trending = (_trendingState.value as? UiState.Success)?.data
            ?.map { it.title }
            ?.filter { it.contains(clean, ignoreCase = true) }
            .orEmpty()
        return (recent + curated + trending).distinctBy { it.lowercase() }.take(6)
    }

    private fun applySearchFilters(
        items: List<CatalogGameItem>,
        filters: SearchFilterState
    ): List<CatalogGameItem> {
        return items.filter { item ->
            val matchesGenre = filters.genreId == null || run {
                val spec = GameCatalogPresets.discoverGenres.firstOrNull { it.id == filters.genreId }
                if (spec == null) true
                else {
                    val haystack = (item.genres + listOf(item.title, item.description)).joinToString(" ").lowercase()
                    item.genres.any { it.equals(spec.nameEn, ignoreCase = true) } ||
                        spec.matchingKeywords.any { kw -> haystack.contains(kw) }
                }
            }
            val matchesPlatform = filters.platform == null || item.platforms.any { p ->
                when (filters.platform) {
                    "Console" -> p.contains("PS", true) || p.contains("Xbox", true) || p.contains("Switch", true) || p.contains("Console", true)
                    else -> p.equals(filters.platform, ignoreCase = true)
                }
            }
            val matchesPricing = when (filters.pricingFilter) {
                SearchPricingFilter.ALL -> true
                SearchPricingFilter.FREE -> item.isFreeToPlay == true || item.priceDisplay.equals("Free", ignoreCase = true)
                SearchPricingFilter.PAID -> item.isFreeToPlay == false
            }
            val matchesStatus = filters.statusFilter == null || item.status == filters.statusFilter
            val matchesRelease = when (filters.releaseFilter) {
                SearchReleaseFilter.ALL -> true
                SearchReleaseFilter.UPCOMING -> item.status == GameReleaseStatus.UPCOMING
                SearchReleaseFilter.RECENT_2024_2026 -> (item.releaseYear ?: 0) in 2024..2026
                SearchReleaseFilter.MODERN_2020_2023 -> (item.releaseYear ?: 0) in 2020..2023
                SearchReleaseFilter.CLASSIC -> (item.releaseYear ?: 9999) < 2020
            }
            matchesGenre && matchesPlatform && matchesPricing && matchesStatus && matchesRelease
        }
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

    fun searchExplorer(query: String, recordHistory: Boolean = true) {
        _explorerQuery.value = query
        viewModelScope.launch {
            if (recordHistory && query.isNotBlank()) {
                settingsDataStore.addRecentSearch(query)
            }
            _rawSearchState.value = UiState.Loading
            val result = remoteRepo.searchGames(query)
            _rawSearchState.value = result.fold(
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
                    _selectedCatalogPreview.value = enriched.copy(
                        trendingReasonEn = enriched.trendingReasonEn ?: item.trendingReasonEn,
                        trendingReasonAr = enriched.trendingReasonAr ?: item.trendingReasonAr,
                        trendingRank = enriched.trendingRank ?: item.trendingRank
                    )
                }
                val news = runCatching { remoteRepo.getNewsForSteamApp(appId, item.title, 8) }
                    .getOrDefault(emptyList())
                _selectedGameNews.value = news
            } else if (item.title.contains("Minecraft", ignoreCase = true)) {
                _selectedGameNews.value = runCatching { remoteRepo.getMojangMinecraftNews() }.getOrDefault(emptyList())
            } else if (item.title.contains("Fortnite", ignoreCase = true)) {
                _selectedGameNews.value = runCatching { remoteRepo.getFortniteOfficialNews() }.getOrDefault(emptyList())
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
            val ar = isArabicLocale()
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.addOrUpdateLibraryGame(libGame)
                _actionMessage.value = if (ar) "تم حفظ ${item.title} محلياً على جهازك" else "${item.title} saved locally"
            } else {
                val res = firestoreRepo.addOrUpdateLibraryGame(currentUserId, libGame)
                _actionMessage.value = if (res.isSuccess) {
                    if (ar) "تمت إضافة ${item.title} إلى مكتبتك" else "${item.title} added to library"
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: if (ar) "فشلت إضافة اللعبة" else "Failed to add game"
                }
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
            val ar = isArabicLocale()
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.addOrUpdateLibraryGame(game)
                _actionMessage.value = if (ar) "تم حفظ ${game.title} محلياً" else "${game.title} saved locally"
            } else {
                val res = firestoreRepo.addOrUpdateLibraryGame(currentUserId, game)
                _actionMessage.value = if (res.isSuccess) {
                    if (ar) "تمت إضافة ${game.title}" else "${game.title} added"
                } else {
                    if (ar) "فشلت إضافة اللعبة" else "Failed to add game"
                }
            }
        }
    }

    fun toggleFavorite(game: LibraryGame) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateLibraryGameFields(game.id) { it.copy(favorite = !it.favorite) }
            } else {
                firestoreRepo.updateLibraryGameFields(
                    currentUserId,
                    game.id,
                    mapOf("favorite" to !game.favorite)
                )
            }
        }
    }

    fun toggleNeedsUpdate(game: LibraryGame) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateLibraryGameFields(game.id) {
                    it.copy(needsUpdate = !it.needsUpdate, lastUpdatedAtMillis = now)
                }
            } else {
                firestoreRepo.updateLibraryGameFields(
                    currentUserId,
                    game.id,
                    mapOf("needsUpdate" to !game.needsUpdate, "lastUpdatedAtMillis" to now)
                )
            }
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
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateLibraryGameFields(game.id) { g ->
                    g.copy(
                        playHours = playHours.coerceAtLeast(0L),
                        playerLevel = playerLevel.coerceAtLeast(0L),
                        achievementsUnlocked = achievementsUnlocked.coerceAtLeast(0L),
                        achievementsTotal = achievementsTotal.coerceAtLeast(0L),
                        nextUpdateDateText = nextUpdateDateText.ifBlank { g.nextUpdateDateText },
                        currentSeasonName = currentSeasonName.ifBlank { g.currentSeasonName },
                        seasonEndsAtMillis = if (seasonDaysRemaining > 0) now + seasonDaysRemaining * 86_400_000L else g.seasonEndsAtMillis,
                        lastUpdatedAtMillis = now
                    )
                }
                _actionMessage.value = "Updated ${game.title}"
            } else {
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
    }

    fun updateGameNotificationPrefs(
        game: LibraryGame,
        notifyUpdates: Boolean,
        notifySeasons: Boolean,
        notifyMissions: Boolean,
        notifyNews: Boolean
    ) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateLibraryGameFields(game.id) {
                    it.copy(
                        notifyUpdates = notifyUpdates,
                        notifySeasons = notifySeasons,
                        notifyMissions = notifyMissions,
                        notifyNews = notifyNews
                    )
                }
            } else {
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
    }

    fun removeGameFromLibrary(gameId: String) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.deleteLibraryGame(gameId)
            } else {
                firestoreRepo.deleteLibraryGame(currentUserId, gameId)
            }
        }
    }

    // --- GAME ACCOUNTS (requires signed-in Google account) ---
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
        val repo = firestoreRepo
        val ar = isArabicLocale()
        if (isGuestMode || repo == null) {
            _actionMessage.value = if (ar) {
                "سجّل الدخول بحساب Google لربط ومزامنة حسابات الألعاب."
            } else {
                "Sign in with Google to link & sync game accounts."
            }
            return
        }
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

            val saveRes = repo.upsertGameAccount(currentUserId, account)
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
        val repo = firestoreRepo ?: return
        viewModelScope.launch {
            repo.deleteGameAccount(currentUserId, accountId)
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
            val ar = isArabicLocale()
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.createMission(mission)
                _actionMessage.value = if (ar) "تم حفظ المهمة محلياً" else "Mission saved locally"
            } else {
                val res = firestoreRepo.createMission(currentUserId, mission)
                _actionMessage.value = if (res.isSuccess) {
                    if (ar) "تمت إضافة المهمة" else "Mission added"
                } else {
                    if (ar) "فشلت إضافة المهمة" else "Failed to add mission"
                }
            }
        }
    }

    fun incrementMission(mission: GameMission) {
        viewModelScope.launch {
            val next = (mission.currentProgress + 1L).coerceAtMost(mission.targetProgress)
            val done = next >= mission.targetProgress
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateMissionProgress(mission.id, next, done)
            } else {
                firestoreRepo.updateMissionProgress(currentUserId, mission.id, next, done)
            }
        }
    }

    fun toggleMissionCompleted(mission: GameMission) {
        viewModelScope.launch {
            val nextDone = !mission.completed
            val nextProg = if (nextDone) mission.targetProgress else mission.currentProgress.coerceAtMost(mission.targetProgress - 1L).coerceAtLeast(0L)
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateMissionProgress(mission.id, nextProg, nextDone)
            } else {
                firestoreRepo.updateMissionProgress(currentUserId, mission.id, nextProg, nextDone)
            }
        }
    }

    fun deleteMission(missionId: String) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.deleteMission(missionId)
            } else {
                firestoreRepo.deleteMission(currentUserId, missionId)
            }
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
            val ar = isArabicLocale()
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.addToWaitlist(waitlistGame)
                _actionMessage.value = if (ar) "تم حفظ ${item.title} في قائمة الانتظار المحلية" else "${item.title} saved to local waitlist"
            } else {
                val res = firestoreRepo.addToWaitlist(currentUserId, waitlistGame)
                _actionMessage.value = if (res.isSuccess) {
                    if (ar) "تمت إضافة ${item.title} لقائمة الانتظار" else "${item.title} added to Waitlist"
                } else {
                    if (ar) "فشلت الإضافة لقائمة الانتظار" else "Failed to add to Waitlist"
                }
            }
        }
    }

    fun toggleWaitlistAlert(item: WaitlistGame, beforeRelease: Boolean, onRelease: Boolean) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.updateWaitlistAlerts(item.id, beforeRelease, onRelease)
            } else {
                firestoreRepo.updateWaitlistNotifications(currentUserId, item.id, beforeRelease, onRelease)
            }
        }
    }

    fun removeFromWaitlist(waitlistId: String) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.removeFromWaitlist(waitlistId)
            } else {
                firestoreRepo.removeFromWaitlist(currentUserId, waitlistId)
            }
        }
    }

    // --- NOTIFICATIONS & SETTINGS ---
    fun setLanguage(code: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(code)
            if (!isGuestMode) {
                firestoreRepo?.updateUserPreferences(currentUserId, mapOf("preferredLanguage" to code))
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDarkMode(enabled)
            if (!isGuestMode) {
                firestoreRepo?.updateUserPreferences(currentUserId, mapOf("darkMode" to enabled))
            }
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
        val repo = firestoreRepo
        if (isGuestMode || repo == null) return
        viewModelScope.launch {
            repo.updateUserPreferences(
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
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.markNotificationRead(notifId)
            } else {
                firestoreRepo.markNotificationRead(currentUserId, notifId)
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.markAllNotificationsRead()
            } else {
                val current = (notifications.value as? UiState.Success)?.data.orEmpty()
                val unread = current.filter { !it.read }.map { it.id }
                if (unread.isNotEmpty()) {
                    firestoreRepo.markAllNotificationsRead(currentUserId, unread)
                }
            }
        }
    }

    fun deleteNotification(notifId: String) {
        viewModelScope.launch {
            if (isGuestMode || firestoreRepo == null) {
                guestRepo.deleteNotification(notifId)
            } else {
                firestoreRepo.deleteNotification(currentUserId, notifId)
            }
        }
    }

    fun scanAlertsNow() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentMissions = (missions.value as? UiState.Success)?.data.orEmpty()
            val currentGames = (libraryGames.value as? UiState.Success)?.data.orEmpty()
            val currentWaitlist = (waitlist.value as? UiState.Success)?.data.orEmpty()
            var createdCount = 0

            suspend fun createNotif(key: String, notif: UserNotification): Boolean {
                return if (isGuestMode || firestoreRepo == null) {
                    guestRepo.createNotificationIfMissing(key, notif)
                } else {
                    firestoreRepo.createNotificationIfMissing(currentUserId, key, notif).getOrDefault(false)
                }
            }

            for (m in currentMissions) {
                if (!m.completed && m.expiresAtMillis > now && m.expiresAtMillis - now <= 12 * 3_600_000L) {
                    val key = "scan_mission_${m.id}_${m.expiresAtMillis / 3_600_000L}"
                    if (createNotif(
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
                    ) createdCount++
                }
            }

            for (g in currentGames) {
                if (g.needsUpdate && g.notifyUpdates) {
                    val key = "scan_update_${g.id}_${g.lastUpdatedAtMillis / 86_400_000L}"
                    if (createNotif(
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
                    ) createdCount++
                }
                val sEnd = g.seasonEndsAtMillis ?: 0L
                if (g.notifySeasons && sEnd > now && sEnd - now <= 72 * 3_600_000L) {
                    val key = "scan_season_${g.id}_${sEnd / 86_400_000L}"
                    if (createNotif(
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
                    ) createdCount++
                }
            }

            for (w in currentWaitlist) {
                if (w.notifyBeforeRelease && w.releaseTimestampMillis > now && w.releaseTimestampMillis - now <= 7L * 86_400_000L) {
                    val key = "scan_wait_${w.id}"
                    if (createNotif(
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
                    ) createdCount++
                }
            }

            val ar = isArabicLocale()
            _actionMessage.value = if (createdCount > 0) {
                if (ar) "تمت إضافة $createdCount تنبيهات جديدة" else "$createdCount new alerts added"
            } else {
                if (ar) "لا توجد تنبيهات عاجلة جديدة حالياً" else "No new urgent alerts found"
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
