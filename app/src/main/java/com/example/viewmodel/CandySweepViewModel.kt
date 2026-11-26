package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CandyRepository
import com.example.data.GameMode
import com.example.data.PlayerProfile
import com.example.data.ScoreRecord
import com.example.data.UiState
import com.example.game.CandyBoard
import com.example.game.CandyEngine
import com.example.game.CandyTile
import com.example.game.CandyType
import com.example.game.CellPos
import com.example.game.MatchResolution
import com.example.game.StageConfig
import com.example.game.SwipeAttempt
import com.example.game.SwipeDirection
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FloatingCallout(
    val id: Long,
    val row: Float,
    val col: Float,
    val text: String,
    val highlight: Boolean = false
)

data class BurstParticle(
    val id: Long,
    val row: Int,
    val col: Int,
    val type: CandyType
)

enum class RunOutcome {
    PLAYING,
    STAGE_CLEARED,
    OUT_OF_MOVES,
    RUSH_COMPLETE
}

enum class SaveStatus {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

data class GameBoardUiState(
    val stageConfig: StageConfig,
    val candyBoard: CandyBoard,
    val score: Long = 0L,
    val movesLeft: Int = stageConfig.maxMoves,
    val movesUsed: Int = 0,
    val candiesCleared: Int = 0,
    val maxCombo: Int = 1,
    val currentCombo: Int = 0,
    val selectedCell: CellPos? = null,
    val hintCells: Set<CellPos> = emptySet(),
    val clearingCells: Set<CellPos> = emptySet(),
    val spawnedTileStartRows: Map<Long, Float> = emptyMap(),
    val newlySpawnedSpecialIds: Set<Long> = emptySet(),
    val bouncedPair: Pair<CellPos, CellPos>? = null,
    val callouts: List<FloatingCallout> = emptyList(),
    val bursts: List<BurstParticle> = emptyList(),
    val bannerMessage: String? = null,
    val busy: Boolean = false,
    val outcome: RunOutcome = RunOutcome.PLAYING,
    val newPersonalBest: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val hapticEventToken: Long = 0L,
    val bigHapticEventToken: Long = 0L
) {
    val board: List<List<CandyTile?>>
        get() = candyBoard.tiles

    val frosting: List<List<Int>>
        get() = candyBoard.frosting

    val frostingRemaining: Int
        get() = candyBoard.frostingRemaining

    val earnedStars: Int
        get() {
            val (s1, s2, s3) = stageConfig.starThresholds
            return when {
                score >= s3 -> 3
                score >= s2 -> 2
                score >= s1 -> 1
                outcome == RunOutcome.STAGE_CLEARED -> 1
                else -> 0
            }
        }
}

class CandySweepViewModel(
    private val repository: CandyRepository,
    private val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    val profileState: StateFlow<UiState<PlayerProfile?>> = repository.observeProfile(currentUserId)
        .map<PlayerProfile?, UiState<PlayerProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load player profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val scoresState: StateFlow<UiState<List<ScoreRecord>>> = repository.observeScores(currentUserId)
        .map<List<ScoreRecord>, UiState<List<ScoreRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing scores", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load high scores"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _gameState = MutableStateFlow(createInitialGameState(GameMode.CLEANUP, 1))
    val gameState: StateFlow<GameBoardUiState> = _gameState.asStateFlow()

    private val _profileFeedback = MutableStateFlow<String?>(null)
    val profileFeedback: StateFlow<String?> = _profileFeedback.asStateFlow()

    private var eventIdCounter = 1L
    private var idleHintJob: Job? = null
    private var syncedInitialStage = false

    init {
        viewModelScope.launch {
            val result = repository.ensureProfile(currentUserId, initialDisplayName)
            result.onSuccess { profile ->
                if (!syncedInitialStage && _gameState.value.movesUsed == 0) {
                    syncedInitialStage = true
                    val startStage = profile.highestStage.toInt().coerceAtLeast(1)
                    if (startStage > 1) {
                        startNewGame(GameMode.CLEANUP, startStage)
                    }
                }
            }
        }
        scheduleIdleHint()
    }

    private fun createInitialGameState(mode: GameMode, stage: Int): GameBoardUiState {
        val config = CandyEngine.buildStageConfig(mode, stage)
        val candyBoard = CandyBoard.createPlayable(stageConfig = config)
        val entranceOrigins = buildMap {
            for (r in 0 until candyBoard.size) {
                for (c in 0 until candyBoard.size) {
                    candyBoard.tiles[r][c]?.let { tile ->
                        put(tile.id, r - 1.6f)
                    }
                }
            }
        }
        return GameBoardUiState(
            stageConfig = config,
            candyBoard = candyBoard,
            spawnedTileStartRows = entranceOrigins
        )
    }

    fun startNewGame(mode: GameMode = _gameState.value.stageConfig.mode, stage: Int? = null) {
        val profileStage = (profileState.value as? UiState.Success)?.data?.highestStage?.toInt() ?: 1
        val targetStage = when {
            mode == GameMode.SCORE_RUSH -> 1
            stage != null -> stage.coerceAtLeast(1)
            else -> profileStage.coerceAtLeast(1)
        }
        _gameState.value = createInitialGameState(mode, targetStage)
        scheduleIdleHint()
    }

    fun startNextStage() {
        val nextStage = _gameState.value.stageConfig.stageNumber + 1
        startNewGame(GameMode.CLEANUP, nextStage)
    }

    fun switchMode(mode: GameMode) {
        if (_gameState.value.busy) return
        val profileStage = (profileState.value as? UiState.Success)?.data?.highestStage?.toInt() ?: 1
        val stage = if (mode == GameMode.CLEANUP) profileStage.coerceAtLeast(1) else 1
        startNewGame(mode, stage)
    }

    fun onCellTapped(pos: CellPos) {
        val state = _gameState.value
        if (state.busy || state.outcome != RunOutcome.PLAYING) return

        val currentlySelected = state.selectedCell
        when {
            currentlySelected == null -> {
                _gameState.update { it.copy(selectedCell = pos, hintCells = emptySet()) }
            }
            currentlySelected == pos -> {
                _gameState.update { it.copy(selectedCell = null) }
            }
            currentlySelected.isAdjacentTo(pos) -> {
                _gameState.update { it.copy(selectedCell = null, hintCells = emptySet()) }
                attemptSwap(currentlySelected, pos)
            }
            else -> {
                _gameState.update { it.copy(selectedCell = pos, hintCells = emptySet()) }
            }
        }
    }

    fun onSwipe(from: CellPos, dRow: Int, dCol: Int) {
        val state = _gameState.value
        if (state.busy || state.outcome != RunOutcome.PLAYING) return
        val direction = SwipeDirection.fromDelta(dRow, dCol) ?: return
        val target = state.candyBoard.targetOf(from, direction) ?: return
        _gameState.update { it.copy(selectedCell = null, hintCells = emptySet()) }
        attemptSwap(from, target)
    }

    fun requestHint() {
        val state = _gameState.value
        if (state.busy || state.outcome != RunOutcome.PLAYING) return
        val hint = state.candyBoard.findHintMove() ?: return
        _gameState.update { it.copy(hintCells = setOf(hint.first, hint.second)) }
    }

    fun manualShuffle() {
        val state = _gameState.value
        if (state.busy || state.outcome != RunOutcome.PLAYING) return
        viewModelScope.launch {
            _gameState.update { it.copy(busy = true, selectedCell = null, hintCells = emptySet()) }
            val shuffledBoard = state.candyBoard.shuffle()
            _gameState.update {
                it.copy(
                    candyBoard = shuffledBoard,
                    bannerMessage = "Fresh candy mix!",
                    busy = false,
                    hapticEventToken = nextEventId()
                )
            }
            delay(1100)
            _gameState.update { it.copy(bannerMessage = null) }
            scheduleIdleHint()
        }
    }

    private fun attemptSwap(a: CellPos, b: CellPos) {
        idleHintJob?.cancel()
        viewModelScope.launch {
            val initial = _gameState.value
            val swappedVisual = initial.candyBoard.swap(a, b)

            _gameState.update {
                it.copy(
                    candyBoard = swappedVisual,
                    spawnedTileStartRows = emptyMap(),
                    newlySpawnedSpecialIds = emptySet(),
                    busy = true,
                    hapticEventToken = nextEventId()
                )
            }
            delay(150)

            when (val swipeResult = initial.candyBoard.evaluateSwap(a, b)) {
                is SwipeAttempt.OutOfBounds -> {
                    _gameState.update { it.copy(candyBoard = initial.candyBoard, busy = false) }
                    scheduleIdleHint()
                    return@launch
                }
                is SwipeAttempt.InvalidSwap -> {
                    _gameState.update {
                        it.copy(
                            candyBoard = initial.candyBoard,
                            bouncedPair = swipeResult.from to swipeResult.to
                        )
                    }
                    delay(180)
                    _gameState.update { it.copy(bouncedPair = null, busy = false) }
                    scheduleIdleHint()
                    return@launch
                }
                is SwipeAttempt.ValidMatch -> {
                    _gameState.update {
                        it.copy(
                            movesLeft = (it.movesLeft - 1).coerceAtLeast(0),
                            movesUsed = it.movesUsed + 1,
                            currentCombo = 0
                        )
                    }

                    var activeBoard = swipeResult.swappedBoard
                    var resolution: MatchResolution? = swipeResult.resolution
                    var comboStep = 0

                    while (resolution != null && resolution.clearedCells.isNotEmpty()) {
                        comboStep++
                        val cleared = resolution.clearedCells
                        val clearStep = activeBoard.clearMatches(resolution, comboStep)

                        val bursts = clearStep.clearedTiles.map { (pos, tile) ->
                            BurstParticle(
                                id = nextEventId(),
                                row = pos.row,
                                col = pos.col,
                                type = tile.type
                            )
                        }
                        val avgRow = cleared.map { it.row }.average().toFloat()
                        val avgCol = cleared.map { it.col }.average().toFloat()
                        val calloutText = if (comboStep >= 2) {
                            "+${clearStep.stepPoints}  x$comboStep!"
                        } else {
                            "+${clearStep.stepPoints}"
                        }
                        val callout = FloatingCallout(
                            id = nextEventId(),
                            row = avgRow,
                            col = avgCol,
                            text = calloutText,
                            highlight = comboStep >= 2 || resolution.triggeredSpecialCount > 0
                        )

                        val bigHit = comboStep >= 2 ||
                            resolution.triggeredSpecialCount > 0 ||
                            clearStep.frostingClearedCount > 0
                        _gameState.update { st ->
                            st.copy(
                                candyBoard = activeBoard.withFrosting(clearStep.boardAfterClear.frosting),
                                clearingCells = cleared,
                                score = st.score + clearStep.stepPoints,
                                candiesCleared = st.candiesCleared + cleared.size,
                                currentCombo = comboStep,
                                maxCombo = maxOf(st.maxCombo, comboStep),
                                bursts = bursts,
                                callouts = (st.callouts + callout).takeLast(5),
                                bannerMessage = if (comboStep >= 2) {
                                    "x$comboStep Sweet Combo!"
                                } else {
                                    st.bannerMessage
                                },
                                bigHapticEventToken = if (bigHit) nextEventId() else st.bigHapticEventToken,
                                hapticEventToken = if (!bigHit) nextEventId() else st.hapticEventToken
                            )
                        }

                        delay(270)

                        // Apply gravity & refill with origin rows above the board for cascading drop-in
                        val refill = clearStep.boardAfterClear.applyGravityAndRefillWithOrigins()
                        activeBoard = refill.board
                        _gameState.update {
                            it.copy(
                                candyBoard = activeBoard,
                                clearingCells = emptySet(),
                                spawnedTileStartRows = refill.spawnedStartRows,
                                newlySpawnedSpecialIds = clearStep.spawnedSpecialIds
                            )
                        }
                        delay(110)
                        _gameState.update { it.copy(bursts = emptyList()) }
                        delay(240)

                        resolution = activeBoard.findMatches()
                    }

                    _gameState.update {
                        it.copy(
                            currentCombo = 0,
                            bannerMessage = null,
                            callouts = emptyList(),
                            spawnedTileStartRows = emptyMap(),
                            newlySpawnedSpecialIds = emptySet()
                        )
                    }

                    val afterTurn = _gameState.value
                    val mode = afterTurn.stageConfig.mode
                    when {
                        mode == GameMode.CLEANUP && afterTurn.frostingRemaining == 0 -> {
                            val moveBonus = afterTurn.movesLeft * 250L
                            finishRun(RunOutcome.STAGE_CLEARED, afterTurn.score + moveBonus)
                        }
                        afterTurn.movesLeft <= 0 -> {
                            val outcome = if (mode == GameMode.SCORE_RUSH) {
                                RunOutcome.RUSH_COMPLETE
                            } else {
                                RunOutcome.OUT_OF_MOVES
                            }
                            finishRun(outcome, afterTurn.score)
                        }
                        else -> {
                            if (!activeBoard.hasValidMoves()) {
                                val reshuffled = activeBoard.shuffle()
                                _gameState.update {
                                    it.copy(
                                        candyBoard = reshuffled,
                                        bannerMessage = "No moves left — reshuffling!"
                                    )
                                }
                                delay(1100)
                                _gameState.update { it.copy(bannerMessage = null, busy = false) }
                            } else {
                                _gameState.update { it.copy(busy = false) }
                            }
                            scheduleIdleHint()
                        }
                    }
                }
            }
        }
    }

    private fun finishRun(outcome: RunOutcome, finalScore: Long) {
        val currentProfile = (profileState.value as? UiState.Success)?.data
        val previousBest = currentProfile?.bestScore ?: 0L
        val isNewBest = finalScore > previousBest && finalScore > 0L

        _gameState.update {
            it.copy(
                score = finalScore,
                outcome = outcome,
                busy = false,
                newPersonalBest = isNewBest,
                saveStatus = SaveStatus.SAVING,
                bigHapticEventToken = nextEventId()
            )
        }

        val snapshot = _gameState.value
        viewModelScope.launch {
            val result = repository.recordGameResult(
                userId = currentUserId,
                currentProfile = currentProfile,
                mode = snapshot.stageConfig.mode,
                score = snapshot.score,
                stage = snapshot.stageConfig.stageNumber,
                stars = snapshot.earnedStars,
                candiesCleared = snapshot.candiesCleared,
                maxCombo = snapshot.maxCombo,
                movesUsed = snapshot.movesUsed,
                stageCleared = outcome == RunOutcome.STAGE_CLEARED
            )
            _gameState.update {
                it.copy(
                    saveStatus = if (result.isSuccess) SaveStatus.SAVED else SaveStatus.FAILED
                )
            }
        }
    }

    fun updateProfileSettings(displayName: String, hapticsEnabled: Boolean) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) {
            _profileFeedback.value = "Enter 1 to 40 characters"
            return
        }
        viewModelScope.launch {
            val res = repository.updateProfileSettings(currentUserId, trimmed, hapticsEnabled)
            _profileFeedback.value = if (res.isSuccess) {
                "Profile updated"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun toggleHaptics(enabled: Boolean) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        updateProfileSettings(profile.displayName, enabled)
    }

    fun deleteScore(scoreId: String) {
        viewModelScope.launch {
            repository.deleteScoreRecord(currentUserId, scoreId)
        }
    }

    fun clearProfileFeedback() {
        _profileFeedback.value = null
    }

    private fun scheduleIdleHint() {
        idleHintJob?.cancel()
        idleHintJob = viewModelScope.launch {
            delay(5500)
            val state = _gameState.value
            if (!state.busy && state.outcome == RunOutcome.PLAYING && state.hintCells.isEmpty()) {
                val hint = state.candyBoard.findHintMove()
                if (hint != null) {
                    _gameState.update { it.copy(hintCells = setOf(hint.first, hint.second)) }
                }
            }
        }
    }

    private fun nextEventId(): Long = eventIdCounter++

    private companion object {
        const val TAG = "CandySweepVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
