package org.aranya.app.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Weather conditions for a location.
 *
 * Every field that a provider may legitimately omit is nullable with an explicit
 * "unknown" meaning. A missing value is NEVER coerced to zero: a temperature of 0 degC and
 * an unreported temperature are completely different facts, and collapsing them would
 * make Aranya display a confidently wrong reading.
 */
@Serializable
data class WeatherConditions(
    @SerialName("temperature_c") val temperatureC: Double? = null,
    @SerialName("apparent_temperature_c") val apparentTemperatureC: Double? = null,
    @SerialName("relative_humidity_pct") val relativeHumidityPct: Int? = null,
    @SerialName("precipitation_mm") val precipitationMm: Double? = null,
    @SerialName("wind_speed_kmh") val windSpeedKmh: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    /** UTC instant the observation is valid for, from the provider. */
    @SerialName("observed_at") val observedAt: String? = null,
) {
    /**
     * Whether any usable reading arrived.
     *
     * Drives the no-data state. A response where every field is null is a valid response
     * that must render as "no data", not as zeroes.
     */
    val hasAnyReading: Boolean
        get() = temperatureC != null || relativeHumidityPct != null ||
            precipitationMm != null || windSpeedKmh != null || weatherCode != null
}

/**
 * One forecast day.
 *
 * [dateIso] is the provider's local date string (YYYY-MM-DD), NOT a UTC instant. Parsing it
 * to an instant would shift the day for a user east or west of the provider's reference
 * meridian, which is the classic off-by-one-day forecast bug.
 */
@Serializable
data class DailyForecast(
    @SerialName("date_iso") val dateIso: String,
    @SerialName("temp_max_c") val tempMaxC: Double? = null,
    @SerialName("temp_min_c") val tempMinC: Double? = null,
    @SerialName("precipitation_sum_mm") val precipitationSumMm: Double? = null,
    @SerialName("precipitation_probability_max_pct") val precipitationProbabilityMaxPct: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
)

/**
 * Provenance for a weather reading, mirroring data/sources.yml.
 *
 * Carried with every fetch so the UI can display provider and licence without consulting
 * the registry again, and so a cached value keeps its attribution even offline.
 */
@Serializable
data class WeatherAttribution(
    @SerialName("source_id") val sourceId: String,
    @SerialName("provider_name") val providerName: String,
    @SerialName("licence") val licence: String,
    @SerialName("attribution_text") val attributionText: String,
    /**
     * Recorded as free text so it can be shown verbatim, but it must also be structured:
     * [nonCommercialOnly] gates whether the app may be distributed commercially.
     */
    @SerialName("non_commercial_only") val nonCommercialOnly: Boolean = false,
    @SerialName("terms_url") val termsUrl: String? = null,
)

/**
 * A complete weather result as returned by any provider.
 *
 * This is the boundary type. The UI and the domain layer see only this, never a provider's
 * own response shape, which is what keeps the provider replaceable.
 */
@Serializable
data class WeatherSnapshot(
    @SerialName("location") val location: WeatherLocation,
    @SerialName("current") val current: WeatherConditions,
    @SerialName("daily") val daily: List<DailyForecast> = emptyList(),
    @SerialName("attribution") val attribution: WeatherAttribution,
    /** ISO-8601 UTC instant this snapshot was fetched. */
    @SerialName("retrieved_at") val retrievedAt: String,
    /** True when this snapshot came from local storage rather than the network. */
    @SerialName("from_cache") val fromCache: Boolean = false,
) {
    /**
     * Whether the reading is forecast data or an observation.
     *
     * Current conditions from a numerical model are a forecast, not a measurement, and
     * the UI must say so. See docs/AI_SAFETY.md on never letting model output imply
     * observation.
     */
    val isModelled: Boolean = true
}
