package com.example.model

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
    val publisher: String? = null
)

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
    val steamAppId: Long? = null
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
