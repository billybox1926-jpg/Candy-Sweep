package com.example.game

import kotlin.random.Random

enum class SwipeDirection(val dRow: Int, val dCol: Int) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1);

    companion object {
        fun fromDelta(dRow: Int, dCol: Int): SwipeDirection? = when {
            dRow == -1 && dCol == 0 -> UP
            dRow == 1 && dCol == 0 -> DOWN
            dRow == 0 && dCol == -1 -> LEFT
            dRow == 0 && dCol == 1 -> RIGHT
            else -> null
        }
    }
}

sealed interface SwipeAttempt {
    data object OutOfBounds : SwipeAttempt
    data class InvalidSwap(val from: CellPos, val to: CellPos) : SwipeAttempt
    data class ValidMatch(
        val swappedBoard: CandyBoard,
        val resolution: MatchResolution
    ) : SwipeAttempt
}

data class ClearStepResult(
    val boardAfterClear: CandyBoard,
    val clearedTiles: List<Pair<CellPos, CandyTile>>,
    val spawnedSpecialIds: Set<Long>,
    val frostingClearedCount: Int,
    val stepPoints: Long
)

data class RefillResult(
    val board: CandyBoard,
    val spawnedStartRows: Map<Long, Float>
)

/**
 * Manages the candy grid, active candy types, sticky frosting layers,
 * swipe validation, match detection, special candy combos, gravity refill,
 * hints, and deadlock shuffling.
 */
class CandyBoard private constructor(
    val size: Int,
    val availableTypes: List<CandyType>,
    val tiles: List<List<CandyTile?>>,
    val frosting: List<List<Int>>
) {
    init {
        require(size >= 3) { "Board size must be at least 3" }
        require(availableTypes.size >= 3) { "At least 3 candy types are required" }
        require(tiles.size == size && tiles.all { it.size == size }) {
            "Tiles grid must be ${size}x$size"
        }
        require(frosting.size == size && frosting.all { it.size == size }) {
            "Frosting grid must be ${size}x$size"
        }
    }

    val frostingRemaining: Int
        get() = frosting.sumOf { row -> row.sum() }

    fun isInside(pos: CellPos): Boolean =
        pos.row in 0 until size && pos.col in 0 until size

    fun tileAt(pos: CellPos): CandyTile? =
        if (isInside(pos)) tiles[pos.row][pos.col] else null

    fun frostingAt(pos: CellPos): Int =
        if (isInside(pos)) frosting[pos.row][pos.col] else 0

    fun targetOf(from: CellPos, direction: SwipeDirection): CellPos? {
        val target = CellPos(from.row + direction.dRow, from.col + direction.dCol)
        return if (isInside(from) && isInside(target)) target else null
    }

    fun swap(a: CellPos, b: CellPos): CandyBoard {
        require(isInside(a) && isInside(b) && a.isAdjacentTo(b)) {
            "Swap positions must be adjacent and inside the board"
        }
        val nextTiles = tiles.map { it.toMutableList() }
        val temp = nextTiles[a.row][a.col]
        nextTiles[a.row][a.col] = nextTiles[b.row][b.col]
        nextTiles[b.row][b.col] = temp
        return copyBoard(tiles = nextTiles)
    }

    fun isSpecialPairSwap(a: CellPos, b: CellPos): Boolean {
        val tileA = tileAt(a) ?: return false
        val tileB = tileAt(b) ?: return false
        if (tileA.special == SpecialType.RAINBOW || tileB.special == SpecialType.RAINBOW) {
            return true
        }
        return tileA.special != SpecialType.NONE && tileB.special != SpecialType.NONE
    }

    fun evaluateSwipe(from: CellPos, direction: SwipeDirection): SwipeAttempt {
        val to = targetOf(from, direction) ?: return SwipeAttempt.OutOfBounds
        return evaluateSwap(from, to)
    }

    fun evaluateSwap(a: CellPos, b: CellPos): SwipeAttempt {
        if (!isInside(a) || !isInside(b) || !a.isAdjacentTo(b)) {
            return SwipeAttempt.OutOfBounds
        }
        val specialPair = isSpecialPairSwap(a, b)
        val swapped = swap(a, b)
        val resolution = if (specialPair) {
            swapped.resolveSpecialPairSwap(a, b)
        } else {
            swapped.findMatches(preferredPivot = setOf(a, b))
        }
        return if (resolution == null || resolution.clearedCells.isEmpty()) {
            SwipeAttempt.InvalidSwap(a, b)
        } else {
            SwipeAttempt.ValidMatch(swappedBoard = swapped, resolution = resolution)
        }
    }

    fun resolveSpecialPairSwap(a: CellPos, b: CellPos): MatchResolution {
        val tileA = tileAt(a) ?: return MatchResolution(emptySet(), emptyMap(), 0L, 0)
        val tileB = tileAt(b) ?: return MatchResolution(emptySet(), emptyMap(), 0L, 0)
        val initialTargets = mutableSetOf(a, b)

        when {
            tileA.special == SpecialType.RAINBOW && tileB.special == SpecialType.RAINBOW -> {
                for (r in 0 until size) {
                    for (c in 0 until size) {
                        initialTargets.add(CellPos(r, c))
                    }
                }
            }
            tileA.special == SpecialType.RAINBOW || tileB.special == SpecialType.RAINBOW -> {
                val targetType = if (tileA.special == SpecialType.RAINBOW) tileB.type else tileA.type
                for (r in 0 until size) {
                    for (c in 0 until size) {
                        if (tiles[r][c]?.type == targetType) {
                            initialTargets.add(CellPos(r, c))
                        }
                    }
                }
            }
            tileA.special == SpecialType.WRAPPED && tileB.special == SpecialType.WRAPPED -> {
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val pos = CellPos(b.row + dr, b.col + dc)
                        if (isInside(pos)) initialTargets.add(pos)
                    }
                }
            }
            (tileA.special == SpecialType.WRAPPED || tileB.special == SpecialType.WRAPPED) &&
                (tileA.special == SpecialType.STRIPED_H || tileA.special == SpecialType.STRIPED_V ||
                    tileB.special == SpecialType.STRIPED_H || tileB.special == SpecialType.STRIPED_V) -> {
                for (dr in -1..1) {
                    val nr = b.row + dr
                    if (nr in 0 until size) {
                        for (c in 0 until size) initialTargets.add(CellPos(nr, c))
                    }
                }
                for (dc in -1..1) {
                    val nc = b.col + dc
                    if (nc in 0 until size) {
                        for (r in 0 until size) initialTargets.add(CellPos(r, nc))
                    }
                }
            }
            else -> {
                // Striped + Striped -> full cross blast
                for (c in 0 until size) initialTargets.add(CellPos(b.row, c))
                for (r in 0 until size) initialTargets.add(CellPos(r, b.col))
            }
        }

        val (expanded, specialsTriggered) = expandSpecialTriggers(initialTargets)
        val basePoints = expanded.size * 75L + 350L
        return MatchResolution(
            clearedCells = expanded,
            spawnedSpecials = emptyMap(),
            basePoints = basePoints,
            triggeredSpecialCount = specialsTriggered + 2
        )
    }

    fun findMatches(preferredPivot: Set<CellPos> = emptySet()): MatchResolution? {
        data class Run(val cells: List<CellPos>, val horizontal: Boolean, val type: CandyType)

        val runs = mutableListOf<Run>()

        // Horizontal runs of length >= 3
        for (r in 0 until size) {
            var c = 0
            while (c < size) {
                val tile = tiles[r][c]
                if (tile == null || tile.special == SpecialType.RAINBOW) {
                    c++
                    continue
                }
                var end = c + 1
                while (end < size &&
                    tiles[r][end]?.type == tile.type &&
                    tiles[r][end]?.special != SpecialType.RAINBOW
                ) {
                    end++
                }
                if (end - c >= 3) {
                    runs.add(Run((c until end).map { CellPos(r, it) }, true, tile.type))
                }
                c = end
            }
        }

        // Vertical runs of length >= 3
        for (c in 0 until size) {
            var r = 0
            while (r < size) {
                val tile = tiles[r][c]
                if (tile == null || tile.special == SpecialType.RAINBOW) {
                    r++
                    continue
                }
                var end = r + 1
                while (end < size &&
                    tiles[end][c]?.type == tile.type &&
                    tiles[end][c]?.special != SpecialType.RAINBOW
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

        // Merge intersecting runs of the same CandyType
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
                    id = CandyEngine.nextId(),
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

        val (expandedCells, triggeredSpecials) = expandSpecialTriggers(matchedCells)
        val extraPoints = (expandedCells.size - matchedCells.size).coerceAtLeast(0) * 65L

        return MatchResolution(
            clearedCells = expandedCells,
            spawnedSpecials = spawnedSpecials,
            basePoints = rawPoints + extraPoints,
            triggeredSpecialCount = triggeredSpecials
        )
    }

    fun clearMatches(resolution: MatchResolution, comboStep: Int = 1): ClearStepResult {
        val cleared = resolution.clearedCells
        val clearedTiles = cleared.mapNotNull { pos ->
            tileAt(pos)?.let { pos to it }
        }

        var frostingCleared = 0
        val nextFrosting = frosting.mapIndexed { r, rowList ->
            rowList.mapIndexed { c, layer ->
                if (layer > 0 && CellPos(r, c) in cleared) {
                    frostingCleared++
                    layer - 1
                } else {
                    layer
                }
            }
        }

        val nextTiles = tiles.mapIndexed { r, rowList ->
            rowList.mapIndexed { c, tile ->
                val pos = CellPos(r, c)
                when {
                    pos in resolution.spawnedSpecials -> resolution.spawnedSpecials[pos]
                    pos in cleared -> null
                    else -> tile
                }
            }
        }

        val multiplier = 1.0 + (comboStep.coerceAtLeast(1) - 1) * 0.45
        val stepPoints = ((resolution.basePoints + frostingCleared * 120L) * multiplier).toLong()

        return ClearStepResult(
            boardAfterClear = copyBoard(tiles = nextTiles, frosting = nextFrosting),
            clearedTiles = clearedTiles,
            spawnedSpecialIds = resolution.spawnedSpecials.values.map { it.id }.toSet(),
            frostingClearedCount = frostingCleared,
            stepPoints = stepPoints
        )
    }

    fun applyGravityAndRefill(random: Random = Random.Default): CandyBoard =
        applyGravityAndRefillWithOrigins(random).board

    fun applyGravityAndRefillWithOrigins(random: Random = Random.Default): RefillResult {
        val next = Array(size) { arrayOfNulls<CandyTile>(size) }
        val spawnedStartRows = mutableMapOf<Long, Float>()

        for (c in 0 until size) {
            var writeRow = size - 1
            for (r in size - 1 downTo 0) {
                val existing = tiles[r][c]
                if (existing != null) {
                    next[writeRow][c] = existing
                    writeRow--
                }
            }
            val emptyCount = writeRow + 1
            while (writeRow >= 0) {
                val newTile = CandyTile(
                    id = CandyEngine.nextId(),
                    type = availableTypes[random.nextInt(availableTypes.size)]
                )
                next[writeRow][c] = newTile
                // Stack newly refilled candies cleanly above the top edge of the board
                spawnedStartRows[newTile.id] = (writeRow - emptyCount - 0.5f)
                writeRow--
            }
        }
        return RefillResult(
            board = copyBoard(tiles = next.map { it.toList() }),
            spawnedStartRows = spawnedStartRows
        )
    }

    fun findHintMove(): Pair<CellPos, CellPos>? {
        var fallback: Pair<CellPos, CellPos>? = null
        for (r in 0 until size) {
            for (c in 0 until size) {
                val pos = CellPos(r, c)
                val neighbors = listOfNotNull(
                    if (c + 1 < size) CellPos(r, c + 1) else null,
                    if (r + 1 < size) CellPos(r + 1, c) else null
                )
                for (next in neighbors) {
                    if (isSpecialPairSwap(pos, next)) {
                        return pos to next
                    }
                    val swapped = swap(pos, next)
                    val match = swapped.findMatches(setOf(pos, next))
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

    fun hasValidMoves(): Boolean = findHintMove() != null

    fun shuffle(random: Random = Random.Default): CandyBoard {
        val flattened = tiles.flatten().filterNotNull().toMutableList()
        if (flattened.size == size * size) {
            repeat(25) {
                flattened.shuffle(random)
                val candidateGrid = List(size) { r ->
                    List(size) { c -> flattened[r * size + c] }
                }
                val candidate = copyBoard(tiles = candidateGrid)
                if (candidate.findMatches() == null && candidate.hasValidMoves()) {
                    return candidate
                }
            }
        }
        return createPlayable(
            size = size,
            frosting = frosting,
            availableTypes = availableTypes,
            random = random
        )
    }

    private fun expandSpecialTriggers(initialCells: Set<CellPos>): Pair<Set<CellPos>, Int> {
        val cleared = initialCells.toMutableSet()
        val processedSpecials = mutableSetOf<CellPos>()
        val queue = ArrayDeque<CellPos>(initialCells)
        var triggeredCount = 0

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (pos in processedSpecials) continue
            val tile = tileAt(pos) ?: continue
            if (tile.special == SpecialType.NONE) continue
            processedSpecials.add(pos)
            triggeredCount++

            val newlyHit = mutableListOf<CellPos>()
            when (tile.special) {
                SpecialType.STRIPED_H -> {
                    for (c in 0 until size) newlyHit.add(CellPos(pos.row, c))
                }
                SpecialType.STRIPED_V -> {
                    for (r in 0 until size) newlyHit.add(CellPos(r, pos.col))
                }
                SpecialType.WRAPPED -> {
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            val neighbor = CellPos(pos.row + dr, pos.col + dc)
                            if (isInside(neighbor)) newlyHit.add(neighbor)
                        }
                    }
                }
                SpecialType.RAINBOW -> {
                    for (r in 0 until size) {
                        for (c in 0 until size) {
                            if (tiles[r][c]?.type == tile.type) {
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

    fun withFrosting(nextFrosting: List<List<Int>>): CandyBoard =
        copyBoard(frosting = nextFrosting)

    private fun copyBoard(
        tiles: List<List<CandyTile?>> = this.tiles,
        frosting: List<List<Int>> = this.frosting
    ): CandyBoard = CandyBoard(
        size = size,
        availableTypes = availableTypes,
        tiles = tiles,
        frosting = frosting
    )

    companion object {
        fun createPlayable(
            stageConfig: StageConfig,
            availableTypes: List<CandyType> = CandyType.entries,
            random: Random = Random.Default
        ): CandyBoard = createPlayable(
            size = stageConfig.initialFrosting.size,
            frosting = stageConfig.initialFrosting,
            availableTypes = availableTypes,
            random = random
        )

        fun createPlayable(
            size: Int = BOARD_SIZE,
            frosting: List<List<Int>> = List(size) { List(size) { 0 } },
            availableTypes: List<CandyType> = CandyType.entries,
            random: Random = Random.Default
        ): CandyBoard {
            repeat(30) {
                val grid = Array(size) {
                    Array(size) {
                        CandyTile(
                            id = CandyEngine.nextId(),
                            type = availableTypes[random.nextInt(availableTypes.size)]
                        )
                    }
                }
                for (r in 0 until size) {
                    for (c in 0 until size) {
                        val forbidden = mutableSetOf<CandyType>()
                        if (c >= 2 && grid[r][c - 1].type == grid[r][c - 2].type) {
                            forbidden.add(grid[r][c - 1].type)
                        }
                        if (r >= 2 && grid[r - 1][c].type == grid[r - 2][c].type) {
                            forbidden.add(grid[r - 1][c].type)
                        }
                        val allowed = availableTypes.filter { it !in forbidden }
                        grid[r][c] = CandyTile(
                            id = CandyEngine.nextId(),
                            type = allowed[random.nextInt(allowed.size)]
                        )
                    }
                }
                val candidate = CandyBoard(
                    size = size,
                    availableTypes = availableTypes,
                    tiles = grid.map { it.toList() },
                    frosting = frosting
                )
                if (candidate.hasValidMoves()) {
                    return candidate
                }
            }
            val fallbackTiles = List(size) {
                List(size) {
                    CandyTile(
                        id = CandyEngine.nextId(),
                        type = availableTypes[random.nextInt(availableTypes.size)]
                    )
                }
            }
            return CandyBoard(
                size = size,
                availableTypes = availableTypes,
                tiles = fallbackTiles,
                frosting = frosting
            )
        }

        fun fromGrid(
            tiles: List<List<CandyTile?>>,
            frosting: List<List<Int>> = List(tiles.size) { List(tiles.size) { 0 } },
            availableTypes: List<CandyType> = CandyType.entries
        ): CandyBoard = CandyBoard(
            size = tiles.size,
            availableTypes = availableTypes,
            tiles = tiles,
            frosting = frosting
        )
    }
}
