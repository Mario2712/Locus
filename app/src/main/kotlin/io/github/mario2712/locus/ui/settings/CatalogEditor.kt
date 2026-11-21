package io.github.mario2712.locus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.common.themedColor
import io.github.mario2712.locus.ui.editor.SwitchRow

/** Liste der Besuchskategorien mit Bearbeiten/Ausblenden und „Neue Kategorie“. */
@Composable
fun VisitCategoriesCard(categories: List<VisitCategory>, onSave: (VisitCategory) -> Unit, newId: () -> String) {
    var edit by remember { mutableStateOf<VisitCategory?>(null) }
    SectionCard(stringResource(R.string.catalog_visit_categories)) {
        Text(stringResource(R.string.catalog_visit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.forEach { category ->
            Row(Modifier.fillMaxWidth().clickable { edit = category }.padding(vertical = 8.dp)) {
                Text(category.name, Modifier.weight(1f), color = if (category.isArchived) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        if (!category.countsForCountryPoints) stringResource(R.string.catalog_no_points) else null,
                        if (category.isArchived) stringResource(R.string.catalog_hidden) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = {
            edit = VisitCategory(newId(), "", countsForCountryPoints = true, icon = "place", sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        }) { Text(stringResource(R.string.catalog_new_category)) }
    }
    edit?.let { category ->
        var name by remember(category.id) { mutableStateOf(category.name) }
        var counts by remember(category.id) { mutableStateOf(category.countsForCountryPoints) }
        var archived by remember(category.id) { mutableStateOf(category.isArchived) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(stringResource(R.string.catalog_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.catalog_name)) }, modifier = Modifier.fillMaxWidth())
                    SwitchRow(stringResource(R.string.catalog_counts), counts) { counts = it }
                    SwitchRow(stringResource(R.string.catalog_archived), archived) { archived = it }
                    Text(stringResource(R.string.catalog_archive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    onSave(category.copy(name = name.trim(), countsForCountryPoints = counts, isArchived = archived)); edit = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Liste der Aufenthaltsarten mit Name, Farbe, Gruppe, Länderpunkt-Regel und Ausblenden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StayTypesCard(types: List<StayType>, onSave: (StayType) -> Unit, newId: () -> String) {
    var edit by remember { mutableStateOf<StayType?>(null) }
    SectionCard(stringResource(R.string.catalog_stay_types)) {
        Text(stringResource(R.string.catalog_types_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        types.forEach { type ->
            Row(
                Modifier.fillMaxWidth().clickable { edit = type }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TypeDot(type.colorArgb)
                Text(type.name, Modifier.weight(1f), color = if (type.isArchived) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        groupLabel(type.group),
                        if (!type.countsForCountryPoints) stringResource(R.string.catalog_no_points) else null,
                        if (type.isArchived) stringResource(R.string.catalog_hidden) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = {
            val used = types.map { it.colorArgb }.toSet()
            edit = StayType(
                newId(), "", StayGroup.FREE, countsForCountryPoints = true, icon = "place",
                colorArgb = Defaults.stayTypePalette.firstOrNull { it !in used } ?: Defaults.stayTypePalette.last(),
                sortOrder = (types.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
        }) { Text(stringResource(R.string.catalog_new_type)) }
    }
    edit?.let { type ->
        var draft by remember(type.id) { mutableStateOf(type) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(stringResource(R.string.catalog_type)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, singleLine = true,
                        label = { Text(stringResource(R.string.catalog_name)) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.catalog_color), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Defaults.stayTypePalette.forEach { color ->
                            val selected = draft.colorArgb == color
                            Box(
                                Modifier.size(32.dp)
                                    .background(themedColor(color), CircleShape)
                                    .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                    .clickable { draft = draft.copy(colorArgb = color) },
                            )
                        }
                    }
                    Text(stringResource(R.string.catalog_group), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StayGroup.entries.forEach { group ->
                            FilterChip(draft.group == group, { draft = draft.copy(group = group) }, label = { Text(groupLabel(group)) })
                        }
                    }
                    SwitchRow(stringResource(R.string.catalog_counts), draft.countsForCountryPoints) { draft = draft.copy(countsForCountryPoints = it) }
                    SwitchRow(stringResource(R.string.catalog_archived), draft.isArchived) { draft = draft.copy(isArchived = it) }
                    Text(stringResource(R.string.catalog_archive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = draft.name.isNotBlank(), onClick = { onSave(draft.copy(name = draft.name.trim())); edit = null }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun groupLabel(group: StayGroup): String = stringResource(
    when (group) {
        StayGroup.WORK -> R.string.catalog_group_work
        StayGroup.FREE -> R.string.catalog_group_free
        StayGroup.VACATION -> R.string.catalog_group_vacation
        StayGroup.OTHER -> R.string.catalog_group_other
    },
)
