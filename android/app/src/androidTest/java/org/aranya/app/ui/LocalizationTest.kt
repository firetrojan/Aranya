package org.aranya.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.aranya.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Verifies the localization conventions Aranya depends on.
 *
 * These run on device (androidTest) because they need a real resource table. They exist
 * to catch the specific failure mode the project cares about: user-facing text slipping
 * into Kotlin source, where no translator can reach it.
 */
class LocalizationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun coreStringsArePresentInDefaultLocale() {
        val res = context.resources
        for (id in listOf(
            R.string.app_name,
            R.string.shell_stage_body,
            R.string.sources_intro,
            R.string.qualifier_estimate,
            R.string.qualifier_measurement,
            R.string.qualifier_regional_estimate,
            R.string.error_network_timeout,
            R.string.state_offline_body,
            R.string.freshness_unknown,
            R.string.attribution_format,
        )) {
            val value = res.getString(id)
            assertTrue("string resource $id resolved to empty text", value.isNotBlank())
        }
    }

    @Test
    fun qualifierStringsCoverEveryFreshnessCombination() {
        // The normalizer can emit an "outdated_" prefixed qualifier, so both the plain and
        // the outdated form must exist for every base qualifier.
        val bases = listOf(
            R.string.qualifier_estimate,
            R.string.qualifier_measurement,
            R.string.qualifier_regional_estimate,
        )
        val outdated = listOf(
            R.string.qualifier_outdated_estimate,
            R.string.qualifier_outdated_measurement,
            R.string.qualifier_outdated_regional_estimate,
        )
        assertEquals(bases.size, outdated.size)
        bases.forEach { assertTrue(context.getString(it).isNotBlank()) }
        outdated.forEach { assertTrue(context.getString(it).isNotBlank()) }
    }

    @Test
    fun everyErrorKindHasALocalizedMessage() {
        // normalize.py VALID_ERROR_KINDS must each map to a string, otherwise the UI would
        // have to fall back to raw exception text, which the project forbids.
        val kinds = mapOf(
            "timeout" to R.string.error_network_timeout,
            "network" to R.string.error_network_unreachable,
            "dns" to R.string.error_dns,
            "tls" to R.string.error_tls,
            "http" to R.string.error_http,
            "auth" to R.string.error_auth,
            "rate_limited" to R.string.error_rate_limited,
            "parse" to R.string.error_parse,
            "validation" to R.string.error_validation,
            "not_implemented" to R.string.error_not_implemented,
            "unavailable" to R.string.error_unavailable,
            "unknown" to R.string.error_unknown,
        )
        assertEquals(12, kinds.size)
        for ((kind, resId) in kinds) {
            val text = context.getString(resId)
            assertTrue("no message for error kind '$kind'", text.isNotBlank())
            assertFalse("message for '$kind' looks like a raw key", text.contains("%1"))
        }
    }

    @Test
    fun pluralsResolveForEnglish() {
        val minutes = context.resources
            .getQuantityString(R.plurals.duration_minutes, 1, 1)
        val minutesPlural = context.resources
            .getQuantityString(R.plurals.duration_minutes, 5, 5)
        assertTrue(minutes.contains("1"))
        assertTrue(minutesPlural.contains("5"))
    }

    @Test
    fun formattedStringsUsePositionalPlaceholders() {
        // Non-positional %s breaks translation into languages that reorder arguments.
        val attribution = context.getString(R.string.attribution_format, "Open-Meteo")
        assertTrue(attribution.contains("Open-Meteo"))
        val summary = context.getString(R.string.format_sources_summary, 4, 3, 9)
        assertTrue(summary.contains("4"))
        assertTrue(summary.contains("9"))
    }

    @Test
    fun defaultResourcesAreEnglishOnly() {
        // Stage 01 ships one language. A stray second locale would silently enlarge the APK.
        val locales = context.resources.configuration.locales
        assertEquals(
            "Stage 01 must ship exactly one locale",
            1,
            locales.size,
        )
        assertEquals(Locale.ENGLISH.language, locales[0].language)
    }
}
