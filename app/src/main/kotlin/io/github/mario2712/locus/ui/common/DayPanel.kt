package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.ui.editor.JourneyDialog
import io.github.mario2712.locus.ui.editor.ReturnHomeDialog
import io.github.mario2712.locus.ui.editor.StationEditorDialog
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.VisitDialog
import io.github.mario2712.locus.ui.editor.VisitDraft

private enum class JourneyMode { START, CONTINUE }

/**
 * Herzstück der Erfassung: Basis ▸ Stationen-Kette, Vorschlag, „Weiter nach …“, Reise-Aktionen
 * und Besuche – für Heute-Screen und Tagesansicht.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPanel(state: DayState, viewModel: BaseDayViewModel) {
    var stationDraft by remember { mutableStateOf<StayDraft?>(null) }
    var visitDraft by remember { mutableStateOf<VisitDraft?>(null) }
    var journeyMode by remember { mutableStateOf<JourneyMode?>(null) }
    var showReturnHome by remember { mutableStateOf(false) }
    val editor = state.editor
    val homeName = editor.homebase?.displayName ?: stringResource(R.string.chain_home)

    // Reise-Kopf
    val tripBase = state.baseAtEnd ?: state.baseAtStart
    if (tripBase != null) {
        SectionCard(
            stringResource(
                R.string.trip_header,
                editor.placeName(tripBase.placeId),
                state.tripDay ?: 1,
            ),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    stationDraft = viewModel.newStationDraft(state, Defaults.StayTypeIds.EXCURSION)
                }) { Text(stringResource(R.string.trip_excursion)) }
                OutlinedButton(onClick = { journeyMode = JourneyMode.CONTINUE }) { Text(stringResource(R.string.trip_continue)) }
                if (editor.homebase != null) {
                    Button(onClick = { showReturnHome = true }) { Text(stringResource(R.string.trip_return_home)) }
                }
            }
        }
    }

    SectionCard(stringResource(R.string.day_chain_title)) {
        val start = state.baseAtStart?.let { "🧳 ${editor.placeName(it.placeId)}" } ?: "🏠 $homeName"
        DayChain(start, state.stations) { item -> stationDraft = StayDraft.from(item.stay) }

        val suggestion = state.suggestion
        if (suggestion != null) {
            Text(
                stringResource(R.string.day_suggestion_label, suggestion.templateName ?: editor.stepsLabel(suggestion.steps)),
                style = MaterialTheme.typography.titleSmall,
            )
            if (suggestion.templateName != null) {
                Text(editor.stepsLabel(suggestion.steps), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.confirmSuggestion(suggestion, state) }) { Text(stringResource(R.string.today_confirm)) }
            }
        }

        Text(
            stringResource(if (state.stations.isEmpty()) R.string.day_where_to else R.string.day_next),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.combos.forEach { combo ->
                AssistChip(
                    onClick = { viewModel.quickStation(combo, state) },
                    label = { Text("${editor.typeName(combo.stayTypeId)} · ${editor.placeName(combo.placeId)}") },
                    leadingIcon = { TypeDot(editor.typeColor(combo.stayTypeId)) },
                )
            }
            AssistChip(
                onClick = { stationDraft = viewModel.newStationDraft(state) },
                label = { Text(stringResource(R.string.day_other_station)) },
            )
            if (tripBase == null) {
                AssistChip(
                    onClick = { journeyMode = JourneyMode.START },
                    label = { Text(stringResource(R.string.trip_start)) },
                )
            }
        }
    }

    SectionCard(stringResource(R.string.day_visits_title)) {
        if (state.visits.isEmpty()) {
            Text(stringResource(R.string.day_visits_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            state.visits.forEach { item -> VisitRow(item) { visitDraft = VisitDraft.from(item.visit) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.recentVisitPlaces.forEach { placeId ->
                AssistChip(
                    onClick = { visitDraft = viewModel.newVisitDraft(state, placeId) },
                    label = { Text("+ ${editor.placeName(placeId)}") },
                )
            }
            AssistChip(
                onClick = { visitDraft = viewModel.newVisitDraft(state) },
                label = { Text(stringResource(R.string.day_add_visit)) },
            )
        }
    }

    if (state.otherStations.isNotEmpty()) {
        SectionCard(stringResource(R.string.day_others_title)) {
            state.otherStations.forEach { item ->
                StayRow(item, onClick = { stationDraft = StayDraft.from(item.stay) })
            }
        }
    }

    // Dialoge
    stationDraft?.let { draft ->
        StationEditorDialog(
            initial = draft,
            editor = editor,
            onDismiss = { stationDraft = null },
            onSave = { viewModel.saveStation(it); stationDraft = null },
            onDelete = draft.id?.let { id -> { viewModel.deleteStation(id); stationDraft = null } },
            onCreatePlace = viewModel::createPlace,
        )
    }
    visitDraft?.let { draft ->
        VisitDialog(
            initial = draft,
            editor = editor,
            onDismiss = { visitDraft = null },
            onSave = { viewModel.saveVisit(it); visitDraft = null },
            onDelete = draft.id?.let { id -> { viewModel.deleteVisit(id); visitDraft = null } },
            onCreatePlace = viewModel::createPlace,
        )
    }
    journeyMode?.let { mode ->
        JourneyDialog(
            title = stringResource(if (mode == JourneyMode.START) R.string.trip_start else R.string.trip_continue),
            editor = editor,
            initialTypeId = tripBase?.typeId,
            initialParticipants = if (mode == JourneyMode.START) editor.defaults.tripParticipants else viewModel.defaultParticipants(state),
            onDismiss = { journeyMode = null },
            onConfirm = { viewModel.journey(it); journeyMode = null },
            onCreatePlace = viewModel::createPlace,
        )
    }
    if (showReturnHome) {
        ReturnHomeDialog(
            homeName = homeName,
            initialTransport = viewModel.prefillTransport(tripBase?.placeId),
            onDismiss = { showReturnHome = false },
            onConfirm = { viewModel.returnHome(state, it); showReturnHome = false },
        )
    }

    if (editor.homebase == null) {
        Text(
            stringResource(R.string.day_no_homebase),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
