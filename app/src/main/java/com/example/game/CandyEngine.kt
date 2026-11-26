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

    fun generatePlayableBoard(random: Random = Random.Default): List<List<CandyTile>> {
        val types = CandyType.entries
        repeat(30) {
            val grid = Array(BOARD_SIZE) { r ->
                Array(BOARD_SIZE) { c ->
                    CandyTile(id = nextId(), type = types[random.nextInt(types.size)])
                }
            }
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    val forbidden = mutableSetOf<CandyType>()
                    if (c >= 2 && grid[r][c - 1].type == grid[r][c - 2].type) {
                        forbidden.add(grid[r][c - 1].type)
                    }
                    if (r >= 2 && grid[r - 1][c].type == grid[r - 2][c].type) {
                        forbidden.add(grid[r - 1][c].type)
                    }
                    val allowed = types.filter { it !in forbidden }
                    grid[r][c] = CandyTile(
                        id = nextId(),
                        type = allowed[random.nextInt(allowed.size)]
                    )
                }
            }
            val listGrid = grid.map { it.toList() }
            if (findHintMove(listGrid) != null) {
                return listGrid
            }
        }
        return List(BOARD_SIZE) {
            List(BOARD_SIZE) {
                CandyTile(id = nextId(), type = types[random.nextInt(types.size)])
            }
        }
    }

    fun swap(grid: List<List<CandyTile?>>, a: CellPos, b: CellPos): List<List<CandyTile?>> {
        val mutable = grid.map { it.toMutableList() }
        val temp = mutable[a.row][a.col]
        mutable[a.row][a.col] = mutable[b.row][b.col]
        mutable[b.row][b.col] = temp
        return mutable
    }

    fun isSpecialPairSwap(grid: List<List<CandyTile?>>, a: CellPos, b: CellPos): Boolean {
        val tileA = grid[a.row][a.col] ?: return false
        val tileB = grid[b.row][b.col] ?: return false
        if (tileA.special == SpecialType.RAINBOW || tileB.special == SpecialType.RAINBOW) {
            return true
        }
        return tileA.special != SpecialType.NONE && tileB.special != SpecialType.NONE
    }

    fun resolveSpecialPairSwap(
        grid: List<List<CandyTile?>>,
        a: CellPos,
        b: CellPos
    ): MatchResolution {
        val tileA = grid[a.row][a.col] ?: return MatchResolution(emptySet(), emptyMap(), 0L, 0)
        val tileB = grid[b.row][b.col] ?: return MatchResolution(emptySet(), emptyMap(), 0L, 0)
        val initialTargets = mutableSetOf(a, b)

        when {
            tileA.special == SpecialType.RAINBOW && tileB.special == SpecialType.RAINBOW -> {
                for (r in 0 until BOARD_SIZE) {
                    for (c in 0 until BOARD_SIZE) {
                        initialTargets.add(CellPos(r, c))
                    }
                }
            }
            tileA.special == SpecialType.RAINBOW || tileB.special == SpecialType.RAINBOW -> {
                val targetType = if (tileA.special == SpecialType.RAINBOW) tileB.type else tileA.type
                for (r in 0 until BOARD_SIZE) {
                    for (c in 0 until BOARD_SIZE) {
                        if (grid[r][c]?.type == targetType) {
                            initialTargets.add(CellPos(r, c))
                        }
                    }
                }
            }
            tileA.special == SpecialType.WRAPPED && tileB.special == SpecialType.WRAPPED -> {
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val nr = b.row + dr
                        val nc = b.col + dc
                        if (nr in 0 until BOARD_SIZE && nc in 0 until BOARD_SIZE) {
                            initialTargets.add(CellPos(nr, nc))
                        }
                    }
                }
            }
            (tileA.special == SpecialType.WRAPPED || tileB.special == SpecialType.WRAPPED) &&
                (tileA.special == SpecialType.STRIPED_H || tileA.special == SpecialType.STRIPED_V ||
                    tileB.special == SpecialType.STRIPED_H || tileB.special == SpecialType.STRIPED_V) -> {
                for (dr in -1..1) {
                    val nr = b.row + dr
                    if (nr in 0 until BOARD_SIZE) {
                        for (c in 0 until BOARD_SIZE) initialTargets.add(CellPos(nr, c))
                    }
                }
                for (dc in -1..1) {
                    val nc = b.col + dc
                    if (nc in 0 until BOARD_SIZE) {
                        for (r in 0 until BOARD_SIZE) initialTargets.add(CellPos(r, nc))
                    }
                }
            }
            else -> {
                // Striped + Striped -> Cross blast
                for (c in 0 until BOARD_SIZE) initialTargets.add(CellPos(b.row, c))
                for (r in 0 until BOARD_SIZE) initialTargets.add(CellPos(r, b.col))
            }
        }

        val (expanded, specialsTriggered) = expandSpecialTriggers(grid, initialTargets)
        val basePoints = expanded.size * 75L + 350L
        return MatchResolution(
            clearedCells = expanded,
            spawnedSpecials = emptyMap(),
            basePoints = basePoints,
            triggeredSpecialCount = specialsTriggered + 2
        )
    }

    fun findMatches(
        grid: List<List<CandyTile?>>,
        preferredPivot: Set<CellPos> = emptySet()
    ): MatchResolution? {
        data class Run(val cells: List<CellPos>, val horizontal: Boolean, val type: CandyType)

        val runs = mutableListOf<Run>()

        // Horizontal runs
        for (r in 0 until BOARD_SIZE) {
            var c = 0
            while (c < BOARD_SIZE) {
                val tile = grid[r][c]
                if (tile == null || tile.special == SpecialType.RAINBOW) {
                    c++
                    continue
                }
                var end = c + 1
                while (end < BOARD_SIZE &&
                    grid[r][end]?.type == tile.type &&
                    grid[r][end]?.special != SpecialType.RAINBOW
                ) {
                    end++
                }
                if (end - c >= 3) {
                    runs.add(Run((c until end).map { CellPos(r, it) }, true, tile.type))
                }
                c = end
            }
        }

        // Vertical runs
        for (c in 0 until BOARD_SIZE) {
            var r = 0
            while (r < BOARD_SIZE) {
                val tile = grid[r][c]
                if (tile == null || tile.special == SpecialType.RAINBOW) {
                    r++
                    continue
                }
                var end = r + 1
                while (end < BOARD_SIZE &&
                    grid[end][c]?.type == tile.type &&
                    grid[end][c]?.special != SpecialType.RAINBOW
                ) {
                    end++
                }
                if (end - r >= 3) {
                    runs.add(Run((r until end).map { CellPos(it, c) }, false, tile.type))
                }
                r = end
            }
        }

        if (runs.isEmpty()) return null

        // Group intersecting runs of the same CandyType
        val visited = BooleanArray(runs.size)
        val matchedCells = mutableSetOf<CellPos>()
        val spawnedSpecials = mutableMapOf<CellPos, CandyTile>()
        var rawPoints = 0L

        for (i in runs.indices) {
            if (visited[i]) continue
            val groupRuns = mutableListOf<Run>()
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                val current = runs[idx]
                groupRuns.add(current)
                val currentSet = current.cells.toSet()
                for (j in runs.indices) {
                    if (!visited[j] && runs[j].type == current.type) {
                        if (runs[j].cells.any { it in currentSet }) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
            }

            val groupCells = groupRuns.flatMap { it.cells }.toSet()
            matchedCells.addAll(groupCells)

            val maxRunLength = groupRuns.maxOf { it.cells.size }
            val hasHorizontal = groupRuns.any { it.horizontal }
            val hasVertical = groupRuns.any { !it.horizontal }
            val pivot = groupCells.firstOrNull { it in preferredPivot }
                ?: groupRuns.first().cells[groupRuns.first().cells.size / 2]

            val specialToSpawn = when {
                maxRunLength >= 5 -> SpecialType.RAINBOW
                hasHorizontal && hasVertical -> SpecialType.WRAPPED
                maxRunLength == 4 -> {
                    val fourRun = groupRuns.first { it.cells.size == 4 }
                    if (fourRun.horizontal) SpecialType.STRIPED_V else SpecialType.STRIPED_H
                }
                else -> SpecialType.NONE
            }

            if (specialToSpawn != SpecialType.NONE) {
                spawnedSpecials[pivot] = CandyTile(
                    id = nextId(),
                    type = groupRuns.first().type,
                    special = specialToSpawn
                )
            }

            rawPoints += when {
                maxRunLength >= 5 -> groupCells.size * 100L + 250L
                hasHorizontal && hasVertical -> groupCells.size * 85L + 180L
                maxRunLength == 4 -> groupCells.size * 70L + 100L
                else -> groupCells.size * 50L
            }
        }

        val (expandedCells, triggeredSpecials) = expandSpecialTriggers(grid, matchedCells)
        val extraPoints = (expandedCells.size - matchedCells.size).coerceAtLeast(0) * 65L

        return MatchResolution(
            clearedCells = expandedCells,
            spawnedSpecials = spawnedSpecials,
            basePoints = rawPoints + extraPoints,
            triggeredSpecialCount = triggeredSpecials
        )
    }

    private fun expandSpecialTriggers(
        grid: List<List<CandyTile?>>,
        initialCells: Set<CellPos>
    ): Pair<Set<CellPos>, Int> {
        val cleared = initialCells.toMutableSet()
        val processedSpecials = mutableSetOf<CellPos>()
        val queue = ArrayDeque<CellPos>(initialCells)
        var triggeredCount = 0

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (pos in processedSpecials) continue
            val tile = grid[pos.row][pos.col] ?: continue
            if (tile.special == SpecialType.NONE) continue
            processedSpecials.add(pos)
            triggeredCount++

            val newlyHit = mutableListOf<CellPos>()
            when (tile.special) {
                SpecialType.STRIPED_H -> {
                    for (c in 0 until BOARD_SIZE) newlyHit.add(CellPos(pos.row, c))
                }
                SpecialType.STRIPED_V -> {
                    for (r in 0 until BOARD_SIZE) newlyHit.add(CellPos(r, pos.col))
                }
                SpecialType.WRAPPED -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            val nr = pos.row + dr
                            val nc = pos.col + dc
                            if (nr in 0 until BOARD_SIZE && nc in 0 until BOARD_SIZE) {
                                newlyHit.add(CellPos(nr, nc))
                            }
                        }
                    }
                }
                SpecialType.RAINBOW -> {
                    for (r in 0 until BOARD_SIZE) {
                        for (c in 0 until BOARD_SIZE) {
                            if (grid[r][c]?.type == tile.type) {
                                newlyHit.add(CellPos(r, c))
                            }
                        }
                    }
                }
                SpecialType.NONE -> Unit
            }

            for (hit in newlyHit) {
                if (cleared.add(hit)) {
                    queue.add(hit)
                }
            }
        }
        return cleared to triggeredCount
    }

    fun applyGravityAndRefill(
        grid: List<List<CandyTile?>>,
        random: Random = Random.Default
    ): List<List<CandyTile>> {
        val types = CandyType.entries
        val next = Array(BOARD_SIZE) { arrayOfNulls<CandyTile>(BOARD_SIZE) }

        for (c in 0 until BOARD_SIZE) {
            var writeRow = BOARD_SIZE - 1
            for (r in BOARD_SIZE - 1 downTo 0) {
                val existing = grid[r][c]
                if (existing != null) {
                    next[writeRow][c] = existing
                    writeRow--
                }
            }
            while (writeRow >= 0) {
                next[writeRow][c] = CandyTile(
                    id = nextId(),
                    type = types[random.nextInt(types.size)]
                )
                writeRow--
            }
        }
        return next.map { row -> row.map { it!! } }
    }

    fun findHintMove(grid: List<List<CandyTile?>>): Pair<CellPos, CellPos>? {
        var fallback: Pair<CellPos, CellPos>? = null
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                val pos = CellPos(r, c)
                val neighbors = listOfNotNull(
                    if (c + 1 < BOARD_SIZE) CellPos(r, c + 1) else null,
                    if (r + 1 < BOARD_SIZE) CellPos(r + 1, c) else null
                )
                for (next in neighbors) {
                    if (isSpecialPairSwap(grid, pos, next)) {
                        return pos to next
                    }
                    val swapped = swap(grid, pos, next)
                    val match = findMatches(swapped, setOf(pos, next))
                    if (match != null) {
                        if (match.clearedCells.size >= 4) {
                            return pos to next
                        }
                        if (fallback == null) {
                            fallback = pos to next
                        }
                    }
                }
            }
        }
        return fallback
    }

    fun shufflePreservingSpecials(
        grid: List<List<CandyTile>>,
        random: Random = Random.Default
    ): List<List<CandyTile>> {
        val flattened = grid.flatten().toMutableList()
        repeat(25) {
            flattened.shuffle(random)
            val candidate = List(BOARD_SIZE) { r ->
                List(BOARD_SIZE) { c -> flattened[r * BOARD_SIZE + c] }
            }
            if (findMatches(candidate) == null && findHintMove(candidate) != null) {
                return candidate
            }
        }
        return generatePlayableBoard(random)
    }
}
