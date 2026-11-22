package io.github.mario2712.locus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.TransportChips
import java.time.LocalDate

private enum class PickerTarget { START_DATE, END_DATE, START_TIME, END_TIME }

/** Schalter-Zeile mit Beschriftung. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Station anlegen/bearbeiten. Wichtig sind Art, Ort und Verkehrsmittel; Datum, Uhrzeiten und
 * Notiz stecken unter „Details“ und sind optional.
 */
@Composable
fun StationEditorDialog(
    initial: StayDraft,
    editor: EditorData,
    onDismiss: () -> Unit,
    onSave: (StayDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onCreatePlace: suspend (PlaceDraft) -> Place,
    suggestedPlaceIds: List<String> = emptyList(),
) {
    var draft by remember { mutableStateOf(initial) }
    var showDetails by remember { mutableStateOf(initial.startTime != null || initial.endTime != null || initial.note.isNotEmpty()) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val noTime = stringResource(R.string.editor_no_time)

    io.github.mario2712.locus.ui.common.FormDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.isNew) R.string.editor_new_stay else R.string.editor_edit_stay)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(editor.activeStayTypes, draft.typeId) { typeId ->
                    draft = draft.copy(
                        typeId = typeId,
                        overnight = if (draft.isNew) editor.defaultOvernight(typeId) else draft.overnight,
                    )
                }
                Text(stringResource(R.string.editor_place), style = MaterialTheme.typography.labelLarge)
                PlaceField(
                    editor.sortedPlaces, draft.placeId, { draft = draft.copy(placeId = it) }, onCreatePlace,
                    suggestedIds = suggestedPlaceIds,
                )
                Text(stringResource(R.string.editor_transport), style = MaterialTheme.typography.labelLarge)
                TransportChips(draft.transport) { draft = draft.copy(transport = it) }
                SwitchRow(stringResource(R.string.editor_overnight), draft.overnight) { draft = draft.copy(overnight = it) }

                Text(stringResource(R.string.editor_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, draft.participants) { draft = draft.copy(participants = it) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showDetails, onCheckedChange = { showDetails = it })
                    Text(stringResource(R.string.editor_details))
                }
                if (showDetails) {
                    ValueButtonRow(stringResource(R.string.editor_from_date), Format.mediumDate(draft.startDate)) {
                        picker = PickerTarget.START_DATE
                    }
                    ValueButtonRow(stringResource(R.string.editor_to_date), Format.mediumDate(draft.endDate)) {
                        picker = PickerTarget.END_DATE
                    }
                    ValueButtonRow(stringResource(R.string.editor_start_time), formatOptionalTime(draft.startTime, noTime)) {
                        picker = PickerTarget.START_TIME
                    }
                    ValueButtonRow(stringResource(R.string.editor_end_time), formatOptionalTime(draft.endTime, noTime)) {
                        picker = PickerTarget.END_TIME
                    }
                    if (!draft.timesValid) {
                        Text(stringResource(R.string.editor_invalid_times), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = draft.note, onValueChange = { draft = draft.copy(note = it) },
                        label = { Text(stringResource(R.string.editor_note)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = draft.isValid, onClick = { onSave(draft) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    when (picker) {
        PickerTarget.START_DATE -> DatePickerDialogFor(draft.startDate, { picker = null }) { date ->
            draft = draft.copy(startDate = date, endDate = maxOf(date, draft.endDate)); picker = null
        }
        PickerTarget.END_DATE -> DatePickerDialogFor(draft.endDate, { picker = null }) { date ->
            draft = draft.copy(endDate = maxOf(date, draft.startDate)); picker = null
        }
        PickerTarget.START_TIME -> TimePickerDialog(
            draft.startTime, { picker = null },
            onConfirm = { draft = draft.copy(startTime = it); picker = null },
            onClear = { draft = draft.copy(startTime = null); picker = null },
        )
        PickerTarget.END_TIME -> TimePickerDialog(
            draft.endTime, { picker = null },
            onConfirm = { draft = draft.copy(endTime = it); picker = null },
            onClear = { draft = draft.copy(endTime = null); picker = null },
        )
        null -> Unit
    }
}

/** Eingaben für das Nachtragen mehrerer Tage. */
data class FillDraft(
    val typeId: String? = null,
    val placeId: String? = null,
    val skipWeekendsAndHolidays: Boolean = true,
)

@Composable
fun FillRangeDialog(
    from: LocalDate,
    to: LocalDate,
    editor: EditorData,
    onDismiss: () -> Unit,
    onConfirm: (FillDraft) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var draft by remember { mutableStateOf(FillDraft()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fill_title, Format.shortDate(from), Format.shortDate(to))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(editor.activeStayTypes, draft.typeId) { draft = draft.copy(typeId = it) }
                Text(stringResource(R.string.editor_place), style = MaterialTheme.typography.labelLarge)
                PlaceField(editor.sortedPlaces, draft.placeId, { draft = draft.copy(placeId = it) }, onCreatePlace)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.skipWeekendsAndHolidays,
                        onCheckedChange = { draft = draft.copy(skipWeekendsAndHolidays = it) },
                    )
                    Text(stringResource(R.string.fill_skip_weekends))
                }
                Text(
                    stringResource(R.string.fill_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = draft.typeId != null && draft.placeId != null, onClick = { onConfirm(draft) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
