package org.aranya.app.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the Open-Meteo forecast response.
 *
 * Every field name and unit here was captured from a live response by
 * `data-pipeline/tests/probe_openmeteo_contract.py` on 2026-10-11. Do not guess at these;
 * rerun that probe if a parsing test starts failing.
 *
 * Note on units: Open-Meteo is asked for metric values in the base units shown in
 * `current_units` / `daily_units`. The adapter asserts those unit strings rather than
 * assuming them, so a provider-side unit change becomes a clear error instead of
 * mislabelled numbers.
 */
@Serializable
internal data class OpenMeteoResponse(
    @SerialName("current") val current: OpenMeteoCurrent? = null,
    @SerialName("current_units") val currentUnits: Map<String, String>? = null,
    @SerialName("daily") val daily: OpenMeteoDaily? = null,
    @SerialName("daily_units") val dailyUnits: Map<String, String>? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
)

@Serializable
internal data class OpenMeteoCurrent(
    /**
     * Local wall-clock time at the requested location, e.g. "2026-10-11T08:15".
     *
     * There is no offset on this value because the request used `timezone=auto`. It is
     * deliberately kept as the provider's local string rather than converted to an instant:
     * converting a zone-less local time would either shift it or silently apply the device
     * zone to a reading that belongs to another place.
     */
    @SerialName("time") val time: String? = null,
    @SerialName("temperature_2m") val temperature2m: Double? = null,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @SerialName("precipitation") val precipitation: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,
)

@Serializable
internal data class OpenMeteoDaily(
    @SerialName("time") val time: List<String>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null,
) {
    /**
     * Zips the parallel arrays into per-day records.
     *
     * Uses the index of [time] as the spine and tolerates shorter arrays: a provider that
     * drops one field yields null for that field on the affected days rather than
     * discarding the whole forecast. Trailing extra entries are ignored, because a
     * provider appending a value is not a reason to lose the days that are complete.
     */
    fun toDailyForecasts(): List<DailyForecast> {
        val dates = time ?: return emptyList()
        return dates.mapIndexed { index, date ->
            DailyForecast(
                dateIso = date,
                weatherCode = weatherCode?.getOrNull(index),
                tempMaxC = temperatureMax?.getOrNull(index),
                tempMinC = temperatureMin?.getOrNull(index),
                precipitationSumMm = precipitationSum?.getOrNull(index),
                precipitationProbabilityMaxPct = precipitationProbabilityMax?.getOrNull(index),
            )
        }
    }
}
