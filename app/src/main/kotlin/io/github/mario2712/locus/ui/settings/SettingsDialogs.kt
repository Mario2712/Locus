package io.github.mario2712.locus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.TransportChips
import io.github.mario2712.locus.ui.editor.PlaceField
import io.github.mario2712.locus.ui.editor.SwitchRow
import io.github.mario2712.locus.ui.editor.TypeChips
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Muster für einen Wochentag: Tagesvorlage oder einzelne Station. */
@Composable
fun RuleDialog(
    day: DayOfWeek,
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onTemplate: (DayTemplate) -> Unit,
    onSingle: (String, String) -> Unit,
    onClear: () -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    val editor = state.editor
    val existing = state.rules[day]
    var templateId by remember { mutableStateOf(existing?.templateId) }
    var typeId by remember { mutableStateOf(existing?.takeIf { it.templateId == null }?.stayTypeId) }
    var placeId by remember { mutableStateOf(existing?.takeIf { it.templateId == null }?.placeId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.getDisplayName(TextStyle.FULL, Locale.GERMAN)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.templates.isNotEmpty()) {
                    Text(stringResource(R.string.settings_rule_template), style = MaterialTheme.typography.labelLarge)
                    editor.templates.forEach { template ->
                        Row(
                            Modifier.fillMaxWidth().clickable { templateId = template.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = templateId == template.id, onClick = { templateId = template.id })
                            Column {
                                Text(template.name)
                                Text(editor.stepsLabel(template.steps), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { templateId = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = templateId == null, onClick = { templateId = null })
                    Text(stringResource(R.string.settings_rule_single))
                }
                if (templateId == null) {
                    TypeChips(editor.activeStayTypes, typeId) { typeId = it }
                    PlaceField(editor.sortedPlaces, placeId, { placeId = it }, onCreatePlace)
                }
                if (existing != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.settings_remove_rule), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            val template = templateId?.let { editor.templatesById[it] }
            val t = typeId
            val p = placeId
            TextButton(
                enabled = template != null || (templateId == null && t != null && p != null),
                onClick = {
                    when {
                        template != null -> onTemplate(template)
                        t != null && p != null -> onSingle(t, p)
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Tagesvorlage anlegen/bearbeiten: Name und Schritte (Art · Ort · Verkehrsmittel). */
@Composable
fun TemplateDialog(
    initial: DayTemplate?,
    editor: EditorData,
    newId: () -> String,
    onDismiss: () -> Unit,
    onSave: (DayTemplate) -> Unit,
    onDelete: (() -> Unit)?,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var steps by remember { mutableStateOf(initial?.steps.orEmpty()) }
    var editStep by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.settings_template_new else R.string.settings_template_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.settings_template_name)) }, modifier = Modifier.fillMaxWidth(),
                )
                steps.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}. ${editor.stepLabel(step)} ${step.transport.joinToString("") { it.emoji }}",
                            Modifier.weight(1f).clickable { editStep = index }.padding(vertical = 6.dp),
                        )
                        TextButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                TextButton(onClick = { editStep = steps.size }) { Text(stringResource(R.string.settings_template_add_step)) }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && steps.isNotEmpty(),
                onClick = { onSave(DayTemplate(initial?.id ?: newId(), name.trim(), steps, initial?.sortOrder ?: editor.templates.size)) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    editStep?.let { index ->
        StepDialog(
            initial = steps.getOrNull(index),
            editor = editor,
            onDismiss = { editStep = null },
            onSave = { step ->
                steps = if (index < steps.size) steps.toMutableList().also { it[index] = step } else steps + step
                editStep = null
            },
            onCreatePlace = onCreatePlace,
        )
    }
}

@Composable
private fun StepDialog(
    initial: TemplateStep?,
    editor: EditorData,
    onDismiss: () -> Unit,
    onSave: (TemplateStep) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var typeId by remember { mutableStateOf(initial?.stayTypeId) }
    var placeId by remember { mutableStateOf(initial?.placeId) }
    var transport by remember { mutableStateOf(initial?.transport.orEmpty()) }
    var overnight by remember { mutableStateOf(initial?.overnight ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_template_step)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(editor.activeStayTypes, typeId) { typeId = it }
                Text(stringResource(R.string.editor_place), style = MaterialTheme.typography.labelLarge)
                PlaceField(editor.sortedPlaces, placeId, { placeId = it }, onCreatePlace)
                Text(stringResource(R.string.editor_transport), style = MaterialTheme.typography.labelLarge)
                TransportChips(transport) { transport = it }
                SwitchRow(stringResource(R.string.editor_overnight), overnight) { overnight = it }
            }
        },
        confirmButton = {
            val t = typeId
            val p = placeId
            TextButton(enabled = t != null && p != null, onClick = {
                if (t != null && p != null) onSave(TemplateStep(t, p, transport, overnight))
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
