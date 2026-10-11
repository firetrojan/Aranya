package org.aranya.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Tests for the Open-Meteo adapter.
 *
 * Every response here is an explicitly labelled FIXTURE. None of it is real observed
 * weather, and no fixture may be presented as a live reading. The app itself fetches real
 * data; only tests are mocked.
 *
 * The success fixture was captured from the live API on 2026-10-11 and its shape is
 * re-verified by `data-pipeline/tests/probe_openmeteo_contract.py`.
 */
class OpenMeteoWeatherProviderTest {

    private val pune = WeatherLocation("Pune", 18.5214, 73.8567, "Maharashtra")

    /** FIXTURE: recorded response, 2026-10-11. Values are representative, not a forecast. */
    private val goodBody = """
        {
          "latitude": 18.523726,
          "longitude": 73.86876,
          "timezone": "Asia/Kolkata",
          "utc_offset_seconds": 19800,
          "current_units": {
            "time": "iso8601",
            "interval": "seconds",
            "temperature_2m": "°C",
            "relative_humidity_2m": "%",
            "precipitation": "mm",
            "wind_speed_10m": "km/h"
          },
          "current": {
            "interval": 900,
            "time": "2026-10-11T08:15",
            "temperature_2m": 26.1,
            "relative_humidity_2m": 56,
            "precipitation": 0.0,
            "wind_speed_10m": 6.0
          },
          "daily_units": {
            "time": "iso8601",
            "weather_code": "wmo code",
            "temperature_2m_max": "°C",
            "temperature_2m_min": "°C",
            "precipitation_sum": "mm",
            "precipitation_probability_max": "%"
          },
          "daily": {
            "time": ["2026-10-11","2026-10-12","2026-10-13"],
            "weather_code": [1, 61, 0],
            "temperature_2m_max": [33.4, 30.1, 33.5],
            "temperature_2m_min": [24.1, 23.4, 22.8],
            "precipitation_sum": [0.0, 12.4, 0.0],
            "precipitation_probability_max": [0, 70, 5]
          }
        }
    """.trimIndent()

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ): HttpClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }
        return HttpClient(engine) { install(HttpTimeout) }
    }

    private fun clientThrowing(error: Throwable): HttpClient {
        val engine = MockEngine { throw error }
        return HttpClient(engine)
    }

    // ------------------------------------------------------- successful parsing

    @Test
    fun `parses a complete response`() = runTest {
        val result = OpenMeteoWeatherProvider(
            client = clientReturning(goodBody),
            nowIso = { "2026-10-11T02:30:00Z" },
        ).fetch(pune)

        val snapshot = (result as WeatherResult.Success).snapshot
        assertEquals("Pune", snapshot.location.name)
        assertEquals(26.1, snapshot.current.temperatureC!!, 0.001)
        assertEquals(56, snapshot.current.relativeHumidityPct)
        assertEquals(0.0, snapshot.current.precipitationMm!!, 0.001)
        assertEquals(6.0, snapshot.current.windSpeedKmh!!, 0.001)
        assertEquals("2026-10-11T08:15", snapshot.current.observedAt)
        assertEquals(3, snapshot.daily.size)
        assertFalse(snapshot.fromCache)
        assertEquals("2026-10-11T02:30:00Z", snapshot.retrievedAt)
    }

    @Test
    fun `parses daily forecast arrays aligned by index`() = runTest {
        val result = OpenMeteoWeatherProvider(clientReturning(goodBody)).fetch(pune)
        val daily = (result as WeatherResult.Success).snapshot.daily

        assertEquals("2026-10-11", daily[0].dateIso)
        assertEquals(33.4, daily[0].tempMaxC!!, 0.001)
        assertEquals(24.1, daily[0].tempMinC!!, 0.001)

        // The rainy day carries its own values, not the first day's.
        assertEquals(61, daily[1].weatherCode)
        assertEquals(12.4, daily[1].precipitationSumMm!!, 0.001)
        assertEquals(70, daily[1].precipitationProbabilityMaxPct)
    }

    @Test
    fun `attribution is attached to every success`() = runTest {
        val result = OpenMeteoWeatherProvider(clientReturning(goodBody)).fetch(pune)
        val attribution = (result as WeatherResult.Success).snapshot.attribution

        assertEquals("open_meteo_forecast", attribution.sourceId)
        assertEquals("Open-Meteo", attribution.providerName)
        assertEquals("CC-BY 4.0", attribution.licence)
        assertTrue("CC-BY requires attribution", attribution.attributionText.isNotBlank())
        assertEquals(
            "the non-commercial restriction must travel with the data",
            true,
            attribution.nonCommercialOnly,
        )
        assertNotNull(attribution.termsUrl)
    }

    @Test
    fun `provider declares its registry id and availability`() {
        val provider = OpenMeteoWeatherProvider(clientReturning(goodBody))
        assertEquals("open_meteo_forecast", provider.sourceId)
        assertTrue(provider.isAvailable)
    }

    // ------------------------------------------------------- missing / malformed

    @Test
    fun `missing current fields become null and are never zero`() = runTest {
        // FIXTURE: response with the current block present but every value absent.
        val body = """
        {
          "current_units": {"temperature_2m":"°C","relative_humidity_2m":"%"},
          "current": {"time":"2026-10-11T08:15"},
          "daily_units": {"temperature_2m_max":"°C","temperature_2m_min":"°C","precipitation_sum":"mm"},
          "daily": {"time":["2026-10-11"],"temperature_2m_max":[33.4],"temperature_2m_min":[24.1]}
        }
        """.trimIndent()

        val snapshot = (OpenMeteoWeatherProvider(clientReturning(body)).fetch(pune)
            as WeatherResult.Success).snapshot

        // The critical assertion: absence must NOT become 0.0 or 0.
        assertNull("absent temperature must be null, not 0.0", snapshot.current.temperatureC)
        assertNull("absent humidity must be null, not 0", snapshot.current.relativeHumidityPct)
        assertNull("absent rainfall must be null, not 0.0", snapshot.current.precipitationMm)
        assertEquals("the real forecast is still usable", 33.4, snapshot.daily[0].tempMaxC!!, 0.001)
    }

    @Test
    fun `empty response is reported as no data`() = runTest {
        // FIXTURE: structurally valid, but nothing was returned.
        val body = """
        {"current_units":{"temperature_2m":"°C","relative_humidity_2m":"%"},
         "daily_units":{"temperature_2m_max":"°C","temperature_2m_min":"°C","precipitation_sum":"mm"},
         "current":{}, "daily":{"time":[]}}
        """.trimIndent()

        val result = OpenMeteoWeatherProvider(clientReturning(body)).fetch(pune)
        assertEquals(WeatherError.NO_DATA, (result as WeatherResult.Failure).error)
    }

    @Test
    fun `malformed json is rejected`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientReturning("this is not json at all"),
        ).fetch(pune)
        assertEquals(
            WeatherError.MALFORMED_RESPONSE,
            (result as WeatherResult.Failure).error,
        )
    }

    @Test
    fun `truncated json is rejected`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientReturning(goodBody.substring(0, goodBody.length / 2)),
        ).fetch(pune)
        assertTrue(result is WeatherResult.Failure)
    }

    @Test
    fun `wrong temperature units are refused rather than mislabelled`() = runTest {
        // FIXTURE: same payload but the provider switched to Fahrenheit. Showing that value
        // next to a Celsius label would be a silent, serious error.
        val body = goodBody.replace("\"temperature_2m\": \"°C\"", "\"temperature_2m\": \"°F\"")
            .replace("\"temperature_2m_max\": \"°C\"", "\"temperature_2m_max\": \"°F\"")
            .replace("\"temperature_2m_min\": \"°C\"", "\"temperature_2m_min\": \"°F\"")

        val result = OpenMeteoWeatherProvider(clientReturning(body)).fetch(pune)
        val failure = result as WeatherResult.Failure
        assertEquals(WeatherError.MALFORMED_RESPONSE, failure.error)
        assertTrue(
            "the failure should name the unit problem, got: ${failure.detail}",
            failure.detail?.contains("temperature") == true,
        )
    }

    @Test
    fun `short parallel arrays yield nulls for the missing days only`() = runTest {
        // FIXTURE: three dates but only two maxima, as a partial provider response.
        val body = """
        {
          "current_units":{"temperature_2m":"°C","relative_humidity_2m":"%"},
          "daily_units":{"temperature_2m_max":"°C","temperature_2m_min":"°C","precipitation_sum":"mm"},
          "current":{"time":"2026-10-11T08:15","temperature_2m":25.0,"relative_humidity_2m":50},
          "daily":{"time":["2026-10-11","2026-10-12","2026-10-13"],
                   "temperature_2m_max":[33.4,30.1],
                   "temperature_2m_min":[24.1,23.4],
                   "precipitation_sum":[0.0,1.0]}
        }
        """.trimIndent()

        val daily = (OpenMeteoWeatherProvider(clientReturning(body)).fetch(pune)
            as WeatherResult.Success).snapshot.daily

        assertEquals(3, daily.size)
        assertEquals(33.4, daily[0].tempMaxC!!, 0.001)
        assertEquals(30.1, daily[1].tempMaxC!!, 0.001)
        assertNull("the third day has no data and must not be invented",
            daily[2].tempMaxC)
        assertNull(daily[2].tempMinC)
    }

    @Test
    fun `daily time array missing yields no forecast days`() = runTest {
        val body = """
        {
          "current_units":{"temperature_2m":"°C","relative_humidity_2m":"%"},
          "daily_units":{"temperature_2m_max":"°C","temperature_2m_min":"°C","precipitation_sum":"mm"},
          "current":{"time":"2026-10-11T08:15","temperature_2m":25.0,"relative_humidity_2m":50},
          "daily":{"temperature_2m_max":[33.4],"temperature_2m_min":[24.1]}
        }
        """.trimIndent()

        val snapshot = (OpenMeteoWeatherProvider(clientReturning(body)).fetch(pune)
            as WeatherResult.Success).snapshot
        assertEquals(0, snapshot.daily.size)
        assertEquals(25.0, snapshot.current.temperatureC!!, 0.001)
    }

    // ------------------------------------------------------------- http failures

    @Test
    fun `http 500 is a server error`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientReturning("{}", status = HttpStatusCode.InternalServerError),
        ).fetch(pune)
        val failure = result as WeatherResult.Failure
        assertEquals(WeatherError.HTTP_SERVER, failure.error)
        assertTrue(failure.error.isRetryable)
    }

    @Test
    fun `http 429 is reported as rate limited`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientReturning("rate limited", status = HttpStatusCode.TooManyRequests),
        ).fetch(pune)
        val failure = result as WeatherResult.Failure
        assertEquals(WeatherError.RATE_LIMITED, failure.error)
        assertTrue(
            "rate limiting is worth retrying later",
            failure.error.isRetryable,
        )
    }

    @Test
    fun `http 404 is a server error not a crash`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientReturning("not found", status = HttpStatusCode.NotFound),
        ).fetch(pune)
        assertEquals(WeatherError.HTTP_SERVER, (result as WeatherResult.Failure).error)
    }

    @Test
    fun `timeout maps to a timeout error`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientThrowing(SocketTimeoutException("read timed out")),
        ).fetch(pune)
        val failure = result as WeatherResult.Failure
        assertEquals(WeatherError.TIMEOUT, failure.error)
        assertTrue(failure.error.isRetryable)
    }

    @Test
    fun `dns failure maps to a dns error`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientThrowing(UnknownHostException("api.open-meteo.com")),
        ).fetch(pune)
        assertEquals(WeatherError.DNS, (result as WeatherResult.Failure).error)
    }

    @Test
    fun `tls failure maps to a tls error`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientThrowing(SSLException("handshake failed")),
        ).fetch(pune)
        assertEquals(WeatherError.TLS, (result as WeatherResult.Failure).error)
    }

    @Test
    fun `generic io failure maps to network unreachable`() = runTest {
        val result = OpenMeteoWeatherProvider(
            clientThrowing(IOException("connection reset")),
        ).fetch(pune)
        assertEquals(
            WeatherError.NETWORK_UNREACHABLE,
            (result as WeatherResult.Failure).error,
        )
    }

    @Test
    fun `out of range coordinates are refused before any request`() = runTest {
        val provider = OpenMeteoWeatherProvider(clientReturning(goodBody))
        val result = provider.fetch(WeatherLocation("Nowhere", 999.0, 999.0))
        assertEquals(WeatherError.MALFORMED_RESPONSE, (result as WeatherResult.Failure).error)
    }

    @Test
    fun `nan coordinates are refused`() = runTest {
        val provider = OpenMeteoWeatherProvider(clientReturning(goodBody))
        val result = provider.fetch(WeatherLocation("Bad", Double.NaN, 0.0))
        assertTrue(result is WeatherResult.Failure)
    }
}
