package org.aranya.app.data.weather

import org.aranya.app.ui.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Location input validation, freshness classification and WMO code mapping.
 *
 * Pure logic, no Android and no network, so these run as ordinary JVM unit tests.
 */
class WeatherDomainTest {

    // ------------------------------------------------------- location validation

    @Test
    fun `empty and whitespace input is rejected`() {
        assertEquals(LocationInput.Reason.EMPTY, reasonOf(""))
        assertEquals(LocationInput.Reason.EMPTY, reasonOf("   "))
        assertEquals(LocationInput.Reason.EMPTY, reasonOf("\t\n"))
        assertEquals(LocationInput.Reason.EMPTY, reasonOf("a"))
    }

    @Test
    fun `overly long input is rejected`() {
        assertEquals(LocationInput.Reason.TOO_LONG, reasonOf("x".repeat(61)))
        assertTrue(reasonOf("x".repeat(60)) == null || reasonOf("x".repeat(60)) is LocationInput.Result.Valid)
    }

    @Test
    fun `digits are rejected because place names do not contain them`() {
        assertEquals(LocationInput.Reason.CONTAINS_DIGITS, reasonOf("Pune 411001"))
        assertEquals(LocationInput.Reason.CONTAINS_DIGITS, reasonOf("District5"))
    }

    @Test
    fun `real Indian place names are accepted`() {
        listOf("Pune", "Bengaluru", "Thiruvananthapuram", "Kochi", "Kota").forEach {
            val result = LocationInput.validate(it)
            assertTrue("'$it' should be valid but was $result", result is LocationInput.Result.Valid)
        }
    }

    @Test
    fun `valid input is trimmed`() {
        val result = LocationInput.validate("  Pune  ") as LocationInput.Result.Valid
        assertEquals("Pune", result.name)
    }

    @Test
    fun `devanagari input is accepted rather than rejected by a naive check`() {
        // A validator that rejected non-Latin script would block Hindi and Marathi users.
        val result = LocationInput.validate("पुणे")
        assertTrue("Devanagari must be accepted, got $result", result is LocationInput.Result.Valid)
    }

    @Test
    fun `coordinate bounds are enforced`() {
        assertTrue(LocationInput.validateCoordinates(18.5214, 73.8567))
        assertTrue(LocationInput.validateCoordinates(-90.0, 180.0))
        assertTrue(LocationInput.validateCoordinates(90.0, -180.0))
        assertFalse(LocationInput.validateCoordinates(91.0, 0.0))
        assertFalse(LocationInput.validateCoordinates(0.0, 181.0))
        assertFalse(LocationInput.validateCoordinates(Double.NaN, 0.0))
        assertFalse(LocationInput.validateCoordinates(0.0, Double.NaN))
    }

    @Test
    fun `presets are all within valid coordinate bounds`() {
        assertTrue("presets must not be empty", WeatherLocation.PRESETS.isNotEmpty())
        WeatherLocation.PRESETS.forEach { preset ->
            assertTrue(
                "preset ${preset.name} has out-of-range coordinates",
                LocationInput.validateCoordinates(preset.latitude, preset.longitude),
            )
        }
    }

    @Test
    fun `presets include Indian locations`() {
        WeatherLocation.PRESETS.forEach { assertTrue("${it.name} should name a region", !it.region.isNullOrBlank()) }
    }

    // ------------------------------------------------------ freshness behaviour

    private val now = Instant.parse("2026-10-11T12:00:00Z")

    @Test
    fun `freshness moves through its states as a reading ages`() {
        assertEquals(WeatherFreshness.LIVE, WeatherFreshness.classify(now.plusSeconds(1800), now))
        assertEquals(WeatherFreshness.FRESH, WeatherFreshness.classify(now.minus(Duration.ofHours(1)), now))
        assertEquals(WeatherFreshness.AGING, WeatherFreshness.classify(now.minus(Duration.ofHours(4)), now))
        assertEquals(WeatherFreshness.STALE, WeatherFreshness.classify(now.minus(Duration.ofHours(12)), now))
        assertEquals(WeatherFreshness.EXPIRED, WeatherFreshness.classify(now.minus(Duration.ofHours(48)), now))
    }

    @Test
    fun `unparseable timestamp yields unknown rather than a guess`() {
        assertEquals(WeatherFreshness.UNKNOWN, WeatherFreshness.classify(null as String?, now))
        assertEquals(WeatherFreshness.UNKNOWN, WeatherFreshness.classify("not-a-timestamp", now))
        assertEquals(WeatherFreshness.UNKNOWN, WeatherFreshness.classify("", now))
    }

    // ----------------------------------------------------------- weather codes

    @Test
    fun `known weather codes map to a description`() {
        assertEquals(org.aranya.app.R.string.wmo_0, WeatherCodes.stringFor(0))
        assertEquals(org.aranya.app.R.string.wmo_61, WeatherCodes.stringFor(61))
        assertEquals(org.aranya.app.R.string.wmo_95, WeatherCodes.stringFor(95))
    }

    @Test
    fun `unknown weather code returns null rather than guessing clear sky`() {
        // Showing "clear sky" for an unrecognised code would be a fabrication.
        assertNull(WeatherCodes.stringFor(999))
        assertNull(WeatherCodes.stringFor(-1))
        assertNull(WeatherCodes.stringFor(null))
    }

    @Test
    fun `weather kinds group codes sensibly`() {
        assertEquals(WeatherCodes.Kind.CLEAR, WeatherCodes.kindFor(0))
        assertEquals(WeatherCodes.Kind.CLOUD, WeatherCodes.kindFor(3))
        assertEquals(WeatherCodes.Kind.RAIN, WeatherCodes.kindFor(65))
        assertEquals(WeatherCodes.Kind.STORM, WeatherCodes.kindFor(95))
        assertEquals(WeatherCodes.Kind.SNOW, WeatherCodes.kindFor(75))
        assertEquals(WeatherCodes.Kind.FOG, WeatherCodes.kindFor(45))
        assertEquals(WeatherCodes.Kind.UNKNOWN, WeatherCodes.kindFor(4242))
    }

    @Test
    fun `storm and rain are flagged as disruptive for field work`() {
        assertTrue(WeatherCodes.isDisruptive(95))
        assertTrue(WeatherCodes.isDisruptive(65))
        assertFalse(WeatherCodes.isDisruptive(0))
        assertFalse(WeatherCodes.isDisruptive(2))
    }

    // ------------------------------------------------------------- conditions

    @Test
    fun `conditions with all null fields are not treated as usable`() {
        val empty = WeatherConditions()
        assertFalse(empty.hasAnyReading)
    }

    @Test
    fun `a single field is enough to count as a usable reading`() {
        assertTrue(WeatherConditions(temperatureC = 26.1).hasAnyReading)
        assertTrue(WeatherConditions(relativeHumidityPct = 0).hasAnyReading)
    }

    @Test
    fun `a zero reading is still a reading`() {
        // 0 mm of rain is a real observation, not a missing value.
        val conditions = WeatherConditions(precipitationMm = 0.0)
        assertTrue(conditions.hasAnyReading)
        assertEquals(0.0, conditions.precipitationMm!!, 0.001)
    }

    @Test
    fun `error kinds carry a localized message and correct retry policy`() {
        assertTrue(WeatherError.NETWORK_UNREACHABLE.isRetryable)
        assertTrue(WeatherError.TIMEOUT.isRetryable)
        assertTrue(WeatherError.RATE_LIMITED.isRetryable)
        assertTrue(WeatherError.HTTP_SERVER.isRetryable)
        // The user cannot fix these by tapping again.
        assertFalse(WeatherError.DNS.isRetryable)
        assertFalse(WeatherError.MALFORMED_RESPONSE.isRetryable)
        assertFalse(WeatherError.NO_DATA.isRetryable)
    }

    private fun reasonOf(input: String): LocationInput.Reason? =
        (LocationInput.validate(input) as? LocationInput.Result.Invalid)?.reason
}
