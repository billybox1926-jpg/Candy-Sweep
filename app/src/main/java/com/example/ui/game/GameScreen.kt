package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameMode
import com.example.game.BOARD_SIZE
import com.example.game.CellPos
import com.example.ui.theme.BerryPlum
import com.example.ui.theme.BerryPlumCard
import com.example.ui.theme.BerryPlumDeep
import com.example.ui.theme.BerryPlumSurface
import com.example.ui.theme.FrostingLightFill
import com.example.ui.theme.FrostingLightRim
import com.example.ui.theme.FrostingThickFill
import com.example.ui.theme.FrostingThickRim
import com.example.ui.theme.HoneyGold
import com.example.ui.theme.MintSpark
import com.example.ui.theme.StrawberryPop
import com.example.viewmodel.GameBoardUiState
import com.example.viewmodel.RunOutcome
import kotlin.math.abs

@Composable
fun GameScreen(
    gameState: GameBoardUiState,
    personalBest: Long,
    hapticsEnabled: Boolean,
    soundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    onCellTapped: (CellPos) -> Unit,
    onSwipe: (CellPos, Int, Int) -> Unit,
    onRequestHint: () -> Unit,
    onShuffle: () -> Unit,
    onRestart: () -> Unit,
    onNextStage: () -> Unit,
    onSwitchMode: (GameMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(gameState.hapticEventToken) {
        if (hapticsEnabled && gameState.hapticEventToken > 0L) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(gameState.bigHapticEventToken) {
        if (hapticsEnabled && gameState.bigHapticEventToken > 0L) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Switcher & Quick Sound Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_selector")
                ) {
                    SegmentedButton(
                        selected = gameState.stageConfig.mode == GameMode.CLEANUP,
                        onClick = { onSwitchMode(GameMode.CLEANUP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("mode_cleanup_button")
                    ) {
                        Text(
                            text = "${stringResource(R.string.mode_cleanup)} • ${stringResource(R.string.stage_label, gameState.stageConfig.stageNumber)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    SegmentedButton(
                        selected = gameState.stageConfig.mode == GameMode.SCORE_RUSH,
                        onClick = { onSwitchMode(GameMode.SCORE_RUSH) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("mode_rush_button")
                    ) {
                        Text(
                            text = stringResource(R.string.mode_score_rush),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { onToggleSound(!soundEnabled) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (soundEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (soundEnabled) R.string.cd_sound_on else R.string.cd_sound_off
                        ),
                        tint = if (soundEnabled) {
                            StrawberryPop
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Compact Top HUD Card
            GameHudCard(
                gameState = gameState,
                personalBest = personalBest
            )

            // Centerpiece 8x8 Interactive Candy Board
            CandyBoardView(
                gameState = gameState,
                onCellTapped = onCellTapped,
                onSwipe = onSwipe,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onRequestHint,
                    enabled = !gameState.busy && !gameState.isGameOver && !gameState.isOutOfMoves,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("hint_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_hint),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onShuffle,
                    enabled = !gameState.busy && !gameState.isGameOver && !gameState.isOutOfMoves,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_shuffle),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onRestart,
                    enabled = !gameState.busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_restart),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Result Modal Overlay when Stage Cleared or Moves Exhausted
        AnimatedVisibility(
            visible = gameState.outcome != RunOutcome.PLAYING,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f)
        ) {
            RunResultOverlay(
                gameState = gameState,
                onNextStage = onNextStage,
                onPlayAgain = onRestart
            )
        }
    }
}

@Composable
private fun GameHudCard(
    gameState: GameBoardUiState,
    personalBest: Long
) {
    val star3 = gameState.stageConfig.starThresholds.third.coerceAtLeast(1L)
    val progress = (gameState.score.toFloat() / star3.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "scoreProgress"
    )

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Metric
                Column {
                    Text(
                        text = stringResource(R.string.hud_score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatNumber(gameState.score),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("hud_score_value")
                    )
                }

                // Clean-Up Frosting Pill OR Best Score Pill
                if (gameState.stageConfig.mode == GameMode.CLEANUP) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MintSpark.copy(alpha = 0.14f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.hud_frosting),
                                style = MaterialTheme.typography.labelSmall,
                                color = MintSpark
                            )
                            Text(
                                text = "${gameState.frostingRemaining}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("hud_frosting_value")
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = HoneyGold.copy(alpha = 0.16f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.hud_best),
                                style = MaterialTheme.typography.labelSmall,
                                color = HoneyGold
                            )
                            Text(
                                text = formatNumber(maxOf(personalBest, gameState.score)),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Animated Move Counter Gauge & Low-Moves Warning Indicator
                MoveCounterBadge(
                    movesLeft = gameState.movesLeft,
                    maxMoves = gameState.maxMoves,
                    isPlaying = !gameState.isGameOver
                )
            }

            // 3-Star Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = StrawberryPop,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val earned = gameState.earnedStars
                    for (i in 1..3) {
                        Icon(
                            imageVector = if (i <= earned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (i <= earned) {
                                stringResource(R.string.cd_star_earned)
                            } else {
                                stringResource(R.string.cd_star_unearned)
                            },
                            tint = if (i <= earned) HoneyGold else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandyBoardView(
    gameState: GameBoardUiState,
    onCellTapped: (CellPos) -> Unit,
    onSwipe: (CellPos, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boardPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseValue"
    )

    val boardCd = stringResource(R.string.cd_candy_board)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(BerryPlumDeep, BerryPlum, BerryPlumSurface)
                )
            )
            .border(
                width = 3.dp,
                color = BerryPlumCard,
                shape = RoundedCornerShape(26.dp)
            )
            .padding(8.dp)
            .semantics { contentDescription = boardCd }
            .testTag("candy_board")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val density = LocalDensity.current
            val boardPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
            val cellPx = boardPx / BOARD_SIZE
            val cellDp = minOf(maxWidth, maxHeight) / BOARD_SIZE

            // 1. Background checker grid + Sticky Frosting layers + Selection/Hint highlights + Burst particles
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gameState.busy, gameState.outcome, gameState.movesLeft) {
                        if (gameState.busy || gameState.isGameOver || gameState.isOutOfMoves) return@pointerInput
                        detectTapGestures { offset ->
                            val col = (offset.x / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                            val row = (offset.y / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                            onCellTapped(CellPos(row, col))
                        }
                    }
                    .pointerInput(gameState.busy, gameState.outcome, gameState.movesLeft) {
                        if (gameState.busy || gameState.isGameOver || gameState.isOutOfMoves) return@pointerInput
                        var startCell: CellPos? = null
                        var accumX = 0f
                        var accumY = 0f
                        var swipeFired = false
                        val threshold = cellPx * 0.28f

                        detectDragGestures(
                            onDragStart = { offset ->
                                val col = (offset.x / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                                val row = (offset.y / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                                startCell = CellPos(row, col)
                                accumX = 0f
                                accumY = 0f
                                swipeFired = false
                            },
                            onDragEnd = {
                                startCell = null
                                swipeFired = false
                            },
                            onDragCancel = {
                                startCell = null
                                swipeFired = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (swipeFired) return@detectDragGestures
                                val origin = startCell ?: return@detectDragGestures
                                accumX += dragAmount.x
                                accumY += dragAmount.y
                                if (abs(accumX) >= threshold || abs(accumY) >= threshold) {
                                    swipeFired = true
                                    if (abs(accumX) > abs(accumY)) {
                                        onSwipe(origin, 0, if (accumX > 0) 1 else -1)
                                    } else {
                                        onSwipe(origin, if (accumY > 0) 1 else -1, 0)
                                    }
                                }
                            }
                        )
                    }
            ) {
                val pad = cellPx * 0.06f
                val tileSize = cellPx - pad * 2f

                for (r in 0 until BOARD_SIZE) {
                    for (c in 0 until BOARD_SIZE) {
                        val x = c * cellPx
                        val y = r * cellPx
                        val cellColor = if ((r + c) % 2 == 0) {
                            Color.White.copy(alpha = 0.06f)
                        } else {
                            Color.Black.copy(alpha = 0.14f)
                        }
                        drawRoundRect(
                            color = cellColor,
                            topLeft = Offset(x + pad, y + pad),
                            size = Size(tileSize, tileSize),
                            cornerRadius = CornerRadius(cellPx * 0.22f)
                        )

                        // Sticky frosting layer underneath candy
                        val frostLayer = gameState.frosting[r][c]
                        if (frostLayer > 0) {
                            val fillColor = if (frostLayer >= 2) FrostingThickFill else FrostingLightFill
                            val rimColor = if (frostLayer >= 2) FrostingThickRim else FrostingLightRim
                            drawRoundRect(
                                color = fillColor.copy(alpha = if (frostLayer >= 2) 0.72f else 0.45f),
                                topLeft = Offset(x + pad, y + pad),
                                size = Size(tileSize, tileSize),
                                cornerRadius = CornerRadius(cellPx * 0.24f)
                            )
                            drawRoundRect(
                                color = rimColor.copy(alpha = 0.9f),
                                topLeft = Offset(x + pad, y + pad),
                                size = Size(tileSize, tileSize),
                                cornerRadius = CornerRadius(cellPx * 0.24f),
                                style = Stroke(width = if (frostLayer >= 2) cellPx * 0.07f else cellPx * 0.045f)
                            )
                            if (frostLayer >= 2) {
                                // Subtle 2-dot caramel thickness marker in bottom corner
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = cellPx * 0.055f,
                                    center = Offset(x + cellPx * 0.80f, y + cellPx * 0.80f)
                                )
                            }
                        }

                        val pos = CellPos(r, c)
                        if (pos == gameState.selectedCell) {
                            drawRoundRect(
                                color = HoneyGold.copy(alpha = 0.25f + pulse * 0.15f),
                                topLeft = Offset(x + pad, y + pad),
                                size = Size(tileSize, tileSize),
                                cornerRadius = CornerRadius(cellPx * 0.24f)
                            )
                            drawRoundRect(
                                color = HoneyGold,
                                topLeft = Offset(x + pad, y + pad),
                                size = Size(tileSize, tileSize),
                                cornerRadius = CornerRadius(cellPx * 0.24f),
                                style = Stroke(width = cellPx * 0.07f)
                            )
                        } else if (pos in gameState.hintCells) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f + pulse * 0.55f),
                                topLeft = Offset(x + pad, y + pad),
                                size = Size(tileSize, tileSize),
                                cornerRadius = CornerRadius(cellPx * 0.24f),
                                style = Stroke(width = cellPx * 0.065f)
                            )
                        }
                    }
                }
            }

            // 2. Animated Candy Tiles keyed by stable tile.id for smooth swap, removal pop & cascading refill
            val flattened = buildList {
                for (r in 0 until BOARD_SIZE) {
                    for (c in 0 until BOARD_SIZE) {
                        val tile = gameState.board[r][c]
                        if (tile != null) {
                            add(Triple(tile, r, c))
                        }
                    }
                }
            }

            val bouncedSet = gameState.bouncedPair?.let { setOf(it.first, it.second) } ?: emptySet()
            val isCascading = gameState.spawnedTileStartRows.isNotEmpty()

            for ((tile, row, col) in flattened) {
                key(tile.id) {
                    val pos = CellPos(row, col)
                    AnimatedCandyTile(
                        tile = tile,
                        row = row,
                        col = col,
                        cellPx = cellPx,
                        cellDp = cellDp,
                        pulse = pulse,
                        spawnStartRow = gameState.spawnedTileStartRows[tile.id],
                        isCascading = isCascading,
                        isClearing = pos in gameState.clearingCells,
                        isSelected = pos == gameState.selectedCell,
                        isHinted = pos in gameState.hintCells,
                        isBounced = pos in bouncedSet,
                        isNewlySpawnedSpecial = tile.id in gameState.newlySpawnedSpecialIds
                    )
                }
            }

            // 3. Expanding Sugar-Sparkle Burst Particles on matched cells
            for (burst in gameState.bursts) {
                key(burst.id) {
                    AnimatedBurstEffect(
                        burst = burst,
                        cellPx = cellPx
                    )
                }
            }

            // 4. Floating Score & Combo Callouts
            for (callout in gameState.callouts) {
                key(callout.id) {
                    AnimatedFloatingCallout(
                        callout = callout,
                        cellPx = cellPx,
                        boardPx = boardPx
                    )
                }
            }

            // 4. Combo / Shuffle Banner Toast
            androidx.compose.animation.AnimatedVisibility(
                visible = gameState.bannerMessage != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = HoneyGold,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = gameState.bannerMessage ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = BerryPlumDeep,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

fun formatNumber(value: Long): String {
    return "%,d".format(value)
}
