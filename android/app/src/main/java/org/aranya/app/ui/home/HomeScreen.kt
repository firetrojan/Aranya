package org.aranya.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.aranya.app.R
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.ui.components.ActionRow
import org.aranya.app.ui.components.Note
import org.aranya.app.ui.components.Rule
import org.aranya.app.ui.components.ScreenTitle
import org.aranya.app.ui.components.SectionLabel
import org.aranya.app.ui.components.Swatch
import org.aranya.app.ui.format.AranyaFormat
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaTheme
import org.aranya.app.ui.weather.WeatherUiState

/**
 * Home screen.
 *
 * Shows what the user last looked at, with a route into each destination and a working
 * refresh. Every row here performs a real action: the two destinations navigate, and the
 * refresh re-requests weather. There are no placeholder entries for features that do not
 * exist yet.
 */
@Composable
fun HomeScreen(
    registry: SourceRegistry,
    weatherState: WeatherUiState,
    onOpenWeather: () -> Unit,
    onOpenSources: () -> Unit,
    onRefreshWeather: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AranyaSpacing.lg,
                end = AranyaSpacing.lg,
                top = AranyaSpacing.md,
                bottom = AranyaSpacing.xxl,
            ),
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.lg),
    ) {
        ScreenTitle(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.app_tagline),
        )

        WeatherSummary(
            state = weatherState,
            onOpen = onOpenWeather,
            onRefresh = onRefreshWeather,
        )

        SectionLabel(stringResource(R.string.home_sections))
        Rule()

        ActionRow(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.weather_title),
            subtitle = stringResource(R.string.home_weather_subtitle),
            onClick = onOpenWeather,
        )

        Rule()

        ActionRow(
            icon = Icons.Filled.List,
            title = stringResource(R.string.sources_title),
            subtitle = stringResource(R.string.home_sources_subtitle),
            trailingText = context.getString(
                R.string.sources_count,
                registry.usableCount,
                registry.sources.size,
            ),
            onClick = onOpenSources,
        )

        Rule()
        Spacer(Modifier.padding(top = AranyaSpacing.xs))

        SectionLabel(stringResource(R.string.home_palette_label))
        PaletteStrip()

        Note(stringResource(R.string.shell_palette_note))
    }
}

/**
 * A compact summary of the current weather.
 *
 * Deliberately shows what the user last saw, including the age, so the home screen is
 * informative on its own and the user knows whether to trust it before opening the full
 * screen. When nothing has been loaded it says so instead of inviting a tap on nothing.
 */
@Composable
private fun WeatherSummary(
    state: WeatherUiState,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current

    val snapshot = when (state) {
        is WeatherUiState.Ready -> state.snapshot
        is WeatherUiState.Refreshing -> state.snapshot
        is WeatherUiState.StaleWithError -> state.snapshot
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.sm)) {
        SectionLabel(stringResource(R.string.home_current_weather))

        if (snapshot == null) {
            Text(
                text = stringResource(R.string.home_no_weather_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionRow(
                icon = Icons.Filled.DateRange,
                title = stringResource(R.string.home_choose_location),
                onClick = onOpen,
            )
            return@Column
        }

        val temp = snapshot.current.temperatureC
        val headline = if (temp != null) {
            AranyaFormat.measurement(context, temp, R.string.unit_celsius, 1)
        } else {
            stringResource(R.string.value_unknown)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = org.aranya.app.ui.theme.NumericSecondary,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = snapshot.location.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        val instant = runCatching { java.time.Instant.parse(snapshot.retrievedAt) }.getOrNull()
        Text(
            text = instant?.let {
                context.getString(R.string.format_updated_ago, AranyaFormat.relativeAge(context, it))
            } ?: stringResource(R.string.freshness_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Rule()

        ActionRow(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.state_retry),
            subtitle = stringResource(R.string.home_open_detail),
            onClick = onRefresh,
            enabled = true,
        )

        ActionRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.home_full_forecast),
            onClick = onOpen,
        )
    }
}

/** Shows the fixed palette so the design system is visible rather than merely claimed. */
@Composable
private fun PaletteStrip() {
    Row(horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.xxs)) {
        Swatch(AranyaTheme.semantics.positive)
        Swatch(AranyaTheme.semantics.rain)
        Swatch(AranyaTheme.semantics.heat)
        Swatch(AranyaTheme.semantics.drought)
        Swatch(AranyaTheme.semantics.riskHigh)
    }
}

@Preview(name = "Home light", showBackground = true, heightDp = 900)
@Composable
private fun HomePreview() {
    AranyaTheme(darkTheme = false) {
        HomeScreen(
            registry = SourceRegistry(emptyList()),
            weatherState = WeatherUiState.NeedsLocation,
            onOpenWeather = {},
            onOpenSources = {},
            onRefreshWeather = {},
        )
    }
}
