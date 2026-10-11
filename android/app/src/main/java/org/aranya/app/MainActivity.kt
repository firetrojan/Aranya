package org.aranya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.data.weather.OpenMeteoWeatherProvider
import org.aranya.app.data.weather.WeatherCache
import org.aranya.app.data.weather.defaultWeatherHttpClient
import org.aranya.app.ui.AranyaShell
import org.aranya.app.ui.theme.AranyaTheme
import org.aranya.app.ui.weather.WeatherViewModel

/**
 * The single Activity.
 *
 * Stage 02 has two destinations, shown with a flat switch rather than a navigation
 * library. Two screens do not justify routes, arguments and a back stack; that structure
 * earns its place when navigation actually nests or needs deep links.
 */
class MainActivity : ComponentActivity() {

    private val httpClient by lazy { defaultWeatherHttpClient() }

    private val weatherViewModel: WeatherViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T = WeatherViewModel(
                provider = OpenMeteoWeatherProvider(httpClient),
                cache = WeatherCache(applicationContext),
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Registry is bundled: no network call at startup, so a cold start works offline.
            val registry = remember { SourceRegistry.load(applicationContext) }
            val state by weatherViewModel.state.collectAsState()

            AranyaTheme {
                AranyaShell(
                    registry = registry,
                    weatherState = state,
                    onSelectLocation = weatherViewModel::selectLocation,
                    onSubmitTypedLocation = weatherViewModel::submitTypedLocation,
                    onRetryWeather = weatherViewModel::refresh,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            httpClient.close()
        }
    }
}
