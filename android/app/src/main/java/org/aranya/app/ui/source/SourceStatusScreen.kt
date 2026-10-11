package org.aranya.app.ui.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.aranya.app.R
import org.aranya.app.data.source.SourceRecord
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.data.source.SourceStatus
import org.aranya.app.data.source.sourceSummary
import org.aranya.app.ui.theme.AranyaSpacing
import org.aranya.app.ui.theme.AranyaTheme
import org.aranya.app.ui.theme.AranyaSize

/**
 * Source Status screen.
 *
 * Shows the real registry with honest statuses. This is genuinely useful information for
 * anyone deciding whether to trust Aranya, and it is the only screen Stage 01 ships that
 * displays data the project has actually verified.
 *
 * Design intent: a table, not a card grid. Status is carried by a coloured rail plus a
 * text label, so it does not depend on colour alone.
 */
@Composable
fun SourceStatusScreen(
    modifier: Modifier = Modifier,
    registry: SourceRegistry,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AranyaSpacing.lg,
            end = AranyaSpacing.lg,
            top = AranyaSpacing.sectionTop,
            bottom = AranyaSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.md),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = stringResource(R.string.sources_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(AranyaSpacing.sm))
                Text(
                    text = stringResource(R.string.sources_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(AranyaSize.maxParagraphWidth),
                )
                Spacer(Modifier.height(AranyaSpacing.md))
                Text(
                    text = sourceSummary(context, registry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AranyaSpacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (registry.sources.isEmpty()) {
            item(key = "empty") {
                EmptyRegistryState()
            }
        } else {
            items(registry.sources, key = { it.id }) { source ->
                SourceRow(source)
            }
        }
    }
}

@Composable
private fun SourceRow(source: SourceRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AranyaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        // Status rail. Colour is a secondary cue; the text label carries the meaning.
        Spacer(
            Modifier
                .width(AranyaSize.qualifierRailWidth)
                .height(44.dp)
                .background(statusColor(source.status), MaterialTheme.shapes.extraSmall),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AranyaSpacing.xxs),
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(source.statusLabelRes),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(source.status),
            )
            Text(
                text = stringResource(
                    source.claimRes,
                    source.verifiedOn ?: "",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AranyaSpacing.xxs))
            Text(
                text = source.limitations,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        AranyaTheme.semantics.explanatoryFill,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .padding(AranyaSpacing.sm),
            )
        }
    }
}

/**
 * Status colour.
 *
 * Deliberately avoids red-for-unavailable on its own: "unavailable" is often a temporary
 * network condition, and colouring it like an error misleads the user about severity.
 */
@Composable
private fun statusColor(status: SourceStatus): Color = when (status) {
    SourceStatus.VERIFIED -> AranyaTheme.semantics.positive
    SourceStatus.RESTRICTED -> MaterialTheme.colorScheme.secondary
    SourceStatus.INVESTIGATING -> AranyaTheme.semantics.rain
    SourceStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    SourceStatus.NOT_INTEGRATED -> MaterialTheme.colorScheme.outline
}

@Composable
private fun EmptyRegistryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AranyaSpacing.xl),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AranyaSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.state_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.sources_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Source status light", showBackground = true)
@Composable
private fun SourceStatusPreview() {
    AranyaTheme(darkTheme = false) {
        val preview = remember {
            SourceRegistry(
                listOf(
                    SourceRecord(
                        id = "nasa_power",
                        name = "NASA POWER",
                        category = "climate",
                        geography = "global_including_india",
                        licence = "NASA open use",
                        attributionRequired = true,
                        status = SourceStatus.VERIFIED,
                        verifiedOn = "2026-10-10",
                        limitations = "Satellite and reanalysis derived; monthly blocks lag.",
                    ),
                    SourceRecord(
                        id = "isric_soilgrids",
                        name = "ISRIC SoilGrids 2.0",
                        category = "soil",
                        geography = "global_including_india",
                        licence = "CC-BY 4.0",
                        attributionRequired = true,
                        status = SourceStatus.RESTRICTED,
                        verifiedOn = "2026-10-10",
                        limitations = "Numeric values currently return null; not for farm level use.",
                    ),
                ),
            )
        }
        SourceStatusScreen(registry = preview)
    }
}
