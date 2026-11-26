package com.example

import com.example.game.CandyBoard
import com.example.game.CandyEngine
import com.example.game.CandyTile
import com.example.game.CandyType
import com.example.game.CellPos
import com.example.game.SpecialType
import com.example.game.SwipeAttempt
import com.example.game.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CandyBoardTest {

    private fun boardFromPattern(
        rows: List<String>,
        frosting: List<List<Int>> = List(rows.size) { List(rows.size) { 0 } }
    ): CandyBoard {
        val map = mapOf(
            'S' to CandyType.STRAWBERRY,
            'L' to CandyType.LEMON,
            'M' to CandyType.MINT,
            'B' to CandyType.BLUEBERRY,
            'G' to CandyType.GRAPE,
            'T' to CandyType.TANGERINE
        )
        val grid = rows.map { row ->
            row.map { ch ->
                CandyTile(id = CandyEngine.nextId(), type = map.getValue(ch))
            }
        }
        return CandyBoard.fromGrid(tiles = grid, frosting = frosting)
    }

    @Test
    fun createPlayable_hasNoInitialMatchesAndHasValidMove() {
        val board = CandyBoard.createPlayable(random = Random(42))
        assertEquals(8, board.size)
        assertNull(board.findMatches())
        assertTrue(board.hasValidMoves())
        assertNotNull(board.findHintMove())
    }

    @Test
    fun evaluateSwipe_validHorizontalMatch3_returnsValidMatchAndClearsFrosting() {
        val frosting = List(4) { r -> List(4) { c -> if (r == 0 && c in 0..2) 2 else 0 } }
        // Row 0: S S L M -> swipe (1,2) S UP to (0,2) forms S S S at (0,0..2)
        val board = boardFromPattern(
            rows = listOf(
                "SSLM",
                "MGSL",
                "BLGB",
                "TMTB"
            ),
            frosting = frosting
        )

        val attempt = board.evaluateSwipe(CellPos(1, 2), SwipeDirection.UP)
        assertTrue(attempt is SwipeAttempt.ValidMatch)
        val match = attempt as SwipeAttempt.ValidMatch
        assertEquals(
            setOf(CellPos(0, 0), CellPos(0, 1), CellPos(0, 2)),
            match.resolution.clearedCells
        )

        val clearStep = match.swappedBoard.clearMatches(match.resolution, comboStep = 1)
        assertEquals(3, clearStep.frostingClearedCount)
        assertEquals(3, clearStep.boardAfterClear.frostingRemaining)

        val refilled = clearStep.boardAfterClear.applyGravityAndRefill(Random(7))
        for (r in 0 until refilled.size) {
            for (c in 0 until refilled.size) {
                assertNotNull(refilled.tileAt(CellPos(r, c)))
            }
        }
    }

    @Test
    fun evaluateSwipe_nonMatchingSwap_returnsInvalidSwap() {
        val board = boardFromPattern(
            rows = listOf(
                "SLMB",
                "MGTS",
                "BLGM",
                "TMLB"
            )
        )
        val attempt = board.evaluateSwipe(CellPos(0, 0), SwipeDirection.RIGHT)
        assertTrue(attempt is SwipeAttempt.InvalidSwap)
    }

    @Test
    fun evaluateSwipe_match4SpawnsStripedCandy() {
        // Swapping (1,2) S UP to (0,2) gives S S S S across row 0
        val board = boardFromPattern(
            rows = listOf(
                "SSLS",
                "MGSL",
                "BLGB",
                "TMTB"
            )
        )
        val attempt = board.evaluateSwipe(CellPos(1, 2), SwipeDirection.UP)
        assertTrue(attempt is SwipeAttempt.ValidMatch)
        val match = attempt as SwipeAttempt.ValidMatch
        assertEquals(4, match.resolution.clearedCells.size)
        val spawned = match.resolution.spawnedSpecials.values.single()
        assertEquals(SpecialType.STRIPED_V, spawned.special)
    }

    @Test
    fun evaluateSwap_rainbowWithCandySweepsAllMatchingColor() {
        val base = boardFromPattern(
            rows = listOf(
                "SLMB",
                "MGLS",
                "BSGM",
                "TMLB"
            )
        )
        val mutable = base.tiles.map { it.toMutableList() }
        mutable[0][0] = CandyTile(id = CandyEngine.nextId(), type = CandyType.STRAWBERRY, special = SpecialType.RAINBOW)
        val rainbowBoard = CandyBoard.fromGrid(mutable)

        val attempt = rainbowBoard.evaluateSwipe(CellPos(0, 0), SwipeDirection.RIGHT)
        assertTrue(attempt is SwipeAttempt.ValidMatch)
        val match = attempt as SwipeAttempt.ValidMatch
        // All LEMON tiles (0,1), (1,2), (3,2) + rainbow origin (0,0) cleared
        assertTrue(CellPos(0, 0) in match.resolution.clearedCells)
        assertTrue(CellPos(0, 1) in match.resolution.clearedCells)
        assertTrue(CellPos(1, 2) in match.resolution.clearedCells)
        assertTrue(CellPos(3, 2) in match.resolution.clearedCells)
    }

    @Test
    fun shuffle_producesMatchFreePlayableBoard() {
        val board = CandyBoard.createPlayable(random = Random(99))
        val shuffled = board.shuffle(Random(101))
        assertNull(shuffled.findMatches())
        assertTrue(shuffled.hasValidMoves())
    }
}
