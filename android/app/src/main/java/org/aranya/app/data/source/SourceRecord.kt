package org.aranya.app.data.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A data source as shown in the app's Source Status screen.
 *
 * Mirrors one entry of data/sources.yml. The YAML file remains the authoritative record;
 * this is the on-device projection of it.
 */
@Serializable
data class SourceRecord(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("official_url") val officialUrl: String? = null,
    @SerialName("category") val category: String,
    @SerialName("geography") val geography: String,
    @SerialName("licence") val licence: String,
    @SerialName("attribution_required") val attributionRequired: Boolean,
    @SerialName("attribution_text") val attributionText: String? = null,
    @SerialName("status") val status: SourceStatus,
    @SerialName("last_success") val lastSuccess: String? = null,
    @SerialName("verified_on") val verifiedOn: String? = null,
    @SerialName("limitations") val limitations: String,
    @SerialName("fallback") val fallback: String? = null,
) {
    /**
     * The honest one-line claim for this source.
     *
     * Returns a string RESOURCE ID rather than prose, because this text reaches the
     * farmer and must be translatable. The status decides which wording is correct.
     */
    val claimRes: Int
        get() = when (status) {
            SourceStatus.VERIFIED -> org.aranya.app.R.string.sources_verified_detail
            SourceStatus.RESTRICTED -> org.aranya.app.R.string.sources_restricted_detail
            SourceStatus.INVESTIGATING -> org.aranya.app.R.string.sources_investigating_detail
            SourceStatus.UNAVAILABLE -> org.aranya.app.R.string.sources_unavailable_detail
            SourceStatus.NOT_INTEGRATED -> org.aranya.app.R.string.sources_not_integrated_detail
        }

    val statusLabelRes: Int
        get() = when (status) {
            SourceStatus.VERIFIED -> org.aranya.app.R.string.sources_verified
            SourceStatus.RESTRICTED -> org.aranya.app.R.string.sources_restricted
            SourceStatus.INVESTIGATING -> org.aranya.app.R.string.sources_investigating
            SourceStatus.UNAVAILABLE -> org.aranya.app.R.string.sources_unavailable
            SourceStatus.NOT_INTEGRATED -> org.aranya.app.R.string.sources_not_integrated
        }
}
