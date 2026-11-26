package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CandyLightColorScheme = lightColorScheme(
    primary = StrawberryPop,
    onPrimary = Color.White,
    primaryContainer = StrawberryContainer,
    onPrimaryContainer = OnStrawberryContainer,
    secondary = MintSpark,
    onSecondary = Color.White,
    secondaryContainer = MintContainer,
    onSecondaryContainer = OnMintContainer,
    tertiary = HoneyGold,
    onTertiary = BerryPlumDeep,
    tertiaryContainer = HoneyGoldContainer,
    onTertiaryContainer = OnHoneyGoldContainer,
    background = SugarCream,
    onBackground = CocoaInk,
    surface = SugarCreamCard,
    onSurface = CocoaInk,
    surfaceVariant = SugarCreamSurface,
    onSurfaceVariant = CocoaMuted,
    outline = SugarBorder,
    outlineVariant = SugarBorder
)

private val CandyDarkColorScheme = darkColorScheme(
    primary = CandyStrawberryLight,
    onPrimary = BerryPlumDeep,
    primaryContainer = Color(0xFF7D1138),
    onPrimaryContainer = StrawberryContainer,
    secondary = CandyMintMain,
    onSecondary = BerryPlumDeep,
    secondaryContainer = Color(0xFF07593F),
    onSecondaryContainer = MintContainer,
    tertiary = CandyLemonMain,
    onTertiary = BerryPlumDeep,
    tertiaryContainer = Color(0xFF6B4800),
    onTertiaryContainer = HoneyGoldContainer,
    background = NightSurface,
    onBackground = NightOnSurface,
    surface = NightContainer,
    onSurface = NightOnSurface,
    surfaceVariant = NightCard,
    onSurfaceVariant = NightMuted,
    outline = NightBorder,
    outlineVariant = NightBorder
)

private val CandyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun CandySweepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CandyDarkColorScheme else CandyLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CandyShapes,
        content = content
    )
}
