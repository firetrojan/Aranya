package org.aranya.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch
import org.aranya.app.data.settings.LANGUAGE_SYSTEM
import org.aranya.app.data.settings.LanguageSettings
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
 * Extends AppCompatActivity rather than ComponentActivity purely for
 * `AppCompatDelegate.setApplicationLocales`, which powers the in-app language override.
 * That override applies a configuration locale for this app only, so every
 * `stringResource` and every locale-aware format call follows the user's choice without
 * changing the phone's system setting and without a restart.
 *
 * Four top-level destinations are switched directly. Four non-nested destinations do not
 * justify a navigation library yet.
 */
class MainActivity : AppCompatActivity() {

    private val httpClient by lazy { defaultWeatherHttpClient() }
    private val languageSettings by lazy { LanguageSettings(applicationContext) }

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
            // Bundled registry: no network call at startup, so a cold start works offline.
            val registry = remember { SourceRegistry.load(applicationContext) }
            val weatherState by weatherViewModel.state.collectAsState()
            val scope = rememberCoroutineScope()

            var languageTag by remember { mutableStateOf(LANGUAGE_SYSTEM) }
            // Apply the stored preference before the first composition of the UI.
            remember {
                scope.launch {
                    languageTag = languageSettings.currentTag()
                    languageSettings.applyNow(languageTag)
                }
            }

            AranyaTheme {
                AranyaShell(
                    registry = registry,
                    weatherState = weatherState,
                    languageTag = languageTag,
                    onSelectLocation = weatherViewModel::selectLocation,
                    onSubmitTypedLocation = weatherViewModel::submitTypedLocation,
                    onRefreshWeather = weatherViewModel::refresh,
                    onLanguageChange = { tag ->
                        languageTag = tag
                        scope.launch { languageSettings.setTag(tag) }
                    },
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
