package com.example

import com.example.data.GameMode
import com.example.game.MoveRules
import com.example.game.MoveUrgency
import com.example.game.TurnOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveRulesTest {

    @Test
    fun consumeMoveDecrementsAndClampsAtZero() {
        assertEquals(19, MoveRules.consumeMove(20))
        assertEquals(0, MoveRules.consumeMove(1))
        assertEquals(0, MoveRules.consumeMove(0))
        assertEquals(0, MoveRules.consumeMove(-3))
    }

    @Test
    fun urgencyReflectsRemainingMoveThresholds() {
        assertEquals(MoveUrgency.NORMAL, MoveRules.urgencyFor(12))
        assertEquals(MoveUrgency.NORMAL, MoveRules.urgencyFor(6))
        assertEquals(MoveUrgency.LOW, MoveRules.urgencyFor(5))
        assertEquals(MoveUrgency.LOW, MoveRules.urgencyFor(2))
        assertEquals(MoveUrgency.CRITICAL, MoveRules.urgencyFor(1))
        assertEquals(MoveUrgency.DEPLETED, MoveRules.urgencyFor(0))
    }

    @Test
    fun cleanupTriggersGameOverWhenOutOfMovesWithFrostingLeft() {
        val outcome = MoveRules.evaluateTurnOutcome(
            mode = GameMode.CLEANUP,
            movesLeft = 0,
            frostingRemaining = 4
        )
        assertEquals(TurnOutcome.OUT_OF_MOVES, outcome)
        assertEquals(0L, MoveRules.unusedMoveBonus(outcome, movesLeft = 0))
    }

    @Test
    fun cleanupClearingLastFrostingOnFinalMoveWinsStage() {
        val outcome = MoveRules.evaluateTurnOutcome(
            mode = GameMode.CLEANUP,
            movesLeft = 0,
            frostingRemaining = 0
        )
        assertEquals(TurnOutcome.STAGE_CLEARED, outcome)
        assertEquals(0L, MoveRules.unusedMoveBonus(outcome, movesLeft = 0))
    }

    @Test
    fun cleanupStageClearAwardsUnusedMoveBonus() {
        val outcome = MoveRules.evaluateTurnOutcome(
            mode = GameMode.CLEANUP,
            movesLeft = 6,
            frostingRemaining = 0
        )
        assertEquals(TurnOutcome.STAGE_CLEARED, outcome)
        assertEquals(1500L, MoveRules.unusedMoveBonus(outcome, movesLeft = 6))
    }

    @Test
    fun scoreRushEndsWhenOutOfMoves() {
        assertEquals(
            TurnOutcome.CONTINUE,
            MoveRules.evaluateTurnOutcome(GameMode.SCORE_RUSH, movesLeft = 1, frostingRemaining = 0)
        )
        assertEquals(
            TurnOutcome.RUSH_COMPLETE,
            MoveRules.evaluateTurnOutcome(GameMode.SCORE_RUSH, movesLeft = 0, frostingRemaining = 0)
        )
    }

    @Test
    fun remainingFractionClampsWithinZeroAndOne() {
        assertEquals(0.5f, MoveRules.remainingFraction(10, 20), 0.001f)
        assertEquals(0f, MoveRules.remainingFraction(0, 20), 0.001f)
        assertEquals(0f, MoveRules.remainingFraction(5, 0), 0.001f)
        assertEquals(1f, MoveRules.remainingFraction(25, 20), 0.001f)
    }
}
