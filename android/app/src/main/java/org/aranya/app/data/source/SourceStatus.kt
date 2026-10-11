package org.aranya.app.data.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kotlin mirror of the statuses in data/sources.yml.
 *
 * These must stay in step with `data-pipeline/sources/registry.py` STATUSES. The registry
 * test suite is the single source of truth; this enum exists so the app cannot display a
 * status the pipeline does not recognize.
 */
@Serializable
enum class SourceStatus {
    /** Reached, licence read, response shape confirmed. */
    @SerialName("verified")
    VERIFIED,

    /** Reachable, but terms or parameters unconfirmed. */
    @SerialName("investigating")
    INVESTIGATING,

    /** Needs registration, a key, a paid tier, or a negotiated agreement. */
    @SerialName("restricted")
    RESTRICTED,

    /** Probed and failed. */
    @SerialName("unavailable")
    UNAVAILABLE,

    /** Registered for later; access deliberately not assessed. */
    @SerialName("not_integrated")
    NOT_INTEGRATED,
    ;

    /**
     * Whether this source may back a feature the user relies on.
     *
     * Only VERIFIED qualifies. This is what stops the UI from presenting an unimplemented
     * or unconfirmed source as if it were working.
     */
    val isUsable: Boolean get() = this == VERIFIED
}
