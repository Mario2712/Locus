package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.VisitCategory

/**
 * Stationen-Kette eines Tages: Basis am Morgen ▸ Stationen (mit Verkehrsmitteln) ▸ …
 * Antippen einer Station öffnet sie zum Bearbeiten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChain(
    startLabel: String,
    stations: List<StayItem>,
    onStationClick: ((StayItem) -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(startLabel, style = MaterialTheme.typography.bodyLarge)
        stations.forEach { item ->
            val transport = item.stay.transport.joinToString("") { it.emoji }
            Text(
                if (transport.isEmpty()) "▸" else "▸$transport▸",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = { onStationClick?.invoke(item) },
                enabled = onStationClick != null,
                label = {
                    Column {
                        Text("${item.typeName} · ${item.placeName}")
                        val extra = listOfNotNull(
                            item.stay.startTime?.let(Format::time),
                            if (item.stay.overnight) stringResource(R.string.chain_overnight) else null,
                        ).joinToString(" · ")
                        if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall)
                    }
                },
                leadingIcon = { TypeDot(item.typeColor) },
            )
        }
    }
}

/** Mehrfachauswahl der Verkehrsmittel (ohne Reihenfolge). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransportChips(selected: Set<TransportMode>, onChange: (Set<TransportMode>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TransportMode.entries.forEach { mode ->
            val isSelected = mode in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - mode else selected + mode) },
                label = { Text("${mode.emoji} ${mode.nameDe}") },
            )
        }
    }
}

/** Mehrfachauswahl der Besuchskategorien. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(categories: List<VisitCategory>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { category ->
            val isSelected = category.id in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - category.id else selected + category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

/** Zeile für einen Besuch: Ort · Kategorien · Personen. */
@Composable
fun VisitRow(item: VisitItem, onClick: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            label = { Text("📍 ${item.placeName} · ${item.categories}") },
        )
    }
}
