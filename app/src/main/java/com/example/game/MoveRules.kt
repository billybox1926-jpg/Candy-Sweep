package com.example.game

import com.example.data.GameMode

/**
 * Urgency band for the move counter UI element.
 */
enum class MoveUrgency {
    NORMAL,
    LOW,
    CRITICAL,
    DEPLETED
}

/**
 * Result of evaluating board state after a turn and its cascades finish.
 */
enum class TurnOutcome {
    CONTINUE,
    STAGE_CLEARED,
    OUT_OF_MOVES,
    RUSH_COMPLETE
}

/**
 * Pure game rules governing move consumption, urgency thresholds, and out-of-moves game-over logic.
 */
object MoveRules {
    const val LOW_MOVES_THRESHOLD = 5
    const val CRITICAL_MOVES_THRESHOLD = 1
    const val UNUSED_MOVE_BONUS_POINTS = 250L

    fun consumeMove(movesLeft: Int): Int = (movesLeft - 1).coerceAtLeast(0)

    fun urgencyFor(movesLeft: Int): MoveUrgency = when {
        movesLeft <= 0 -> MoveUrgency.DEPLETED
        movesLeft <= CRITICAL_MOVES_THRESHOLD -> MoveUrgency.CRITICAL
        movesLeft <= LOW_MOVES_THRESHOLD -> MoveUrgency.LOW
        else -> MoveUrgency.NORMAL
    }

    fun remainingFraction(movesLeft: Int, maxMoves: Int): Float {
        if (maxMoves <= 0) return 0f
        return (movesLeft.toFloat() / maxMoves.toFloat()).coerceIn(0f, 1f)
    }

    fun evaluateTurnOutcome(
        mode: GameMode,
        movesLeft: Int,
        frostingRemaining: Int
    ): TurnOutcome = when {
        mode == GameMode.CLEANUP && frostingRemaining <= 0 -> TurnOutcome.STAGE_CLEARED
        movesLeft <= 0 && mode == GameMode.SCORE_RUSH -> TurnOutcome.RUSH_COMPLETE
        movesLeft <= 0 -> TurnOutcome.OUT_OF_MOVES
        else -> TurnOutcome.CONTINUE
    }

    fun unusedMoveBonus(outcome: TurnOutcome, movesLeft: Int): Long =
        if (outcome == TurnOutcome.STAGE_CLEARED) {
            movesLeft.coerceAtLeast(0) * UNUSED_MOVE_BONUS_POINTS
        } else {
            0L
        }
}
