package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

inline fun <reified T : Enum<T>> safeEnumValueOf(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

enum class GameSourceType {
    STEAM, FREETOGAME, MOJANG, FORTNITE, ITUNES, MANUAL
}

enum class AccountApiMode {
    LIVE_OFFICIAL_API, LIVE_COMMUNITY_XML, MANUAL_NO_PUBLIC_API
}

enum class MissionCategory {
    DAILY, WEEKLY, SEASONAL
}

enum class GameReleaseStatus {
    UPCOMING, AVAILABLE, DELAYED, CANCELLED
}

enum class NotificationType {
    UPDATE_RELEASED, UPCOMING_UPDATE, SEASON_ENDING, MISSION_EXPIRING, WAITLIST_RELEASE, FAVORITE_NEWS
}

enum class LibrarySortOrder {
    MOST_PLAYED, FAVORITES, RECENTLY_UPDATED
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = "ar",
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notifyOnUpdates: Boolean = true,
    val notifyOnSeasons: Boolean = true,
    val notifyOnMissions: Boolean = true,
    val notifyOnUpcoming: Boolean = true,
    val notifyOnNews: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName.take(80).ifBlank { "Gamer" })
        email?.take(120)?.takeIf { it.isNotBlank() }?.let { put("email", it) }
        photoUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it) }
        put("preferredLanguage", preferredLanguage)
        put("darkMode", darkMode)
        put("notificationsEnabled", notificationsEnabled)
        put("notifyOnUpdates", notifyOnUpdates)
        put("notifyOnSeasons", notifyOnSeasons)
        put("notifyOnMissions", notifyOnMissions)
        put("notifyOnUpcoming", notifyOnUpcoming)
        put("notifyOnNews", notifyOnNews)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class LibraryGame(
    @DocumentId val id: String = "",
    val userId: String = "",
    val externalId: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val iconUrl: String? = null,
    val sourceType: String = GameSourceType.STEAM.name,
    val steamAppId: Long? = null,
    val platforms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val favorite: Boolean = false,
    val needsUpdate: Boolean = false,
    val updateNotes: String? = null,
    val nextUpdateDateText: String? = null,
    val nextUpdateAtMillis: Long? = null,
    val currentSeasonName: String? = null,
    val seasonEndsAtMillis: Long? = null,
    val playHours: Long = 0L,
    val playerLevel: Long = 1L,
    val achievementsUnlocked: Long = 0L,
    val achievementsTotal: Long = 0L,
    val officialUrl: String? = null,
    val communityUrl: String? = null,
    val notifyUpdates: Boolean = true,
    val notifySeasons: Boolean = true,
    val notifyMissions: Boolean = true,
    val notifyNews: Boolean = true,
    val pcRequirementsMin: String? = null,
    val pcRequirementsRec: String? = null,
    val ratingSummary: String? = null,
    val lastUpdatedAtMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedSourceType: GameSourceType
        get() = safeEnumValueOf(sourceType, GameSourceType.MANUAL)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("externalId", externalId.take(64))
        put("title", title.take(120).ifBlank { "Untitled Game" })
        coverUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
        iconUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("iconUrl", it) }
        put("sourceType", resolvedSourceType.name)
        steamAppId?.takeIf { it >= 0 }?.let { put("steamAppId", it) }
        put("platforms", platforms.take(10).map { it.take(60) })
        put("genres", genres.take(10).map { it.take(60) })
        put("favorite", favorite)
        put("needsUpdate", needsUpdate)
        updateNotes?.take(500)?.takeIf { it.isNotBlank() }?.let { put("updateNotes", it) }
        nextUpdateDateText?.take(80)?.takeIf { it.isNotBlank() }?.let { put("nextUpdateDateText", it) }
        nextUpdateAtMillis?.takeIf { it >= 0 }?.let { put("nextUpdateAtMillis", it) }
        currentSeasonName?.take(120)?.takeIf { it.isNotBlank() }?.let { put("currentSeasonName", it) }
        seasonEndsAtMillis?.takeIf { it >= 0 }?.let { put("seasonEndsAtMillis", it) }
        put("playHours", playHours.coerceIn(0L, 1_000_000L))
        put("playerLevel", playerLevel.coerceIn(0L, 100_000L))
        put("achievementsUnlocked", achievementsUnlocked.coerceAtLeast(0L))
        put("achievementsTotal", achievementsTotal.coerceAtLeast(0L))
        put("notifyUpdates", notifyUpdates)
        put("notifySeasons", notifySeasons)
        put("notifyMissions", notifyMissions)
        put("notifyNews", notifyNews)
        put("lastUpdatedAtMillis", lastUpdatedAtMillis.coerceAtLeast(0L))
        officialUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("officialUrl", it) }
        communityUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("communityUrl", it) }
        pcRequirementsMin?.take(1200)?.takeIf { it.isNotBlank() }?.let { put("pcRequirementsMin", it) }
        pcRequirementsRec?.take(1200)?.takeIf { it.isNotBlank() }?.let { put("pcRequirementsRec", it) }
        ratingSummary?.take(120)?.takeIf { it.isNotBlank() }?.let { put("ratingSummary", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class GameAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameKey: String = "",
    val gameName: String = "",
    val playerIdentifier: String = "",
    val platform: String = "",
    val serverRegion: String? = null,
    val level: Long = 1L,
    val rankTier: String? = null,
    val statsSummary: String? = null,
    val achievementsText: String? = null,
    val apiMode: String = AccountApiMode.MANUAL_NO_PUBLIC_API.name,
    val apiAvailabilityNote: String? = null,
    val avatarUrl: String? = null,
    val verifiedPlayerId: String? = null,
    val lastSyncedAtMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedApiMode: AccountApiMode
        get() = safeEnumValueOf(apiMode, AccountApiMode.MANUAL_NO_PUBLIC_API)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("gameKey", gameKey.take(40))
        put("gameName", gameName.take(80))
        put("playerIdentifier", playerIdentifier.take(80))
        put("platform", platform.take(40))
        serverRegion?.take(40)?.takeIf { it.isNotBlank() }?.let { put("serverRegion", it) }
        put("level", level.coerceIn(0L, 100_000L))
        rankTier?.take(60)?.takeIf { it.isNotBlank() }?.let { put("rankTier", it) }
        statsSummary?.take(400)?.takeIf { it.isNotBlank() }?.let { put("statsSummary", it) }
        achievementsText?.take(400)?.takeIf { it.isNotBlank() }?.let { put("achievementsText", it) }
        put("apiMode", resolvedApiMode.name)
        apiAvailabilityNote?.take(300)?.takeIf { it.isNotBlank() }?.let { put("apiAvailabilityNote", it) }
        avatarUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("avatarUrl", it) }
        verifiedPlayerId?.take(120)?.takeIf { it.isNotBlank() }?.let { put("verifiedPlayerId", it) }
        put("lastSyncedAtMillis", lastSyncedAtMillis.coerceAtLeast(0L))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class GameMission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameDocId: String? = null,
    val gameName: String = "",
    val title: String = "",
    val description: String? = null,
    val category: String = MissionCategory.DAILY.name,
    val currentProgress: Long = 0L,
    val targetProgress: Long = 1L,
    val completed: Boolean = false,
    val expiresAtMillis: Long = 0L,
    val rewardText: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedCategory: MissionCategory
        get() = safeEnumValueOf(category, MissionCategory.DAILY)

    val progressFraction: Float
        get() = if (targetProgress <= 0L) 0f else (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        gameDocId?.take(128)?.takeIf { it.isNotBlank() }?.let { put("gameDocId", it) }
        put("gameName", gameName.take(80))
        put("title", title.take(140))
        description?.take(300)?.takeIf { it.isNotBlank() }?.let { put("description", it) }
        put("category", resolvedCategory.name)
        put("currentProgress", currentProgress.coerceAtLeast(0L))
        put("targetProgress", targetProgress.coerceIn(1L, 1_000_000L))
        put("completed", completed)
        put("expiresAtMillis", expiresAtMillis.coerceAtLeast(0L))
        rewardText?.take(100)?.takeIf { it.isNotBlank() }?.let { put("rewardText", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class WaitlistGame(
    @DocumentId val id: String = "",
    val userId: String = "",
    val externalId: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val releaseDateText: String = "",
    val releaseTimestampMillis: Long = 0L,
    val platforms: List<String> = emptyList(),
    val genre: String? = null,
    val description: String? = null,
    val status: String = GameReleaseStatus.UPCOMING.name,
    val notifyBeforeRelease: Boolean = true,
    val notifyOnRelease: Boolean = true,
    val steamAppId: Long? = null,
    val trailerUrl: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedStatus: GameReleaseStatus
        get() = safeEnumValueOf(status, GameReleaseStatus.UPCOMING)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("externalId", externalId.take(64))
        put("title", title.take(120))
        coverUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
        put("releaseDateText", releaseDateText.take(80).ifBlank { "TBA" })
        put("releaseTimestampMillis", releaseTimestampMillis.coerceAtLeast(0L))
        put("platforms", platforms.take(10).map { it.take(60) })
        genre?.take(80)?.takeIf { it.isNotBlank() }?.let { put("genre", it) }
        description?.take(600)?.takeIf { it.isNotBlank() }?.let { put("description", it) }
        put("status", resolvedStatus.name)
        put("notifyBeforeRelease", notifyBeforeRelease)
        put("notifyOnRelease", notifyOnRelease)
        steamAppId?.takeIf { it >= 0 }?.let { put("steamAppId", it) }
        trailerUrl?.take(500)?.takeIf { it.isNotBlank() }?.let { put("trailerUrl", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = NotificationType.UPDATE_RELEASED.name,
    val title: String = "",
    val body: String = "",
    val gameName: String? = null,
    val read: Boolean = false,
    val createdAtMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val resolvedType: NotificationType
        get() = safeEnumValueOf(type, NotificationType.UPDATE_RELEASED)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("type", resolvedType.name)
        put("title", title.take(140))
        put("body", body.take(400))
        gameName?.take(80)?.takeIf { it.isNotBlank() }?.let { put("gameName", it) }
        put("read", read)
        put("createdAtMillis", createdAtMillis.coerceAtLeast(0L))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}
