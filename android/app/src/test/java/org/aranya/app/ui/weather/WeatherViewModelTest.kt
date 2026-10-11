package org.aranya.app.ui.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.aranya.app.data.weather.DailyForecast
import org.aranya.app.data.weather.WeatherAttribution
import org.aranya.app.data.weather.WeatherStore
import org.aranya.app.data.weather.WeatherConditions
import org.aranya.app.data.weather.WeatherError
import org.aranya.app.data.weather.WeatherFreshness
import org.aranya.app.data.weather.WeatherLocation
import org.aranya.app.data.weather.WeatherProvider
import org.aranya.app.data.weather.WeatherResult
import org.aranya.app.data.weather.WeatherSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * ViewModel behaviour: state transitions, cache persistence and the offline fallback.
 *
 * A fake [WeatherCache] is used rather than the real DataStore one, because DataStore
 * needs a real Context. The fake records what was written so the persistence contract is
 * asserted, not assumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val pune = WeatherLocation("Pune", 18.5214, 73.8567, "Maharashtra")
    private val fixedNow = Instant.parse("2026-10-11T12:00:00Z")

    private val attribution = WeatherAttribution(
        sourceId = "open_meteo_forecast",
        providerName = "Open-Meteo",
        licence = "CC-BY 4.0",
        attributionText = "Weather data by Open-Meteo.com",
        nonCommercialOnly = true,
    )

    private fun snapshotAt(retrievedAt: String, temp: Double = 26.1) = WeatherSnapshot(
        location = pune,
        current = WeatherConditions(temperatureC = temp, relativeHumidityPct = 56),
        daily = listOf(DailyForecast("2026-10-11", 1, 33.4, 24.1, 0.0, 0)),
        attribution = attribution,
        retrievedAt = retrievedAt,
    )

    /** Records writes; returns a stored snapshot without needing Android. */
    private class FakeCache(initial: WeatherSnapshot? = null) : WeatherStore {
        var stored: WeatherSnapshot? = initial
        var saveCount = 0

        override suspend fun save(snapshot: WeatherSnapshot) {
            stored = snapshot
            saveCount++
        }

        override suspend fun loadStale(): WeatherSnapshot? = stored?.copy(fromCache = true)
    }

    private class FakeProvider(
        var result: WeatherResult,
    ) : WeatherProvider {
        override val sourceId = "open_meteo_forecast"
        override val isAvailable = true
        var callCount = 0
        override suspend fun fetch(location: WeatherLocation): WeatherResult {
            callCount++
            return result
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts needing a location when nothing is cached`() = runTest(dispatcher) {
        val vm = WeatherViewModel(
            provider = FakeProvider(WeatherResult.Failure(WeatherError.DNS)),
            cache = FakeCache(),
            now = { fixedNow },
        )
        advanceUntilIdle()
        assertEquals(WeatherUiState.NeedsLocation, vm.state.value)
    }

    @Test
    fun `restores a cached snapshot on cold start`() = runTest(dispatcher) {
        val cached = snapshotAt("2026-10-11T11:00:00Z")
        val vm = WeatherViewModel(
            provider = FakeProvider(WeatherResult.Failure(WeatherError.DNS)),
            cache = FakeCache(cached),
            now = { fixedNow },
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Ready but was $state", state is WeatherUiState.Ready)
        state as WeatherUiState.Ready
        assertEquals(26.1, state.snapshot.current.temperatureC!!, 0.001)
        assertEquals(WeatherFreshness.FRESH, state.freshness)
        // Attribution must survive into the cached reading.
        assertEquals("Open-Meteo", state.snapshot.attribution.providerName)
    }

    @Test
    fun `selecting a location fetches and persists the result`() = runTest(dispatcher) {
        val cache = FakeCache()
        val provider = FakeProvider(
            WeatherResult.Success(snapshotAt("2026-10-11T12:00:00Z", 27.5)),
        )
        val vm = WeatherViewModel(provider, cache, { fixedNow })

        vm.selectLocation(pune)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is WeatherUiState.Ready)
        assertEquals(27.5, (state as WeatherUiState.Ready).snapshot.current.temperatureC!!, 0.001)
        assertEquals("a successful fetch must be persisted", 1, cache.saveCount)
        assertEquals(27.5, cache.stored!!.current.temperatureC!!, 0.001)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `persisted snapshot keeps its timestamp and source metadata`() = runTest(dispatcher) {
        val cache = FakeCache()
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Success(snapshotAt("2026-10-11T12:00:00Z"))),
            cache,
            { fixedNow },
        )
        vm.selectLocation(pune)
        advanceUntilIdle()

        val stored = cache.stored!!
        assertEquals("2026-10-11T12:00:00Z", stored.retrievedAt)
        assertEquals("open_meteo_forecast", stored.attribution.sourceId)
        assertTrue(stored.attribution.nonCommercialOnly)
        assertEquals("Maharashtra", stored.location.region)
    }

    @Test
    fun `failure with no cache surfaces an error state`() = runTest(dispatcher) {
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Failure(WeatherError.TIMEOUT)),
            FakeCache(),
            { fixedNow },
        )
        vm.selectLocation(pune)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Error but was $state", state is WeatherUiState.Error)
        assertEquals(WeatherError.TIMEOUT, (state as WeatherUiState.Error).error)
    }

    @Test
    fun `failure with a cached snapshot shows stale data and the error`() = runTest(dispatcher) {
        // This is the offline path: a real reading plus an honest reason it is not current.
        val stale = snapshotAt("2026-10-10T06:00:00Z")
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Failure(WeatherError.NETWORK_UNREACHABLE)),
            FakeCache(stale),
            { fixedNow },
        )
        vm.selectLocation(pune)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected StaleWithError but was $state", state is WeatherUiState.StaleWithError)
        state as WeatherUiState.StaleWithError
        assertEquals(WeatherError.NETWORK_UNREACHABLE, state.error)
        assertEquals(26.1, state.snapshot.current.temperatureC!!, 0.001)
        // A 30-hour-old reading must not be presented as current.
        assertEquals(WeatherFreshness.EXPIRED, state.freshness)
    }

    @Test
    fun `no-data response maps to the no-data state`() = runTest(dispatcher) {
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Failure(WeatherError.NO_DATA)),
            FakeCache(),
            { fixedNow },
        )
        vm.selectLocation(pune)
        advanceUntilIdle()
        assertEquals(WeatherUiState.NoData, vm.state.value)
    }

    @Test
    fun `failed refresh does not overwrite the good cached value`() = runTest(dispatcher) {
        val good = snapshotAt("2026-10-11T11:00:00Z", 26.1)
        val cache = FakeCache(good)
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Failure(WeatherError.TIMEOUT)),
            cache,
            { fixedNow },
        )
        advanceUntilIdle()

        vm.selectLocation(pune)
        advanceUntilIdle()

        assertEquals(
            "a failed refresh must not destroy the stored reading",
            26.1,
            cache.stored!!.current.temperatureC!!,
            0.001,
        )
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun `refresh while data is shown does not blank the screen`() = runTest(dispatcher) {
        val cache = FakeCache(snapshotAt("2026-10-11T11:00:00Z"))
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Failure(WeatherError.TIMEOUT)),
            cache,
            { fixedNow },
        )
        advanceUntilIdle()
        assertTrue(vm.state.value is WeatherUiState.Ready)

        // The intermediate state must still carry the snapshot, so the UI has something
        // to render while the request is in flight.
        vm.refresh()
        val intermediate = vm.state.value
        assertTrue(
            "expected Refreshing with data, got $intermediate",
            intermediate is WeatherUiState.Refreshing,
        )
        assertEquals(
            26.1,
            (intermediate as WeatherUiState.Refreshing).snapshot.current.temperatureC!!,
            0.001,
        )
    }

    @Test
    fun `invalid typed location is rejected without a network call`() = runTest(dispatcher) {
        val provider = FakeProvider(WeatherResult.Failure(WeatherError.DNS))
        val vm = WeatherViewModel(provider, FakeCache(), { fixedNow })

        vm.submitTypedLocation("   ")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected InvalidLocation but was $state", state is WeatherUiState.InvalidLocation)
        assertEquals(
            "typing nonsense must not trigger a request",
            0,
            provider.callCount,
        )
    }

    @Test
    fun `typed location that is not in the preset list is refused honestly`() = runTest(dispatcher) {
        val provider = FakeProvider(WeatherResult.Failure(WeatherError.DNS))
        val vm = WeatherViewModel(provider, FakeCache(), { fixedNow })

        // Well-formed but not resolvable without a geocoder.
        vm.submitTypedLocation("Some Unknown Village")
        advanceUntilIdle()

        assertTrue(vm.state.value is WeatherUiState.InvalidLocation)
        assertEquals("no geocoder means no request", 0, provider.callCount)
    }

    @Test
    fun `typed location matching a preset loads it`() = runTest(dispatcher) {
        val provider = FakeProvider(WeatherResult.Success(snapshotAt("2026-10-11T12:00:00Z")))
        val vm = WeatherViewModel(provider, FakeCache(), { fixedNow })

        vm.submitTypedLocation("pune")
        advanceUntilIdle()

        assertEquals(1, provider.callCount)
        assertEquals(WeatherLocation.PRESETS.first { it.name == "Pune" }, vm.currentLocation())
    }

    @Test
    fun `selected location is remembered for restoration`() = runTest(dispatcher) {
        val vm = WeatherViewModel(
            FakeProvider(WeatherResult.Success(snapshotAt("2026-10-11T12:00:00Z"))),
            FakeCache(),
            { fixedNow },
        )
        vm.selectLocation(pune)
        advanceUntilIdle()
        assertNotNull(vm.currentLocation())
        assertEquals("Pune", vm.currentLocation()!!.name)
    }

    @Test
    fun `refresh with no selection does nothing`() = runTest(dispatcher) {
        val provider = FakeProvider(WeatherResult.Success(snapshotAt("2026-10-11T12:00:00Z")))
        val vm = WeatherViewModel(provider, FakeCache(), { fixedNow })
        vm.refresh()
        advanceUntilIdle()
        assertEquals(0, provider.callCount)
        assertNull(vm.currentLocation())
    }
}
