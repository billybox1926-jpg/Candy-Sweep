package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GameMode(val firestoreValue: String) {
    CLEANUP("CLEANUP"),
    SCORE_RUSH("SCORE_RUSH");

    companion object {
        fun fromFirestore(raw: String?): GameMode =
            entries.firstOrNull { it.firestoreValue == raw } ?: CLEANUP
    }
}

@Keep
@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Sweet Sweeper",
    val bestScore: Long = 0L,
    val highestStage: Long = 1L,
    val totalCandiesCleared: Long = 0L,
    val bestCombo: Long = 0L,
    val gamesPlayed: Long = 0L,
    val hapticsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScoreRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = GameMode.CLEANUP.firestoreValue,
    val score: Long = 0L,
    val stage: Long = 1L,
    val stars: Long = 0L,
    val candiesCleared: Long = 0L,
    val maxCombo: Long = 1L,
    val movesUsed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedMode: GameMode
        get() = GameMode.fromFirestore(mode)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
