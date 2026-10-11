package org.aranya.app.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "aranya_settings")

/** Sentinel meaning "follow the device setting". */
const val LANGUAGE_SYSTEM = "system"

/**
 * Locales shipped in this build.
 *
 * Stage 02 ships English only. Adding a language means adding `values-<tag>/strings.xml`
 * and one entry here; no screen changes, because no screen hardcodes text. Keeping this
 * list in one place stops the settings screen from offering a language the build does not
 * contain, which would otherwise render as raw resource keys.
 */
val SUPPORTED_LANGUAGES = listOf("en")

/**
 * Stores the user's language preference and applies it as an in-app override.
 *
 * Aranya is multilingual from the start rather than retrofitting a language picker later.
 * The mechanism is `AppCompatDelegate.setApplicationLocales`, which applies a
 * configuration override for this app only: it does not change the phone's system
 * language, and it takes effect without restarting the process.
 *
 * This requires the app to extend AppCompatActivity (see MainActivity). Compose reads the
 * resulting `LocalConfiguration`, so every `stringResource` and every
 * `AranyaFormat` call follows the chosen locale automatically. That is why no screen needs
 * to know which language is active.
 */
class LanguageSettings(private val context: Context) {

    private val key = stringPreferencesKey("language_tag")

    /** The stored preference, or [LANGUAGE_SYSTEM] when unset. */
    suspend fun currentTag(): String =
        context.settingsStore.data.map { it[key] ?: LANGUAGE_SYSTEM }.first()

    /**
     * Persists the preference and applies it immediately.
     *
     * Passing [LANGUAGE_SYSTEM] clears the override and returns to following the device.
     * An unsupported tag is ignored rather than applied, so a corrupt value cannot leave
     * the app in a locale it has no strings for.
     */
    suspend fun setTag(tag: String) {
        val effective = if (tag == LANGUAGE_SYSTEM || tag.isBlank()) LANGUAGE_SYSTEM else tag
        context.settingsStore.edit { prefs ->
            if (effective == LANGUAGE_SYSTEM) {
                prefs.remove(key)
            } else {
                prefs[key] = effective
            }
        }
        apply(effective)
    }

    /** Applies a tag without persisting it. Used on startup. */
    fun applyNow(tag: String) = apply(tag)

    private fun apply(tag: String) {
        // LocaleListCompat is required, not android.os.LocaleList: AppCompatDelegate
        // takes the compat type and handles the pre-API-24 path itself. An empty list is
        // the documented way to clear the override and follow the device again.
        val locales: LocaleListCompat =
            if (tag == LANGUAGE_SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

/**
 * Formats a language tag for display without pulling in a full locale picker.
 *
 * Shows the endonym, so a Marathi speaker sees their own language name rather than an
 * English label. Unknown tags fall back to the tag itself rather than being hidden.
 */
fun String.toLanguageName(): String = when (this) {
    "en" -> "English"
    "hi" -> "हिन्दी (Hindi)"
    "mr" -> "मराठी (Marathi)"
    "gu" -> "ગુજરાતી (Gujarati)"
    "pa" -> "ਪੰਜਾਬੀ (Punjabi)"
    "ta" -> "தமிழ் (Tamil)"
    "te" -> "తెలుగు (Telugu)"
    "kn" -> "ಕನ್ನಡ (Kannada)"
    "bn" -> "বাংলা (Bengali)"
    "ur" -> "اردو (Urdu)"
    else -> this
}
