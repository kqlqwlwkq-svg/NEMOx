package com.example.model

enum class SearchPricingFilter {
    ALL, FREE, PAID
}

enum class SearchReleaseFilter {
    ALL, UPCOMING, RECENT_2024_2026, MODERN_2020_2023, CLASSIC
}

enum class GamePlatformSegment {
    MOBILE, PC, ALL
}

enum class MobileSubPlatformFilter {
    ALL, ANDROID, IOS
}

enum class PcSubPlatformFilter {
    ALL, WINDOWS_STEAM, MAC, LINUX
}

data class PlatformCatalogFilterState(
    val segment: GamePlatformSegment = GamePlatformSegment.MOBILE,
    val mobileSubPlatform: MobileSubPlatformFilter = MobileSubPlatformFilter.ALL,
    val pcSubPlatform: PcSubPlatformFilter = PcSubPlatformFilter.ALL,
    val allGamesPlatformFilter: String? = null,
    val genreId: String? = null,
    val pricingFilter: SearchPricingFilter = SearchPricingFilter.ALL,
    val statusFilter: GameReleaseStatus? = null
)

object PlatformClassifier {
    private val MOBILE_TOKENS = setOf("mobile", "android", "ios", "iphone", "ipad")
    private val PC_TOKENS = setOf("pc", "windows", "steam", "mac", "macos", "linux")
    private val CONSOLE_TOKENS = setOf("ps5", "ps4", "playstation", "xbox", "switch", "nintendo", "console")

    fun hasVerifiedPlatforms(platforms: List<String>): Boolean =
        platforms.any {
            it.isNotBlank() &&
                !it.equals("Unspecified", ignoreCase = true) &&
                !it.equals("غير محدد", ignoreCase = true)
        }

    fun isVerifiedMobile(platforms: List<String>): Boolean =
        platforms.any { p ->
            val lower = p.lowercase().trim()
            MOBILE_TOKENS.any { token -> lower.contains(token) }
        }

    fun isVerifiedAndroid(platforms: List<String>): Boolean =
        platforms.any { it.contains("android", ignoreCase = true) || it.equals("Mobile", ignoreCase = true) }

    fun isVerifiedIos(platforms: List<String>): Boolean =
        platforms.any {
            it.contains("ios", ignoreCase = true) ||
                it.contains("iphone", ignoreCase = true) ||
                it.contains("ipad", ignoreCase = true) ||
                it.equals("Mobile", ignoreCase = true)
        }

    fun isVerifiedPc(platforms: List<String>): Boolean =
        platforms.any { p ->
            val lower = p.lowercase().trim()
            PC_TOKENS.any { token -> lower.contains(token) }
        }

    fun isVerifiedConsole(platforms: List<String>): Boolean =
        platforms.any { p ->
            val lower = p.lowercase().trim()
            CONSOLE_TOKENS.any { token -> lower.contains(token) }
        }

    fun matchesSegment(platforms: List<String>, segment: GamePlatformSegment): Boolean =
        when (segment) {
            GamePlatformSegment.MOBILE -> isVerifiedMobile(platforms)
            GamePlatformSegment.PC -> isVerifiedPc(platforms)
            GamePlatformSegment.ALL -> true
        }
}

data class SearchFilterState(
    val genreId: String? = null,
    val platform: String? = null,
    val releaseFilter: SearchReleaseFilter = SearchReleaseFilter.ALL,
    val pricingFilter: SearchPricingFilter = SearchPricingFilter.ALL,
    val statusFilter: GameReleaseStatus? = null
)

data class CatalogGameItem(
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val iconUrl: String?,
    val sourceType: GameSourceType,
    val steamAppId: Long? = null,
    val freeToGameId: Long? = null,
    val platforms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val releaseDateText: String = "TBA",
    val releaseTimestampMillis: Long = 0L,
    val status: GameReleaseStatus = GameReleaseStatus.AVAILABLE,
    val description: String = "",
    val screenshots: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val officialUrl: String? = null,
    val communityUrl: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val isFreeToPlay: Boolean? = null,
    val priceDisplay: String? = null,
    val metacriticScore: Int? = null,
    val ratingScore: Double? = null,
    val ratingCount: Int? = null,
    val pcRequirementsMin: String? = null,
    val pcRequirementsRec: String? = null,
    val trendingReasonEn: String? = null,
    val trendingReasonAr: String? = null,
    val trendingRank: Int? = null,
    val releaseYear: Int? = null
)

enum class NewsCategory {
    BREAKING,
    GAME_UPDATES,
    MOBILE_GAMES,
    PC_GAMES,
    CONSOLE_GAMES,
    ANNOUNCEMENTS_RELEASES,
    ESPORTS_TOURNAMENTS,
    STUDIOS_INDUSTRY,
    DEALS_FREE,
    LEAKS_RUMORS
}

enum class NewsVerificationLevel {
    OFFICIAL,
    VERIFIED_MEDIA,
    UNCONFIRMED_LEAK
}

enum class NewsDateRangeFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

enum class NewsTranslationStatus {
    IDLE, TRANSLATING, TRANSLATED, UNAVAILABLE
}

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val isOfficial: Boolean,
    val imageUrl: String?,
    val articleUrl: String,
    val publishedAtMillis: Long,
    val gameTitle: String,
    val gameKey: String,
    val steamAppId: Long? = null,
    val updatedAtMillis: Long? = null,
    val category: NewsCategory = NewsCategory.BREAKING,
    val verificationLevel: NewsVerificationLevel = if (isOfficial) NewsVerificationLevel.OFFICIAL else NewsVerificationLevel.VERIFIED_MEDIA,
    val platformTags: List<String> = emptyList(),
    val leakConfidenceLabel: String? = null,
    val titleAr: String? = null,
    val summaryAr: String? = null,
    val translationStatus: NewsTranslationStatus = NewsTranslationStatus.IDLE
)

data class NewsFilterState(
    val query: String = "",
    val selectedCategory: NewsCategory? = null,
    val selectedGameTitle: String? = null,
    val selectedPlatform: String? = null,
    val dateRange: NewsDateRangeFilter = NewsDateRangeFilter.ALL,
    val officialOnly: Boolean = false,
    val followedOnly: Boolean = false
)

data class NewsFeedReport(
    val articles: List<NewsArticle>,
    val activeSourceNames: List<String>,
    val failedSourceNames: List<String>,
    val lastRefreshedAtMillis: Long
)

data class LiveEventItem(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val platforms: String,
    val endDateText: String,
    val openUrl: String,
    val type: String
)

data class AccountSyncResult(
    val playerIdentifier: String,
    val verifiedPlayerId: String?,
    val avatarUrl: String?,
    val level: Long?,
    val rankTier: String?,
    val statsSummary: String?,
    val achievementsText: String?,
    val apiMode: AccountApiMode,
    val availabilityNote: String
)
