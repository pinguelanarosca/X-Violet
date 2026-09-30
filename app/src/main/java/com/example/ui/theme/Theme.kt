package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.steampunk.SteampunkLiveMiniVisualizer
import com.example.ui.theme.steampunk.SteampunkLiveVisualizer

/**
 * Ponto central de enumeração de temas visuais do aplicativo.
 * Cada tema possui identidade própria, paleta semântica completa e visualizadores Live exclusivos.
 */
enum class ThemeType {
    STEAMPUNK,
    MIDNIGHT,
    NEON,
    NATURE,
    MINIMAL;

    fun getTitleRes(): Int = when (this) {
        STEAMPUNK -> R.string.theme_steampunk_name
        MIDNIGHT -> R.string.theme_midnight_name
        NEON -> R.string.theme_neon_name
        NATURE -> R.string.theme_nature_name
        MINIMAL -> R.string.theme_minimal_name
    }

    fun getDescriptionRes(): Int = when (this) {
        STEAMPUNK -> R.string.theme_steampunk_desc
        MIDNIGHT -> R.string.theme_midnight_desc
        NEON -> R.string.theme_neon_desc
        NATURE -> R.string.theme_nature_desc
        MINIMAL -> R.string.theme_minimal_desc
    }

    fun getPreviewColors(): List<Color> = when (this) {
        STEAMPUNK -> listOf(SteampunkBrassPrimary, SteampunkBronzeSecondary, SteampunkCopperTertiary, SteampunkSurface)
        MIDNIGHT -> listOf(MidnightPrimary, MidnightSecondary, MidnightTertiary, MidnightSurface)
        NEON -> listOf(NeonPrimary, NeonSecondary, NeonTertiary, NeonSurface)
        NATURE -> listOf(NaturePrimary, NatureSecondary, NatureTertiary, NatureSurface)
        MINIMAL -> listOf(MinimalPrimary, MinimalSecondary, MinimalTertiary, MinimalSurface)
    }
}

/**
 * Cores temáticas para os estados em tempo real do Modo Live.
 */
data class LiveStateColors(
    val disconnected: Color,
    val listening: Color,
    val userSpeaking: Color,
    val thinking: Color,
    val modelSpeaking: Color
)

/**
 * Conjunto de características de estilo visual e comportamento do tema.
 */
data class ThemeVisualStyle(
    val cardShape: Shape,
    val buttonShape: Shape,
    val chipShape: Shape,
    val cardBorder: BorderStroke?,
    val surfaceBorder: BorderStroke?,
    val activeIndicatorColor: Color,
    val inactiveIndicatorColor: Color,
    val brassAccent: Color,
    val copperAccent: Color,
    val bronzeAccent: Color,
    val furnaceGlow: Color,
    val steamColor: Color,
    val gaugeBezel: Color,
    val gaugeFace: Color,
    val gaugeNeedle: Color,
    val rivetColor: Color,
    val hasRivets: Boolean,
    val hasMetallicSheen: Boolean,
    val liveStateColors: LiveStateColors
)

/**
 * Definição completa e centralizada de um tema visual.
 */
data class AppThemeDefinition(
    val type: ThemeType,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val visualStyle: ThemeVisualStyle,
    val liveVisualizerContent: @Composable (userAmplitude: Float, modelAmplitude: Float, isThinking: Boolean, isConnected: Boolean, modifier: Modifier) -> Unit,
    val liveMiniVisualizerContent: @Composable (userAmplitude: Float, modelAmplitude: Float, isThinking: Boolean, modifier: Modifier) -> Unit
)

// ==========================================
// COLOR SCHEMES COMPLETOS
// ==========================================

val SteampunkColorScheme = darkColorScheme(
    primary = SteampunkBrassPrimary,
    onPrimary = SteampunkBrassOnPrimary,
    primaryContainer = SteampunkBrassContainer,
    onPrimaryContainer = SteampunkBrassOnContainer,
    secondary = SteampunkBronzeSecondary,
    onSecondary = SteampunkBronzeOnSecondary,
    secondaryContainer = SteampunkBronzeContainer,
    onSecondaryContainer = SteampunkBronzeOnContainer,
    tertiary = SteampunkCopperTertiary,
    onTertiary = SteampunkCopperOnTertiary,
    tertiaryContainer = SteampunkCopperContainer,
    onTertiaryContainer = SteampunkCopperOnContainer,
    background = SteampunkBackground,
    onBackground = SteampunkOnBackground,
    surface = SteampunkSurface,
    onSurface = SteampunkOnSurface,
    surfaceVariant = SteampunkSurfaceVariant,
    onSurfaceVariant = SteampunkOnSurfaceVariant,
    surfaceContainer = SteampunkSurfaceContainer,
    surfaceContainerHigh = SteampunkSurfaceContainerHigh,
    surfaceContainerHighest = SteampunkSurfaceContainerHighest,
    outline = SteampunkOutline,
    outlineVariant = SteampunkOutlineVariant,
    error = SteampunkError,
    onError = SteampunkOnError,
    errorContainer = SteampunkErrorContainer,
    onErrorContainer = SteampunkOnErrorContainer
)

val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MidnightOnPrimary,
    primaryContainer = MidnightPrimaryContainer,
    onPrimaryContainer = MidnightOnPrimaryContainer,
    secondary = MidnightSecondary,
    onSecondary = MidnightOnSecondary,
    secondaryContainer = MidnightSecondaryContainer,
    onSecondaryContainer = MidnightOnSecondaryContainer,
    tertiary = MidnightTertiary,
    onTertiary = MidnightOnTertiary,
    tertiaryContainer = MidnightTertiaryContainer,
    onTertiaryContainer = MidnightOnTertiaryContainer,
    background = MidnightBackground,
    onBackground = MidnightOnBackground,
    surface = MidnightSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    outline = MidnightOutline,
    outlineVariant = MidnightOutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val NeonColorScheme = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = NeonOnPrimary,
    primaryContainer = NeonPrimaryContainer,
    onPrimaryContainer = NeonOnPrimaryContainer,
    secondary = NeonSecondary,
    onSecondary = NeonOnSecondary,
    secondaryContainer = NeonSecondaryContainer,
    onSecondaryContainer = NeonOnSecondaryContainer,
    tertiary = NeonTertiary,
    onTertiary = NeonOnTertiary,
    tertiaryContainer = NeonTertiaryContainer,
    onTertiaryContainer = NeonOnTertiaryContainer,
    background = NeonBackground,
    onBackground = NeonOnBackground,
    surface = NeonSurface,
    onSurface = NeonOnSurface,
    surfaceVariant = NeonSurfaceVariant,
    onSurfaceVariant = NeonOnSurfaceVariant,
    outline = NeonOutline,
    outlineVariant = NeonOutlineVariant,
    error = Color(0xFFFF5252),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5A0000),
    onErrorContainer = Color(0xFFFF8A80)
)

val NatureColorScheme = lightColorScheme(
    primary = NaturePrimary,
    onPrimary = NatureOnPrimary,
    primaryContainer = NaturePrimaryContainer,
    onPrimaryContainer = NatureOnPrimaryContainer,
    secondary = NatureSecondary,
    onSecondary = NatureOnSecondary,
    secondaryContainer = NatureSecondaryContainer,
    onSecondaryContainer = NatureOnSecondaryContainer,
    tertiary = NatureTertiary,
    onTertiary = NatureOnTertiary,
    tertiaryContainer = NatureTertiaryContainer,
    onTertiaryContainer = NatureOnTertiaryContainer,
    background = NatureBackground,
    onBackground = NatureOnBackground,
    surface = NatureSurface,
    onSurface = NatureOnSurface,
    surfaceVariant = NatureSurfaceVariant,
    onSurfaceVariant = NatureOnSurfaceVariant,
    outline = NatureOutline,
    outlineVariant = NatureOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val MinimalColorScheme = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = MinimalOnPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalSecondary,
    onSecondary = MinimalOnSecondary,
    secondaryContainer = MinimalSecondaryContainer,
    onSecondaryContainer = MinimalOnSecondaryContainer,
    tertiary = MinimalTertiary,
    onTertiary = MinimalOnTertiary,
    tertiaryContainer = MinimalTertiaryContainer,
    onTertiaryContainer = MinimalOnTertiaryContainer,
    background = MinimalBackground,
    onBackground = MinimalOnBackground,
    surface = MinimalSurface,
    onSurface = MinimalOnSurface,
    surfaceVariant = MinimalSurfaceVariant,
    onSurfaceVariant = MinimalOnSurfaceVariant,
    outline = MinimalOutline,
    outlineVariant = MinimalOutlineVariant,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// ==========================================
// THEME RESOLUTION ENGINE
// ==========================================

fun resolveThemeDefinition(themeType: ThemeType): AppThemeDefinition {
    return when (themeType) {
        ThemeType.STEAMPUNK -> AppThemeDefinition(
            type = ThemeType.STEAMPUNK,
            colorScheme = SteampunkColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(10.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(22.dp)
            ),
            visualStyle = ThemeVisualStyle(
                cardShape = RoundedCornerShape(10.dp),
                buttonShape = RoundedCornerShape(8.dp),
                chipShape = RoundedCornerShape(8.dp),
                cardBorder = BorderStroke(1.dp, SteampunkOutlineVariant),
                surfaceBorder = BorderStroke(1.2.dp, SteampunkOutline),
                activeIndicatorColor = SteampunkGreenPatina,
                inactiveIndicatorColor = SteampunkOutlineVariant,
                brassAccent = SteampunkBrassPrimary,
                copperAccent = SteampunkCopperTertiary,
                bronzeAccent = SteampunkBronzeSecondary,
                furnaceGlow = SteampunkFurnaceAmber,
                steamColor = SteampunkSteamWhite,
                gaugeBezel = SteampunkBrassPrimary,
                gaugeFace = SteampunkGaugeDial,
                gaugeNeedle = SteampunkGaugeNeedle,
                rivetColor = SteampunkRivetColor,
                hasRivets = true,
                hasMetallicSheen = true,
                liveStateColors = LiveStateColors(
                    disconnected = SteampunkOutline,
                    listening = SteampunkGreenPatina,
                    userSpeaking = SteampunkFurnaceAmber,
                    thinking = SteampunkBronzeSecondary,
                    modelSpeaking = SteampunkCopperTertiary
                )
            ),
            liveVisualizerContent = { userAmp, modelAmp, thinking, connected, mod ->
                SteampunkLiveVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    isConnected = connected,
                    modifier = mod
                )
            },
            liveMiniVisualizerContent = { userAmp, modelAmp, thinking, mod ->
                SteampunkLiveMiniVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    modifier = mod
                )
            }
        )

        ThemeType.MIDNIGHT -> AppThemeDefinition(
            type = ThemeType.MIDNIGHT,
            colorScheme = MidnightColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            visualStyle = ThemeVisualStyle(
                cardShape = RoundedCornerShape(14.dp),
                buttonShape = RoundedCornerShape(12.dp),
                chipShape = RoundedCornerShape(10.dp),
                cardBorder = BorderStroke(1.dp, MidnightOutlineVariant),
                surfaceBorder = BorderStroke(1.dp, MidnightOutline),
                activeIndicatorColor = MidnightPrimary,
                inactiveIndicatorColor = MidnightOutlineVariant,
                brassAccent = MidnightTertiary,
                copperAccent = MidnightSecondary,
                bronzeAccent = MidnightPrimary,
                furnaceGlow = MidnightTertiary,
                steamColor = Color(0xFFC7DEFA),
                gaugeBezel = MidnightPrimary,
                gaugeFace = MidnightSurfaceVariant,
                gaugeNeedle = MidnightTertiary,
                rivetColor = MidnightOutline,
                hasRivets = false,
                hasMetallicSheen = false,
                liveStateColors = LiveStateColors(
                    disconnected = Color.Gray,
                    listening = MidnightPrimary,
                    userSpeaking = Color(0xFF00E5FF),
                    thinking = MidnightTertiary,
                    modelSpeaking = Color(0xFF82B1FF)
                )
            ),
            liveVisualizerContent = { userAmp, modelAmp, thinking, connected, mod ->
                MidnightLiveVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    isConnected = connected,
                    modifier = mod
                )
            },
            liveMiniVisualizerContent = { userAmp, modelAmp, thinking, mod ->
                MidnightLiveMiniVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    modifier = mod
                )
            }
        )

        ThemeType.NEON -> AppThemeDefinition(
            type = ThemeType.NEON,
            colorScheme = NeonColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(4.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(14.dp),
                extraLarge = RoundedCornerShape(20.dp)
            ),
            visualStyle = ThemeVisualStyle(
                cardShape = RoundedCornerShape(8.dp),
                buttonShape = RoundedCornerShape(6.dp),
                chipShape = RoundedCornerShape(4.dp),
                cardBorder = BorderStroke(1.2.dp, NeonOutline),
                surfaceBorder = BorderStroke(1.5.dp, NeonPrimary),
                activeIndicatorColor = NeonPrimary,
                inactiveIndicatorColor = NeonOutlineVariant,
                brassAccent = NeonTertiary,
                copperAccent = NeonSecondary,
                bronzeAccent = NeonPrimary,
                furnaceGlow = NeonSecondary,
                steamColor = NeonPrimary,
                gaugeBezel = NeonPrimary,
                gaugeFace = NeonSurfaceVariant,
                gaugeNeedle = NeonSecondary,
                rivetColor = NeonOutline,
                hasRivets = false,
                hasMetallicSheen = true,
                liveStateColors = LiveStateColors(
                    disconnected = Color.DarkGray,
                    listening = NeonPrimary,
                    userSpeaking = NeonTertiary,
                    thinking = NeonSecondary,
                    modelSpeaking = NeonPrimary
                )
            ),
            liveVisualizerContent = { userAmp, modelAmp, thinking, connected, mod ->
                NeonLiveVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    isConnected = connected,
                    modifier = mod
                )
            },
            liveMiniVisualizerContent = { userAmp, modelAmp, thinking, mod ->
                NeonLiveMiniVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    modifier = mod
                )
            }
        )

        ThemeType.NATURE -> AppThemeDefinition(
            type = ThemeType.NATURE,
            colorScheme = NatureColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp)
            ),
            visualStyle = ThemeVisualStyle(
                cardShape = RoundedCornerShape(16.dp),
                buttonShape = RoundedCornerShape(16.dp),
                chipShape = RoundedCornerShape(12.dp),
                cardBorder = BorderStroke(1.dp, NatureOutlineVariant),
                surfaceBorder = BorderStroke(1.dp, NatureOutlineVariant),
                activeIndicatorColor = NaturePrimary,
                inactiveIndicatorColor = NatureOutlineVariant,
                brassAccent = NatureTertiary,
                copperAccent = NatureSecondary,
                bronzeAccent = NaturePrimary,
                furnaceGlow = NatureSecondary,
                steamColor = Color(0xFFF0ECE1),
                gaugeBezel = NaturePrimary,
                gaugeFace = NatureSurface,
                gaugeNeedle = NatureSecondary,
                rivetColor = NatureOutline,
                hasRivets = false,
                hasMetallicSheen = false,
                liveStateColors = LiveStateColors(
                    disconnected = Color.Gray,
                    listening = NaturePrimary,
                    userSpeaking = NatureTertiary,
                    thinking = NatureSecondary,
                    modelSpeaking = NaturePrimary
                )
            ),
            liveVisualizerContent = { userAmp, modelAmp, thinking, connected, mod ->
                NatureLiveVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    isConnected = connected,
                    modifier = mod
                )
            },
            liveMiniVisualizerContent = { userAmp, modelAmp, thinking, mod ->
                NatureLiveMiniVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    modifier = mod
                )
            }
        )

        ThemeType.MINIMAL -> AppThemeDefinition(
            type = ThemeType.MINIMAL,
            colorScheme = MinimalColorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(4.dp),
                medium = RoundedCornerShape(6.dp),
                large = RoundedCornerShape(10.dp),
                extraLarge = RoundedCornerShape(16.dp)
            ),
            visualStyle = ThemeVisualStyle(
                cardShape = RoundedCornerShape(6.dp),
                buttonShape = RoundedCornerShape(4.dp),
                chipShape = RoundedCornerShape(4.dp),
                cardBorder = BorderStroke(1.dp, MinimalOutline),
                surfaceBorder = BorderStroke(1.dp, MinimalOutline),
                activeIndicatorColor = MinimalPrimary,
                inactiveIndicatorColor = MinimalOutlineVariant,
                brassAccent = MinimalSecondary,
                copperAccent = MinimalTertiary,
                bronzeAccent = MinimalPrimary,
                furnaceGlow = MinimalSecondary,
                steamColor = Color.LightGray,
                gaugeBezel = MinimalPrimary,
                gaugeFace = MinimalSurface,
                gaugeNeedle = MinimalPrimary,
                rivetColor = MinimalOutline,
                hasRivets = false,
                hasMetallicSheen = false,
                liveStateColors = LiveStateColors(
                    disconnected = Color.Gray,
                    listening = MinimalPrimary,
                    userSpeaking = MinimalSecondary,
                    thinking = MinimalTertiary,
                    modelSpeaking = MinimalPrimary
                )
            ),
            liveVisualizerContent = { userAmp, modelAmp, thinking, connected, mod ->
                MinimalLiveVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    isConnected = connected,
                    modifier = mod
                )
            },
            liveMiniVisualizerContent = { userAmp, modelAmp, thinking, mod ->
                MinimalLiveMiniVisualizer(
                    userAmplitude = userAmp,
                    modelAmplitude = modelAmp,
                    isThinking = thinking,
                    modifier = mod
                )
            }
        )
    }
}

// Composition Locals
val LocalTheme = compositionLocalOf { ThemeType.STEAMPUNK }
val LocalAppTheme = staticCompositionLocalOf { resolveThemeDefinition(ThemeType.STEAMPUNK) }

@Composable
fun MyApplicationTheme(
    themeType: ThemeType = ThemeType.STEAMPUNK,
    content: @Composable () -> Unit,
) {
    val themeDef = resolveThemeDefinition(themeType)

    CompositionLocalProvider(
        LocalTheme provides themeType,
        LocalAppTheme provides themeDef
    ) {
        MaterialTheme(
            colorScheme = themeDef.colorScheme,
            typography = themeDef.typography,
            shapes = themeDef.shapes,
            content = content
        )
    }
}
