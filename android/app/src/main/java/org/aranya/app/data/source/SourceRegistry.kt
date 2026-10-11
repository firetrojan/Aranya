package org.aranya.app.data.source

import android.content.Context
import kotlinx.serialization.json.Json
import org.aranya.app.R

/**
 * Loads the bundled copy of data/sources.yml into the app.
 *
 * The registry ships as a pre-parsed JSON asset generated from the YAML at build time in
 * a later stage. Stage 01 ships a small generated list so the Source Status screen shows
 * real registry data rather than a placeholder, with no network call at startup.
 *
 * Note on honesty: this list is the registry's own content. If a source is not verified
 * here, the app says so. It never invents a status.
 *
 * The constructor is `internal` rather than private so Compose previews can build a small
 * registry without the Android asset loader, which a preview cannot access. Production code
 * should still obtain one via [load].
 */
class SourceRegistry internal constructor(
    val sources: List<SourceRecord>,
) {
    val usableCount: Int get() = sources.count { it.status.isUsable }

    fun byId(id: String): SourceRecord? = sources.firstOrNull { it.id == id }

    fun byStatus(status: SourceStatus): List<SourceRecord> = sources.filter { it.status == status }

    companion object {
        /**
         * Parses the bundled registry snapshot.
         *
         * If the asset is missing or malformed this returns an empty registry rather than
         * throwing, because the shell must still start. An empty registry renders as an
         * explicit empty state, never as a fake list.
         */
        fun load(context: Context): SourceRegistry {
            val raw = runCatching {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return SourceRegistry(emptyList())

            val parsed = runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<SourceRecord>>(raw)
            }.getOrNull() ?: return SourceRegistry(emptyList())

            return SourceRegistry(parsed)
        }

        private const val ASSET_NAME = "sources_snapshot.json"
    }
}

/** Human-readable summary shown under the source list. */
fun sourceSummary(context: Context, registry: SourceRegistry): String {
    val verified = registry.byStatus(SourceStatus.VERIFIED).size
    val restricted = registry.byStatus(SourceStatus.RESTRICTED).size
    val unusable = registry.sources.size - verified - restricted
    return context.getString(
        R.string.format_sources_summary, verified, restricted, unusable.coerceAtLeast(0),
    )
}
