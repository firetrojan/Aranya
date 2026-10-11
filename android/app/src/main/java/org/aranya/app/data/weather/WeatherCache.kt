package org.aranya.app.data.weather

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Duration
import java.time.Instant

private val Context.weatherStore by preferencesDataStore(name = "weather_cache")

/**
 * Storage contract for the last successful reading.
 *
 * Extracted so the ViewModel can be tested against a fake without an Android Context or
 * DataStore. Keeping this narrow also documents exactly what the cache is for: hold one
 * reading per location.
 */
interface WeatherStore {
    suspend fun save(snapshot: WeatherSnapshot)
    /** Last stored reading regardless of age, marked as cached. Null when none or unreadable. */
    suspend fun loadStale(): WeatherSnapshot?
}

/**
 * Stores the last successful weather result.
 *
 * Uses DataStore rather than Room because there is exactly one record per location: a
 * small typed blob is the honest shape for this, and Room would add a database, an entity
 * and a DAO for no benefit at Stage 02. Room earns its place in Stage 04 when there is a
 * series of observations to query.
 *
 * Every stored snapshot keeps its full [WeatherSnapshot], which includes the retrieval
 * timestamp and the attribution. A cached reading therefore always knows how old it is
 * and who provided it, even with no network, which is the whole point of persisting the
 * envelope rather than just the numbers.
 */
class WeatherCache(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val now: () -> Instant = { Instant.now() },
) : WeatherStore {
    private val key: Preferences.Key<String>
        get() = stringPreferencesKey(KEY_PREFIX + "last")

    override suspend fun save(snapshot: WeatherSnapshot) {
        try {
            context.weatherStore.edit { prefs ->
                prefs[key] = json.encodeToString(WeatherSnapshot.serializer(), snapshot)
            }
        } catch (e: IOException) {
            // A cache write failure must never break the reading the user just got.
        }
    }

    /**
     * Returns the last stored snapshot, or null when there is none or it is unreadable.
     *
     * Uses `data.first()` for reading. `edit {}` is for mutation only, and calling it here
     * would clear the cache on every read.
     */
    suspend fun load(): WeatherSnapshot? {
        val raw = try {
            context.weatherStore.data.first()[key]
        } catch (e: Exception) {
            // A corrupt or unreadable store must degrade to "no cache", not crash the screen.
            null
        } ?: return null
        return runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull()
    }

    /**
     * Returns the cached snapshot only when it is still young enough to be worth showing
     * without a warning.
     *
     * Separate from [load] because the UI has two distinct needs: "what do we have" and
     * "what is good enough to present as current". A 12-hour-old forecast is still useful
     * with a stale marker; a week-old one is a different product.
     */
    suspend fun loadFresh(maxAge: Duration = Duration.ofHours(3)): WeatherSnapshot? {
        val stored = load() ?: return null
        val retrieved = runCatching { Instant.parse(stored.retrievedAt) }.getOrNull() ?: return null
        val age = Duration.between(retrieved, now())
        if (age.isNegative || age > maxAge) return null
        return stored.copy(fromCache = true)
    }

    /** Returns the cached snapshot regardless of age, for the offline fallback path. */
    override suspend fun loadStale(): WeatherSnapshot? = load()?.copy(fromCache = true)

    suspend fun clear() {
        try {
            context.weatherStore.edit { it.clear() }
        } catch (e: IOException) {
        }
    }

    companion object {
        const val KEY_PREFIX = "weather."
    }
}

/**
 * Freshness of a cached reading, mapped to the states the UI renders.
 *
 * Deliberately mirrors the Python normalizer's thresholds in
 * `data-pipeline/normalizers/normalize.py` so the app and the pipeline agree on when data
 * is too old to rely on.
 */
enum class WeatherFreshness { LIVE, FRESH, AGING, STALE, EXPIRED, UNKNOWN;

    companion object {
        /**
         * Weather is expected to refresh every 6 hours, matching the provider's model cycle.
         */
        const val REFRESH_INTERVAL_HOURS: Long = 6

        fun classify(retrievedAt: Instant, now: Instant): WeatherFreshness {
            val age = Duration.between(retrievedAt, now)
            if (age.isNegative) return LIVE
            val ratioMinutes = age.toMinutes().toDouble()
            val intervalMinutes = REFRESH_INTERVAL_HOURS * 60.0
            val ratio = ratioMinutes / intervalMinutes
            return when {
                ratio <= 0.5 -> FRESH
                ratio <= 1.0 -> AGING
                ratio <= 4.0 -> STALE
                else -> EXPIRED
            }
        }

        fun classify(retrievedAtIso: String?, now: Instant = Instant.now()): WeatherFreshness {
            val parsed = runCatching { Instant.parse(retrievedAtIso ?: return UNKNOWN) }.getOrNull()
                ?: return UNKNOWN
            return classify(parsed, now)
        }
    }
}
