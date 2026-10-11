package org.aranya.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Aranya typography.
 *
 * The platform default family (Roboto) is used deliberately. Bundling a custom font
 * would add megabytes for marginal benefit and hurt the size budget; readability in
 * Devanagari and other scripts comes from the system font's coverage, which a bundled
 * Latin-only face would actually degrade.
 *
 * Scale notes:
 *  - Line heights are set explicitly and generously. Agricultural readings are scanned,
 *    not read, often at a glance in poor light.
 *  - `numericReadout` uses tighter tracking so multi-digit values align visually.
 *  - Labels are uppercase with wide tracking ONLY for small section headers, never for
 *    sentence-length text, where wide tracking hurts readability.
 */
private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AranyaTypography = Typography(
    // Screen title. Large but not display-sized; this is a tool, not a landing page.
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = trim,
    ),

    // Major section heading inside a screen.
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = trim,
    ),

    // Sub-section heading.
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = trim,
    ),

    // Row title, list item heading.
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = trim,
    ),

    // Body. 16sp keeps text legible for older users on small, low-density screens.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = trim,
    ),

    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = trim,
    ),

    // Explanatory and provenance text.
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = trim,
    ),

    // Small all-caps section label. Restrict to 1-3 words.
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
        lineHeightStyle = trim,
    ),

    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        lineHeightStyle = trim,
    ),

    // Smallest label, e.g. a units suffix or a provenance tag.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = trim,
    ),
)

/**
 * The style for a primary numeric reading (temperature, price, rainfall).
 *
 * Large, medium weight, tight tracking. These are the values a farmer looks for, so they
 * get their own token instead of being assembled from display + body pieces.
 */
val NumericReadout = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp,
)

/** Secondary figure in a row, e.g. a comparison value or an alternative estimate. */
val NumericSecondary = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.2).sp,
)
