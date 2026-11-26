package com.example.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.CandyTile
import com.example.ui.theme.BerryPlumDeep
import com.example.ui.theme.HoneyGold
import com.example.ui.theme.StrawberryPop
import com.example.viewmodel.BurstParticle
import com.example.viewmodel.FloatingCallout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders an individual candy tile with choreographed Compose animations:
 * - Cascading drop-in from above the board with wave stagger and bouncy spring landing
 * - Smooth swap slide and invalid-move bounce wiggle
 * - Match-removal pop overshoot, twist, white flash, and fade-out vanish
 * - Special candy creation pop-in
 */
@Composable
fun AnimatedCandyTile(
    tile: CandyTile,
    row: Int,
    col: Int,
    cellPx: Float,
    cellDp: Dp,
    pulse: Float,
    spawnStartRow: Float?,
    isCascading: Boolean,
    isClearing: Boolean,
    isSelected: Boolean,
    isHinted: Boolean,
    isBounced: Boolean,
    isNewlySpawnedSpecial: Boolean
) {
    val initialRow = remember(tile.id) { spawnStartRow ?: row.toFloat() }
    val rowAnim = remember(tile.id) { Animatable(initialRow) }
    val colAnim = remember(tile.id) { Animatable(col.toFloat()) }
    val scaleAnim = remember(tile.id) { Animatable(if (isNewlySpawnedSpecial) 0.2f else 1f) }
    val alphaAnim = remember(tile.id) { Animatable(1f) }
    val rotationAnim = remember(tile.id) { Animatable(0f) }
    val flashAlphaAnim = remember(tile.id) { Animatable(0f) }

    // Cascading gravity drop or vertical swap movement
    LaunchedEffect(row) {
        val target = row.toFloat()
        if (target != rowAnim.value) {
            val isGravityFall = target > rowAnim.value && (isCascading || spawnStartRow != null)
            if (isGravityFall) {
                val staggerMs = (col * 16L).coerceAtMost(112L)
                if (staggerMs > 0L) delay(staggerMs)
                rowAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.64f,
                        stiffness = 340f
                    )
                )
            } else {
                rowAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 560f
                    )
                )
            }
        }
    }

    // Horizontal swap slide
    LaunchedEffect(col) {
        val target = col.toFloat()
        if (target != colAnim.value) {
            colAnim.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 560f
                )
            )
        }
    }

    // Match removal pop-overshoot + spin + vanish, special spawn celebration, or selection/idle scale
    LaunchedEffect(isClearing, isSelected, isNewlySpawnedSpecial) {
        when {
            isClearing -> {
                val twistDir = if ((tile.id % 2L) == 0L) 1f else -1f
                launch {
                    flashAlphaAnim.snapTo(0.85f)
                    flashAlphaAnim.animateTo(0f, tween(200))
                }
                launch {
                    scaleAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 250
                            1.28f at 80 using FastOutSlowInEasing
                            0.0f at 250 using FastOutSlowInEasing
                        }
                    )
                }
                launch {
                    alphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 240, delayMillis = 55)
                    )
                }
                launch {
                    rotationAnim.animateTo(
                        targetValue = 28f * twistDir,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                }
            }
            isNewlySpawnedSpecial -> {
                scaleAnim.snapTo(0.2f)
                flashAlphaAnim.snapTo(0.75f)
                launch {
                    scaleAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    flashAlphaAnim.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                }
            }
            else -> {
                val targetScale = if (isSelected) 1.13f else 1f
                launch {
                    scaleAnim.animateTo(
                        targetValue = targetScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                if (alphaAnim.value != 1f) {
                    launch { alphaAnim.snapTo(1f) }
                }
                if (rotationAnim.value != 0f) {
                    launch { rotationAnim.animateTo(0f, tween(120)) }
                }
            }
        }
    }

    // Subtle invalid-swap bounce shake
    LaunchedEffect(isBounced) {
        if (isBounced) {
            rotationAnim.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 180
                    -10f at 45
                    10f at 95
                    -5f at 140
                    0f at 180
                }
            )
        }
    }

    val hintScale = if (isHinted && !isClearing && !isSelected) 1f + pulse * 0.08f else 1f
    val combinedScale = scaleAnim.value * hintScale

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (colAnim.value * cellPx).roundToInt(),
                    y = (rowAnim.value * cellPx).roundToInt()
                )
            }
            .size(cellDp)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = alphaAnim.value
                rotationZ = rotationAnim.value
            }
    ) {
        CandyGlyph(tile = tile, pulse = pulse)

        val flashAlpha = flashAlphaAnim.value
        if (flashAlpha > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = flashAlpha),
                    radius = size.minDimension * 0.42f
                )
            }
        }
    }
}

/**
 * One-shot expanding shockwave and radial sugar-sparkle burst emitted when a matched candy pops.
 */
@Composable
fun AnimatedBurstEffect(
    burst: BurstParticle,
    cellPx: Float,
    modifier: Modifier = Modifier
) {
    val progress = remember(burst.id) { Animatable(0f) }

    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        )
    }

    val t = progress.value
    val fade = (1f - t).coerceIn(0f, 1f)
    if (fade <= 0.01f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = burst.col * cellPx + cellPx / 2f
        val cy = burst.row * cellPx + cellPx / 2f
        val candyColor = burst.type.mainColor()

        // Expanding shockwave ring
        drawCircle(
            color = Color.White.copy(alpha = fade * 0.85f),
            radius = cellPx * (0.18f + t * 0.52f),
            center = Offset(cx, cy),
            style = Stroke(width = cellPx * (0.09f * fade).coerceAtLeast(1.5f))
        )

        // 8 radial sugar-crystal shards flying outward
        for (i in 0 until 8) {
            val angle = (i * 45.0 + 18.0) * PI / 180.0
            val speedFactor = if (i % 2 == 0) 0.72f else 0.52f
            val dist = cellPx * (0.12f + t * speedFactor)
            val px = cx + (dist * cos(angle)).toFloat()
            val py = cy + (dist * sin(angle)).toFloat()
            val shardRadius = cellPx * (if (i % 2 == 0) 0.095f else 0.065f) * (0.4f + fade * 0.6f)
            drawCircle(
                color = (if (i % 2 == 0) candyColor else Color.White).copy(alpha = fade),
                radius = shardRadius,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Floating score / combo callout pill that pops in, drifts smoothly upward, and fades out.
 */
@Composable
fun AnimatedFloatingCallout(
    callout: FloatingCallout,
    cellPx: Float,
    boardPx: Float
) {
    val riseAnim = remember(callout.id) { Animatable(0f) }
    val popScale = remember(callout.id) { Animatable(0.55f) }

    LaunchedEffect(callout.id) {
        launch {
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            riseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 680, easing = LinearEasing)
            )
        }
    }

    val t = riseAnim.value
    val alpha = if (t < 0.65f) 1f else ((1f - t) / 0.35f).coerceIn(0f, 1f)
    val driftPx = t * cellPx * 0.95f

    Surface(
        shape = CircleShape,
        color = if (callout.highlight) HoneyGold else StrawberryPop,
        shadowElevation = 4.dp,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = ((callout.col * cellPx) - cellPx * 0.25f)
                        .roundToInt()
                        .coerceIn(0, (boardPx - cellPx * 2.2f).roundToInt().coerceAtLeast(0)),
                    y = ((callout.row * cellPx) - cellPx * 0.35f - driftPx)
                        .roundToInt()
                        .coerceAtLeast(0)
                )
            }
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
                this.alpha = alpha
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
