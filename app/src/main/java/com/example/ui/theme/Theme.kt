package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeType { STEAMPUNK, MIDNIGHT, NEON, NATURE, MINIMAL }

val LocalTheme = compositionLocalOf { ThemeType.STEAMPUNK }

private val SteampunkColors = darkColorScheme(primary = Gold, secondary = DarkGold, background = Black, surface = DarkGray)
private val MidnightColors = darkColorScheme(primary = MidnightBlue, secondary = Slate, background = Color(0xFF000010), surface = Color(0xFF101030))
private val NeonColors = darkColorScheme(primary = NeonCyan, secondary = NeonPink, background = Black, surface = Color(0xFF222222))
private val NatureColors = lightColorScheme(primary = ForestGreen, secondary = EarthBrown, background = Beige, surface = Color(0xFFE0D0B0))
private val MinimalColors = lightColorScheme(primary = SoftBlack, secondary = Color.Gray, background = Color.White, surface = LightGray)

@Composable
fun MyApplicationTheme(
    themeType: ThemeType = ThemeType.STEAMPUNK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeType) {
        ThemeType.STEAMPUNK -> SteampunkColors
        ThemeType.MIDNIGHT -> MidnightColors
        ThemeType.NEON -> NeonColors
        ThemeType.NATURE -> NatureColors
        ThemeType.MINIMAL -> MinimalColors
    }

    CompositionLocalProvider(LocalTheme provides themeType) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
