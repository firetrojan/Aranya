package org.aranya.app.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.aranya.app.R
import org.aranya.app.data.weather.WeatherAttribution
import org.aranya.app.data.weather.WeatherConditions
import org.aranya.app.data.weather.WeatherFreshness
import org.aranya.app.data.weather.WeatherLocation
import org.aranya.app.data.weather.WeatherSnapshot
import org.aranya.app.data.weather.DailyForecast
import org.aranya.app.ui.format.AranyaFormat
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaSize
import org.aranya.app.ui.theme.AranyaTheme
import org.aranya.app.ui.theme.NumericReadout
import org.aranya.app.ui.theme.NumericSecondary

/**
 * Weather screen.
 *
 * Shows real retrieved data with its provenance. Design intent follows the rest of
 * Aranya: a composed reading with rules and rails rather than a grid of rounded cards,
 * because the useful information here is a temperature, a small forecast table and an
 * honest timestamp, not a set of tiles.
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onSelectLocation: (WeatherLocation) -> Unit,
    onSubmitTyped: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AranyaSpacing.lg,
            end = AranyaSpacing.lg,
            top = AranyaSpacing.xl,
            bottom = AranyaSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.md),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.weather_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item(key = "picker") {
            LocationPicker(onSelectLocation = onSelectLocation, onSubmitTyped = onSubmitTyped)
        }

        when (state) {
            is WeatherUiState.NeedsLocation -> item(key = "needs") {
                QuietNote(stringResource(R.string.weather_use_preset))
            }

            is WeatherUiState.InvalidLocation -> item(key = "invalid") {
                ValidationNote(state.reason)
            }

            is WeatherUiState.Loading -> item(key = "loading") {
                LoadingNote()
            }

            is WeatherUiState.NoData -> item(key = "nodata") {
                QuietNote(stringResource(R.string.state_no_weather_data))
            }

            is WeatherUiState.Error -> item(key = "error") {
                ErrorState(state, onRetry)
            }

            is WeatherUiState.Refreshing -> {
                item(key = "refreshing") {
                    RefreshingNote()
                }
                item(key = "refreshing-body") {
                    WeatherBody(state.snapshot)
                }
            }

            is WeatherUiState.Ready -> {
                if (state.freshness != WeatherFreshness.FRESH &&
                    state.freshness != WeatherFreshness.LIVE
                ) {
                    item(key = "stale") {
                        FreshnessNote(state.freshness, state.snapshot.retrievedAt)
                    }
                }
                item(key = "ready-body") {
                    WeatherBody(state.snapshot)
                }
            }

            is WeatherUiState.StaleWithError -> {
                item(key = "stale-error") {
                    StaleErrorNote(state.error, state.snapshot.retrievedAt)
                }
                item(key = "stale-body") {
                    WeatherBody(state.snapshot)
                }
            }
        }
    }
}

/** Location entry: presets plus a validated typed field. No GPS permission is requested. */
@Composable
private fun LocationPicker(
    onSelectLocation: (WeatherLocation) -> Unit,
    onSubmitTyped: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.sm)) {
        Text(
            text = stringResource(R.string.weather_choose_location),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text(stringResource(R.string.weather_location_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitTyped(typed) }),
            modifier = Modifier.fillMaxWidth(),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm)) {
            items(WeatherLocation.PRESETS, key = { it.name }) { preset ->
                TextButton(
                    onClick = { onSelectLocation(preset) },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The reading itself: current conditions, forecast table, provenance. */
@Composable
private fun WeatherBody(snapshot: WeatherSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.lg)) {
        Text(
            text = snapshot.location.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (snapshot.current.hasAnyReading) {
            CurrentConditions(snapshot.current)
        }

        // The honesty notice. These are model values, and a farmer making a spray or
        // irrigation decision needs to know that is not a local measurement.
        ModelNotice()

        if (snapshot.daily.isNotEmpty()) {
            Text(
                text = stringResource(R.string.weather_forecast_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DailyTable(snapshot.daily)
        }

        FreshnessLine(snapshot.retrievedAt, snapshot.fromCache)
        AttributionBlock(snapshot.attribution)
    }
}

@Composable
private fun CurrentConditions(current: WeatherConditions) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xs)) {
        Text(
            text = stringResource(R.string.weather_current_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val description = WeatherCodes.stringFor(current.weatherCode)
        if (description != null) {
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Big number, or an honest gap when the provider sent nothing.
        val temp = current.temperatureC
        if (temp != null) {
            Text(
                text = AranyaFormat.measurement(
                    context = LocalContext.current,
                    value = temp,
                    unitRes = R.string.unit_celsius,
                    maxDecimals = 1,
                ),
                style = NumericReadout,
                color = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Text(
                text = stringResource(R.string.state_no_weather_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Hairline()

        // Each row renders only when the value exists. No placeholder zeroes.
        current.relativeHumidityPct?.let {
            MetricRow(
                stringResource(
                    R.string.weather_humidity,
                    AranyaFormat.number(LocalContext.current, it.toDouble(), 0) + "%",
                ),
            )
        }
        current.precipitationMm?.let {
            MetricRow(
                stringResource(
                    R.string.weather_precipitation,
                    AranyaFormat.measurement(LocalContext.current, it, R.string.unit_millimetre, 1),
                ),
            )
        }
        current.windSpeedKmh?.let {
            MetricRow(
                stringResource(
                    R.string.weather_wind,
                    AranyaFormat.number(LocalContext.current, it, 1) + " km/h",
                ),
            )
        }
    }
}

@Composable
private fun MetricRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Forecast as a table, not cards.
 *
 * A five-day forecast is a comparison, and comparison reads better as aligned columns than
 * as five separate surfaces.
 */
@Composable
private fun DailyTable(days: List<DailyForecast>) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xs)) {
        days.forEachIndexed { index, day ->
            if (index > 0) Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AranyaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DayLabel(day.dateIso, index == 0)

                val desc = WeatherCodes.stringFor(day.weatherCode)
                if (desc != null) {
                    Text(
                        text = stringResource(desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = kindColor(WeatherCodes.kindFor(day.weatherCode)),
                        modifier = Modifier.width(104.dp),
                    )
                }

                day.precipitationProbabilityMaxPct?.let {
                    Text(
                        text = stringResource(
                            R.string.weather_forecast_precipitation,
                            AranyaFormat.number(LocalContext.current, it.toDouble(), 0) + "%",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp),
                    )
                }

                RangeCell(day)
            }
        }
    }
}

@Composable
private fun DayLabel(dateIso: String, isFirst: Boolean) {
    Text(
        text = if (isFirst) stringResource(R.string.weather_today) else dateIso.take(5),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(56.dp),
    )
}

@Composable
private fun RangeCell(day: DailyForecast) {
    val max = day.tempMaxC
    val min = day.tempMinC
    val text = when {
        max != null && min != null -> stringResource(
            R.string.weather_range,
            AranyaFormat.number(LocalContext.current, max, 0),
            AranyaFormat.number(LocalContext.current, min, 0),
        )
        max != null -> AranyaFormat.number(LocalContext.current, max, 0)
        min != null -> AranyaFormat.number(LocalContext.current, min, 0)
        else -> "\u2013"
    }
    Text(
        text = text,
        style = NumericSecondary,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Model-versus-measurement notice. Always shown. */
@Composable
private fun ModelNotice() {
    Text(
        text = stringResource(R.string.weather_modelled_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(AranyaTheme.semantics.explanatoryFill, MaterialTheme.shapes.extraSmall)
            .padding(AranyaSpacing.sm),
    )
}

/**
 * Retrieval time.
 *
 * Always visible, because "how old is this" is the first question a farmer should ask of
 * any weather reading. States the plain relative age rather than a raw timestamp alone.
 */
@Composable
private fun FreshnessLine(retrievedAt: String, fromCache: Boolean) {
    val context = LocalContext.current
    val instant = runCatching { java.time.Instant.parse(retrievedAt) }.getOrNull()
    val age = if (instant != null) AranyaFormat.relativeAge(context, instant) else null

    Text(
        text = if (age != null) {
            context.getString(R.string.format_updated_ago, age)
        } else {
            context.getString(R.string.freshness_unknown)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (fromCache) {
        Text(
            text = stringResource(R.string.state_stale_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Provider and licence.
 *
 * Not optional and not hidden in a settings screen: Open-Meteo requires attribution for
 * CC-BY 4.0, and the non-commercial restriction is a real constraint the user should be
 * able to see.
 */
@Composable
private fun AttributionBlock(attribution: WeatherAttribution) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xxs)) {
        Text(
            text = stringResource(R.string.provider_label, attribution.providerName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.licence_label, attribution.licence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (attribution.nonCommercialOnly) {
            Text(
                text = stringResource(R.string.licence_non_commercial_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

// ----------------------------------------------------------------- state notes

@Composable
private fun LoadingNote() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        // Small, inline, and not a full-screen spinner: the user may be waiting on a
        // slow connection and a blank screen is worse than a quiet progress hint.
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RefreshingNote() {
    Text(
        text = stringResource(R.string.state_refreshing),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QuietNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ValidationNote(reason: org.aranya.app.data.weather.LocationInput.Reason) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xs)) {
        Text(
            text = stringResource(reason.stringRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        // Say why a listed location works and a typed one does not, instead of failing oddly.
        Text(
            text = stringResource(R.string.location_error_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FreshnessNote(freshness: WeatherFreshness, retrievedAt: String) {
    val context = LocalContext.current
    val instant = runCatching { java.time.Instant.parse(retrievedAt) }.getOrNull()
    val age = instant?.let { AranyaFormat.relativeAge(context, it) }
    val label = when (freshness) {
        WeatherFreshness.AGING -> stringResource(R.string.freshness_aging, age ?: "")
        WeatherFreshness.STALE -> stringResource(R.string.freshness_stale, age ?: "")
        WeatherFreshness.EXPIRED -> stringResource(R.string.freshness_expired, age ?: "")
        else -> stringResource(R.string.freshness_unknown)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun StaleErrorNote(
    error: org.aranya.app.data.weather.WeatherError,
    retrievedAt: String,
) {
    val context = LocalContext.current
    val instant = runCatching { java.time.Instant.parse(retrievedAt) }.getOrNull()
    val age = instant?.let { AranyaFormat.relativeAge(context, it) } ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xs)) {
        Text(
            text = stringResource(R.string.weather_error_with_cached, age),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(error.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(
    state: WeatherUiState.Error,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AranyaSpacing.sm)) {
        Text(
            text = stringResource(R.string.state_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(state.error.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.error.isRetryable) {
            TextButton(onClick = onRetry, shape = MaterialTheme.shapes.small) {
                Text(
                    text = stringResource(R.string.state_retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Hairline() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(AranyaSize.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Weather-kind colour. Semantic, not decorative, and always paired with a text label. */
@Composable
private fun kindColor(kind: WeatherCodes.Kind): Color = when (kind) {
    WeatherCodes.Kind.CLEAR -> AranyaTheme.semantics.heat
    WeatherCodes.Kind.CLOUD -> MaterialTheme.colorScheme.onSurfaceVariant
    WeatherCodes.Kind.FOG -> MaterialTheme.colorScheme.onSurfaceVariant
    WeatherCodes.Kind.DRIZZLE -> AranyaTheme.semantics.rain
    WeatherCodes.Kind.RAIN -> AranyaTheme.semantics.rain
    WeatherCodes.Kind.SNOW -> MaterialTheme.colorScheme.tertiary
    WeatherCodes.Kind.STORM -> AranyaTheme.semantics.riskHigh
    WeatherCodes.Kind.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Preview(name = "Weather ready", showBackground = true, heightDp = 1000)
@Composable
private fun WeatherReadyPreview() {
    AranyaTheme(darkTheme = false) {
        WeatherScreen(
            state = WeatherUiState.Ready(
                snapshot = WeatherSnapshot(
                    location = WeatherLocation("Pune", 18.5214, 73.8567, "Maharashtra"),
                    current = WeatherConditions(
                        temperatureC = 26.1,
                        relativeHumidityPct = 56,
                        precipitationMm = 0.0,
                        windSpeedKmh = 6.0,
                        observedAt = "2026-10-11T08:15",
                    ),
                    daily = listOf(
                    // Named arguments: the field order mixes Int and Double, so a
                    // positional call would silently compile against the wrong types.
                    DailyForecast(
                        dateIso = "2026-10-11",
                        weatherCode = 1,
                        tempMaxC = 33.4,
                        tempMinC = 24.1,
                        precipitationSumMm = 0.0,
                        precipitationProbabilityMaxPct = 0,
                    ),
                    DailyForecast(
                        dateIso = "2026-10-12",
                        weatherCode = 61,
                        tempMaxC = 30.1,
                        tempMinC = 23.4,
                        precipitationSumMm = 12.0,
                        precipitationProbabilityMaxPct = 70,
                    ),
                ),
                    attribution = WeatherAttribution(
                        sourceId = "open_meteo_forecast",
                        providerName = "Open-Meteo",
                        licence = "CC-BY 4.0",
                        attributionText = "Weather data by Open-Meteo.com",
                        nonCommercialOnly = true,
                    ),
                    retrievedAt = "2026-10-11T02:30:00Z",
                ),
                freshness = WeatherFreshness.FRESH,
            ),
            onSelectLocation = {},
            onSubmitTyped = {},
            onRetry = {},
        )
    }
}
