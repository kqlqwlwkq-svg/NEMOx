package com.example.data

import com.example.model.GameMission
import com.example.model.GameSourceType
import com.example.model.LibraryGame
import com.example.model.MissionCategory
import com.example.model.NotificationType
import com.example.model.UserNotification
import com.example.model.WaitlistGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class GuestLibraryDto(
    val id: String,
    val externalId: String,
    val title: String,
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
    val lastUpdatedAtMillis: Long = 0L
) {
    fun toDomain() = LibraryGame(
        id = id,
        userId = "guest_local",
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        iconUrl = iconUrl,
        sourceType = sourceType,
        steamAppId = steamAppId,
        platforms = platforms,
        genres = genres,
        favorite = favorite,
        needsUpdate = needsUpdate,
        updateNotes = updateNotes,
        nextUpdateDateText = nextUpdateDateText,
        currentSeasonName = currentSeasonName,
        seasonEndsAtMillis = seasonEndsAtMillis,
        playHours = playHours,
        playerLevel = playerLevel,
        achievementsUnlocked = achievementsUnlocked,
        achievementsTotal = achievementsTotal,
        officialUrl = officialUrl,
        communityUrl = communityUrl,
        notifyUpdates = notifyUpdates,
        notifySeasons = notifySeasons,
        notifyMissions = notifyMissions,
        notifyNews = notifyNews,
        pcRequirementsMin = pcRequirementsMin,
        pcRequirementsRec = pcRequirementsRec,
        ratingSummary = ratingSummary,
        lastUpdatedAtMillis = lastUpdatedAtMillis
    )
}

@Serializable
private data class GuestMissionDto(
    val id: String,
    val gameDocId: String? = null,
    val gameName: String,
    val title: String,
    val description: String? = null,
    val category: String = MissionCategory.DAILY.name,
    val currentProgress: Long = 0L,
    val targetProgress: Long = 1L,
    val completed: Boolean = false,
    val expiresAtMillis: Long = 0L,
    val rewardText: String? = null
) {
    fun toDomain() = GameMission(
        id = id,
        userId = "guest_local",
        gameDocId = gameDocId,
        gameName = gameName,
        title = title,
        description = description,
        category = category,
        currentProgress = currentProgress,
        targetProgress = targetProgress,
        completed = completed,
        expiresAtMillis = expiresAtMillis,
        rewardText = rewardText
    )
}

@Serializable
private data class GuestWaitlistDto(
    val id: String,
    val externalId: String,
    val title: String,
    val coverUrl: String? = null,
    val releaseDateText: String = "",
    val releaseTimestampMillis: Long = 0L,
    val platforms: List<String> = emptyList(),
    val genre: String? = null,
    val description: String? = null,
    val status: String = "UPCOMING",
    val notifyBeforeRelease: Boolean = true,
    val notifyOnRelease: Boolean = true,
    val steamAppId: Long? = null,
    val trailerUrl: String? = null
) {
    fun toDomain() = WaitlistGame(
        id = id,
        userId = "guest_local",
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        releaseDateText = releaseDateText,
        releaseTimestampMillis = releaseTimestampMillis,
        platforms = platforms,
        genre = genre,
        description = description,
        status = status,
        notifyBeforeRelease = notifyBeforeRelease,
        notifyOnRelease = notifyOnRelease,
        steamAppId = steamAppId,
        trailerUrl = trailerUrl
    )
}

@Serializable
private data class GuestNotificationDto(
    val id: String,
    val type: String = NotificationType.UPDATE_RELEASED.name,
    val title: String,
    val body: String,
    val gameName: String? = null,
    val read: Boolean = false,
    val createdAtMillis: Long = 0L
) {
    fun toDomain() = UserNotification(
        id = id,
        userId = "guest_local",
        type = type,
        title = title,
        body = body,
        gameName = gameName,
        read = read,
        createdAtMillis = createdAtMillis
    )
}

class LocalGuestRepository(private val dataStore: SettingsDataStore) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "doc_${System.currentTimeMillis()}" }

    fun observeLibraryGames(): Flow<List<LibraryGame>> = dataStore.guestLibraryJsonFlow.map { raw ->
        runCatching { json.decodeFromString<List<GuestLibraryDto>>(raw).map { it.toDomain() } }
            .getOrDefault(emptyList())
    }

    suspend fun addOrUpdateLibraryGame(game: LibraryGame) {
        val docId = sanitizeId(game.id.ifBlank { game.externalId })
        val current = runCatching {
            json.decodeFromString<List<GuestLibraryDto>>(dataStore.guestLibraryJsonFlow.first())
        }.getOrDefault(emptyList()).toMutableList()
        val idx = current.indexOfFirst { it.id == docId }
        val dto = GuestLibraryDto(
            id = docId,
            externalId = game.externalId,
            title = game.title,
            coverUrl = game.coverUrl,
            iconUrl = game.iconUrl,
            sourceType = game.sourceType,
            steamAppId = game.steamAppId,
            platforms = game.platforms,
            genres = game.genres,
            favorite = if (idx >= 0) current[idx].favorite else game.favorite,
            needsUpdate = if (idx >= 0) current[idx].needsUpdate else game.needsUpdate,
            updateNotes = if (idx >= 0) current[idx].updateNotes else game.updateNotes,
            nextUpdateDateText = if (idx >= 0) current[idx].nextUpdateDateText else game.nextUpdateDateText,
            currentSeasonName = if (idx >= 0) current[idx].currentSeasonName else game.currentSeasonName,
            seasonEndsAtMillis = if (idx >= 0) current[idx].seasonEndsAtMillis else game.seasonEndsAtMillis,
            playHours = if (idx >= 0) current[idx].playHours else game.playHours,
            playerLevel = if (idx >= 0) current[idx].playerLevel else game.playerLevel,
            achievementsUnlocked = if (idx >= 0) current[idx].achievementsUnlocked else game.achievementsUnlocked,
            achievementsTotal = if (idx >= 0) current[idx].achievementsTotal else game.achievementsTotal,
            officialUrl = game.officialUrl,
            communityUrl = game.communityUrl,
            pcRequirementsMin = game.pcRequirementsMin,
            pcRequirementsRec = game.pcRequirementsRec,
            ratingSummary = game.ratingSummary,
            lastUpdatedAtMillis = game.lastUpdatedAtMillis
        )
        if (idx >= 0) current[idx] = dto else current.add(0, dto)
        dataStore.saveGuestLibraryJson(json.encodeToString(current))
    }

    suspend fun updateLibraryGameFields(gameId: String, updater: (LibraryGame) -> LibraryGame) {
        val current = runCatching {
            json.decodeFromString<List<GuestLibraryDto>>(dataStore.guestLibraryJsonFlow.first())
        }.getOrDefault(emptyList()).toMutableList()
        val idx = current.indexOfFirst { it.id == gameId }
        if (idx >= 0) {
            val g = updater(current[idx].toDomain())
            current[idx] = GuestLibraryDto(
                id = g.id,
                externalId = g.externalId,
                title = g.title,
                coverUrl = g.coverUrl,
                iconUrl = g.iconUrl,
                sourceType = g.sourceType,
                steamAppId = g.steamAppId,
                platforms = g.platforms,
                genres = g.genres,
                favorite = g.favorite,
                needsUpdate = g.needsUpdate,
                updateNotes = g.updateNotes,
                nextUpdateDateText = g.nextUpdateDateText,
                currentSeasonName = g.currentSeasonName,
                seasonEndsAtMillis = g.seasonEndsAtMillis,
                playHours = g.playHours,
                playerLevel = g.playerLevel,
                achievementsUnlocked = g.achievementsUnlocked,
                achievementsTotal = g.achievementsTotal,
                officialUrl = g.officialUrl,
                communityUrl = g.communityUrl,
                notifyUpdates = g.notifyUpdates,
                notifySeasons = g.notifySeasons,
                notifyMissions = g.notifyMissions,
                notifyNews = g.notifyNews,
                pcRequirementsMin = g.pcRequirementsMin,
                pcRequirementsRec = g.pcRequirementsRec,
                ratingSummary = g.ratingSummary,
                lastUpdatedAtMillis = g.lastUpdatedAtMillis
            )
            dataStore.saveGuestLibraryJson(json.encodeToString(current))
        }
    }

    suspend fun deleteLibraryGame(gameId: String) {
        val current = runCatching {
            json.decodeFromString<List<GuestLibraryDto>>(dataStore.guestLibraryJsonFlow.first())
        }.getOrDefault(emptyList()).filterNot { it.id == gameId }
        dataStore.saveGuestLibraryJson(json.encodeToString(current))
    }

    fun observeMissions(): Flow<List<GameMission>> = dataStore.guestMissionsJsonFlow.map { raw ->
        runCatching { json.decodeFromString<List<GuestMissionDto>>(raw).map { it.toDomain() } }
            .getOrDefault(emptyList())
    }

    suspend fun createMission(mission: GameMission) {
        val current = runCatching {
            json.decodeFromString<List<GuestMissionDto>>(dataStore.guestMissionsJsonFlow.first())
        }.getOrDefault(emptyList()).toMutableList()
        val id = "gm_${System.currentTimeMillis()}"
        current.add(
            0,
            GuestMissionDto(
                id = id,
                gameDocId = mission.gameDocId,
                gameName = mission.gameName,
                title = mission.title,
                description = mission.description,
                category = mission.category,
                currentProgress = mission.currentProgress,
                targetProgress = mission.targetProgress,
                completed = mission.completed,
                expiresAtMillis = mission.expiresAtMillis,
                rewardText = mission.rewardText
            )
        )
        dataStore.saveGuestMissionsJson(json.encodeToString(current))
    }

    suspend fun updateMissionProgress(missionId: String, currentProgress: Long, completed: Boolean) {
        val current = runCatching {
            json.decodeFromString<List<GuestMissionDto>>(dataStore.guestMissionsJsonFlow.first())
        }.getOrDefault(emptyList()).map { dto ->
            if (dto.id == missionId) dto.copy(currentProgress = currentProgress, completed = completed) else dto
        }
        dataStore.saveGuestMissionsJson(json.encodeToString(current))
    }

    suspend fun deleteMission(missionId: String) {
        val current = runCatching {
            json.decodeFromString<List<GuestMissionDto>>(dataStore.guestMissionsJsonFlow.first())
        }.getOrDefault(emptyList()).filterNot { it.id == missionId }
        dataStore.saveGuestMissionsJson(json.encodeToString(current))
    }

    fun observeWaitlist(): Flow<List<WaitlistGame>> = dataStore.guestWaitlistJsonFlow.map { raw ->
        runCatching { json.decodeFromString<List<GuestWaitlistDto>>(raw).map { it.toDomain() } }
            .getOrDefault(emptyList())
    }

    suspend fun addToWaitlist(game: WaitlistGame) {
        val id = sanitizeId(game.externalId)
        val current = runCatching {
            json.decodeFromString<List<GuestWaitlistDto>>(dataStore.guestWaitlistJsonFlow.first())
        }.getOrDefault(emptyList()).toMutableList()
        if (current.none { it.id == id }) {
            current.add(
                0,
                GuestWaitlistDto(
                    id = id,
                    externalId = game.externalId,
                    title = game.title,
                    coverUrl = game.coverUrl,
                    releaseDateText = game.releaseDateText,
                    releaseTimestampMillis = game.releaseTimestampMillis,
                    platforms = game.platforms,
                    genre = game.genre,
                    description = game.description,
                    status = game.status,
                    notifyBeforeRelease = game.notifyBeforeRelease,
                    notifyOnRelease = game.notifyOnRelease,
                    steamAppId = game.steamAppId,
                    trailerUrl = game.trailerUrl
                )
            )
            dataStore.saveGuestWaitlistJson(json.encodeToString(current))
        }
    }

    suspend fun updateWaitlistAlerts(waitlistId: String, beforeRelease: Boolean, onRelease: Boolean) {
        val current = runCatching {
            json.decodeFromString<List<GuestWaitlistDto>>(dataStore.guestWaitlistJsonFlow.first())
        }.getOrDefault(emptyList()).map {
            if (it.id == waitlistId) it.copy(notifyBeforeRelease = beforeRelease, notifyOnRelease = onRelease) else it
        }
        dataStore.saveGuestWaitlistJson(json.encodeToString(current))
    }

    suspend fun removeFromWaitlist(waitlistId: String) {
        val current = runCatching {
            json.decodeFromString<List<GuestWaitlistDto>>(dataStore.guestWaitlistJsonFlow.first())
        }.getOrDefault(emptyList()).filterNot { it.id == waitlistId }
        dataStore.saveGuestWaitlistJson(json.encodeToString(current))
    }

    fun observeNotifications(): Flow<List<UserNotification>> = dataStore.guestNotificationsJsonFlow.map { raw ->
        runCatching { json.decodeFromString<List<GuestNotificationDto>>(raw).map { it.toDomain() } }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAtMillis }
    }

    suspend fun createNotificationIfMissing(key: String, notification: UserNotification): Boolean {
        val id = sanitizeId(key)
        val current = runCatching {
            json.decodeFromString<List<GuestNotificationDto>>(dataStore.guestNotificationsJsonFlow.first())
        }.getOrDefault(emptyList()).toMutableList()
        if (current.any { it.id == id }) return false
        current.add(
            0,
            GuestNotificationDto(
                id = id,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                gameName = notification.gameName,
                read = notification.read,
                createdAtMillis = notification.createdAtMillis
            )
        )
        dataStore.saveGuestNotificationsJson(json.encodeToString(current))
        return true
    }

    suspend fun markNotificationRead(notifId: String) {
        val current = runCatching {
            json.decodeFromString<List<GuestNotificationDto>>(dataStore.guestNotificationsJsonFlow.first())
        }.getOrDefault(emptyList()).map { if (it.id == notifId) it.copy(read = true) else it }
        dataStore.saveGuestNotificationsJson(json.encodeToString(current))
    }

    suspend fun markAllNotificationsRead() {
        val current = runCatching {
            json.decodeFromString<List<GuestNotificationDto>>(dataStore.guestNotificationsJsonFlow.first())
        }.getOrDefault(emptyList()).map { it.copy(read = true) }
        dataStore.saveGuestNotificationsJson(json.encodeToString(current))
    }

    suspend fun deleteNotification(notifId: String) {
        val current = runCatching {
            json.decodeFromString<List<GuestNotificationDto>>(dataStore.guestNotificationsJsonFlow.first())
        }.getOrDefault(emptyList()).filterNot { it.id == notifId }
        dataStore.saveGuestNotificationsJson(json.encodeToString(current))
    }
}
