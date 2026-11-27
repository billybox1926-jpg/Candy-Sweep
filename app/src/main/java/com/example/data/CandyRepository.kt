package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CandyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user Firestore operation." }
        return uid
    }

    fun observeProfile(userId: String): Flow<PlayerProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users")
                .document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PlayerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeScores(userId: String): Flow<List<ScoreRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/scores"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("scores")
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ScoreRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfile(userId: String, initialDisplayName: String): Result<PlayerProfile> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val parsed = existing.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: PlayerProfile(id = uid, userId = uid)
                return Result.success(parsed)
            }
            val safeName = sanitizeDisplayName(initialDisplayName, uid)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "bestScore" to 0L,
                "highestStage" to 1L,
                "totalCandiesCleared" to 0L,
                "bestCombo" to 0L,
                "gamesPlayed" to 0L,
                "hapticsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                PlayerProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordGameResult(
        userId: String,
        currentProfile: PlayerProfile?,
        mode: GameMode,
        score: Long,
        stage: Int,
        stars: Int,
        candiesCleared: Int,
        maxCombo: Int,
        movesUsed: Int,
        stageCleared: Boolean
    ): Result<Unit> {
        var activePath = "users/$userId/scores"
        return try {
            val uid = requireUserId(userId)

            val clampedScore = score.coerceIn(0L, MAX_SCORE)
            val clampedStage = stage.toLong().coerceIn(1L, MAX_STAGE)
            val clampedStars = stars.toLong().coerceIn(0L, 3L)
            val clampedCandies = candiesCleared.toLong().coerceIn(0L, MAX_RUN_CANDIES)
            val clampedCombo = maxCombo.toLong().coerceIn(1L, MAX_COMBO)
            val clampedMoves = movesUsed.toLong().coerceIn(0L, MAX_MOVES)

            // Ensure profile exists before updating stats
            val profile = currentProfile ?: ensureProfile(
                userId = uid,
                initialDisplayName = auth.currentUser?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Sweet Sweeper #${uid.takeLast(4)}"
            ).getOrThrow()

            // 1. Write immutable score record
            val scoreDocRef = db.collection("users").document(uid).collection("scores").document()
            activePath = scoreDocRef.path
            val scorePayload = mapOf(
                "userId" to uid,
                "mode" to mode.firestoreValue,
                "score" to clampedScore,
                "stage" to clampedStage,
                "stars" to clampedStars,
                "candiesCleared" to clampedCandies,
                "maxCombo" to clampedCombo,
                "movesUsed" to clampedMoves,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            scoreDocRef.set(scorePayload).await()

            // 2. Update cumulative profile stats using Action 1 allowlisted keys only
            val profileDocRef = db.collection("users").document(uid)
            activePath = profileDocRef.path
            val nextUnlockedStage = if (mode == GameMode.CLEANUP && stageCleared) {
                maxOf(profile.highestStage, (stage + 1).toLong()).coerceIn(1L, MAX_STAGE)
            } else {
                profile.highestStage.coerceIn(1L, MAX_STAGE)
            }
            val statsUpdate = mapOf(
                "bestScore" to maxOf(profile.bestScore, clampedScore).coerceIn(0L, MAX_SCORE),
                "highestStage" to nextUnlockedStage,
                "totalCandiesCleared" to (profile.totalCandiesCleared + clampedCandies)
                    .coerceIn(0L, MAX_TOTAL_CANDIES),
                "bestCombo" to maxOf(profile.bestCombo, clampedCombo).coerceIn(0L, MAX_COMBO),
                "gamesPlayed" to (profile.gamesPlayed + 1L).coerceIn(0L, MAX_GAMES),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profileDocRef.update(statsUpdate).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, activePath)
            Result.failure(e)
        }
    }

    suspend fun updateProfileSettings(
        userId: String,
        displayName: String,
        hapticsEnabled: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val trimmed = displayName.trim()
            require(trimmed.length in 1..MAX_DISPLAY_NAME) {
                "Arcade name must be between 1 and $MAX_DISPLAY_NAME characters."
            }
            db.collection("users")
                .document(uid)
                .update(
                    mapOf(
                        "displayName" to trimmed,
                        "hapticsEnabled" to hapticsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScoreRecord(userId: String, scoreId: String): Result<Unit> {
        val docPath = "users/$userId/scores/$scoreId"
        return try {
            val uid = requireUserId(userId)
            require(scoreId.isNotBlank()) { "Score ID must not be blank." }
            db.collection("users")
                .document(uid)
                .collection("scores")
                .document(scoreId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeDisplayName(raw: String, userId: String): String {
        val cleaned = raw.trim().take(MAX_DISPLAY_NAME)
        return cleaned.ifEmpty { "Sweet Sweeper #${userId.takeLast(4)}" }
    }

    private companion object {
        const val MAX_DISPLAY_NAME = 40
        const val MAX_SCORE = 100_000_000L
        const val MAX_STAGE = 1_000L
        const val MAX_RUN_CANDIES = 100_000L
        const val MAX_TOTAL_CANDIES = 1_000_000_000L
        const val MAX_COMBO = 500L
        const val MAX_MOVES = 500L
        const val MAX_GAMES = 1_000_000L
    }
}
