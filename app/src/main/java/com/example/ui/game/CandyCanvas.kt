package com.example.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.game.CandyTile
import com.example.game.CandyType
import com.example.game.SpecialType
import com.example.ui.theme.CandyBlueberryDark
import com.example.ui.theme.CandyBlueberryLight
import com.example.ui.theme.CandyBlueberryMain
import com.example.ui.theme.CandyGrapeDark
import com.example.ui.theme.CandyGrapeLight
import com.example.ui.theme.CandyGrapeMain
import com.example.ui.theme.CandyLemonDark
import com.example.ui.theme.CandyLemonLight
import com.example.ui.theme.CandyLemonMain
import com.example.ui.theme.CandyMintDark
import com.example.ui.theme.CandyMintLight
import com.example.ui.theme.CandyMintMain
import com.example.ui.theme.CandyStrawberryDark
import com.example.ui.theme.CandyStrawberryLight
import com.example.ui.theme.CandyStrawberryMain
import com.example.ui.theme.CandyTangerineDark
import com.example.ui.theme.CandyTangerineLight
import com.example.ui.theme.CandyTangerineMain
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun CandyType.mainColor(): Color = when (this) {
    CandyType.STRAWBERRY -> CandyStrawberryMain
    CandyType.LEMON -> CandyLemonMain
    CandyType.MINT -> CandyMintMain
    CandyType.BLUEBERRY -> CandyBlueberryMain
    CandyType.GRAPE -> CandyGrapeMain
    CandyType.TANGERINE -> CandyTangerineMain
}

@Composable
fun CandyGlyph(
    tile: CandyTile,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val dim = min(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = dim * 0.39f

        // Soft drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = radius * 0.96f,
            center = Offset(cx, cy + dim * 0.06f)
        )

        // Wrapped aura behind candy
        if (tile.special == SpecialType.WRAPPED) {
            val auraScale = 1.08f + pulse * 0.08f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        tile.type.mainColor().copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = dim * 0.48f * auraScale
                ),
                topLeft = Offset(cx - dim * 0.46f, cy - dim * 0.46f),
                size = Size(dim * 0.92f, dim * 0.92f),
                cornerRadius = CornerRadius(dim * 0.24f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.7f),
                topLeft = Offset(cx - dim * 0.43f, cy - dim * 0.43f),
                size = Size(dim * 0.86f, dim * 0.86f),
                cornerRadius = CornerRadius(dim * 0.22f),
                style = Stroke(width = dim * 0.045f)
            )
        }

        if (tile.special == SpecialType.RAINBOW) {
            drawRainbowOrb(cx, cy, radius * 1.05f, pulse)
            return@Canvas
        }

        when (tile.type) {
            CandyType.STRAWBERRY -> drawStrawberrySwirl(cx, cy, radius)
            CandyType.LEMON -> drawLemonGem(cx, cy, radius)
            CandyType.MINT -> drawMintStar(cx, cy, radius)
            CandyType.BLUEBERRY -> drawBlueberryHex(cx, cy, radius)
            CandyType.GRAPE -> drawGrapeMacaron(cx, cy, radius)
            CandyType.TANGERINE -> drawTangerinePillow(cx, cy, radius)
        }

        // Striped energy bands on top of candy
        when (tile.special) {
            SpecialType.STRIPED_H -> drawStripedOverlay(cx, cy, radius, horizontal = true, pulse = pulse)
            SpecialType.STRIPED_V -> drawStripedOverlay(cx, cy, radius, horizontal = false, pulse = pulse)
            else -> Unit
        }
    }
}

private fun DrawScope.drawStrawberrySwirl(cx: Float, cy: Float, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CandyStrawberryLight, CandyStrawberryMain, CandyStrawberryDark),
            center = Offset(cx - r * 0.3f, cy - r * 0.3f),
            radius = r * 1.35f
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    // Peppermint swirl arcs
    for (angle in listOf(0f, 120f, 240f)) {
        rotate(degrees = angle, pivot = Offset(cx, cy)) {
            val path = Path().apply {
                moveTo(cx, cy)
                quadraticTo(cx + r * 0.45f, cy - r * 0.55f, cx + r * 0.78f, cy - r * 0.15f)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.75f),
                style = Stroke(width = r * 0.2f, cap = StrokeCap.Round)
            )
        }
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = r * 0.18f,
        center = Offset(cx - r * 0.32f, cy - r * 0.34f)
    )
}

private fun DrawScope.drawLemonGem(cx: Float, cy: Float, r: Float) {
    val path = Path().apply {
        moveTo(cx, cy - r * 1.06f)
        lineTo(cx + r * 0.96f, cy)
        lineTo(cx, cy + r * 1.06f)
        lineTo(cx - r * 0.96f, cy)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(CandyLemonLight, CandyLemonMain, CandyLemonDark),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        )
    )
    val inner = Path().apply {
        moveTo(cx, cy - r * 0.62f)
        lineTo(cx + r * 0.54f, cy)
        lineTo(cx, cy + r * 0.62f)
        lineTo(cx - r * 0.54f, cy)
        close()
    }
    drawPath(
        path = inner,
        color = Color.White.copy(alpha = 0.32f)
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = r * 0.08f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = r * 0.15f,
        center = Offset(cx - r * 0.16f, cy - r * 0.36f)
    )
}

private fun DrawScope.drawMintStar(cx: Float, cy: Float, r: Float) {
    val points = 5
    val outerR = r * 1.1f
    val innerR = r * 0.52f
    val path = Path()
    for (i in 0 until points * 2) {
        val angle = (i * PI / points) - PI / 2.0
        val rad = if (i % 2 == 0) outerR else innerR
        val x = cx + (rad * cos(angle)).toFloat()
        val y = cy + (rad * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(CandyMintLight, CandyMintMain, CandyMintDark),
            center = Offset(cx - r * 0.2f, cy - r * 0.25f),
            radius = outerR * 1.25f
        )
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.65f),
        style = Stroke(width = r * 0.07f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = r * 0.18f,
        center = Offset(cx, cy - r * 0.12f)
    )
}

private fun DrawScope.drawBlueberryHex(cx: Float, cy: Float, r: Float) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = (i * 60.0 - 90.0) * PI / 180.0
        val x = cx + (r * 1.02f * cos(angle)).toFloat()
        val y = cy + (r * 1.02f * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(CandyBlueberryLight, CandyBlueberryMain, CandyBlueberryDark),
            center = Offset(cx - r * 0.28f, cy - r * 0.3f),
            radius = r * 1.35f
        )
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.26f),
        radius = r * 0.56f,
        center = Offset(cx, cy)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.78f),
        topLeft = Offset(cx - r * 0.44f, cy - r * 0.55f),
        size = Size(r * 0.42f, r * 0.24f)
    )
}

private fun DrawScope.drawGrapeMacaron(cx: Float, cy: Float, r: Float) {
    // Upper shell
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(CandyGrapeLight, CandyGrapeMain, CandyGrapeDark),
            start = Offset(cx, cy - r),
            end = Offset(cx, cy + r)
        ),
        topLeft = Offset(cx - r * 1.02f, cy - r * 0.82f),
        size = Size(r * 2.04f, r * 1.64f),
        cornerRadius = CornerRadius(r * 0.78f)
    )
    // Cream filling band across center
    drawRoundRect(
        color = Color(0xFFFFF2FC),
        topLeft = Offset(cx - r * 1.04f, cy - r * 0.16f),
        size = Size(r * 2.08f, r * 0.32f),
        cornerRadius = CornerRadius(r * 0.16f)
    )
    // Top gloss highlight
    drawOval(
        color = Color.White.copy(alpha = 0.68f),
        topLeft = Offset(cx - r * 0.55f, cy - r * 0.62f),
        size = Size(r * 0.75f, r * 0.26f)
    )
}

private fun DrawScope.drawTangerinePillow(cx: Float, cy: Float, r: Float) {
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(CandyTangerineLight, CandyTangerineMain, CandyTangerineDark),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        ),
        topLeft = Offset(cx - r * 0.92f, cy - r * 0.92f),
        size = Size(r * 1.84f, r * 1.84f),
        cornerRadius = CornerRadius(r * 0.46f)
    )
    // Diagonal sugar gloss stripe
    drawLine(
        color = Color.White.copy(alpha = 0.45f),
        start = Offset(cx - r * 0.48f, cy - r * 0.58f),
        end = Offset(cx + r * 0.56f, cy + r * 0.46f),
        strokeWidth = r * 0.22f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.78f),
        radius = r * 0.16f,
        center = Offset(cx - r * 0.42f, cy - r * 0.42f)
    )
}

private fun DrawScope.drawStripedOverlay(
    cx: Float,
    cy: Float,
    r: Float,
    horizontal: Boolean,
    pulse: Float
) {
    val alpha = 0.75f + pulse * 0.22f
    val strokeW = r * 0.18f
    val offset = r * 0.34f
    if (horizontal) {
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx - r * 0.82f, cy - offset),
            end = Offset(cx + r * 0.82f, cy - offset),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx - r * 0.88f, cy),
            end = Offset(cx + r * 0.88f, cy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx - r * 0.82f, cy + offset),
            end = Offset(cx + r * 0.82f, cy + offset),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    } else {
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx - offset, cy - r * 0.82f),
            end = Offset(cx - offset, cy + r * 0.82f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx, cy - r * 0.88f),
            end = Offset(cx, cy + r * 0.88f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(cx + offset, cy - r * 0.82f),
            end = Offset(cx + offset, cy + r * 0.82f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRainbowOrb(cx: Float, cy: Float, r: Float, pulse: Float) {
    // Glowing rainbow outer halo
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                CandyStrawberryMain,
                CandyTangerineMain,
                CandyLemonMain,
                CandyMintMain,
                CandyBlueberryMain,
                CandyGrapeMain,
                CandyStrawberryMain
            ),
            center = Offset(cx, cy)
        ),
        radius = r * (1.06f + pulse * 0.08f),
        center = Offset(cx, cy)
    )
    // Dark chocolate truffle core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF6B3E2E), Color(0xFF3E1F14), Color(0xFF210E08)),
            center = Offset(cx - r * 0.2f, cy - r * 0.2f),
            radius = r
        ),
        radius = r * 0.86f,
        center = Offset(cx, cy)
    )
    // Rainbow sprinkles
    val sprinkleColors = listOf(
        CandyStrawberryMain,
        CandyLemonMain,
        CandyMintMain,
        CandyBlueberryMain,
        CandyGrapeMain,
        CandyTangerineMain
    )
    for (i in 0 until 6) {
        val angle = (i * 60.0 + pulse * 25.0) * PI / 180.0
        val sx = cx + (r * 0.52f * cos(angle)).toFloat()
        val sy = cy + (r * 0.52f * sin(angle)).toFloat()
        drawCircle(
            color = sprinkleColors[i],
            radius = r * 0.16f,
            center = Offset(sx, sy)
        )
    }
    drawCircle(
        color = Color.White,
        radius = r * 0.18f,
        center = Offset(cx, cy)
    )
}
