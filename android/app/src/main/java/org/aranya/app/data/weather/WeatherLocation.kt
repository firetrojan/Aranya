package org.aranya.app.data.weather

/**
 * A selectable location, entered by the user.
 *
 * No GPS by design. Agricultural weather advice needs a district-scale location, not a
 * precise fix, and requesting location permission for this would be a privacy cost out of
 * proportion to the benefit. See docs/PRIVACY.md section 3.
 */
data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Admin region when known, e.g. "Maharashtra". Optional: user may type only a name. */
    val region: String? = null,
) {
    companion object {
        /**
         * A small set of Indian reference points offered for quick selection.
         *
         * This is a convenience list, not a geocoder. It exists so a farmer can get
         * working weather without typing, while remaining free of any network lookup and
         * of any geocoding rate limit. Nominatim allows only 1 request/second and
         * prohibits bulk geocoding, so typing a place name requires a different
         * mechanism that Stage 02 deliberately does not build.
         */
        val PRESETS: List<WeatherLocation> = listOf(
            WeatherLocation("Pune", 18.5214, 73.8567, "Maharashtra"),
            WeatherLocation("Nagpur", 21.1458, 79.0882, "Maharashtra"),
            WeatherLocation("Nashik", 19.9975, 73.7898, "Maharashtra"),
            WeatherLocation("Mumbai", 19.0760, 72.8777, "Maharashtra"),
            WeatherLocation("Delhi", 28.6139, 77.2090, "Delhi"),
            WeatherLocation("Lucknow", 26.8467, 80.9462, "Uttar Pradesh"),
            WeatherLocation("Bengaluru", 12.9716, 77.5946, "Karnataka"),
            WeatherLocation("Hyderabad", 17.3850, 78.4867, "Telangana"),
            WeatherLocation("Chennai", 13.0827, 80.2707, "Tamil Nadu"),
            WeatherLocation("Kolkata", 22.5726, 88.3639, "West Bengal"),
            WeatherLocation("Ahmedabad", 23.0225, 72.5714, "Gujarat"),
            WeatherLocation("Jaipur", 26.9124, 75.7873, "Rajasthan"),
            WeatherLocation("Bhopal", 23.2599, 77.4126, "Madhya Pradesh"),
            WeatherLocation("Patna", 25.5941, 85.1376, "Bihar"),
            WeatherLocation("Guwahati", 26.1445, 91.7362, "Assam"),
            WeatherLocation("Thiruvananthapuram", 8.5241, 76.9366, "Kerala"),
        )
    }
}

/**
 * Validation for user-entered location text.
 *
 * Kept as pure functions so it can be unit tested without Android.
 */
object LocationInput {

    sealed interface Result {
        data class Valid(val name: String) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason(val stringRes: Int) {
        EMPTY(org.aranya.app.R.string.location_error_empty),
        TOO_LONG(org.aranya.app.R.string.location_error_too_long),
        CONTAINS_DIGITS(org.aranya.app.R.string.location_error_digits),
    }

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 60

    /**
     * Validates a typed location name.
     *
     * Deliberately conservative: it rejects obvious nonsense (empty, absurdly long,
     * digits) but does NOT try to be clever about Unicode, because place names in Indian
     * scripts may legitimately contain characters a naive validator would reject.
     */
    fun validate(raw: String): Result {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> Result.Invalid(Reason.EMPTY)
            trimmed.length < MIN_LENGTH -> Result.Invalid(Reason.EMPTY)
            trimmed.length > MAX_LENGTH -> Result.Invalid(Reason.TOO_LONG)
            trimmed.any { it.isDigit() } -> Result.Invalid(Reason.CONTAINS_DIGITS)
            else -> Result.Valid(trimmed)
        }
    }

    /** Guards a latitude/longitude pair before it is sent to any provider. */
    fun validateCoordinates(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()
}
