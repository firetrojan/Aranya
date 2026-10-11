package org.aranya.app.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces Aranya's localization and payload rules at the source level.
 *
 * Stage 01 states that user-facing strings must not be hardcoded. That is easy to assert
 * and easy to violate six months later, so it is checked mechanically here rather than
 * trusted. This is a host-side test: it reads source files and needs no device.
 */
class SourcePolicyTest {

    private val repoRoot: File = runCatching {
        // Gradle runs this with the module directory as the working directory.
        File("src/main/java")
    }.getOrElse { File("src/main/java") }

    private fun kotlinSources(): List<File> =
        if (!repoRoot.exists()) emptyList()
        else repoRoot.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun xmlResources(): List<File> {
        val resDir = File("src/main/res")
        if (!resDir.exists()) return emptyList()
        return resDir.walkTopDown().filter { it.name.endsWith(".xml") }.toList()
    }

    @Test
    fun userFacingStringsAreNotHardcodedInKotlin() {
        val offenders = mutableListOf<String>()

        for (file in kotlinSources()) {
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                // Ignore comments and documentation, which may legitimately quote English.
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                // A Text( call with a bare string literal is a hardcoded user-facing string.
                val quoted = Regex("\"([^\"\\\\]|\\\\.){4,}\"").find(trimmed)?.value ?: return@forEachIndexed
                val textCall = trimmed.contains("Text(") ||
                    trimmed.contains("contentDescription =") ||
                    trimmed.contains("semantics {") ||
                    trimmed.contains("title =")
                if (textCall && quoted.startsWith("\"")) {
                    offenders += "${file.name}:${index + 1}: $trimmed"
                }
            }
        }

        assertTrue(
            "User-facing text must come from string resources, not literals:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun noDynamicColourIsUsed() {
        // dynamicLightColorScheme / dynamicDarkColorScheme would break the fixed-palette
        // requirement. This is the single most important rule in the design system.
        val offenders = mutableListOf<String>()
        for (file in kotlinSources()) {
            val text = file.readText()
            if (text.contains("dynamicLightColorScheme") || text.contains("dynamicDarkColorScheme")) {
                offenders += file.name
            }
        }
        assertTrue(
            "Aranya must use fixed authored palettes, not Material You dynamic colour. " +
                "Offending files: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun noAnalyticsOrAdvertisingLibrariesArePresent() {
        val offenders = mutableListOf<String>()
        val gradle = File("build.gradle.kts")
        if (gradle.exists()) {
            val text = gradle.readText().lowercase()
            val banned = listOf(
                "firebase", "crashlytics", "admob", "appsflyer", "adjust", "branch.io",
                "amplitude", "mixpanel", "segment", "facebook", "google-services",
                "play-services-location", "play-services-maps", "mapbox",
            )
            for (token in banned) {
                if (text.contains(token)) offenders += token
            }
        }
        assertTrue(
            "Stage 01 forbids analytics, advertising and commercial map SDKs. Found: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun noApiKeysAreCommitted() {
        // Secrets belong in the user's device or an opt-in config, never the APK.
        val offenders = mutableListOf<String>()
        val pattern = Regex(
            """(api[_-]?key|apikey|secret|token|client[_-]?secret)\s*[:=]\s*["'][A-Za-z0-9_\-]{16,}["']""",
            RegexOption.IGNORE_CASE,
        )
        for (file in kotlinSources()) {
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) return@forEachIndexed
                if (pattern.containsMatchIn(line)) {
                    offenders += "${file.name}:${index + 1}"
                }
            }
        }
        assertTrue(
            "No API keys or secrets may be committed. Offending lines: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun shippedXmlResourcesCarryOnlyEnglish() {
        // Adding values-hi would enlarge the APK silently. A second language is a
        // deliberate stage decision, not an accident.
        //
        // `values-night` is a dark-theme configuration qualifier, not a locale, so it is
        // expected and must not be flagged.
        val nonLocale = setOf("values", "values-night", "values-day", "values-v26", "values-v31")
        val offenders = xmlResources()
            .map { it.parentFile?.name ?: "" }
            .filter { it.startsWith("values") && it !in nonLocale }
        assertTrue(
            "Stage 01 ships English only; found extra locale resource dirs: $offenders",
            offenders.isEmpty(),
        )
    }
}
