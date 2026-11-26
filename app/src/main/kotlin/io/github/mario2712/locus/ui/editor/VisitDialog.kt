package io.github.mario2712.locus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.ui.common.CategoryChips
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft

/** Besuch erfassen/bearbeiten: Ort, Kategorien (mehrere), Personen, Länderpunkt-Schalter. */
@Composable
fun VisitDialog(
    initial: VisitDraft,
    editor: EditorData,
    onDismiss: () -> Unit,
    onSave: (VisitDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onCreatePlace: suspend (PlaceDraft) -> Place,
    suggestedPlaceIds: List<String> = emptyList(),
    anchorPlaceIds: List<String> = emptyList(),
) {
    var draft by remember { mutableStateOf(initial) }
    val byCategory = editor.categoriesCount(draft.categoryIds)
    val counts = draft.countsOverride ?: byCategory

    io.github.mario2712.locus.ui.common.FormDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.isNew) R.string.visit_new else R.string.visit_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_place), style = MaterialTheme.typography.labelLarge)
                PlaceField(
                    editor.sortedPlaces, draft.placeId, { draft = draft.copy(placeId = it) }, onCreatePlace,
                    preferLocations = true, suggestedIds = suggestedPlaceIds,
                    anchorIds = (listOfNotNull(initial.placeId) + anchorPlaceIds).distinct(),
                )
                if (!initial.isNew) io.github.mario2712.locus.ui.common.OpenPlaceRecordButton(initial.placeId, onDismiss)
                Text(stringResource(R.string.visit_categories), style = MaterialTheme.typography.labelLarge)
                CategoryChips(editor.activeCategories, draft.categoryIds) { draft = draft.copy(categoryIds = it) }
                SwitchRow(stringResource(R.string.visit_counts), counts) { value ->
                    // Override nur speichern, wenn er von der Kategorie-Regel abweicht.
                    draft = draft.copy(countsOverride = value.takeIf { it != byCategory })
                }
                Text(stringResource(R.string.editor_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, draft.participants) { draft = draft.copy(participants = it) }
                OutlinedTextField(
                    value = draft.note, onValueChange = { draft = draft.copy(note = it) },
                    label = { Text(stringResource(R.string.editor_note)) }, modifier = Modifier.fillMaxWidth(),
                )
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
}
