package com.example.data

import com.example.model.AccountApiMode
import com.example.model.AccountSyncResult
import com.example.model.CatalogGameItem
import com.example.model.GameCatalogPresets
import com.example.model.GameReleaseStatus
import com.example.model.GameSourceType
import com.example.model.GenreSpec
import com.example.model.LiveEventItem
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsFeedReport
import com.example.model.NewsVerificationLevel
import com.example.model.PlatformClassifier
import com.example.network.NetworkClient
import com.example.network.NewsRssParser
import com.example.network.RssFeedSourceSpec
import com.example.network.SteamXmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GamingRemoteRepository {

    private val cacheMutex = Mutex()
    private var cachedFreeToGameList: List<CatalogGameItem>? = null
    private var freeToGameFetchedAt: Long = 0L

    private var cachedUpcoming: List<CatalogGameItem>? = null
    private var upcomingFetchedAt: Long = 0L

    private var cachedTrending: List<CatalogGameItem>? = null
    private var trendingFetchedAt: Long = 0L

    private var cachedMobileCatalog: List<CatalogGameItem>? = null
    private var mobileCatalogFetchedAt: Long = 0L

    private var cachedPcCatalog: List<CatalogGameItem>? = null
    private var pcCatalogFetchedAt: Long = 0L

    private var cachedEvents: List<LiveEventItem>? = null
    private var eventsFetchedAt: Long = 0L

    private val detailCache = mutableMapOf<Long, CatalogGameItem>()
    private val ftgDetailCache = mutableMapOf<Long, CatalogGameItem>()
    private val genreCache = mutableMapOf<String, Pair<Long, List<CatalogGameItem>>>()

    private val verifiedRssSources: List<RssFeedSourceSpec> = listOf(
        RssFeedSourceSpec(
            id = "ps_blog",
            sourceName = "PlayStation Blog",
            feedUrl = "https://blog.playstation.com/feed/",
            isOfficial = true,
            defaultPlatformTags = listOf("PlayStation"),
            defaultCategoryHint = NewsCategory.CONSOLE_GAMES
        ),
        RssFeedSourceSpec(
            id = "xbox_wire",
            sourceName = "Xbox Wire",
            feedUrl = "https://news.xbox.com/en-us/feed/",
            isOfficial = true,
            defaultPlatformTags = listOf("Xbox"),
            defaultCategoryHint = NewsCategory.CONSOLE_GAMES
        ),
        RssFeedSourceSpec(
            id = "pc_gamer",
            sourceName = "PC Gamer",
            feedUrl = "https://www.pcgamer.com/rss/",
            isOfficial = false,
            defaultPlatformTags = listOf("PC"),
            defaultCategoryHint = NewsCategory.PC_GAMES
        ),
        RssFeedSourceSpec(
            id = "pocket_gamer",
            sourceName = "Pocket Gamer",
            feedUrl = "https://www.pocketgamer.com/news/index.rss",
            isOfficial = false,
            defaultPlatformTags = listOf("Mobile", "Android", "iOS"),
            defaultCategoryHint = NewsCategory.MOBILE_GAMES
        ),
        RssFeedSourceSpec(
            id = "toucharcade",
            sourceName = "TouchArcade",
            feedUrl = "https://toucharcade.com/feed/",
            isOfficial = false,
            defaultPlatformTags = listOf("Mobile", "iOS"),
            defaultCategoryHint = NewsCategory.MOBILE_GAMES
        ),
        RssFeedSourceSpec(
            id = "my_nintendo_news",
            sourceName = "My Nintendo News",
            feedUrl = "https://mynintendonews.com/feed",
            isOfficial = false,
            defaultPlatformTags = listOf("Nintendo"),
            defaultCategoryHint = NewsCategory.CONSOLE_GAMES
        ),
        RssFeedSourceSpec(
            id = "gematsu",
            sourceName = "Gematsu",
            feedUrl = "https://www.gematsu.com/feed",
            isOfficial = false,
            defaultPlatformTags = emptyList(),
            defaultCategoryHint = NewsCategory.ANNOUNCEMENTS_RELEASES
        ),
        RssFeedSourceSpec(
            id = "vgc",
            sourceName = "VGC",
            feedUrl = "https://www.videogameschronicle.com/feed/",
            isOfficial = false,
            defaultPlatformTags = emptyList(),
            defaultCategoryHint = null
        ),
        RssFeedSourceSpec(
            id = "eurogamer",
            sourceName = "Eurogamer",
            feedUrl = "https://www.eurogamer.net/feed",
            isOfficial = false,
            defaultPlatformTags = emptyList(),
            defaultCategoryHint = null
        ),
        RssFeedSourceSpec(
            id = "ign_games",
            sourceName = "IGN",
            feedUrl = "https://feeds.feedburner.com/ign/games-all",
            isOfficial = false,
            defaultPlatformTags = emptyList(),
            defaultCategoryHint = null
        )
    )

    suspend fun searchGames(query: String): Result<List<CatalogGameItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                val mobileDef = async { getMobileGamesCatalog(forceRefresh = false).getOrDefault(emptyList()) }
                val pcDef = async { getPcGamesCatalog(forceRefresh = false).getOrDefault(emptyList()) }
                val merged = (GameCatalogPresets.curatedMultiPlatformCatalog + mobileDef.await() + pcDef.await())
                    .distinctBy { it.title.lowercase(Locale.ROOT) }
                return@withContext Result.success(merged)
            }

            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val steamDeferred = async {
                val rawSteam = fetchSteamSearch(encoded)
                coroutineScope {
                    rawSteam.take(8).map { base ->
                        async {
                            val appId = base.steamAppId
                            if (appId != null) fetchSteamAppDetails(appId) ?: base else base
                        }
                    }.awaitAll() + rawSteam.drop(8)
                }
            }
            val itunesDeferred = async {
                runCatching { fetchItunesMobileGames(cleanQuery, limit = 12) }.getOrDefault(emptyList())
            }
            val ftgDeferred = async {
                val allFtg = getFreeToGameCatalog()
                allFtg.filter {
                    it.title.contains(cleanQuery, ignoreCase = true) ||
                        it.genres.any { g -> g.contains(cleanQuery, ignoreCase = true) }
                }.take(16)
            }
            val curatedMatches = GameCatalogPresets.curatedMultiPlatformCatalog.filter {
                it.title.contains(cleanQuery, ignoreCase = true) ||
                    it.genres.any { g -> g.contains(cleanQuery, ignoreCase = true) }
            }
            val upcomingMatches = runCatching { getUpcomingGames().getOrDefault(emptyList()) }
                .getOrDefault(emptyList())
                .filter { it.title.contains(cleanQuery, ignoreCase = true) }

            val steamResults = steamDeferred.await()
            val itunesResults = itunesDeferred.await()
            val ftgResults = ftgDeferred.await()

            val merged = LinkedHashMap<String, CatalogGameItem>()
            for (item in curatedMatches + upcomingMatches + itunesResults + steamResults + ftgResults) {
                val key = item.title.lowercase(Locale.ROOT)
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = item
                } else {
                    // Merge verified platforms if the same game appears on both Steam and Mobile
                    val combinedPlatforms = (existing.platforms + item.platforms).distinct()
                    merged[key] = existing.copy(
                        platforms = combinedPlatforms,
                        genres = existing.genres.ifEmpty { item.genres },
                        pcRequirementsMin = existing.pcRequirementsMin ?: item.pcRequirementsMin,
                        pcRequirementsRec = existing.pcRequirementsRec ?: item.pcRequirementsRec,
                        ratingScore = existing.ratingScore ?: item.ratingScore,
                        ratingCount = existing.ratingCount ?: item.ratingCount
                    )
                }
            }
            Result.success(merged.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMobileGamesCatalog(forceRefresh: Boolean = false): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if (!forceRefresh) {
                    cacheMutex.withLock {
                        val cached = cachedMobileCatalog
                        if (cached != null && now - mobileCatalogFetchedAt < 15 * 60_000L) {
                            return@withContext Result.success(cached)
                        }
                    }
                }

                val curatedMobile = GameCatalogPresets.curatedMultiPlatformCatalog
                    .filter { PlatformClassifier.isVerifiedMobile(it.platforms) }

                val liveQueries = listOf("battle royale", "rpg", "strategy", "racing", "sports")
                val fetchedLists = coroutineScope {
                    liveQueries.map { term ->
                        async {
                            runCatching { fetchItunesMobileGames(term, limit = 10) }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }.flatten()

                val merged = LinkedHashMap<String, CatalogGameItem>()
                for (item in curatedMobile + fetchedLists) {
                    if (!PlatformClassifier.isVerifiedMobile(item.platforms)) continue
                    val key = item.title.lowercase(Locale.ROOT)
                    if (!merged.containsKey(key)) {
                        merged[key] = item
                    }
                }
                val result = merged.values.toList()
                cacheMutex.withLock {
                    cachedMobileCatalog = result
                    mobileCatalogFetchedAt = now
                }
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getPcGamesCatalog(forceRefresh: Boolean = false): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if (!forceRefresh) {
                    cacheMutex.withLock {
                        val cached = cachedPcCatalog
                        if (cached != null && now - pcCatalogFetchedAt < 15 * 60_000L) {
                            return@withContext Result.success(cached)
                        }
                    }
                }

                val curatedPc = GameCatalogPresets.curatedMultiPlatformCatalog
                    .filter { PlatformClassifier.isVerifiedPc(it.platforms) }

                val trendingDef = async { runCatching { getTrendingGames(forceRefresh = false).getOrDefault(emptyList()) }.getOrDefault(emptyList()) }
                val ftgDef = async { runCatching { getFreeToGameCatalog(sortBy = "popularity").take(24) }.getOrDefault(emptyList()) }

                val pool = curatedPc + trendingDef.await() + ftgDef.await()
                val merged = LinkedHashMap<String, CatalogGameItem>()
                for (item in pool) {
                    if (!PlatformClassifier.isVerifiedPc(item.platforms)) continue
                    val key = item.title.lowercase(Locale.ROOT)
                    if (!merged.containsKey(key)) {
                        merged[key] = item
                    }
                }
                val result = merged.values.toList()
                cacheMutex.withLock {
                    cachedPcCatalog = result
                    pcCatalogFetchedAt = now
                }
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getAllPlatformGamesCatalog(forceRefresh: Boolean = false): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val mobileDef = async { getMobileGamesCatalog(forceRefresh).getOrDefault(emptyList()) }
                val pcDef = async { getPcGamesCatalog(forceRefresh).getOrDefault(emptyList()) }
                val upcomingDef = async { runCatching { getUpcomingGames().getOrDefault(emptyList()) }.getOrDefault(emptyList()) }

                val merged = LinkedHashMap<String, CatalogGameItem>()
                for (item in GameCatalogPresets.curatedMultiPlatformCatalog + mobileDef.await() + pcDef.await() + upcomingDef.await()) {
                    val key = item.title.lowercase(Locale.ROOT)
                    val existing = merged[key]
                    if (existing == null) {
                        merged[key] = item
                    } else {
                        merged[key] = existing.copy(
                            platforms = (existing.platforms + item.platforms).distinct(),
                            pcRequirementsMin = existing.pcRequirementsMin ?: item.pcRequirementsMin,
                            pcRequirementsRec = existing.pcRequirementsRec ?: item.pcRequirementsRec
                        )
                    }
                }
                Result.success(merged.values.toList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getTrendingGames(forceRefresh: Boolean = false): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if (!forceRefresh) {
                    cacheMutex.withLock {
                        val cached = cachedTrending
                        if (cached != null && now - trendingFetchedAt < 10 * 60_000L) {
                            return@withContext Result.success(cached)
                        }
                    }
                }

                val chartEntries = fetchSteamMostPlayedChart().take(10)
                val enrichedChart = coroutineScope {
                    chartEntries.map { (rank, appId, peakPlayers) ->
                        async {
                            val detail = fetchSteamAppDetails(appId)
                            val peakFormatted = if (peakPlayers >= 1000L) "${peakPlayers / 1000L}K" else peakPlayers.toString()
                            detail?.copy(
                                trendingRank = rank,
                                trendingReasonEn = "Steam Most Played #$rank • $peakFormatted peak players",
                                trendingReasonAr = "المركز #$rank على Steam • ذروة $peakFormatted لاعب"
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                val topSellers = runCatching { fetchSteamFeaturedList("top_sellers") }
                    .getOrDefault(emptyList())
                    .take(8)
                    .mapIndexed { idx, item ->
                        item.copy(
                            trendingRank = enrichedChart.size + idx + 1,
                            trendingReasonEn = "Steam Global Top Sellers",
                            trendingReasonAr = "الأكثر مبيعاً عالمياً على Steam"
                        )
                    }

                val popularFtg = runCatching { getFreeToGameCatalog(sortBy = "popularity") }
                    .getOrDefault(emptyList())
                    .take(8)
                    .mapIndexed { idx, item ->
                        item.copy(
                            trendingRank = enrichedChart.size + topSellers.size + idx + 1,
                            trendingReasonEn = "Top Free-to-Play Popularity Chart",
                            trendingReasonAr = "ضمن الألعاب المجانية الأكثر شعبية"
                        )
                    }

                val merged = (enrichedChart + topSellers + popularFtg)
                    .distinctBy { it.title.lowercase(Locale.ROOT) }
                    .take(20)
                    .mapIndexed { index, item ->
                        item.copy(trendingRank = index + 1)
                    }

                cacheMutex.withLock {
                    cachedTrending = merged
                    trendingFetchedAt = now
                }
                Result.success(merged)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getGamesByGenre(genre: GenreSpec): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                cacheMutex.withLock {
                    val cached = genreCache[genre.id]
                    if (cached != null && now - cached.first < 15 * 60_000L) {
                        return@withContext Result.success(cached.second)
                    }
                }

                val curated = GameCatalogPresets.curatedMultiPlatformCatalog.filter { item ->
                    item.genres.any { g ->
                        g.equals(genre.nameEn, ignoreCase = true) ||
                            genre.matchingKeywords.any { kw -> g.contains(kw, ignoreCase = true) }
                    } || genre.matchingKeywords.any { kw -> item.title.contains(kw, ignoreCase = true) }
                }

                val ftgDeferred = async {
                    val tag = genre.ftgCategoryTag
                    if (tag != null) {
                        runCatching { fetchFreeToGameByCategory(tag) }.getOrDefault(emptyList())
                    } else {
                        runCatching {
                            getFreeToGameCatalog().filter { item ->
                                item.genres.any { g ->
                                    genre.matchingKeywords.any { kw -> g.contains(kw, ignoreCase = true) }
                                }
                            }
                        }.getOrDefault(emptyList())
                    }
                }

                val steamDeferred = async {
                    val encoded = URLEncoder.encode(genre.steamQueryTerm, "UTF-8")
                    val raw = fetchSteamSearch(encoded).take(8)
                    coroutineScope {
                        raw.map { base ->
                            async {
                                val appId = base.steamAppId
                                if (appId != null) fetchSteamAppDetails(appId) ?: base else base
                            }
                        }.awaitAll()
                    }
                }

                val mobileDeferred = async {
                    runCatching { fetchItunesMobileGames(genre.nameEn, limit = 8) }.getOrDefault(emptyList())
                }

                val ftg = ftgDeferred.await().take(12)
                val steam = steamDeferred.await()
                val mobile = mobileDeferred.await()

                val merged = LinkedHashMap<String, CatalogGameItem>()
                for (item in curated + steam + mobile + ftg) {
                    val key = item.title.lowercase(Locale.ROOT)
                    if (!merged.containsKey(key)) {
                        merged[key] = if (item.genres.isEmpty()) item.copy(genres = listOf(genre.nameEn)) else item
                    }
                }
                val finalList = merged.values.toList()
                cacheMutex.withLock {
                    genreCache[genre.id] = now to finalList
                }
                Result.success(finalList)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getRecommendedGames(preferredGenreIds: List<String>): Result<List<CatalogGameItem>> =
        withContext(Dispatchers.IO) {
            try {
                val selectedSpecs = GameCatalogPresets.discoverGenres.filter { it.id in preferredGenreIds }
                val trending = getTrendingGames().getOrDefault(emptyList())
                val ftg = runCatching { getFreeToGameCatalog(sortBy = "popularity").take(40) }.getOrDefault(emptyList())
                val genreFetched = if (selectedSpecs.isNotEmpty()) {
                    coroutineScope {
                        selectedSpecs.take(3).map { spec ->
                            async { getGamesByGenre(spec).getOrDefault(emptyList()) }
                        }.awaitAll().flatten()
                    }
                } else emptyList()

                val pool = (GameCatalogPresets.curatedMultiPlatformCatalog + genreFetched + trending + ftg)
                    .distinctBy { it.title.lowercase(Locale.ROOT) }

                if (selectedSpecs.isEmpty()) {
                    return@withContext Result.success(pool.take(15))
                }

                val scored = pool.map { game ->
                    val haystack = (game.genres + listOf(game.title, game.description)).joinToString(" ").lowercase(Locale.ROOT)
                    val matchCount = selectedSpecs.count { spec ->
                        game.genres.any { it.equals(spec.nameEn, ignoreCase = true) } ||
                            spec.matchingKeywords.any { kw -> haystack.contains(kw) }
                    }
                    game to matchCount
                }.filter { it.second > 0 }
                    .sortedWith(
                        compareByDescending<Pair<CatalogGameItem, Int>> { it.second }
                            .thenByDescending { it.first.metacriticScore ?: 0 }
                            .thenBy { it.first.trendingRank ?: Int.MAX_VALUE }
                    )
                    .map { it.first }
                    .take(18)

                Result.success(scored.ifEmpty { trending.take(12) })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getUpcomingGames(): Result<List<CatalogGameItem>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            cacheMutex.withLock {
                val cached = cachedUpcoming
                if (cached != null && now - upcomingFetchedAt < 10 * 60_000L) {
                    return@withContext Result.success(cached)
                }
            }

            val comingSoon = fetchSteamFeaturedList("coming_soon")
            val enriched = coroutineScope {
                comingSoon.take(12).map { base ->
                    async {
                        val appId = base.steamAppId
                        if (appId != null) {
                            fetchSteamAppDetails(appId) ?: base
                        } else base
                    }
                }.awaitAll()
            }

            cacheMutex.withLock {
                cachedUpcoming = enriched
                upcomingFetchedAt = now
            }
            Result.success(enriched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGameDetailsBySteamId(appId: Long): Result<CatalogGameItem?> = withContext(Dispatchers.IO) {
        try {
            val detail = fetchSteamAppDetails(appId)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFreeToGameDetails(id: Long): Result<CatalogGameItem?> = withContext(Dispatchers.IO) {
        try {
            cacheMutex.withLock {
                ftgDetailCache[id]?.let { return@withContext Result.success(it) }
            }
            val url = "https://www.freetogame.com/api/game?id=$id"
            val jsonStr = NetworkClient.fetchJsonString(url) ?: return@withContext Result.success(null)
            val obj = JSONObject(jsonStr)
            val reqObj = obj.optJSONObject("minimum_system_requirements")
            val minSpecs = if (reqObj != null) {
                listOfNotNull(
                    reqObj.optString("os").takeIf { it.isNotBlank() }?.let { "OS: $it" },
                    reqObj.optString("processor").takeIf { it.isNotBlank() }?.let { "CPU: $it" },
                    reqObj.optString("memory").takeIf { it.isNotBlank() }?.let { "RAM: $it" },
                    reqObj.optString("graphics").takeIf { it.isNotBlank() }?.let { "GPU: $it" },
                    reqObj.optString("storage").takeIf { it.isNotBlank() }?.let { "Storage: $it" }
                ).joinToString("\n").takeIf { it.isNotBlank() }
            } else null

            val platformRaw = obj.optString("platform", "")
            val platforms = buildList {
                if (platformRaw.contains("PC", true) || platformRaw.contains("Windows", true)) add("PC")
                if (platformRaw.contains("Browser", true)) add("Web")
            }
            val parsed = CatalogGameItem(
                externalId = "ftg_$id",
                title = obj.optString("title").trim(),
                coverUrl = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                iconUrl = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                sourceType = GameSourceType.FREETOGAME,
                freeToGameId = id,
                platforms = platforms,
                genres = listOfNotNull(obj.optString("genre").takeIf { it.isNotBlank() }),
                releaseDateText = obj.optString("release_date", "Available"),
                status = GameReleaseStatus.AVAILABLE,
                description = NewsRssParser.stripHtml(obj.optString("description")).ifBlank {
                    obj.optString("short_description").trim()
                },
                officialUrl = obj.optString("game_url").takeIf { it.isNotBlank() },
                communityUrl = obj.optString("freetogame_profile_url").takeIf { it.isNotBlank() },
                developer = obj.optString("developer").takeIf { it.isNotBlank() },
                publisher = obj.optString("publisher").takeIf { it.isNotBlank() },
                isFreeToPlay = true,
                priceDisplay = "Free",
                pcRequirementsMin = minSpecs
            )
            cacheMutex.withLock {
                ftgDetailCache[id] = parsed
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNewsForSteamApp(appId: Long, gameTitle: String, count: Int = 10): List<NewsArticle> =
        withContext(Dispatchers.IO) {
            val url = "https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=$appId&count=$count&maxlength=360"
            val jsonStr = NetworkClient.fetchJsonString(url) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)
            val appNews = root.optJSONObject("appnews") ?: return@withContext emptyList()
            val items = appNews.optJSONArray("newsitems") ?: return@withContext emptyList()
            val fallbackCover = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val gid = obj.optString("gid")
                    val title = obj.optString("title").trim()
                    val articleUrl = obj.optString("url").trim()
                    val feedLabel = obj.optString("feedlabel").ifBlank { obj.optString("author").ifBlank { "Steam News" } }
                    val feedName = obj.optString("feedname")
                    val isOfficial = feedName == "steam_community_announcements" ||
                        feedLabel.contains("Announcements", ignoreCase = true) ||
                        obj.optInt("feed_type", 0) == 1
                    val rawContents = obj.optString("contents")
                    val summary = cleanNewsBody(rawContents)
                    val dateSec = obj.optLong("date", 0L)
                    val extractedImg = extractFirstImageUrl(rawContents) ?: fallbackCover

                    if (title.isNotBlank() && NewsRssParser.isValidHttpUrl(articleUrl)) {
                        val haystack = "$title $summary".lowercase(Locale.ROOT)
                        val cat = when {
                            haystack.contains("esports") || haystack.contains("tournament") || haystack.contains("major") ->
                                NewsCategory.ESPORTS_TOURNAMENTS
                            haystack.contains("patch") || haystack.contains("update") || haystack.contains("hotfix") ||
                                haystack.contains("season") || haystack.contains("notes") ->
                                NewsCategory.GAME_UPDATES
                            else -> NewsCategory.PC_GAMES
                        }
                        add(
                            NewsArticle(
                                id = "steam_${appId}_$gid",
                                title = title,
                                summary = summary.ifBlank { title },
                                sourceName = feedLabel,
                                isOfficial = isOfficial,
                                imageUrl = extractedImg,
                                articleUrl = articleUrl,
                                publishedAtMillis = dateSec * 1000L,
                                gameTitle = gameTitle,
                                gameKey = "steam_$appId",
                                steamAppId = appId,
                                category = cat,
                                verificationLevel = if (isOfficial) NewsVerificationLevel.OFFICIAL else NewsVerificationLevel.VERIFIED_MEDIA,
                                platformTags = listOf("PC", "Steam")
                            )
                        )
                    }
                }
            }
        }

    suspend fun getMojangMinecraftNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val jsonStr = NetworkClient.fetchJsonString("https://launchercontent.mojang.com/news.json")
            ?: return@withContext emptyList()
        val root = JSONObject(jsonStr)
        val entries = root.optJSONArray("entries") ?: return@withContext emptyList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        buildList {
            for (i in 0 until minOf(entries.length(), 10)) {
                val obj = entries.optJSONObject(i) ?: continue
                val id = obj.optString("id", "mc_$i")
                val title = obj.optString("title").trim()
                val text = obj.optString("text").trim()
                val readMore = obj.optString("readMoreLink").ifBlank { "https://www.minecraft.net/en-us/articles" }
                val dateStr = obj.optString("date")
                val publishedMillis = runCatching { dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis() }
                    .getOrDefault(System.currentTimeMillis())
                val imgObj = obj.optJSONObject("newsPageImage") ?: obj.optJSONObject("playPageImage")
                val imgPath = imgObj?.optString("url")
                val imageUrl = when {
                    imgPath.isNullOrBlank() -> null
                    imgPath.startsWith("http") -> imgPath
                    else -> "https://launchercontent.mojang.com$imgPath"
                }

                if (title.isNotBlank() && NewsRssParser.isValidHttpUrl(readMore)) {
                    val cat = if (title.contains("snapshot", true) || title.contains("update", true) ||
                        title.contains("patch", true) || title.contains("release", true)
                    ) NewsCategory.GAME_UPDATES else NewsCategory.ANNOUNCEMENTS_RELEASES
                    add(
                        NewsArticle(
                            id = "mojang_$id",
                            title = title,
                            summary = text.ifBlank { title },
                            sourceName = "Mojang Official",
                            isOfficial = true,
                            imageUrl = imageUrl,
                            articleUrl = readMore,
                            publishedAtMillis = publishedMillis,
                            gameTitle = "Minecraft",
                            gameKey = "minecraft",
                            category = cat,
                            verificationLevel = NewsVerificationLevel.OFFICIAL,
                            platformTags = listOf("PC", "Mobile", "Console")
                        )
                    )
                }
            }
        }
    }

    suspend fun getFortniteOfficialNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val jsonStr = NetworkClient.fetchJsonString("https://fortnite-api.com/v2/news")
            ?: return@withContext emptyList()
        val root = JSONObject(jsonStr)
        val data = root.optJSONObject("data") ?: return@withContext emptyList()
        val br = data.optJSONObject("br") ?: return@withContext emptyList()
        val dateStr = br.optString("date")
        val publishedMillis = parseIsoDate(dateStr) ?: System.currentTimeMillis()
        val motds = br.optJSONArray("motds") ?: JSONArray()

        buildList {
            for (i in 0 until motds.length()) {
                val obj = motds.optJSONObject(i) ?: continue
                val id = obj.optString("id", "fn_$i")
                val title = obj.optString("title").trim()
                val body = obj.optString("body").trim()
                val img = obj.optString("image").takeIf { it.isNotBlank() }
                if (title.isNotBlank()) {
                    add(
                        NewsArticle(
                            id = "fortnite_$id",
                            title = title,
                            summary = body.ifBlank { title },
                            sourceName = "Fortnite Official Feed",
                            isOfficial = true,
                            imageUrl = img,
                            articleUrl = "https://www.fortnite.com/news",
                            publishedAtMillis = publishedMillis,
                            gameTitle = "Fortnite",
                            gameKey = "fortnite",
                            category = NewsCategory.GAME_UPDATES,
                            verificationLevel = NewsVerificationLevel.OFFICIAL,
                            platformTags = listOf("PC", "Mobile", "PlayStation", "Xbox", "Nintendo")
                        )
                    )
                }
            }
        }
    }

    suspend fun getEpicFreeGamesDealsNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val url = "https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US&country=US&allowCountries=US"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return@withContext emptyList()
        val root = JSONObject(jsonStr)
        val elements = root.optJSONObject("data")
            ?.optJSONObject("Catalog")
            ?.optJSONObject("searchStore")
            ?.optJSONArray("elements")
            ?: return@withContext emptyList()

        buildList {
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val title = el.optString("title").trim()
                val slug = el.optString("productSlug").ifBlank { el.optString("urlSlug") }.trim()
                val promotions = el.optJSONObject("promotions")
                val activeOffers = promotions?.optJSONArray("promotionalOffers")
                val upcomingOffers = promotions?.optJSONArray("upcomingPromotionalOffers")
                val hasActiveDiscount = activeOffers != null && activeOffers.length() > 0
                val hasUpcoming = upcomingOffers != null && upcomingOffers.length() > 0
                if (!hasActiveDiscount && !hasUpcoming) continue

                val offerObj = (if (hasActiveDiscount) activeOffers else upcomingOffers)
                    ?.optJSONObject(0)
                    ?.optJSONArray("promotionalOffers")
                    ?.optJSONObject(0)
                val discountPct = offerObj?.optJSONObject("discountSetting")?.optInt("discountPercentage", 100) ?: 100
                if (discountPct != 0 && !hasActiveDiscount) continue

                val keyImages = el.optJSONArray("keyImages")
                var imageUrl: String? = null
                if (keyImages != null) {
                    for (j in 0 until keyImages.length()) {
                        val imgObj = keyImages.optJSONObject(j) ?: continue
                        val candidate = imgObj.optString("url")
                        if (NewsRssParser.isValidHttpUrl(candidate)) {
                            imageUrl = candidate
                            if (imgObj.optString("type").contains("Wide", true)) break
                        }
                    }
                }

                val startDate = offerObj?.optString("startDate").orEmpty()
                val publishedAt = parseIsoDate(startDate) ?: System.currentTimeMillis()
                val desc = el.optString("description").trim().ifBlank { "Official Epic Games Store free promotion" }
                val articleUrl = if (slug.isNotBlank()) "https://store.epicgames.com/en-US/p/${slug.substringBefore("/")}"
                else "https://store.epicgames.com/en-US/free-games"

                if (title.isNotBlank()) {
                    val headline = if (hasActiveDiscount) {
                        "Free on Epic Games Store: $title"
                    } else {
                        "Upcoming Free Game on Epic Store: $title"
                    }
                    add(
                        NewsArticle(
                            id = "epic_free_${el.optString("id", i.toString())}",
                            title = headline,
                            summary = desc.take(260),
                            sourceName = "Epic Games Store Official",
                            isOfficial = true,
                            imageUrl = imageUrl,
                            articleUrl = articleUrl,
                            publishedAtMillis = publishedAt,
                            gameTitle = title,
                            gameKey = "epic_$i",
                            category = NewsCategory.DEALS_FREE,
                            verificationLevel = NewsVerificationLevel.OFFICIAL,
                            platformTags = listOf("PC", "Epic Games")
                        )
                    )
                }
            }
        }
    }

    suspend fun getGlobalGamingNewsReport(
        trackedSteamApps: List<Pair<Long, String>>,
        includeMinecraft: Boolean = true,
        includeFortnite: Boolean = true
    ): Result<NewsFeedReport> = withContext(Dispatchers.IO) {
        try {
            val targets = if (trackedSteamApps.isNotEmpty()) {
                (trackedSteamApps + GameCatalogPresets.defaultNewsSteamAppIds).distinctBy { it.first }.take(10)
            } else {
                GameCatalogPresets.defaultNewsSteamAppIds
            }

            val activeSources = mutableSetOf<String>()
            val failedSources = mutableSetOf<String>()

            val collected = coroutineScope {
                val steamJobs = targets.map { (appId, title) ->
                    async {
                        val items = runCatching { getNewsForSteamApp(appId, title, count = 4) }.getOrNull()
                        appId to items
                    }
                }
                val mcJob = async {
                    if (!includeMinecraft) null
                    else runCatching { getMojangMinecraftNews() }.getOrNull()
                }
                val fnJob = async {
                    if (!includeFortnite) null
                    else runCatching { getFortniteOfficialNews() }.getOrNull()
                }
                val epicDealsJob = async {
                    runCatching { getEpicFreeGamesDealsNews() }.getOrNull()
                }
                val giveawayDealsJob = async {
                    runCatching { getGamerPowerDealArticles() }.getOrNull()
                }
                val rssJobs = verifiedRssSources.map { spec ->
                    async {
                        spec to runCatching {
                            val xml = NetworkClient.fetchXmlOrFeedString(spec.feedUrl) ?: return@runCatching null
                            NewsRssParser.parseFeed(xml, spec, maxItems = 10)
                        }.getOrNull()
                    }
                }

                val all = mutableListOf<NewsArticle>()

                val steamResults = steamJobs.awaitAll()
                if (steamResults.any { !it.second.isNullOrEmpty() }) {
                    activeSources.add("Steam Official News")
                    steamResults.forEach { it.second?.let(all::addAll) }
                } else {
                    failedSources.add("Steam Official News")
                }

                if (includeMinecraft) {
                    val mc = mcJob.await()
                    if (!mc.isNullOrEmpty()) {
                        activeSources.add("Mojang Official")
                        all.addAll(mc)
                    } else failedSources.add("Mojang Official")
                }

                if (includeFortnite) {
                    val fn = fnJob.await()
                    if (!fn.isNullOrEmpty()) {
                        activeSources.add("Fortnite Official Feed")
                        all.addAll(fn)
                    } else failedSources.add("Fortnite Official Feed")
                }

                val epicDeals = epicDealsJob.await()
                if (!epicDeals.isNullOrEmpty()) {
                    activeSources.add("Epic Games Store Official")
                    all.addAll(epicDeals)
                } else {
                    failedSources.add("Epic Games Store Official")
                }

                val giveaways = giveawayDealsJob.await()
                if (!giveaways.isNullOrEmpty()) {
                    activeSources.add("GamerPower Deals")
                    all.addAll(giveaways)
                }

                for ((spec, items) in rssJobs.awaitAll()) {
                    if (!items.isNullOrEmpty()) {
                        activeSources.add(spec.sourceName)
                        all.addAll(items)
                    } else {
                        failedSources.add(spec.sourceName)
                    }
                }
                all
            }

            if (collected.isEmpty() && activeSources.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Unable to reach any verified news feed. Check internet connection.")
                )
            }

            val deduped = NewsRssParser.deduplicateNews(collected)
            Result.success(
                NewsFeedReport(
                    articles = deduped,
                    activeSourceNames = activeSources.toList().sorted(),
                    failedSourceNames = failedSources.toList().sorted(),
                    lastRefreshedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAggregatedNews(
        trackedSteamApps: List<Pair<Long, String>>,
        includeMinecraft: Boolean = true,
        includeFortnite: Boolean = true
    ): Result<List<NewsArticle>> =
        getGlobalGamingNewsReport(trackedSteamApps, includeMinecraft, includeFortnite)
            .map { it.articles }

    private fun getGamerPowerDealArticles(): List<NewsArticle> {
        val jsonStr = NetworkClient.fetchJsonString("https://www.gamerpower.com/api/giveaways?sort-by=date")
            ?: return emptyList()
        val arr = JSONArray(jsonStr)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildList {
            for (i in 0 until minOf(arr.length(), 10)) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optLong("id", i.toLong())
                val title = obj.optString("title").trim()
                val desc = obj.optString("description").trim()
                val img = obj.optString("image").takeIf { NewsRssParser.isValidHttpUrl(it) }
                val url = obj.optString("open_giveaway_url").ifBlank { obj.optString("gamerpower_url") }.trim()
                val pubStr = obj.optString("published_date")
                val publishedAt = runCatching { dateFmt.parse(pubStr)?.time }.getOrNull() ?: System.currentTimeMillis()
                val plats = obj.optString("platforms", "")
                val platTags = buildList {
                    if (plats.contains("PC", true) || plats.contains("Steam", true) || plats.contains("Epic", true)) add("PC")
                    if (plats.contains("Android", true) || plats.contains("iOS", true) || plats.contains("Mobile", true)) add("Mobile")
                    if (plats.contains("PlayStation", true) || plats.contains("PS", true)) add("PlayStation")
                    if (plats.contains("Xbox", true)) add("Xbox")
                    if (plats.contains("Switch", true)) add("Nintendo")
                }
                if (title.isNotBlank() && NewsRssParser.isValidHttpUrl(url)) {
                    add(
                        NewsArticle(
                            id = "gp_deal_$id",
                            title = title,
                            summary = desc.take(260),
                            sourceName = "GamerPower",
                            isOfficial = false,
                            imageUrl = img,
                            articleUrl = url,
                            publishedAtMillis = publishedAt,
                            gameTitle = title.substringBefore("(").trim().take(50),
                            gameKey = "deal_$id",
                            category = NewsCategory.DEALS_FREE,
                            verificationLevel = NewsVerificationLevel.VERIFIED_MEDIA,
                            platformTags = platTags
                        )
                    )
                }
            }
        }
    }

    suspend fun getLiveGamingEvents(): Result<List<LiveEventItem>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            cacheMutex.withLock {
                val cached = cachedEvents
                if (cached != null && now - eventsFetchedAt < 15 * 60_000L) {
                    return@withContext Result.success(cached)
                }
            }

            val jsonStr = NetworkClient.fetchJsonString("https://www.gamerpower.com/api/giveaways?sort-by=date")
                ?: return@withContext Result.success(emptyList())
            val arr = JSONArray(jsonStr)
            val events = buildList {
                for (i in 0 until minOf(arr.length(), 15)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", i.toLong()).toString()
                    val title = obj.optString("title").trim()
                    val desc = obj.optString("description").trim()
                    val img = obj.optString("image").takeIf { it.isNotBlank() }
                    val platforms = obj.optString("platforms", "")
                    val endDate = obj.optString("end_date", "Limited Time").let {
                        if (it.equals("N/A", ignoreCase = true)) "Limited Time" else it
                    }
                    val openUrl = obj.optString("open_giveaway_url").ifBlank { obj.optString("gamerpower_url") }
                    val type = obj.optString("type", "Drop / Event")
                    if (title.isNotBlank()) {
                        add(LiveEventItem(id, title, desc, img, platforms, endDate, openUrl, type))
                    }
                }
            }

            cacheMutex.withLock {
                cachedEvents = events
                eventsFetchedAt = now
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncGameAccount(
        gameKey: String,
        playerIdentifier: String
    ): Result<AccountSyncResult> = withContext(Dispatchers.IO) {
        try {
            val cleanId = playerIdentifier.trim()
            require(cleanId.isNotBlank()) { "Player ID cannot be empty" }

            when (gameKey) {
                "minecraft" -> {
                    val encoded = URLEncoder.encode(cleanId, "UTF-8")
                    val jsonStr = NetworkClient.fetchJsonString(
                        "https://api.mojang.com/users/profiles/minecraft/$encoded"
                    ) ?: return@withContext Result.failure(
                        IllegalStateException("Minecraft Java username '$cleanId' not found on Mojang API.")
                    )
                    val obj = JSONObject(jsonStr)
                    val uuid = obj.optString("id")
                    val canonicalName = obj.optString("name", cleanId)
                    val avatarUrl = "https://mc-heads.net/avatar/$uuid/128"
                    Result.success(
                        AccountSyncResult(
                            playerIdentifier = canonicalName,
                            verifiedPlayerId = uuid,
                            avatarUrl = avatarUrl,
                            level = null,
                            rankTier = null,
                            statsSummary = "Verified Mojang Java Account • UUID: ${uuid.take(8)}…",
                            achievementsText = "Mojang public profile does not expose server-level stats.",
                            apiMode = AccountApiMode.LIVE_OFFICIAL_API,
                            availabilityNote = "Verified live via api.mojang.com"
                        )
                    )
                }

                "steam" -> {
                    val profile = SteamXmlParser.fetchPublicProfile(cleanId)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Steam profile '$cleanId' not found or unavailable.")
                        )
                    val isPublic = profile.privacyState.equals("public", ignoreCase = true)
                    val stats = buildString {
                        append("Status: ${profile.onlineState ?: "Unknown"}")
                        if (!profile.hoursLastTwoWeeks.isNullOrBlank()) {
                            append(" • Last 2w: ${profile.hoursLastTwoWeeks}h")
                        }
                        if (!profile.recentGamesSummary.isNullOrBlank()) {
                            append("\nRecent: ${profile.recentGamesSummary}")
                        }
                        if (!isPublic) {
                            append("\nNote: Profile privacy is '${profile.privacyState ?: "private"}'. Set Steam Game Details to Public for recent hours.")
                        }
                    }
                    Result.success(
                        AccountSyncResult(
                            playerIdentifier = profile.personaName,
                            verifiedPlayerId = profile.steamId64,
                            avatarUrl = profile.avatarUrl,
                            level = null,
                            rankTier = profile.onlineState,
                            statsSummary = stats.take(400),
                            achievementsText = profile.recentGamesSummary,
                            apiMode = AccountApiMode.LIVE_COMMUNITY_XML,
                            availabilityNote = "Fetched live from Steam Community public XML."
                        )
                    )
                }

                else -> {
                    val spec = GameCatalogPresets.supportedAccountGames.firstOrNull { it.gameKey == gameKey }
                    Result.success(
                        AccountSyncResult(
                            playerIdentifier = cleanId,
                            verifiedPlayerId = null,
                            avatarUrl = null,
                            level = null,
                            rankTier = null,
                            statsSummary = null,
                            achievementsText = null,
                            apiMode = AccountApiMode.MANUAL_NO_PUBLIC_API,
                            availabilityNote = spec?.availabilitySummaryEn
                                ?: "No public unauthenticated stats API; Player ID stored securely for manual tracking."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchItunesMobileGames(term: String, limit: Int = 12): List<CatalogGameItem> {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&entity=software&genreId=6014&country=us&limit=$limit"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("results") ?: return emptyList()

        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val trackId = obj.optLong("trackId", -1L)
                if (trackId <= 0L) continue
                val title = obj.optString("trackName").trim()
                if (title.isBlank()) continue
                val artwork = obj.optString("artworkUrl512")
                    .ifBlank { obj.optString("artworkUrl100") }
                    .takeIf { it.isNotBlank() }
                val shotsArr = obj.optJSONArray("screenshotUrls")
                val screenshots = buildList {
                    if (shotsArr != null) {
                        for (j in 0 until minOf(shotsArr.length(), 4)) {
                            val s = shotsArr.optString(j)
                            if (s.isNotBlank()) add(s)
                        }
                    }
                }
                val cover = screenshots.firstOrNull() ?: artwork
                val genresArr = obj.optJSONArray("genres")
                val genres = buildList {
                    if (genresArr != null) {
                        for (j in 0 until genresArr.length()) {
                            val g = genresArr.optString(j)
                            if (g.isNotBlank() && !g.equals("Games", ignoreCase = true)) add(g)
                        }
                    }
                }.ifEmpty { listOf("Mobile") }
                val price = obj.optDouble("price", -1.0)
                val formattedPrice = obj.optString("formattedPrice").takeIf { it.isNotBlank() }
                val isFree = price == 0.0 || formattedPrice.equals("Free", ignoreCase = true)
                val rating = obj.optDouble("averageUserRating", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
                val ratingCount = obj.optInt("userRatingCount", 0).takeIf { it > 0 }
                val releaseDate = obj.optString("releaseDate").take(10).ifBlank { "App Store" }
                val releaseYear = releaseDate.take(4).toIntOrNull()
                val dev = obj.optString("artistName").ifBlank { obj.optString("sellerName") }.takeIf { it.isNotBlank() }
                val storeUrl = obj.optString("trackViewUrl").takeIf { it.isNotBlank() }
                val desc = obj.optString("description").replace(Regex("\\s+"), " ").trim().take(360)

                add(
                    CatalogGameItem(
                        externalId = "ios_$trackId",
                        title = title,
                        coverUrl = cover,
                        iconUrl = artwork,
                        sourceType = GameSourceType.ITUNES,
                        // Strictly label verified source platform (iOS App Store) without fabricating Android
                        platforms = listOf("iOS", "Mobile"),
                        genres = genres,
                        releaseDateText = releaseDate,
                        releaseYear = releaseYear,
                        status = GameReleaseStatus.AVAILABLE,
                        description = desc,
                        screenshots = screenshots,
                        officialUrl = storeUrl,
                        developer = dev,
                        publisher = dev,
                        isFreeToPlay = if (price >= 0.0) isFree else null,
                        priceDisplay = formattedPrice ?: if (isFree) "Free" else null,
                        ratingScore = rating,
                        ratingCount = ratingCount
                    )
                )
            }
        }
    }

    private suspend fun getFreeToGameCatalog(sortBy: String = "relevance"): List<CatalogGameItem> {
        val now = System.currentTimeMillis()
        if (sortBy == "relevance") {
            cacheMutex.withLock {
                val cached = cachedFreeToGameList
                if (cached != null && now - freeToGameFetchedAt < 30 * 60_000L) {
                    return cached
                }
            }
        }
        val url = if (sortBy == "relevance") "https://www.freetogame.com/api/games"
        else "https://www.freetogame.com/api/games?sort-by=$sortBy"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        val parsed = parseFreeToGameArray(JSONArray(jsonStr))
        if (sortBy == "relevance") {
            cacheMutex.withLock {
                cachedFreeToGameList = parsed
                freeToGameFetchedAt = now
            }
        }
        return parsed
    }

    private fun fetchFreeToGameByCategory(category: String): List<CatalogGameItem> {
        val url = "https://www.freetogame.com/api/games?category=${URLEncoder.encode(category, "UTF-8")}&sort-by=popularity"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        return parseFreeToGameArray(JSONArray(jsonStr))
    }

    private fun parseFreeToGameArray(arr: JSONArray): List<CatalogGameItem> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optLong("id", -1L)
            if (id < 0) continue
            val title = obj.optString("title").trim()
            val thumb = obj.optString("thumbnail").takeIf { it.isNotBlank() }
            val desc = obj.optString("short_description").trim()
            val genre = obj.optString("genre").trim()
            val platformRaw = obj.optString("platform", "")
            // Strictly extract verified platforms; leave empty when unspecified instead of guessing
            val platforms = buildList {
                if (platformRaw.contains("PC", ignoreCase = true) || platformRaw.contains("Windows", ignoreCase = true)) {
                    add("PC")
                }
                if (platformRaw.contains("Browser", ignoreCase = true)) {
                    add("Web")
                }
            }
            val releaseDate = obj.optString("release_date", "Available")
            val year = Regex("\\b(19|20)\\d{2}\\b").find(releaseDate)?.value?.toIntOrNull()
            val gameUrl = obj.optString("game_url").takeIf { it.isNotBlank() }
            val profileUrl = obj.optString("freetogame_profile_url").takeIf { it.isNotBlank() }
            val dev = obj.optString("developer").takeIf { it.isNotBlank() }
            val pub = obj.optString("publisher").takeIf { it.isNotBlank() }

            add(
                CatalogGameItem(
                    externalId = "ftg_$id",
                    title = title,
                    coverUrl = thumb,
                    iconUrl = thumb,
                    sourceType = GameSourceType.FREETOGAME,
                    freeToGameId = id,
                    platforms = platforms,
                    genres = if (genre.isNotBlank()) listOf(genre) else emptyList(),
                    releaseDateText = releaseDate,
                    releaseYear = year,
                    status = GameReleaseStatus.AVAILABLE,
                    description = desc,
                    officialUrl = gameUrl,
                    communityUrl = profileUrl,
                    developer = dev,
                    publisher = pub,
                    isFreeToPlay = true,
                    priceDisplay = "Free"
                )
            )
        }
    }

    private fun fetchSteamMostPlayedChart(): List<Triple<Int, Long, Long>> {
        val url = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        val root = JSONObject(jsonStr)
        val response = root.optJSONObject("response") ?: return emptyList()
        val ranks = response.optJSONArray("ranks") ?: return emptyList()
        return buildList {
            for (i in 0 until ranks.length()) {
                val obj = ranks.optJSONObject(i) ?: continue
                val rank = obj.optInt("rank", i + 1)
                val appId = obj.optLong("appid", -1L)
                val peak = obj.optLong("peak_in_game", 0L)
                if (appId > 0) add(Triple(rank, appId, peak))
            }
        }
    }

    private fun fetchSteamSearch(encodedQuery: String): List<CatalogGameItem> {
        val url = "https://store.steampowered.com/api/storesearch/?term=$encodedQuery&l=english&cc=US"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        val root = JSONObject(jsonStr)
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optLong("id", -1L)
                if (id <= 0) continue
                val name = obj.optString("name").trim()
                val tinyImg = obj.optString("tiny_image").takeIf { it.isNotBlank() }
                val headerImg = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"
                val platObj = obj.optJSONObject("platforms")
                val platforms = buildList {
                    if (platObj?.optBoolean("windows", false) == true) {
                        add("PC")
                        add("Windows")
                    }
                    if (platObj?.optBoolean("mac", false) == true) add("Mac")
                    if (platObj?.optBoolean("linux", false) == true) add("Linux")
                }
                val priceObj = obj.optJSONObject("price")
                val finalCents = priceObj?.optInt("final", -1) ?: -1
                val isFree = finalCents == 0
                val priceStr = when {
                    finalCents == 0 -> "Free"
                    finalCents > 0 -> "$${String.format(Locale.US, "%.2f", finalCents / 100.0)}"
                    else -> null
                }

                add(
                    CatalogGameItem(
                        externalId = "steam_$id",
                        title = name,
                        coverUrl = headerImg,
                        iconUrl = tinyImg ?: headerImg,
                        sourceType = GameSourceType.STEAM,
                        steamAppId = id,
                        platforms = platforms,
                        releaseDateText = "Steam Store",
                        status = GameReleaseStatus.AVAILABLE,
                        officialUrl = "https://store.steampowered.com/app/$id",
                        communityUrl = "https://steamcommunity.com/app/$id",
                        isFreeToPlay = if (finalCents >= 0) isFree else null,
                        priceDisplay = priceStr
                    )
                )
            }
        }
    }

    private fun fetchSteamFeaturedList(categoryKey: String): List<CatalogGameItem> {
        val url = "https://store.steampowered.com/api/featuredcategories/?cc=US&l=english"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return emptyList()
        val root = JSONObject(jsonStr)
        val catObj = root.optJSONObject(categoryKey) ?: return emptyList()
        val items = catObj.optJSONArray("items") ?: return emptyList()

        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val id = obj.optLong("id", -1L)
                if (id <= 0) continue
                val name = obj.optString("name").trim()
                val header = obj.optString("large_capsule_image")
                    .ifBlank { obj.optString("header_image") }
                    .ifBlank { "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg" }
                val small = obj.optString("small_capsule_image").ifBlank { header }
                val isComingSoon = categoryKey == "coming_soon"
                val finalPrice = obj.optInt("final_price", -1)
                val discountPct = obj.optInt("discount_percent", 0)
                val isFree = finalPrice == 0

                val platforms = buildList {
                    if (obj.optBoolean("windows_available", false)) {
                        add("PC")
                        add("Windows")
                    }
                    if (obj.optBoolean("mac_available", false)) add("Mac")
                    if (obj.optBoolean("linux_available", false)) add("Linux")
                }

                add(
                    CatalogGameItem(
                        externalId = "steam_$id",
                        title = name,
                        coverUrl = header,
                        iconUrl = small,
                        sourceType = GameSourceType.STEAM,
                        steamAppId = id,
                        platforms = platforms,
                        releaseDateText = if (isComingSoon) "Coming Soon" else "Available",
                        status = if (isComingSoon) GameReleaseStatus.UPCOMING else GameReleaseStatus.AVAILABLE,
                        officialUrl = "https://store.steampowered.com/app/$id",
                        communityUrl = "https://steamcommunity.com/app/$id",
                        isFreeToPlay = if (finalPrice >= 0) isFree else null,
                        priceDisplay = when {
                            finalPrice == 0 -> "Free"
                            finalPrice > 0 && discountPct > 0 -> "$${String.format(Locale.US, "%.2f", finalPrice / 100.0)} (-$discountPct%)"
                            finalPrice > 0 -> "$${String.format(Locale.US, "%.2f", finalPrice / 100.0)}"
                            else -> null
                        }
                    )
                )
            }
        }
    }

    private suspend fun fetchSteamAppDetails(appId: Long): CatalogGameItem? {
        cacheMutex.withLock {
            detailCache[appId]?.let { return it }
        }
        val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=english"
        val jsonStr = NetworkClient.fetchJsonString(url) ?: return null
        val root = JSONObject(jsonStr)
        val appWrapper = root.optJSONObject(appId.toString()) ?: return null
        if (!appWrapper.optBoolean("success", false)) return null
        val data = appWrapper.optJSONObject("data") ?: return null

        val name = data.optString("name").trim()
        val header = data.optString("header_image").takeIf { it.isNotBlank() }
        val desc = cleanNewsBody(data.optString("short_description"))
        val website = data.optString("website").takeIf { it.isNotBlank() }
            ?: "https://store.steampowered.com/app/$appId"

        val releaseObj = data.optJSONObject("release_date")
        val comingSoon = releaseObj?.optBoolean("coming_soon", false) ?: false
        val releaseText = releaseObj?.optString("date").orEmpty().ifBlank {
            if (comingSoon) "Coming Soon" else "Available"
        }
        val releaseYear = Regex("\\b(19|20)\\d{2}\\b").find(releaseText)?.value?.toIntOrNull()

        val status = when {
            releaseText.contains("cancel", ignoreCase = true) -> GameReleaseStatus.CANCELLED
            releaseText.contains("delay", ignoreCase = true) -> GameReleaseStatus.DELAYED
            comingSoon -> GameReleaseStatus.UPCOMING
            else -> GameReleaseStatus.AVAILABLE
        }

        val platObj = data.optJSONObject("platforms")
        val platforms = buildList {
            if (platObj?.optBoolean("windows", false) == true) {
                add("PC")
                add("Windows")
            }
            if (platObj?.optBoolean("mac", false) == true) add("Mac")
            if (platObj?.optBoolean("linux", false) == true) add("Linux")
        }

        val pcReqObj = data.optJSONObject("pc_requirements")
        val pcReqMin = pcReqObj?.optString("minimum")?.let(::formatPcRequirementsHtml)?.takeIf { it.isNotBlank() }
        val pcReqRec = pcReqObj?.optString("recommended")?.let(::formatPcRequirementsHtml)?.takeIf { it.isNotBlank() }

        val genresArr = data.optJSONArray("genres")
        val genres = buildList {
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optJSONObject(i)?.optString("description")
                    if (!g.isNullOrBlank()) add(g)
                }
            }
        }

        val screenshotsArr = data.optJSONArray("screenshots")
        val screenshots = buildList {
            if (screenshotsArr != null) {
                for (i in 0 until minOf(screenshotsArr.length(), 6)) {
                    val path = screenshotsArr.optJSONObject(i)?.optString("path_thumbnail")
                    if (!path.isNullOrBlank()) add(path)
                }
            }
        }

        val moviesArr = data.optJSONArray("movies")
        var trailerUrl: String? = null
        if (moviesArr != null && moviesArr.length() > 0) {
            val firstMovie = moviesArr.optJSONObject(0)
            val mp4Obj = firstMovie?.optJSONObject("mp4")
            val webmObj = firstMovie?.optJSONObject("webm")
            trailerUrl = mp4Obj?.optString("480")?.takeIf { it.isNotBlank() }
                ?: webmObj?.optString("480")?.takeIf { it.isNotBlank() }
        }

        val devsArr = data.optJSONArray("developers")
        val dev = devsArr?.optString(0)?.takeIf { it.isNotBlank() }
        val pubsArr = data.optJSONArray("publishers")
        val pub = pubsArr?.optString(0)?.takeIf { it.isNotBlank() }

        val isFree = data.optBoolean("is_free", false)
        val priceFormatted = data.optJSONObject("price_overview")?.optString("final_formatted")?.takeIf { it.isNotBlank() }
            ?: if (isFree) "Free" else null
        val metacriticScore = data.optJSONObject("metacritic")?.optInt("score", -1)?.takeIf { it in 1..100 }

        val parsed = CatalogGameItem(
            externalId = "steam_$appId",
            title = name,
            coverUrl = header,
            iconUrl = header,
            sourceType = GameSourceType.STEAM,
            steamAppId = appId,
            platforms = platforms,
            genres = genres,
            releaseDateText = releaseText,
            releaseTimestampMillis = parseSteamReleaseDate(releaseText),
            status = status,
            description = desc,
            screenshots = screenshots,
            trailerUrl = trailerUrl ?: "https://store.steampowered.com/app/$appId",
            officialUrl = website,
            communityUrl = "https://steamcommunity.com/app/$appId",
            developer = dev,
            publisher = pub,
            isFreeToPlay = isFree,
            priceDisplay = priceFormatted,
            metacriticScore = metacriticScore,
            pcRequirementsMin = pcReqMin,
            pcRequirementsRec = pcReqRec,
            releaseYear = releaseYear
        )

        cacheMutex.withLock {
            detailCache[appId] = parsed
        }
        return parsed
    }

    private fun formatPcRequirementsHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace("&quot;", "\"")
            .replace("Minimum:", "")
            .replace("Recommended:", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(900)
    }

    private fun cleanNewsBody(raw: String): String {
        if (raw.isBlank()) return ""
        return NewsRssParser.stripHtml(
            raw.replace(Regex("\\{STEAM_CLAN_IMAGE\\}[^\\s\\]]+"), "")
                .replace(Regex("\\[/?[a-zA-Z0-9=*#\"':/.\\-_ ]+\\]"), " ")
        ).take(260)
    }

    private fun extractFirstImageUrl(raw: String): String? {
        val clanRegex = Regex("\\{STEAM_CLAN_IMAGE\\}([^\\s\\]\\[\"<>]+)")
        val clanMatch = clanRegex.find(raw)
        if (clanMatch != null) {
            return "https://clan.fastly.steamstatic.com/images/${clanMatch.groupValues[1]}"
        }
        val httpImg = Regex("https?://[^\\s\"'\\]]+\\.(?:jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)
        return httpImg.find(raw)?.value
    }

    private fun parseSteamReleaseDate(dateText: String): Long {
        val patterns = listOf("d MMM, yyyy", "MMM d, yyyy", "yyyy-MM-dd")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(dateText)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    private fun parseIsoDate(iso: String): Long? {
        if (iso.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(iso.take(19))?.time
        }.getOrNull()
    }
}
