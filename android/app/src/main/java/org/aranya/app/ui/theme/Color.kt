package org.aranya.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aranya's colour tokens.
 *
 * Every colour here is deliberately authored. There is NO dynamic colour, NO wallpaper
 * derivation, and NO Material You tonal-palette generation anywhere in Aranya.
 *
 * Why: this is a tool a farmer checks before leaving the house, often in bright sun on a
 * cheap panel with weak colour reproduction. A palette sampled from someone's wallpaper is
 * unpredictable, can destroy contrast, and would make the app look different on every
 * device. Agricultural information must look the same and read the same everywhere.
 *
 * Palette rationale:
 *  - Primary is a deep, desaturated forest green. It reads as vegetation without being
 *    the bright "grass green" of stock agriculture branding.
 *  - Surface is warm ivory rather than pure white. Lower glare in sunlight, less eye
 *    strain at dawn, and it keeps long text sessions comfortable.
 *  - On-surface is a warm charcoal rather than pure black, which is easier on LCD panels.
 *  - The accent is a muted terracotta used sparingly for attention, never for decoration.
 *  - Semantic colours are named by meaning (rain, heat, risk, drought) rather than by hue,
 *    so a later palette revision cannot silently change what "warning" means.
 *
 * Contrast: every `on*` colour against its paired surface meets WCAG AA (4.5:1) for body
 * text; large numeric readouts meet AAA (7:1). Verified in ColorContrastTest.
 */

// ----------------------------------------------------------------- light palette

internal val Leaf900 = Color(0xFF12301C) // deepest green, used for strong text on light
internal val Leaf700 = Color(0xFF1E4D2B)
internal val Leaf600 = Color(0xFF2A6B3C) // primary
internal val Leaf100 = Color(0xFFD8E7D9) // primary container tint
internal val Leaf050 = Color(0xFFEDF4EC)

internal val Bark600 = Color(0xFF5A4A38) // muted earth accent, used sparingly
internal val Bark300 = Color(0xFFB08E6E)
internal val Bark100 = Color(0xFFF0E6DA)

internal val Ivory = Color(0xFFFBF9F4) // app background, light
internal val IvorySunk = Color(0xFFF2EFE7) // grouped/sunken surface, light
internal val Parchment = Color(0xFFFDFCFA) // raised content surface, light
// Not pure white: pure #FFF is harsh on a low-quality panel, and this is the
// surface most body text sits on.
internal val Charcoal = Color(0xFF23261F) // primary text, light
internal val Slate = Color(0xFF4E5349) // secondary text, light
internal val OutlineWarm = Color(0xFFBFC2B6)

// Dark palette: not an inversion. Deep ink-blue-green grounds, low-glare, and the accent
// is warmed up because the light terracotta fails contrast on dark surfaces.
internal val Night = Color(0xFF11150F) // app background, dark
internal val NightSunk = Color(0xFF0B0E0A)
internal val Slate900 = Color(0xFF1B2019) // raised content surface, dark
internal val Mist = Color(0xFFE6E9DE) // primary text, dark
internal val MistDim = Color(0xFFB4BAA9) // secondary text, dark
internal val OutlineNight = Color(0xFF3C4238)

internal val Leaf300 = Color(0xFF8FBF97) // primary, dark
internal val Leaf900Dark = Color(0xFF07240F) // on-primary container text
internal val LeafContainerDark = Color(0xFF1E3D27)
internal val LeafTintDark = Color(0xFF27382A) // subtle container tint, dark

internal val Bark300Dark = Color(0xFFD3A97F)
internal val BarkContainerDark = Color(0xFF3A2C1D)

// ------------------------------------------------------------------- semantic

// Semantic colours are intentionally NOT the Material error/warning defaults. Rain blue,
// heat amber and drought amber-brown were chosen to stay distinguishable for the most
// common forms of colour vision deficiency, and to survive a dimmed screen.
internal val RainLight = Color(0xFF0B3D6B)
internal val RainDark = Color(0xFF6FAED8)
internal val HeatLight = Color(0xFFA8541B)
internal val HeatDark = Color(0xFFCE8A45)
internal val DroughtLight = Color(0xFF6E5A21)
internal val DroughtDark = Color(0xFFC7A94F)
internal val RiskHighLight = Color(0xFF8C2F1B)
internal val RiskHighDark = Color(0xFFE08A73)
internal val PositiveLight = Color(0xFF2A6B3C)
internal val PositiveDark = Color(0xFF8FBF97)

// Chart series. Ordered so adjacent series stay separable in greyscale and for
// deuteranopia/protanopia; never rely on hue alone.
internal val ChartSeries1Light = Color(0xFF2A6B3C)
internal val ChartSeries2Light = Color(0xFFA8541B)
internal val ChartSeries3Light = Color(0xFF15527D)
internal val ChartSeries4Light = Color(0xFF6B573F)

internal val ChartSeries1Light = Color(0xFF374236)
internal val ChartSeries2Light = Color(0xFFFAA554)
internal val ChartSeries3Light = Color(0xFF4290C7)
internal val ChartSeries4Light = Color(0xFF736048)
internal val ChartSeries1Dark = Color(0xFF86A182)
internal val ChartSeries2Dark = Color(0xFFFFD26B)
internal val ChartSeries3Dark = Color(0xFF30688F)
internal val ChartSeries4Dark = Color(0xFF43382A)
