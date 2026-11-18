package com.example.data

import android.content.Context
import com.example.R
import com.example.model.GameAccount
import com.example.model.GameMission
import com.example.model.LibraryGame
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.example.model.WaitlistGame
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NemoxFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "doc_${System.currentTimeMillis()}" }
    }

    // --- USER PROFILE ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        defaultLang: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "Gamer" },
                    email = email,
                    photoUrl = photoUrl,
                    preferredLanguage = defaultLang,
                    darkMode = true,
                    notificationsEnabled = true
                )
                ref.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        userId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUid(userId)
            val payload = fields.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- LIBRARY GAMES ---
    fun observeLibraryGames(userId: String): Flow<List<LibraryGame>> =
        db.collection("users").document(userId).collection("library_games")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(LibraryGame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addOrUpdateLibraryGame(userId: String, game: LibraryGame): Result<Unit> {
        val docId = sanitizeDocId(game.id.ifBlank { game.externalId })
        val path = "users/$userId/library_games/$docId"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid).collection("library_games").document(docId)
            val existing = ref.get().await()
            if (!existing.exists()) {
                ref.set(game.copy(userId = uid).toCreateMap()).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("title", game.title.take(120))
                    game.coverUrl?.take(500)?.let { put("coverUrl", it) }
                    game.iconUrl?.take(500)?.let { put("iconUrl", it) }
                    put("lastUpdatedAtMillis", game.lastUpdatedAtMillis)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLibraryGameFields(
        userId: String,
        gameDocId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/library_games/$gameDocId"
        return try {
            val uid = requireUid(userId)
            require(gameDocId.isNotBlank()) { "Game doc ID cannot be blank" }
            val payload = fields.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).collection("library_games")
                .document(gameDocId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLibraryGame(userId: String, gameDocId: String): Result<Unit> {
        val path = "users/$userId/library_games/$gameDocId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("library_games")
                .document(gameDocId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- LINKED GAME ACCOUNTS ---
    fun observeGameAccounts(userId: String): Flow<List<GameAccount>> =
        db.collection("users").document(userId).collection("game_accounts")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(GameAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun upsertGameAccount(userId: String, account: GameAccount): Result<Unit> {
        val docId = sanitizeDocId(
            account.id.ifBlank { "${account.gameKey}_${account.playerIdentifier.hashCode().toUInt()}" }
        )
        val path = "users/$userId/game_accounts/$docId"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid).collection("game_accounts").document(docId)
            val existing = ref.get().await()
            if (!existing.exists()) {
                ref.set(account.copy(userId = uid).toCreateMap()).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("gameName", account.gameName.take(80))
                    put("playerIdentifier", account.playerIdentifier.take(80))
                    put("platform", account.platform.take(40))
                    account.serverRegion?.take(40)?.let { put("serverRegion", it) }
                    put("level", account.level.coerceIn(0L, 100_000L))
                    account.rankTier?.take(60)?.let { put("rankTier", it) }
                    account.statsSummary?.take(400)?.let { put("statsSummary", it) }
                    account.achievementsText?.take(400)?.let { put("achievementsText", it) }
                    put("apiMode", account.resolvedApiMode.name)
                    account.apiAvailabilityNote?.take(300)?.let { put("apiAvailabilityNote", it) }
                    account.avatarUrl?.take(500)?.let { put("avatarUrl", it) }
                    account.verifiedPlayerId?.take(120)?.let { put("verifiedPlayerId", it) }
                    put("lastSyncedAtMillis", account.lastSyncedAtMillis)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGameAccount(userId: String, accountId: String): Result<Unit> {
        val path = "users/$userId/game_accounts/$accountId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("game_accounts")
                .document(accountId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- MISSIONS ---
    fun observeMissions(userId: String): Flow<List<GameMission>> =
        db.collection("users").document(userId).collection("missions")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(GameMission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun createMission(userId: String, mission: GameMission): Result<Unit> {
        val path = "users/$userId/missions"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid).collection("missions").document()
            ref.set(mission.copy(userId = uid).toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMissionProgress(
        userId: String,
        missionId: String,
        currentProgress: Long,
        completed: Boolean
    ): Result<Unit> {
        val path = "users/$userId/missions/$missionId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("missions").document(missionId)
                .update(
                    mapOf(
                        "currentProgress" to currentProgress.coerceAtLeast(0L),
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMission(userId: String, missionId: String): Result<Unit> {
        val path = "users/$userId/missions/$missionId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("missions")
                .document(missionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- WAITLIST ---
    fun observeWaitlist(userId: String): Flow<List<WaitlistGame>> =
        db.collection("users").document(userId).collection("waitlist")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(WaitlistGame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addToWaitlist(userId: String, game: WaitlistGame): Result<Unit> {
        val docId = sanitizeDocId(game.externalId)
        val path = "users/$userId/waitlist/$docId"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid).collection("waitlist").document(docId)
            if (!ref.get().await().exists()) {
                ref.set(game.copy(userId = uid).toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWaitlistNotifications(
        userId: String,
        waitlistId: String,
        notifyBeforeRelease: Boolean,
        notifyOnRelease: Boolean
    ): Result<Unit> {
        val path = "users/$userId/waitlist/$waitlistId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("waitlist").document(waitlistId)
                .update(
                    mapOf(
                        "notifyBeforeRelease" to notifyBeforeRelease,
                        "notifyOnRelease" to notifyOnRelease,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeFromWaitlist(userId: String, waitlistId: String): Result<Unit> {
        val path = "users/$userId/waitlist/$waitlistId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("waitlist")
                .document(waitlistId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---
    fun observeNotifications(userId: String): Flow<List<UserNotification>> =
        db.collection("users").document(userId).collection("notifications")
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(UserNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAtMillis }
            }

    suspend fun createNotificationIfMissing(
        userId: String,
        deterministicKey: String,
        notification: UserNotification
    ): Result<Boolean> {
        val docId = sanitizeDocId(deterministicKey)
        val path = "users/$userId/notifications/$docId"
        return try {
            val uid = requireUid(userId)
            val ref = db.collection("users").document(uid).collection("notifications").document(docId)
            if (!ref.get().await().exists()) {
                ref.set(notification.copy(userId = uid).toCreateMap()).await()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(userId: String, notifId: String): Result<Unit> {
        val path = "users/$userId/notifications/$notifId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("notifications").document(notifId)
                .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String, unreadIds: List<String>): Result<Unit> {
        val path = "users/$userId/notifications"
        return try {
            val uid = requireUid(userId)
            val col = db.collection("users").document(uid).collection("notifications")
            for (id in unreadIds) {
                col.document(id)
                    .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(userId: String, notifId: String): Result<Unit> {
        val path = "users/$userId/notifications/$notifId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("notifications")
                .document(notifId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
