package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.GamingRemoteRepository
import com.example.data.NemoxFirestoreRepository
import com.example.data.SettingsDataStore
import com.example.model.GameReleaseStatus
import com.example.model.NotificationType
import com.example.model.UserNotification
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first

class NemoxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val user = Firebase.auth.currentUser ?: return Result.success()
        val uid = user.uid
        val db = FirebaseFirestore.getInstance(
            applicationContext.getString(R.string.firestore_database_id)
        )
        val firestoreRepo = NemoxFirestoreRepository(db, Firebase.auth)
        val remoteRepo = GamingRemoteRepository()
        val dataStore = SettingsDataStore(applicationContext)

        return try {
            val profile = firestoreRepo.observeUserProfile(uid).first()
            if (profile != null && !profile.notificationsEnabled) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val library = firestoreRepo.observeLibraryGames(uid).first()
            val missions = firestoreRepo.observeMissions(uid).first()
            val waitlist = firestoreRepo.observeWaitlist(uid).first()

            // 1. Check expiring missions (< 6 hours remaining)
            if (profile?.notifyOnMissions != false) {
                val sixHoursMs = 6 * 3_600_000L
                for (mission in missions) {
                    if (!mission.completed && mission.expiresAtMillis > now && mission.expiresAtMillis - now <= sixHoursMs) {
                        val key = "mission_exp_${mission.id}_${mission.expiresAtMillis / 3_600_000L}"
                        val notif = UserNotification(
                            userId = uid,
                            type = NotificationType.MISSION_EXPIRING.name,
                            title = "Mission Expiring: ${mission.gameName}",
                            body = "${mission.title} (${mission.currentProgress}/${mission.targetProgress})",
                            gameName = mission.gameName,
                            read = false,
                            createdAtMillis = now
                        )
                        val created = firestoreRepo.createNotificationIfMissing(uid, key, notif).getOrDefault(false)
                        if (created) {
                            NemoxNotificationHelper.postLocalNotification(
                                applicationContext,
                                NemoxNotificationHelper.CHANNEL_MISSIONS,
                                key.hashCode(),
                                notif.title,
                                notif.body
                            )
                        }
                    }
                }
            }

            // 2. Check ending seasons (< 48 hours remaining)
            if (profile?.notifyOnSeasons != false) {
                val twoDaysMs = 48 * 3_600_000L
                for (game in library) {
                    val seasonEnd = game.seasonEndsAtMillis ?: 0L
                    if (game.notifySeasons && seasonEnd > now && seasonEnd - now <= twoDaysMs) {
                        val seasonLabel = game.currentSeasonName?.ifBlank { "Current Season" } ?: "Current Season"
                        val key = "season_end_${game.id}_${seasonEnd / 86_400_000L}"
                        val notif = UserNotification(
                            userId = uid,
                            type = NotificationType.SEASON_ENDING.name,
                            title = "Season Ending Soon: ${game.title}",
                            body = "$seasonLabel ends within 48 hours.",
                            gameName = game.title,
                            read = false,
                            createdAtMillis = now
                        )
                        val created = firestoreRepo.createNotificationIfMissing(uid, key, notif).getOrDefault(false)
                        if (created) {
                            NemoxNotificationHelper.postLocalNotification(
                                applicationContext,
                                NemoxNotificationHelper.CHANNEL_MISSIONS,
                                key.hashCode(),
                                notif.title,
                                notif.body
                            )
                        }
                    }
                }
            }

            // 3. Check Steam library games for recent official patches (< 48 hours)
            if (profile?.notifyOnUpdates != false || profile?.notifyOnNews != false) {
                for (game in library.take(6)) {
                    val appId = game.steamAppId ?: continue
                    if (!game.notifyUpdates && !game.notifyNews) continue
                    val news = runCatching { remoteRepo.getNewsForSteamApp(appId, game.title, 2) }
                        .getOrDefault(emptyList())
                    val latest = news.firstOrNull() ?: continue
                    if (now - latest.publishedAtMillis <= 48 * 3_600_000L) {
                        val isPatch = latest.title.contains("update", ignoreCase = true) ||
                            latest.title.contains("patch", ignoreCase = true) ||
                            latest.isOfficial
                        val type = if (isPatch) NotificationType.UPDATE_RELEASED else NotificationType.FAVORITE_NEWS
                        val key = "news_${latest.id}"
                        val notif = UserNotification(
                            userId = uid,
                            type = type.name,
                            title = "${game.title}: ${latest.title.take(80)}",
                            body = latest.summary.take(200),
                            gameName = game.title,
                            read = false,
                            createdAtMillis = now
                        )
                        val created = firestoreRepo.createNotificationIfMissing(uid, key, notif).getOrDefault(false)
                        if (created) {
                            NemoxNotificationHelper.postLocalNotification(
                                applicationContext,
                                NemoxNotificationHelper.CHANNEL_UPDATES,
                                key.hashCode(),
                                notif.title,
                                notif.body
                            )
                        }
                    }
                }
            }

            // 4. Check waitlisted upcoming games for release
            if (profile?.notifyOnUpcoming != false) {
                for (item in waitlist) {
                    val appId = item.steamAppId ?: continue
                    val detail = remoteRepo.getGameDetailsBySteamId(appId).getOrNull() ?: continue
                    if (detail.status == GameReleaseStatus.AVAILABLE && item.resolvedStatus == GameReleaseStatus.UPCOMING && item.notifyOnRelease) {
                        val key = "waitlist_release_${item.id}"
                        val notif = UserNotification(
                            userId = uid,
                            type = NotificationType.WAITLIST_RELEASE.name,
                            title = "${item.title} is Now Available!",
                            body = "Your waitlisted game has officially launched.",
                            gameName = item.title,
                            read = false,
                            createdAtMillis = now
                        )
                        val created = firestoreRepo.createNotificationIfMissing(uid, key, notif).getOrDefault(false)
                        if (created) {
                            NemoxNotificationHelper.postLocalNotification(
                                applicationContext,
                                NemoxNotificationHelper.CHANNEL_RELEASES,
                                key.hashCode(),
                                notif.title,
                                notif.body
                            )
                        }
                    }
                }
            }

            dataStore.setLastBackgroundSync(now)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
