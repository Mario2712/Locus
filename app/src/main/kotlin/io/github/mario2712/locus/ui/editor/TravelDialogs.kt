package io.github.mario2712.locus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.usecase.JourneyLeg
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.TransportChips

/** Teilstück im Reise-Dialog. */
data class LegDraft(
    val placeId: String? = null,
    val transport: Set<TransportMode> = emptySet(),
    val nextDay: Boolean = false,
)

/** Ergebnis des Reise-Dialogs. */
data class JourneyDraft(
    val typeId: String,
    val legs: List<JourneyLeg>,
    val participants: Set<PersonId>,
)

/**
 * „Reise starten“ bzw. „Weiterreise“: Ziel mit Art und Verkehrsmittel, optional Zwischenstopps
 * (Abflughafen, Umstieg), jeweils mit „Ankunft am Folgetag“.
 */
@Composable
fun JourneyDialog(
    title: String,
    editor: EditorData,
    initialTypeId: String?,
    initialParticipants: Set<PersonId>,
    onDismiss: () -> Unit,
    onConfirm: (JourneyDraft) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var typeId by remember { mutableStateOf(initialTypeId ?: editor.tripTypes.firstOrNull()?.id) }
    var destination by remember { mutableStateOf(LegDraft()) }
    var stops by remember { mutableStateOf(listOf<LegDraft>()) }
    var participants by remember { mutableStateOf(initialParticipants) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(editor.tripTypes, typeId) { typeId = it }

                stops.forEachIndexed { index, stop ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.journey_stop, index + 1), style = MaterialTheme.typography.labelLarge)
                            LegFields(stop, editor, onCreatePlace) { updated ->
                                stops = stops.toMutableList().also { it[index] = updated }
                            }
                            TextButton(onClick = { stops = stops.filterIndexed { i, _ -> i != index } }) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
                TextButton(onClick = { stops = stops + LegDraft(nextDay = stops.lastOrNull()?.nextDay ?: false) }) {
                    Text(stringResource(R.string.journey_add_stop))
                }

                Text(stringResource(R.string.journey_destination), style = MaterialTheme.typography.labelLarge)
                LegFields(destination, editor, onCreatePlace) { destination = it }

                Text(stringResource(R.string.editor_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, participants) { participants = it }
                Text(
                    stringResource(R.string.journey_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            val type = typeId
            val valid = type != null && destination.placeId != null && stops.all { it.placeId != null } && participants.isNotEmpty()
            TextButton(enabled = valid, onClick = {
                if (type != null) {
                    // „Folgetag“ wirkt kumulativ: jedes markierte Teilstück verschiebt um einen Tag.
                    var offset = 0
                    val legs = (stops + destination).map { leg ->
                        if (leg.nextDay) offset++
                        JourneyLeg(requireNotNull(leg.placeId), leg.transport, offset)
                    }
                    onConfirm(JourneyDraft(type, legs, participants))
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LegFields(
    leg: LegDraft,
    editor: EditorData,
    onCreatePlace: suspend (PlaceDraft) -> Place,
    onChange: (LegDraft) -> Unit,
) {
    PlaceField(editor.sortedPlaces, leg.placeId, { onChange(leg.copy(placeId = it)) }, onCreatePlace)
    TransportChips(leg.transport) { onChange(leg.copy(transport = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = leg.nextDay, onCheckedChange = { onChange(leg.copy(nextDay = it)) })
        Text(stringResource(R.string.journey_next_day))
    }
}

/** Ergebnis des Heimreise-Dialogs: Zwischenstopps (Transit) und Ankunft zuhause, Tage relativ zum Abreisetag. */
data class ReturnDraft(
    val stops: List<JourneyLeg>,
    val homeTransport: Set<TransportMode>,
    val homeDayOffset: Int,
)

/**
 * Heimreise: Verkehrsmittel nach Hause, optional Zwischenstopps (Umstieg, Landung in München …),
 * jeweils mit „Ankunft am Folgetag“ (kumulativ wie beim Reise-Dialog).
 */
@Composable
fun ReturnHomeDialog(
    homeName: String,
    initialTransport: Set<TransportMode>,
    editor: EditorData,
    onDismiss: () -> Unit,
    onConfirm: (ReturnDraft) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var stops by remember { mutableStateOf(listOf<LegDraft>()) }
    var home by remember { mutableStateOf(LegDraft(transport = initialTransport)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.return_home_title, homeName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stops.forEachIndexed { index, stop ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.journey_stop, index + 1), style = MaterialTheme.typography.labelLarge)
                            LegFields(stop, editor, onCreatePlace) { updated ->
                                stops = stops.toMutableList().also { it[index] = updated }
                            }
                            TextButton(onClick = { stops = stops.filterIndexed { i, _ -> i != index } }) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
                TextButton(onClick = { stops = stops + LegDraft() }) { Text(stringResource(R.string.journey_add_stop)) }
                Text(stringResource(R.string.return_home_arrival, homeName), style = MaterialTheme.typography.labelLarge)
                TransportChips(home.transport) { home = home.copy(transport = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = home.nextDay, onCheckedChange = { home = home.copy(nextDay = it) },
                        modifier = Modifier.testTag("home-next-day"),
                    )
                    Text(stringResource(R.string.journey_next_day))
                }
                Text(
                    stringResource(R.string.return_home_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = stops.all { it.placeId != null }, onClick = {
                var offset = 0
                val legs = stops.map { leg ->
                    if (leg.nextDay) offset++
                    JourneyLeg(requireNotNull(leg.placeId), leg.transport, offset)
                }
                if (home.nextDay) offset++
                onConfirm(ReturnDraft(legs, home.transport, offset))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
