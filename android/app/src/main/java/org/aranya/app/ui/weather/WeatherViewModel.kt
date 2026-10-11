package org.aranya.app.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.aranya.app.data.weather.LocationInput
import org.aranya.app.data.weather.WeatherStore
import org.aranya.app.data.weather.WeatherError
import org.aranya.app.data.weather.WeatherFreshness
import org.aranya.app.data.weather.WeatherLocation
import org.aranya.app.data.weather.WeatherProvider
import org.aranya.app.data.weather.WeatherResult
import org.aranya.app.data.weather.WeatherSnapshot
import java.time.Instant

/**
 * Everything the weather screen can be showing.
 *
 * One sealed state per real product condition, including the awkward ones. A farmer with
 * no connection needs to be told what they are looking at, not shown an empty screen or a
 * spinner that never resolves.
 */
sealed interface WeatherUiState {

    /** No location chosen yet. */
    data object NeedsLocation : WeatherUiState

    /** Typed text failed validation. */
    data class InvalidLocation(val reason: LocationInput.Reason) : WeatherUiState

    /** First fetch in progress, nothing cached to show yet. */
    data object Loading : WeatherUiState

    /**
     * Showing stored data while a refresh is attempted.
     *
     * Distinct from [Loading] so the screen does not blank out a usable reading just
     * because the user pulled to refresh.
     */
    data class Refreshing(val snapshot: WeatherSnapshot) : WeatherUiState

    /** A usable reading, fresh or cached, with an honest freshness label. */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val freshness: WeatherFreshness,
    ) : WeatherUiState

    /**
     * The fetch failed but a stored reading is available.
     *
     * Shows the reading with the error attached, because a day-old forecast is more useful
     * than a blank screen, provided it is clearly labelled as not current.
     */
    data class StaleWithError(
        val snapshot: WeatherSnapshot,
        val freshness: WeatherFreshness,
        val error: WeatherError,
    ) : WeatherUiState

    /** The fetch failed and nothing is stored. */
    data class Error(val error: WeatherError) : WeatherUiState

    /** The provider responded but contained no weather. */
    data object NoData : WeatherUiState
}

/**
 * Drives the weather screen.
 *
 * Holds no provider knowledge: it is constructed with a [WeatherProvider], so swapping in
 * the IMD adapter later changes nothing here or in any composable.
 */
class WeatherViewModel(
    private val provider: WeatherProvider,
    private val cache: WeatherStore,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.NeedsLocation)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var selected: WeatherLocation? = null

    init {
        // Show anything already stored so a cold start with no network is useful.
        viewModelScope.launch {
            cache.loadStale()?.let { stored ->
                selected = stored.location
                _state.value = WeatherUiState.Ready(
                    snapshot = stored,
                    freshness = WeatherFreshness.classify(stored.retrievedAt, now()),
                )
            }
        }
    }

    /** User picked a location from the preset list. */
    fun selectLocation(location: WeatherLocation) {
        selected = location
        refresh()
    }

    /** User typed a location name. Without a geocoder, a name alone cannot become
     *  coordinates, so this is validated and rejected honestly rather than guessed at. */
    fun submitTypedLocation(raw: String) {
        when (val result = LocationInput.validate(raw)) {
            is LocationInput.Result.Invalid -> _state.value = WeatherUiState.InvalidLocation(result.reason)
            is LocationInput.Result.Valid -> {
                val match = WeatherLocation.PRESETS.firstOrNull {
                    it.name.equals(result.name, ignoreCase = true)
                }
                if (match != null) {
                    selectLocation(match)
                } else {
                    // No geocoding in Stage 02. Nominatim permits 1 request/second and
                    // prohibits bulk use, so silently calling it per keystroke is not an
                    // option. Say so rather than failing oddly.
                    _state.value = WeatherUiState.InvalidLocation(LocationInput.Reason.EMPTY)
                }
            }
        }
    }

    /** Re-fetch for the selected location. */
    fun refresh() {
        val location = selected ?: return
        val cached = _state.value
        // Keep showing what we have while refreshing, rather than flashing a spinner.
        if (cached is WeatherUiState.Ready) {
            _state.value = WeatherUiState.Refreshing(cached.snapshot)
        } else {
            _state.value = WeatherUiState.Loading
        }

        viewModelScope.launch {
            when (val result = provider.fetch(location)) {
                is WeatherResult.Success -> {
                    cache.save(result.snapshot)
                    _state.value = WeatherUiState.Ready(
                        snapshot = result.snapshot,
                        freshness = WeatherFreshness.classify(result.snapshot.retrievedAt, now()),
                    )
                }

                is WeatherResult.Failure -> {
                    val fallback = result.cachedSnapshot ?: cache.loadStale()
                    when {
                        fallback != null -> _state.value = WeatherUiState.StaleWithError(
                            snapshot = fallback,
                            freshness = WeatherFreshness.classify(fallback.retrievedAt, now()),
                            error = result.error,
                        )

                        result.error == WeatherError.NO_DATA ->
                            _state.value = WeatherUiState.NoData

                        else -> _state.value = WeatherUiState.Error(result.error)
                    }
                }
            }
        }
    }

    /** The currently selected location, for restoring state across a process death. */
    fun currentLocation(): WeatherLocation? = selected

    /** Restores a previously selected location, e.g. after process recreation. */
    fun restore(location: WeatherLocation) {
        if (selected == null) {
            selected = location
        }
    }

    internal fun setStateForTest(state: WeatherUiState) {
        _state.update { state }
    }
}
