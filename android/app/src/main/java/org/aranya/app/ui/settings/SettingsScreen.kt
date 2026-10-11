package org.aranya.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import org.aranya.app.R
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.data.settings.LANGUAGE_SYSTEM
import org.aranya.app.data.settings.SUPPORTED_LANGUAGES
import org.aranya.app.data.settings.toLanguageName
import org.aranya.app.ui.components.Rule
import org.aranya.app.ui.components.ScreenTitle
import org.aranya.app.ui.components.SectionLabel
import org.aranya.app.ui.theme.AranyaSize
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaTheme

/**
 * Settings screen.
 *
 * Holds the language override, which is the one preference that exists in Stage 02, plus
 * the data-sources summary. Every control here does something real; there is no decorative
 * toggle awaiting a feature.
 *
 * Language is infrastructure rather than a cosmetic dropdown: selecting a tag applies a
 * configuration override so the app renders in that locale without changing the phone's
 * system setting, and without a restart. Only locales that actually exist in the build are
 * offered.
 */
@Composable
fun SettingsScreen(
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    registry: SourceRegistry,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = AranyaSpacing.md,
                bottom = AranyaSpacing.xxl,
            ),
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.md),
    ) {
        ScreenTitle(
            title = stringResource(R.string.settings_title),
        )

        SectionLabel(stringResource(R.string.settings_language))
        Rule()

        LanguageOption(
            label = stringResource(R.string.settings_language_system),
            selected = languageTag == LANGUAGE_SYSTEM,
            onClick = { onLanguageChange(LANGUAGE_SYSTEM) },
        )
        Rule()
        SUPPORTED_LANGUAGES.forEach { tag ->
            LanguageOption(
                label = tag.toLanguageName(),
                selected = languageTag == tag,
                onClick = { onLanguageChange(tag) },
            )
            Rule()
        }

        Note(stringResource(R.string.settings_language_note))
        Note(stringResource(R.string.settings_language_coming))

        SectionLabel(stringResource(R.string.settings_units))
        Text(
            text = stringResource(R.string.settings_units_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel(stringResource(R.string.settings_about))
        Text(
            text = stringResource(R.string.sources_count, registry.usableCount, registry.sources.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * A single language choice.
 *
 * The whole row is the touch target, not just the radio button, and the row is at least
 * [AranyaSize.minTouchTarget] tall so it stays usable on a small screen.
 */
@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(vertical = AranyaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Locales shipped in this build live in `data/settings/LanguageSettings.kt`, so the
 * settings screen cannot offer a language the build does not contain.
 */

@Preview(name = "Settings light", showBackground = true, heightDp = 800)
@Composable
private fun SettingsPreview() {
    AranyaTheme(darkTheme = false) {
        SettingsScreen(
            languageTag = "system",
            onLanguageChange = {},
            registry = SourceRegistry(emptyList()),
        )
    }
}
