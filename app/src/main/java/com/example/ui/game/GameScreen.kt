package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
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
import com.example.viewmodel.SaveStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameScreen(
    gameState: GameBoardUiState,
    personalBest: Long,
    hapticsEnabled: Boolean,
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
            // Mode Switcher Row
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
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
                    enabled = !gameState.busy && gameState.outcome == RunOutcome.PLAYING,
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
                    enabled = !gameState.busy && gameState.outcome == RunOutcome.PLAYING,
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
    val lowMoves = gameState.movesLeft <= 5 && gameState.outcome == RunOutcome.PLAYING
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

                // Moves Remaining Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (lowMoves) StrawberryPop else BerryPlum
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.hud_moves),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "${gameState.movesLeft}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("hud_moves_value")
                        )
                    }
                }
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val boardPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
            val cellPx = boardPx / BOARD_SIZE
            val cellDp = minOf(maxWidth, maxHeight) / BOARD_SIZE

            // 1. Background checker grid + Sticky Frosting layers + Selection/Hint highlights + Burst particles
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gameState.busy, gameState.outcome) {
                        if (gameState.busy || gameState.outcome != RunOutcome.PLAYING) return@pointerInput
                        detectTapGestures { offset ->
                            val col = (offset.x / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                            val row = (offset.y / cellPx).toInt().coerceIn(0, BOARD_SIZE - 1)
                            onCellTapped(CellPos(row, col))
                        }
                    }
                    .pointerInput(gameState.busy, gameState.outcome) {
                        if (gameState.busy || gameState.outcome != RunOutcome.PLAYING) return@pointerInput
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

                // Burst particle flashes on clearing cells
                for (burst in gameState.bursts) {
                    val cx = burst.col * cellPx + cellPx / 2f
                    val cy = burst.row * cellPx + cellPx / 2f
                    val cColor = burst.type.mainColor()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = cellPx * (0.28f + pulse * 0.22f),
                        center = Offset(cx, cy),
                        style = Stroke(width = cellPx * 0.06f)
                    )
                    for (i in 0 until 6) {
                        val angle = (i * 60.0) * PI / 180.0
                        val dist = cellPx * (0.34f + pulse * 0.14f)
                        val px = cx + (dist * cos(angle)).toFloat()
                        val py = cy + (dist * sin(angle)).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) cColor else Color.White,
                            radius = cellPx * 0.08f,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // 2. Animated Candy Tiles keyed by stable tile.id for smooth swap & gravity motion
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

            for ((tile, row, col) in flattened) {
                key(tile.id) {
                    val animatedRow by animateFloatAsState(
                        targetValue = row.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tileRow"
                    )
                    val animatedCol by animateFloatAsState(
                        targetValue = col.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tileCol"
                    )

                    val pos = CellPos(row, col)
                    val isClearing = pos in gameState.clearingCells
                    val isSelected = pos == gameState.selectedCell
                    val isHinted = pos in gameState.hintCells

                    val targetScale = when {
                        isClearing -> 0.15f
                        isSelected -> 1.12f
                        isHinted -> 1.0f + pulse * 0.08f
                        else -> 1f
                    }
                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = tween(durationMillis = if (isClearing) 180 else 140),
                        label = "tileScale"
                    )

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (animatedCol * cellPx).roundToInt(),
                                    y = (animatedRow * cellPx).roundToInt()
                                )
                            }
                            .size(cellDp)
                            .scale(animatedScale)
                    ) {
                        CandyGlyph(tile = tile, pulse = pulse)
                    }
                }
            }

            // 3. Floating Score & Combo Callouts
            for (callout in gameState.callouts) {
                key(callout.id) {
                    Surface(
                        shape = CircleShape,
                        color = if (callout.highlight) HoneyGold else StrawberryPop,
                        shadowElevation = 4.dp,
                        modifier = Modifier.offset {
                            IntOffset(
                                x = ((callout.col * cellPx) - cellPx * 0.25f)
                                    .roundToInt()
                                    .coerceIn(0, (boardPx - cellPx * 2.2f).roundToInt().coerceAtLeast(0)),
                                y = ((callout.row * cellPx) - cellPx * 0.35f)
                                    .roundToInt()
                                    .coerceAtLeast(0)
                            )
                        }
                    ) {
                        Text(
                            text = callout.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (callout.highlight) BerryPlumDeep else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
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

@Composable
private fun RunResultOverlay(
    gameState: GameBoardUiState,
    onNextStage: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val isVictory = gameState.outcome == RunOutcome.STAGE_CLEARED ||
        gameState.outcome == RunOutcome.RUSH_COMPLETE
    val titleRes = when (gameState.outcome) {
        RunOutcome.STAGE_CLEARED -> R.string.result_stage_cleared
        RunOutcome.RUSH_COMPLETE -> R.string.result_rush_complete
        RunOutcome.OUT_OF_MOVES -> R.string.result_out_of_moves
        RunOutcome.PLAYING -> R.string.result_stage_cleared
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BerryPlumDeep.copy(alpha = 0.78f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .testTag("run_result_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (gameState.newPersonalBest) {
                    Surface(
                        shape = CircleShape,
                        color = HoneyGold
                    ) {
                        Text(
                            text = stringResource(R.string.result_new_best),
                            style = MaterialTheme.typography.labelMedium,
                            color = BerryPlumDeep,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Earned Stars Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val stars = gameState.earnedStars
                    for (i in 1..3) {
                        Icon(
                            imageVector = if (i <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (i <= stars) {
                                stringResource(R.string.cd_star_earned)
                            } else {
                                stringResource(R.string.cd_star_unearned)
                            },
                            tint = if (i <= stars) HoneyGold else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Final Score Display
                Text(
                    text = formatNumber(gameState.score),
                    style = MaterialTheme.typography.displayLarge,
                    color = StrawberryPop
                )

                // Compact Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ResultMiniStat(
                        label = stringResource(R.string.stat_candies_swept),
                        value = "${gameState.candiesCleared}"
                    )
                    ResultMiniStat(
                        label = stringResource(R.string.stat_best_combo),
                        value = "x${gameState.maxCombo}"
                    )
                    ResultMiniStat(
                        label = stringResource(R.string.hud_moves),
                        value = "${gameState.movesUsed}"
                    )
                }

                // Cloud Sync Status Pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (gameState.saveStatus) {
                            SaveStatus.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = stringResource(R.string.result_saving_score),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            SaveStatus.SAVED -> {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = MintSpark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.result_score_saved),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MintSpark
                                )
                            }
                            SaveStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.result_save_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            SaveStatus.IDLE -> Unit
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (isVictory && gameState.stageConfig.mode == GameMode.CLEANUP) {
                    Button(
                        onClick = onNextStage,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrawberryPop,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("next_stage_button")
                    ) {
                        Text(
                            text = stringResource(R.string.result_next_stage),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onPlayAgain,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("play_again_button")
                    ) {
                        Text(
                            text = stringResource(R.string.result_play_again),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Button(
                        onClick = onPlayAgain,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrawberryPop,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("play_again_button")
                    ) {
                        Text(
                            text = stringResource(R.string.result_play_again),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatNumber(value: Long): String {
    return "%,d".format(value)
}
