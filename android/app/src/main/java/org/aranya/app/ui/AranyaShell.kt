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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.aranya.app.data.weather.WeatherLocation
import org.aranya.app.ui.weather.WeatherScreen
import org.aranya.app.ui.weather.WeatherUiState

/**
 * Stage 02 application shell.
 *
 * Two destinations now exist: Weather, which works, and Sources, which reports the real
 * registry. Navigation is a single flat switch rather than a navigation library: with two
 * destinations a back stack, routes and arguments would be scaffolding for a structure
 * that does not exist yet. A predictable flat list is also easier for a first-time user
 * than a nested menu.
 *
 * The shell no longer claims to be stage-only. Weather fetches real data, and the sources
 * screen reports the registry truthfully.
 */
@Composable
fun AranyaShell(
    registry: SourceRegistry,
    weatherState: WeatherUiState,
    onSelectLocation: (WeatherLocation) -> Unit,
    onSubmitTypedLocation: (String) -> Unit,
    onRetryWeather: () -> Unit,
    modifier: Modifier = Modifier,
    initialDestination: Destination = Destination.WEATHER,
) {
    var destination by rememberSaveable { mutableStateOf(initialDestination) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Two real destinations, so navigation is now warranted. Flat and predictable:
            // every function is one tap away with no nesting.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AranyaSpacing.lg,
                    end = AranyaSpacing.lg,
                    top = AranyaSpacing.md,
                    bottom = AranyaSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(AranyaSpacing.lg),
        ) {
            when (destination) {
                Destination.WEATHER -> WeatherScreen(
                    state = weatherState,
                    onSelectLocation = onSelectLocation,
                    onSubmitTyped = onSubmitTypedLocation,
                    onRetry = onRetryWeather,
                    modifier = Modifier.fillMaxWidth(),
                )

                Destination.SOURCES -> {
                    Masthead()
                    Divider()
                    SourceStatusScreen(
                        registry = registry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The two destinations available in Stage 02.
 *
 * Icons come from `material-icons-core` only. The extended artifact costs several MB,
 * which conflicts with the size budget, so the set is deliberately limited to core icons
 * and checked by `data-pipeline/tests/verify_icons.py`.
 */
enum class Destination(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    WEATHER(R.string.weather_title, androidx.compose.material.icons.Icons.Filled.DateRange),
    SOURCES(R.string.sources_title, androidx.compose.material.icons.Icons.Filled.List),
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
