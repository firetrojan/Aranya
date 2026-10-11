package org.aranya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.aranya.app.R
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.data.weather.WeatherLocation
import org.aranya.app.ui.home.HomeScreen
import org.aranya.app.ui.settings.SettingsScreen
import org.aranya.app.ui.source.SourceStatusScreen
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaTheme
import org.aranya.app.ui.weather.WeatherScreen
import org.aranya.app.ui.weather.WeatherUiState

/**
 * Application shell.
 *
 * Four destinations, each of which does real work: Home summarises and navigates,
 * Weather fetches and displays a forecast, Sources reports the registry truthfully, and
 * Settings holds the language override. There are no placeholder destinations for
 * features that do not exist.
 *
 * Navigation is a flat `NavigationBar` rather than a navigation library. Four top-level
 * destinations with no nesting do not justify routes, arguments and a back stack yet, and
 * a flat bar is easier for a first-time user than a nested menu. When a feature genuinely
 * needs deep linking or a back stack, a library is introduced then.
 */
@Composable
fun AranyaShell(
    registry: SourceRegistry,
    weatherState: WeatherUiState,
    languageTag: String,
    onSelectLocation: (WeatherLocation) -> Unit,
    onSubmitTypedLocation: (String) -> Unit,
    onRefreshWeather: () -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialDestination: Destination = Destination.HOME,
) {
    // The selected destination is saved as an Int index, not as the enum itself.
    // rememberSaveable can only put primitives in a Bundle, and an enum is not one, so
    // storing it directly would crash on process death on older API levels.
    var destinationIndex by rememberSaveable { mutableStateOf(initialDestination.ordinal) }
    val destination = Destination.entries.getOrElse(destinationIndex) { Destination.HOME }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                // Flat, because elevation would introduce a shadow the rest of the
                // deliberately flat design avoids.
                tonalElevation = 0.dp,
            ) {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry,
                        onClick = { destinationIndex = entry.ordinal },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        // Each destination owns its scrolling, so the content area stays a plain Box.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = AranyaSpacing.lg),
        ) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    registry = registry,
                    weatherState = weatherState,
                    onOpenWeather = { destinationIndex = Destination.WEATHER },
                    onOpenSources = { destinationIndex = Destination.SOURCES },
                    onRefreshWeather = onRefreshWeather,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.WEATHER -> WeatherScreen(
                    state = weatherState,
                    onSelectLocation = onSelectLocation,
                    onSubmitTyped = onSubmitTypedLocation,
                    onRetry = onRefreshWeather,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.SOURCES -> SourceStatusScreen(
                    registry = registry,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.SETTINGS -> SettingsScreen(
                    languageTag = languageTag,
                    onLanguageChange = onLanguageChange,
                    registry = registry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}



/**
 * Top-level destinations.
 *
 * Icons come from `material-icons-core` only. The extended artifact costs several MB,
 * which conflicts with the size budget, so the set stays limited to core icons and is
 * checked by `data-pipeline/tests/verify_icons.py` against the real artifact contents.
 */
enum class Destination(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    WEATHER(R.string.weather_title, Icons.Filled.DateRange),
    SOURCES(R.string.sources_title, Icons.Filled.List),
    SETTINGS(R.string.settings_title, Icons.Filled.Settings),
}

@Preview(name = "Shell light", showBackground = true, heightDp = 880)
@Composable
private fun ShellPreviewLight() {
    AranyaTheme(darkTheme = false) {
        AranyaShell(
            registry = SourceRegistry(emptyList()),
            weatherState = WeatherUiState.NeedsLocation,
            languageTag = "en",
            onSelectLocation = {},
            onSubmitTypedLocation = {},
            onRefreshWeather = {},
            onLanguageChange = {},
        )
    }
}

@Preview(name = "Shell dark", showBackground = true, heightDp = 880)
@Composable
private fun ShellPreviewDark() {
    AranyaTheme(darkTheme = true) {
        AranyaShell(
            registry = SourceRegistry(emptyList()),
            weatherState = WeatherUiState.NeedsLocation,
            languageTag = "en",
            onSelectLocation = {},
            onSubmitTypedLocation = {},
            onRefreshWeather = {},
            onLanguageChange = {},
        )
    }
}
