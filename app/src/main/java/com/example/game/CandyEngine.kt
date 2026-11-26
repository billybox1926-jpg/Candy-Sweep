package com.example.game

import com.example.data.GameMode
import kotlin.math.abs
import kotlin.random.Random

const val BOARD_SIZE = 8

enum class CandyType {
    STRAWBERRY,
    LEMON,
    MINT,
    BLUEBERRY,
    GRAPE,
    TANGERINE
}

enum class SpecialType {
    NONE,
    STRIPED_H,
    STRIPED_V,
    WRAPPED,
    RAINBOW
}

data class CandyTile(
    val id: Long,
    val type: CandyType,
    val special: SpecialType = SpecialType.NONE
)

data class CellPos(val row: Int, val col: Int) {
    fun isAdjacentTo(other: CellPos): Boolean =
        abs(row - other.row) + abs(col - other.col) == 1
}

data class StageConfig(
    val stageNumber: Int,
    val mode: GameMode,
    val maxMoves: Int,
    val starThresholds: Triple<Long, Long, Long>,
    val initialFrosting: List<List<Int>>
) {
    val totalFrostingLayers: Int
        get() = initialFrosting.sumOf { row -> row.sum() }
}

data class MatchResolution(
    val clearedCells: Set<CellPos>,
    val spawnedSpecials: Map<CellPos, CandyTile>,
    val basePoints: Long,
    val triggeredSpecialCount: Int
)

object CandyEngine {
    private var nextTileId = 1L

    fun nextId(): Long = nextTileId++

    fun buildStageConfig(mode: GameMode, stage: Int): StageConfig {
        if (mode == GameMode.SCORE_RUSH) {
            val emptyFrosting = List(BOARD_SIZE) { List(BOARD_SIZE) { 0 } }
            return StageConfig(
                stageNumber = 1,
                mode = GameMode.SCORE_RUSH,
                maxMoves = 25,
                starThresholds = Triple(2500L, 5500L, 9500L),
                initialFrosting = emptyFrosting
            )
        }

        val safeStage = stage.coerceAtLeast(1)
        val frosting = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
        val patternIndex = (safeStage - 1) % 5
        val doubleLayerAllowed = safeStage >= 2

        when (patternIndex) {
            0 -> {
                // Sweet Center Diamond
                for (r in 2..5) {
                    for (c in 2..5) {
                        val dist = abs(r - 3.5) + abs(c - 3.5)
                        if (dist <= 2.2) {
                            frosting[r][c] = if (doubleLayerAllowed && dist <= 1.2) 2 else 1
                        }
                    }
                }
            }
            1 -> {
                // Twin Sugar Ribbons
                for (c in 1..6) {
                    frosting[2][c] = if (c in 3..4) 2 else 1
                    frosting[5][c] = if (c in 3..4) 2 else 1
                }
            }
            2 -> {
                // Candy Cross
                for (i in 1..6) {
                    frosting[3][i] = if (i in 3..4) 2 else 1
                    frosting[4][i] = if (i in 3..4) 2 else 1
                    frosting[i][3] = if (i in 3..4) 2 else 1
                    frosting[i][4] = if (i in 3..4) 2 else 1
                }
            }
            3 -> {
                // Checkerboard Glaze
                for (r in 1..6) {
                    for (c in 1..6) {
                        if ((r + c) % 2 == 0) {
                            frosting[r][c] = if (r in 3..4 && c in 3..4) 2 else 1
                        }
                    }
                }
            }
            else -> {
                // Caramel Frame
                for (i in 1..6) {
                    frosting[1][i] = 1
                    frosting[6][i] = 1
                    frosting[i][1] = 1
                    frosting[i][6] = 1
                }
                frosting[3][3] = 2
                frosting[3][4] = 2
                frosting[4][3] = 2
                frosting[4][4] = 2
            }
        }

        // Extra caramel layers on higher stage loops
        if (safeStage >= 6) {
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    if (frosting[r][c] == 1 && (r + c + safeStage) % 3 == 0) {
                        frosting[r][c] = 2
                    }
                }
            }
        }

        val totalLayers = frosting.sumOf { it.sum() }
        val maxMoves = (18 + (totalLayers / 3)).coerceIn(20, 30)
        val star1 = (1500L + totalLayers * 90L)
        val star2 = (star1 * 1.65).toLong()
        val star3 = (star1 * 2.4).toLong()

        return StageConfig(
            stageNumber = safeStage,
            mode = GameMode.CLEANUP,
            maxMoves = maxMoves,
            starThresholds = Triple(star1, star2, star3),
            initialFrosting = frosting.map { it.toList() }
        )
    }

    fun generatePlayableBoard(random: Random = Random.Default): List<List<CandyTile>> =
        CandyBoard.createPlayable(random = random).tiles.map { row -> row.map { it!! } }

    fun swap(grid: List<List<CandyTile?>>, a: CellPos, b: CellPos): List<List<CandyTile?>> =
        CandyBoard.fromGrid(grid).swap(a, b).tiles

    fun isSpecialPairSwap(grid: List<List<CandyTile?>>, a: CellPos, b: CellPos): Boolean =
        CandyBoard.fromGrid(grid).isSpecialPairSwap(a, b)

    fun resolveSpecialPairSwap(
        grid: List<List<CandyTile?>>,
        a: CellPos,
        b: CellPos
    ): MatchResolution = CandyBoard.fromGrid(grid).resolveSpecialPairSwap(a, b)

    fun findMatches(
        grid: List<List<CandyTile?>>,
        preferredPivot: Set<CellPos> = emptySet()
    ): MatchResolution? = CandyBoard.fromGrid(grid).findMatches(preferredPivot)

    fun applyGravityAndRefill(
        grid: List<List<CandyTile?>>,
        random: Random = Random.Default
    ): List<List<CandyTile>> =
        CandyBoard.fromGrid(grid).applyGravityAndRefill(random).tiles.map { row -> row.map { it!! } }

    fun findHintMove(grid: List<List<CandyTile?>>): Pair<CellPos, CellPos>? =
        CandyBoard.fromGrid(grid).findHintMove()

    fun shufflePreservingSpecials(
        grid: List<List<CandyTile>>,
        random: Random = Random.Default
    ): List<List<CandyTile>> =
        CandyBoard.fromGrid(grid).shuffle(random).tiles.map { row -> row.map { it!! } }
}
