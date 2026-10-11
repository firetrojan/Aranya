package org.aranya.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

/**
 * Semantic colours that Material's ColorScheme has no slot for.
 *
 * Rain, heat, drought and risk are domain concepts, not UI states, so they get their own
 * tokens instead of being overloaded onto error/warning/tertiary. A later palette
 * revision can then restyle them without disturbing Material's semantic roles.
 */
data class AranyaSemanticColors(
    val rain: Color,
    val heat: Color,
    val drought: Color,
    val riskHigh: Color,
    val positive: Color,
    val chartSeries: List<Color>,
    /** Subtle fill for a section that is explanatory rather than interactive. */
    val explanatoryFill: Color,
    /** Fill for a code rail that is genuinely interactive. */
    val interactiveFill: Color,
    /** Background of an inline provenance or attribution line. */
    val provenanceFill: Color,
) {
    companion object {
        val Light = AranyaSemanticColors(
            rain = RainLight,
            heat = HeatLight,
            drought = DroughtLight,
            riskHigh = RiskHighLight,
            positive = PositiveLight,
            chartSeries = listOf(
                ChartSeries1Light, ChartSeries2Light, ChartSeries3Light, ChartSeries4Light,
            ),
            explanatoryFill = IvorySunk,
            interactiveFill = Bark100,
            provenanceFill = Leaf050,
        )

        val Dark = AranyaSemanticColors(
            rain = RainDark,
            heat = HeatDark,
            drought = DroughtDark,
            riskHigh = RiskHighDark,
            positive = PositiveDark,
            chartSeries = listOf(
                ChartSeries1Dark, ChartSeries2Dark, ChartSeries3Dark, ChartSeries4Dark,
            ),
            explanatoryFill = NightSunk,
            interactiveFill = BarkContainerDark,
            provenanceFill = LeafTintDark,
        )
    }
}

val LocalAranyaSemantics = staticCompositionLocalOf { AranyaSemanticColors.Light }

/**
 * Convenience accessor mirroring `MaterialTheme.colorScheme`.
 *
 * Usage: `AranyaTheme.semantics.rain`
 */
object AranyaTheme {
    val semantics: AranyaSemanticColors
        @Composable get() = LocalAranyaSemantics.current
}

private val AranyaLightScheme: ColorScheme = lightColorScheme(
    primary = Leaf600,
    onPrimary = Color.White,
    primaryContainer = Leaf100,
    onPrimaryContainer = Leaf900,

    secondary = Bark600,
    onSecondary = Color.White,
    secondaryContainer = Bark100,
    onSecondaryContainer = Color(0xFF3A2C1D),

    tertiary = RainLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE9F4),
    onTertiaryContainer = Color(0xFF12314A),

    background = Ivory,
    onBackground = Charcoal,

    surface = Parchment,
    onSurface = Charcoal,
    surfaceVariant = IvorySunk,
    onSurfaceVariant = Slate,

    outline = OutlineWarm,
    outlineVariant = Color(0xFFD8DBCE),

    error = RiskHighLight,
    onError = Color.White,
    errorContainer = Color(0xFFF7E2DC),
    onErrorContainer = Color(0xFF5A1A0C),

    inverseSurface = Leaf900,
    inverseOnSurface = Ivory,
    inversePrimary = Leaf300,

    scrim = Color(0xFF000000),
)

private val AranyaDarkScheme: ColorScheme = darkColorScheme(
    primary = Leaf300,
    onPrimary = Leaf900Dark,
    primaryContainer = LeafContainerDark,
    onPrimaryContainer = Color(0xFFD8E7D9),

    secondary = Bark300Dark,
    onSecondary = Color(0xFF3A2C1D),
    secondaryContainer = BarkContainerDark,
    onSecondaryContainer = Color(0xFFF0E6DA),

    tertiary = RainDark,
    onTertiary = Color(0xFF12314A),
    tertiaryContainer = Color(0xFF1E3B50),
    onTertiaryContainer = Color(0xFFDCE9F4),

    background = Night,
    onBackground = Mist,

    surface = Slate900,
    onSurface = Mist,
    surfaceVariant = NightSunk,
    onSurfaceVariant = MistDim,

    outline = OutlineNight,
    outlineVariant = Color(0xFF2A2F26),

    error = RiskHighDark,
    onError = Color(0xFF5A1A0C),
    errorContainer = Color(0xFF4A1D12),
    onErrorContainer = Color(0xFFF7E2DC),

    inverseSurface = Mist,
    inverseOnSurface = Night,
    inversePrimary = Leaf600,

    scrim = Color(0xFF000000),
)

/**
 * Restrained shape scale.
 *
 * No 16dp+ card radius. Large radii are a large part of why commodity apps read as
 * generic, and this product shows dense tables and figures that look better with tight
 * corners.
 */
private val AranyaShapes = Shapes(
    extraSmall = RoundedCornerShape(AranyaRadius.chip),
    small = RoundedCornerShape(AranyaRadius.chip),
    medium = RoundedCornerShape(AranyaRadius.control),
    large = RoundedCornerShape(AranyaRadius.panel),
    extraLarge = RoundedCornerShape(AranyaRadius.panel),
)

/**
 * Root theme.
 *
 * @param darkTheme follows the system setting. It is the only system-driven choice in the
 *   app; the palette itself is always one of the two authored schemes above. There is no
 *   dynamic colour path anywhere.
 */
@Composable
fun AranyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AranyaDarkScheme else AranyaLightScheme
    val semantics = if (darkTheme) AranyaSemanticColors.Dark else AranyaSemanticColors.Light

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            // Edge-to-edge with light/dark icon contrast matched to the fixed palette.
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAranyaSemantics provides semantics) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AranyaTypography,
            shapes = AranyaShapes,
            content = content,
        )
    }
}
