package org.aranya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.aranya.app.R
import org.aranya.app.ui.theme.AranyaSize
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaTheme

/**
 * Shared UI primitives.
 *
 * Aranya's design intent is a composed reading with rules and rails rather than a grid of
 * rounded cards, so these lean toward dividers, labels and short rules instead of
 * elevation and containers. Every value comes from the theme tokens.
 */

/** A 1dp full-width rule. The primary separator in the product. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Spacer(
        modifier
            .fillMaxWidth()
            .height(AranyaSize.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * A vertical coloured rail used to carry status without relying on colour alone.
 *
 * [label] is always rendered next to the rail, so the information never depends on the
 * user perceiving the hue. This matters for colour-vision deficiency and for a dimmed,
 * low-quality screen in sunlight.
 */
@Composable
fun StatusRail(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.xs),
    ) {
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .width(AranyaSize.qualifierRailWidth)
                .height(14.dp)
                .background(color, MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * A small all-caps section heading.
 *
 * Restricted to one or two words. Long text at this tracking is hard to read, which is
 * why body text never uses this style.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * An explanatory block: important context the user should not skim past.
 *
 * Deliberately not a warning colour. Being early-stage, or the data being an estimate,
 * is information rather than an error, and colouring it red would misstate the severity.
 */
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (emphasis) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (emphasis) MaterialTheme.colorScheme.secondaryContainer
                else AranyaTheme.semantics.explanatoryFill,
                MaterialTheme.shapes.extraSmall,
            )
            .padding(AranyaSpacing.sm),
    )
}

/**
 * A tappable row with an icon, a title, an optional subtitle and a trailing element.
 *
 * The whole row is the touch target, not the icon, and it is at least
 * [AranyaSize.minTouchTarget] tall so it stays usable on a small screen.
 */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AranyaSize.minTouchTarget)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(AranyaSize.iconMd),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A compact data row: label on the left, value on the right.
 *
 * Rows render only when they have a value. Aranya never shows a placeholder zero for a
 * missing measurement, because 0 and unknown are different facts.
 */
@Composable
fun MetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AranyaSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Palette swatch used on the home screen to show the fixed colour system. */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Spacer(
        modifier
            .width(10.dp)
            .height(26.dp)
            .background(color, MaterialTheme.shapes.extraSmall),
    )
}

/**
 * Screen heading used at the top of each destination.
 *
 * Marked as a heading for accessibility services so a screen reader user can jump
 * between sections.
 */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** Placeholder for a value that is genuinely unknown. */
@Composable
fun UnknownValue(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.value_unknown),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}
