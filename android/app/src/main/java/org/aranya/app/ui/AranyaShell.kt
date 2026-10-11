package org.aranya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.aranya.app.R
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.ui.format.AranyaFormat
import org.aranya.app.ui.source.SourceStatusScreen
import org.aranya.app.ui.theme.AranyaSemanticColors
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaSize
import org.aranya.app.ui.theme.AranyaTheme

/**
 * Stage 01 application shell.
 *
 * This screen deliberately does NOT present agricultural data. Stage 01 built the
 * foundation, the design system and the source registry; no weather, soil, market or
 * policy feature works yet. The shell says so plainly rather than showing sample figures
 * that could be mistaken for real readings.
 *
 * It exists to validate the design system on a real device: type scale, fixed palette,
 * spacing rhythm, small-screen layout.
 */
@Composable
fun AranyaShell(
    registry: SourceRegistry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // No bottom bar and no FAB yet: with one real destination, navigation chrome would
        // be decoration. Added in Stage 02 when there are actual destinations.
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AranyaSpacing.lg,
                    end = AranyaSpacing.lg,
                    top = AranyaSpacing.xl,
                    bottom = AranyaSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(AranyaSpacing.lg),
        ) {
            Masthead()

            // Honest stage notice, not a fake dashboard.
            StageNotice()

            PaletteNote()

            Divider()

            Text(
                text = stringResource(R.string.shell_theme_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.shell_theme_system),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Divider()

            // The one genuinely useful Stage 01 screen: what Aranya has actually checked.
            SourceStatusScreen(
                registry = registry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Masthead() {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xs)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The stage notice.
 *
 * Uses an explanatory fill rather than a warning colour: being early-stage is not an
 * error condition, and alarming the user about the software's own maturity would be
 * misleading. Plain, legible, unfriendly-free.
 */
@Composable
private fun StageNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AranyaTheme.semantics.explanatoryFill, MaterialTheme.shapes.medium)
            .padding(AranyaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.shell_stage_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.shell_stage_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(AranyaSize.maxParagraphWidth),
        )
    }
}

@Composable
private fun PaletteNote() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        // Small swatch strip: shows the palette is fixed rather than wallpaper-derived.
        Row(horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.xxs)) {
            Swatch(AranyaTheme.semantics.positive)
            Swatch(AranyaTheme.semantics.rain)
            Swatch(AranyaTheme.semantics.heat)
            Swatch(AranyaTheme.semantics.drought)
        }
        Text(
            text = stringResource(R.string.shell_palette_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(AranyaSize.maxParagraphWidth),
        )
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Spacer(
        Modifier
            .width(8.dp)
            .height(28.dp)
            .background(color, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(AranyaSize.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Preview(name = "Shell light", showBackground = true, heightDp = 900)
@Composable
private fun ShellPreviewLight() {
    AranyaTheme(darkTheme = false) {
        AranyaShell(registry = SourceRegistry(emptyList()))
    }
}

@Preview(name = "Shell dark", showBackground = true, heightDp = 900)
@Composable
private fun ShellPreviewDark() {
    AranyaTheme(darkTheme = true) {
        AranyaShell(registry = SourceRegistry(emptyList()))
    }
}
