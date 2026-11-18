package com.example.data

import com.example.model.AccountApiMode
import com.example.model.AccountSyncResult
import com.example.model.CatalogGameItem
import com.example.model.GameCatalogPresets
import com.example.model.GameReleaseStatus
import com.example.model.GameSourceType
import com.example.model.LiveEventItem
import com.example.model.NewsArticle
import com.example.network.NetworkClient
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

class GamingRemoteRepository {

    private val cacheMutex = Mutex()
    private var cachedFreeToGameList: List<CatalogGameItem>? = null
    private var freeToGameFetchedAt: Long = 0L

    private var cachedUpcoming: List<CatalogGameItem>? = null
    private var upcomingFetchedAt: Long = 0L

    private var cachedEvents: List<LiveEventItem>? = null
    private var eventsFetchedAt: Long = 0L

    private val detailCache = mutableMapOf<Long, CatalogGameItem>()

    suspend fun searchGames(query: String): Result<List<CatalogGameItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                return@withContext Result.success(getFeaturedPopularGames())
            }

            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val steamDeferred = async {
                fetchSteamSearch(encoded)
            }
            val ftgDeferred = async {
                val allFtg = getFreeToGameCatalog()
                allFtg.filter {
                    it.title.contains(cleanQuery, ignoreCase = true) ||
                        it.genres.any { g -> g.contains(cleanQuery, ignoreCase = true) }
                }.take(15)
            }

            val steamResults = steamDeferred.await()
            val ftgResults = ftgDeferred.await()

            val merged = LinkedHashMap<String, CatalogGameItem>()
            for (item in steamResults) {
                merged[item.title.lowercase(Locale.ROOT)] = item
            }
            for (item in ftgResults) {
                val key = item.title.lowercase(Locale.ROOT)
                if (!merged.containsKey(key)) {
                    merged[key] = item
                }
            }
            Result.success(merged.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeaturedPopularGames(): List<CatalogGameItem> = withContext(Dispatchers.IO) {
        val ftg = runCatching { getFreeToGameCatalog().take(12) }.getOrDefault(emptyList())
        val steamFeatured = runCatching { fetchSteamFeaturedList("top_sellers") }.getOrDefault(emptyList())
        (steamFeatured + ftg).distinctBy { it.title.lowercase(Locale.ROOT) }.take(20)
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

                    if (title.isNotBlank() && articleUrl.isNotBlank()) {
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
                                steamAppId = appId
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

                if (title.isNotBlank()) {
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
                            gameKey = "minecraft"
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
                            sourceName = "Fortnite In-Game Feed",
                            isOfficial = true,
                            imageUrl = img,
                            articleUrl = "https://www.fortnite.com/news",
                            publishedAtMillis = publishedMillis,
                            gameTitle = "Fortnite",
                            gameKey = "fortnite"
                        )
                    )
                }
            }
        }
    }

    suspend fun getAggregatedNews(
        trackedSteamApps: List<Pair<Long, String>>,
        includeMinecraft: Boolean = true,
        includeFortnite: Boolean = true
    ): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            val targets = if (trackedSteamApps.isNotEmpty()) {
                trackedSteamApps.distinctBy { it.first }.take(8)
            } else {
                GameCatalogPresets.defaultNewsSteamAppIds
            }

            val allArticles = coroutineScope {
                val steamJobs = targets.map { (appId, title) ->
                    async { runCatching { getNewsForSteamApp(appId, title, count = 6) }.getOrDefault(emptyList()) }
                }
                val mcJob = async {
                    if (includeMinecraft) runCatching { getMojangMinecraftNews() }.getOrDefault(emptyList())
                    else emptyList()
                }
                val fnJob = async {
                    if (includeFortnite) runCatching { getFortniteOfficialNews() }.getOrDefault(emptyList())
                    else emptyList()
                }

                val list = mutableListOf<NewsArticle>()
                steamJobs.awaitAll().forEach { list.addAll(it) }
                list.addAll(mcJob.await())
                list.addAll(fnJob.await())
                list
            }

            // Sort official developer news first within recent windows, then chronologically
            val sorted = allArticles
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<NewsArticle> { it.publishedAtMillis / 86_400_000L }
                        .thenByDescending { it.isOfficial }
                        .thenByDescending { it.publishedAtMillis }
                )

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
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
                    val platforms = obj.optString("platforms", "PC")
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

    private suspend fun getFreeToGameCatalog(): List<CatalogGameItem> {
        val now = System.currentTimeMillis()
        cacheMutex.withLock {
            val cached = cachedFreeToGameList
            if (cached != null && now - freeToGameFetchedAt < 30 * 60_000L) {
                return cached
            }
        }
        val jsonStr = NetworkClient.fetchJsonString("https://www.freetogame.com/api/games")
            ?: return emptyList()
        val arr = JSONArray(jsonStr)
        val parsed = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optLong("id", -1L)
                if (id < 0) continue
                val title = obj.optString("title").trim()
                val thumb = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                val desc = obj.optString("short_description").trim()
                val genre = obj.optString("genre").trim()
                val platform = obj.optString("platform", "PC").split(",").map { it.trim() }.filter { it.isNotBlank() }
                val releaseDate = obj.optString("release_date", "Available")
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
                        platforms = platform.ifEmpty { listOf("PC") },
                        genres = if (genre.isNotBlank()) listOf(genre) else emptyList(),
                        releaseDateText = releaseDate,
                        status = GameReleaseStatus.AVAILABLE,
                        description = desc,
                        officialUrl = gameUrl,
                        communityUrl = profileUrl,
                        developer = dev,
                        publisher = pub
                    )
                )
            }
        }
        cacheMutex.withLock {
            cachedFreeToGameList = parsed
            freeToGameFetchedAt = now
        }
        return parsed
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
                    if (platObj?.optBoolean("windows") == true) add("PC")
                    if (platObj?.optBoolean("mac") == true) add("Mac")
                    if (platObj?.optBoolean("linux") == true) add("Linux")
                }.ifEmpty { listOf("PC") }

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
                        communityUrl = "https://steamcommunity.com/app/$id"
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

                val platforms = buildList {
                    if (obj.optBoolean("windows_available", true)) add("PC")
                    if (obj.optBoolean("mac_available", false)) add("Mac")
                    if (obj.optBoolean("linux_available", false)) add("Linux")
                }.ifEmpty { listOf("PC") }

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
                        communityUrl = "https://steamcommunity.com/app/$id"
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

        val status = when {
            releaseText.contains("cancel", ignoreCase = true) -> GameReleaseStatus.CANCELLED
            releaseText.contains("delay", ignoreCase = true) -> GameReleaseStatus.DELAYED
            comingSoon -> GameReleaseStatus.UPCOMING
            else -> GameReleaseStatus.AVAILABLE
        }

        val platObj = data.optJSONObject("platforms")
        val platforms = buildList {
            if (platObj?.optBoolean("windows") == true) add("PC")
            if (platObj?.optBoolean("mac") == true) add("Mac")
            if (platObj?.optBoolean("linux") == true) add("Linux")
        }.ifEmpty { listOf("PC") }

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
            publisher = pub
        )

        cacheMutex.withLock {
            detailCache[appId] = parsed
        }
        return parsed
    }

    private fun cleanNewsBody(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("\\{STEAM_CLAN_IMAGE\\}[^\\s\\]]+"), "")
            .replace(Regex("\\[/?[a-zA-Z0-9=*#\"':/.\\-_ ]+\\]"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
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
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))?.time
        }.getOrNull()
    }
}
