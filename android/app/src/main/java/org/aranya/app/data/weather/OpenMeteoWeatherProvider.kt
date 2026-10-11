package org.aranya.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Open-Meteo forecast adapter.
 *
 * TERMS OF USE, checked 2026-10-11 against https://open-meteo.com/en/terms:
 *  - Data licence: CC-BY 4.0.
 *  - Attribution required ("Weather data by Open-Meteo.com").
 *  - **The free tier is NON-COMMERCIAL ONLY.** Open-Meteo permits use by apps without
 *    subscriptions or advertising and forbids it for apps that have either. Free-tier
 *    limits: 10,000 calls/day, 5,000/hour, 600/minute.
 *  - Aranya currently has neither subscriptions nor advertising, so the free tier is
 *    correct today. If that changes, this adapter must be replaced or a paid plan
 *    obtained. Tracked in docs/LICENCES.md and recorded as `nonCommercialOnly` in the
 *    attribution so the app can refuse to ship rather than breach.
 *
 * This is the ONLY provider implemented in Stage 02. NASA POWER is deliberately NOT
 * integrated: it serves historical environmental series, which are a different thing from
 * a forecast, and the task requires those two to be clearly distinguished rather than
 * blended. See docs/DATA_SOURCES.md.
 */
class OpenMeteoWeatherProvider(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val nowIso: () -> String = { java.time.Instant.now().toString() },
) : WeatherProvider {

    override val sourceId: String = "open_meteo_forecast"

    override val isAvailable: Boolean = true

    override suspend fun fetch(location: WeatherLocation): WeatherResult {
        if (!LocationInput.validateCoordinates(location.latitude, location.longitude)) {
            return WeatherResult.Failure(
                error = WeatherError.MALFORMED_RESPONSE,
                detail = "coordinates out of range for ${location.name}",
            )
        }

        return try {
            val response: HttpResponse = client.get(BASE_URL) {
                parameter("latitude", location.latitude)
                parameter("longitude", location.longitude)
                parameter("current", CURRENT_FIELDS)
                parameter("daily", DAILY_FIELDS)
                parameter("forecast_days", "5")
                // Local time at the requested place, so a daily value belongs to that
                // place's day rather than the device's.
                parameter("timezone", "auto")
            }

            when {
                response.status == HttpStatusCode.TooManyRequests ||
                    response.status.value == 429 ->
                    return WeatherResult.Failure(WeatherError.RATE_LIMITED, detail = "HTTP 429")

                !response.status.isSuccess() ->
                    return WeatherResult.Failure(
                        WeatherError.HTTP_SERVER,
                        detail = "HTTP ${response.status.value}",
                    )
            }

            val payload = response.bodyAsText()
            val parsed = runCatching { json.decodeFromString<OpenMeteoResponse>(payload) }
                .getOrElse {
                    return WeatherResult.Failure(
                        WeatherError.MALFORMED_RESPONSE,
                        detail = "decode failed: ${it.message}",
                    )
                }

            buildSnapshot(location, parsed)
        } catch (e: UnknownHostException) {
            WeatherResult.Failure(WeatherError.DNS, detail = e.message)
        } catch (e: SocketTimeoutException) {
            WeatherResult.Failure(WeatherError.TIMEOUT, detail = e.message)
        } catch (e: SSLException) {
            WeatherResult.Failure(WeatherError.TLS, detail = e.message)
        } catch (e: IOException) {
            WeatherResult.Failure(WeatherError.NETWORK_UNREACHABLE, detail = e.message)
        } catch (e: Exception) {
            // Never let an unexpected exception crash the screen; surface it as a
            // non-retryable failure so the cache fallback still runs.
            WeatherResult.Failure(
                error = WeatherError.MALFORMED_RESPONSE,
                detail = e::class.simpleName ?: "unknown",
            )
        }
    }

    private fun buildSnapshot(
        location: WeatherLocation,
        parsed: OpenMeteoResponse,
    ): WeatherResult {
        // Verify the units we assumed. A silent unit change would put "mm" next to a value
        // that is now inches, which is worse than an explicit refusal.
        val unitProblem = verifyUnits(parsed)
        if (unitProblem != null) {
            return WeatherResult.Failure(WeatherError.MALFORMED_RESPONSE, detail = unitProblem)
        }

        val current = parsed.current
        val conditions = WeatherConditions(
            temperatureC = current?.temperature2m,
            relativeHumidityPct = current?.relativeHumidity2m,
            precipitationMm = current?.precipitation,
            windSpeedKmh = current?.windSpeed10m,
            observedAt = current?.time,
        )

        val daily = parsed.daily?.toDailyForecasts().orEmpty()

        // An empty response is a real state, not a failure to hide. Report NO_DATA so the
        // UI can say "nothing available" instead of showing an empty forecast.
        if (!conditions.hasAnyReading && daily.isEmpty()) {
            return WeatherResult.Failure(WeatherError.NO_DATA, detail = "empty current and daily")
        }

        return WeatherResult.Success(
            WeatherSnapshot(
                location = location,
                current = conditions,
                daily = daily,
                attribution = OPEN_METEO_ATTRIBUTION,
                retrievedAt = nowIso(),
                fromCache = false,
            ),
        )
    }

    /**
     * Checks the unit strings the adapter depends on.
     *
     * Returns a human-readable problem, or null when the response is usable. `°C` is
     * checked exactly because the UI's Celsius label assumes it.
     */
    private fun verifyUnits(parsed: OpenMeteoResponse): String? {
        val cu = parsed.currentUnits ?: return "current_units missing"
        val du = parsed.dailyUnits ?: return "daily_units missing"
        if (cu["temperature_2m"] != UNIT_CELSIUS) {
            return "current_units.temperature_2m was '${cu["temperature_2m"]}', expected '$UNIT_CELSIUS'"
        }
        if (cu["relative_humidity_2m"] != UNIT_PERCENT) {
            return "current_units.relative_humidity_2m was '${cu["relative_humidity_2m"]}'"
        }
        if (du["precipitation_sum"] != UNIT_MM) {
            return "daily_units.precipitation_sum was '${du["precipitation_sum"]}'"
        }
        if (du["temperature_2m_max"] != UNIT_CELSIUS || du["temperature_2m_min"] != UNIT_CELSIUS) {
            return "daily temperature units were not $UNIT_CELSIUS"
        }
        return null
    }

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

        internal const val CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m"
        internal const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_sum,precipitation_probability_max"

        internal const val UNIT_CELSIUS = "\u00b0C"
        internal const val UNIT_PERCENT = "%"
        internal const val UNIT_MM = "mm"

        /**
         * Attribution carried with every reading, including cached ones.
         *
         * [nonCommercialOnly] is a hard gate, not documentation. The release build is
         * expected to assert it when the app is packaged for commercial distribution.
         */
        val OPEN_METEO_ATTRIBUTION = WeatherAttribution(
            sourceId = "open_meteo_forecast",
            providerName = "Open-Meteo",
            licence = "CC-BY 4.0",
            attributionText = "Weather data by Open-Meteo.com",
            nonCommercialOnly = true,
            termsUrl = "https://open-meteo.com/en/terms",
        )
    }
}

/** Default client configuration. Timeouts are short: a farmer on a weak connection
 *  would rather see cached data quickly than wait. */
fun defaultWeatherHttpClient(): HttpClient = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
