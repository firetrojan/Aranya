package org.aranya.app.ui.theme

/**
 * Aranya spacing and sizing tokens.
 *
 * A 4dp base grid. Small screens are the design target, so the scale is intentionally
 * tighter than Material's default and the radius range stays small: rounded-card
 * dashboards read as generic, and a restrained 4-8dp radius suits dense tabular data.
 * Touch targets never go below 48dp regardless of visual size.
 */
object AranyaSpacing {
    /** Hairline-ish gap for icon to label. */
    val xxs: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(2f)

    /** 4dp - between an icon and its own label. */
    val xs: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(4f)

    /** 8dp - default gap inside a row of related items. */
    val sm: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(8f)

    /** 12dp - gap between items in a list. */
    val md: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(12f)

    /** 16dp - screen horizontal padding on small phones. */
    val lg: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(16f)

    /** 24dp - gap between distinct sections. */
    val xl: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(24f)

    /** 32dp - generous separation before a new topic. */
    val xxl: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(32f)

    /** 40dp - top breathing room above a screen title. */
    val sectionTop: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(40f)
}

object AranyaRadius {
    val chip: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(4f)
    val control: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(8f)
    val panel: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(8f)

    /** Pill shape, used only for badges and filters, not for whole cards. */
    val badge: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(999f)
}

object AranyaSize {
    /** Minimum interactive height. Material and platform guidance both require 48dp. */
    val minTouchTarget: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(48f)

    /** Standard icon size inside a list row. */
    val iconSm: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(18f)
    val iconMd: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(24f)
    val iconLg: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(32f)

    /** Rule under a section heading. Full width, 1dp, low contrast. */
    val hairline: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(1f)

    /** Width of the state/qualifier rail beside a value. */
    val qualifierRailWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(3f)

    /** Maximum readable measure for a paragraph on small screens. */
    val maxParagraphWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(560f)
}
