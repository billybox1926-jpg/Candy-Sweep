package com.example.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameMode
import com.example.game.MoveRules
import com.example.game.MoveUrgency
import com.example.ui.theme.BerryPlum
import com.example.ui.theme.BerryPlumDeep
import com.example.ui.theme.HoneyGold
import com.example.ui.theme.MintSpark
import com.example.ui.theme.StrawberryPop
import com.example.viewmodel.GameBoardUiState
import com.example.viewmodel.RunOutcome
import com.example.viewmodel.SaveStatus

/**
 * Tactile move counter HUD widget featuring a live radial progress gauge, rolling digit
 * countdown animation, and low-moves urgency pulse that warns as game-over approaches.
 */
@Composable
fun MoveCounterBadge(
    movesLeft: Int,
    maxMoves: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val urgency = MoveRules.urgencyFor(movesLeft)
    val isUrgent = isPlaying && (urgency == MoveUrgency.LOW || urgency == MoveUrgency.CRITICAL)
    val fraction = MoveRules.remainingFraction(movesLeft, maxMoves)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "moveGaugeSweep"
    )

    val containerColor by animateColorAsState(
        targetValue = when (urgency) {
            MoveUrgency.DEPLETED -> MaterialTheme.colorScheme.error
            MoveUrgency.CRITICAL, MoveUrgency.LOW -> StrawberryPop
            MoveUrgency.NORMAL -> BerryPlum
        },
        animationSpec = tween(durationMillis = 260),
        label = "moveCounterBg"
    )

    val ringColor by animateColorAsState(
        targetValue = when (urgency) {
            MoveUrgency.DEPLETED -> Color.White.copy(alpha = 0.35f)
            MoveUrgency.CRITICAL -> HoneyGold
            MoveUrgency.LOW -> HoneyGold
            MoveUrgency.NORMAL -> MintSpark
        },
        animationSpec = tween(durationMillis = 260),
        label = "moveRingColor"
    )

    val pulseScale = if (isUrgent) {
        val pulseTransition = rememberInfiniteTransition(label = "lowMovesPulse")
        val pulse by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (urgency == MoveUrgency.CRITICAL) 1.09f else 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (urgency == MoveUrgency.CRITICAL) 420 else 650,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "movePulseScale"
        )
        pulse
    } else {
        1f
    }

    val accessibilityText = when (urgency) {
        MoveUrgency.DEPLETED -> stringResource(R.string.cd_move_counter_empty)
        MoveUrgency.LOW, MoveUrgency.CRITICAL -> stringResource(R.string.cd_move_counter_low, movesLeft)
        MoveUrgency.NORMAL -> stringResource(R.string.cd_move_counter, movesLeft, maxMoves)
    }

    val subtitleText = when (urgency) {
        MoveUrgency.DEPLETED -> stringResource(R.string.hud_moves_empty)
        MoveUrgency.CRITICAL -> stringResource(R.string.hud_moves_last)
        else -> stringResource(R.string.hud_moves_total, maxMoves)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        shadowElevation = if (isUrgent) 5.dp else 2.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityText
            }
            .testTag("move_counter")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Radial move-gauge dial with animated countdown digit
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 3.5.dp.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)

                    drawArc(
                        color = Color.White.copy(alpha = 0.22f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    if (animatedFraction > 0.001f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedFraction,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }

                AnimatedContent(
                    targetState = movesLeft,
                    transitionSpec = {
                        if (targetState < initialState) {
                            (slideInVertically { -it / 2 } + fadeIn(tween(160))) togetherWith
                                (slideOutVertically { it / 2 } + fadeOut(tween(140)))
                        } else {
                            (slideInVertically { it / 2 } + fadeIn(tween(180))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(140)))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "moveCountRoll"
                ) { count ->
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("hud_moves_value")
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.hud_moves),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgency == MoveUrgency.CRITICAL) HoneyGold else Color.White.copy(alpha = 0.76f)
                )
            }
        }
    }
}

/**
 * Full-screen modal overlay presented when a run ends — either Game Over (out of moves)
 * or Victory (Clean-Up stage cleared / Score Rush complete).
 */
@Composable
fun RunResultOverlay(
    gameState: GameBoardUiState,
    onNextStage: () -> Unit,
    onPlayAgain: () -> Unit
) {
    val isOutOfMovesGameOver = gameState.outcome == RunOutcome.OUT_OF_MOVES
    val isStageCleared = gameState.outcome == RunOutcome.STAGE_CLEARED
    val isRushComplete = gameState.outcome == RunOutcome.RUSH_COMPLETE

    val badgeRes = when (gameState.outcome) {
        RunOutcome.OUT_OF_MOVES -> R.string.badge_game_over
        RunOutcome.STAGE_CLEARED -> R.string.badge_stage_cleared
        RunOutcome.RUSH_COMPLETE -> R.string.badge_out_of_moves
        RunOutcome.PLAYING -> R.string.badge_stage_cleared
    }

    val titleRes = when (gameState.outcome) {
        RunOutcome.STAGE_CLEARED -> R.string.result_stage_cleared
        RunOutcome.RUSH_COMPLETE -> R.string.result_rush_complete
        RunOutcome.OUT_OF_MOVES -> R.string.result_out_of_moves
        RunOutcome.PLAYING -> R.string.result_stage_cleared
    }

    val subtitleText = when (gameState.outcome) {
        RunOutcome.OUT_OF_MOVES -> stringResource(
            R.string.result_game_over_subtitle,
            gameState.frostingRemaining
        )
        RunOutcome.RUSH_COMPLETE -> stringResource(
            R.string.result_rush_subtitle,
            gameState.maxMoves
        )
        RunOutcome.STAGE_CLEARED -> stringResource(
            R.string.result_stage_cleared_subtitle,
            gameState.movesLeft
        )
        RunOutcome.PLAYING -> ""
    }

    val accentColor = when {
        isOutOfMovesGameOver -> MaterialTheme.colorScheme.error
        isStageCleared -> MintSpark
        else -> HoneyGold
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BerryPlumDeep.copy(alpha = 0.80f))
            // Consume all touch events so taps on the scrim never leak to the board below
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
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
                .testTag(if (isOutOfMovesGameOver) "game_over_dialog" else "run_result_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Eyebrow Badge (GAME OVER / STAGE CLEARED / OUT OF MOVES)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        modifier = Modifier.testTag("outcome_badge")
                    ) {
                        Text(
                            text = stringResource(badgeRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRushComplete) BerryPlumDeep else accentColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }

                    if (gameState.newPersonalBest) {
                        Surface(
                            shape = CircleShape,
                            color = HoneyGold
                        ) {
                            Text(
                                text = stringResource(R.string.result_new_best),
                                style = MaterialTheme.typography.labelMedium,
                                color = BerryPlumDeep,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Outcome Medallion Icon
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isOutOfMovesGameOver -> Icons.Rounded.HourglassBottom
                                isStageCleared -> Icons.Rounded.AutoAwesome
                                else -> Icons.Rounded.EmojiEvents
                            },
                            contentDescription = stringResource(
                                if (isOutOfMovesGameOver) R.string.cd_game_over_icon
                                else R.string.cd_victory_icon
                            ),
                            tint = accentColor,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("outcome_title")
                )

                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("outcome_subtitle")
                    )
                }

                // Earned Stars Row (shown on victory / score rush finish)
                if (!isOutOfMovesGameOver) {
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
                }

                // Final Score Display
                Text(
                    text = formatNumber(gameState.score),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isOutOfMovesGameOver) MaterialTheme.colorScheme.onSurface else StrawberryPop,
                    modifier = Modifier.testTag("outcome_score_value")
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
                        label = stringResource(R.string.stat_moves_used),
                        value = "${gameState.movesUsed}/${gameState.maxMoves}"
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

                if (isStageCleared && gameState.stageConfig.mode == GameMode.CLEANUP) {
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
                            text = stringResource(
                                if (isOutOfMovesGameOver) R.string.result_try_again
                                else R.string.result_play_again
                            ),
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
