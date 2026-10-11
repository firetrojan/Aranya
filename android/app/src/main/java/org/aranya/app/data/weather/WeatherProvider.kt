package org.aranya.app.data.weather

/**
 * Why a weather fetch failed.
 *
 * Each case maps to a distinct user-visible state. Collapsing them into a generic "error"
 * would force the UI into a message that is either useless or alarming, and rate limiting
 * in particular is not a fault the user can do anything about.
 *
 * [messageRes] is a string resource id so the wording is translatable while the decision
 * stays in testable code.
 */
enum class WeatherError(val messageRes: Int) {
    NETWORK_UNREACHABLE(org.aranya.app.R.string.error_network_unreachable),
    TIMEOUT(org.aranya.app.R.string.error_network_timeout),
    DNS(org.aranya.app.R.string.error_dns),
    TLS(org.aranya.app.R.string.error_tls),
    HTTP_SERVER(org.aranya.app.R.string.error_http),
    RATE_LIMITED(org.aranya.app.R.string.error_rate_limited),
    MALFORMED_RESPONSE(org.aranya.app.R.string.error_parse),
    NO_DATA(org.aranya.app.R.string.state_no_weather_data),
    ;

    /** Whether an automatic retry is worth attempting. */
    val isRetryable: Boolean
        get() = this == NETWORK_UNREACHABLE || this == TIMEOUT || this == RATE_LIMITED || this == HTTP_SERVER
}

/** The outcome of a fetch attempt. */
sealed interface WeatherResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherResult

    /**
     * A failure.
     *
     * [cachedSnapshot] carries the last good reading when one exists, so the UI can show
     * stale-but-useful data alongside an honest error instead of an empty screen.
     */
    data class Failure(
        val error: WeatherError,
        val cachedSnapshot: WeatherSnapshot? = null,
        val detail: String? = null,
    ) : WeatherResult
}

/**
 * A source of weather data.
 *
 * The whole point of this interface is that no provider is hardwired into the UI or the
 * domain layer. Swapping Open-Meteo for the official IMD API when its key arrives is a
 * new implementation of this interface plus a registry status change, with no change to
 * any screen.
 *
 * Implementations must:
 *  - never fabricate a value, and never convert a missing value to a default;
 *  - attach [WeatherAttribution] to every success, including cached results;
 *  - report failures as [WeatherError] rather than by throwing.
 */
interface WeatherProvider {
    /** Stable id matching data/sources.yml. */
    val sourceId: String

    /**
     * Whether this provider may currently serve data.
     *
     * False when the registry status is not `verified` or credentials are absent. The
     * shell checks this so an unavailable provider is never presented as working.
     */
    val isAvailable: Boolean

    suspend fun fetch(location: WeatherLocation): WeatherResult
}
