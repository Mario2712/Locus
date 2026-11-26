package io.github.mario2712.locus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.editor.DatePickerDialogFor
import io.github.mario2712.locus.ui.editor.PlacePickerDialog
import java.time.LocalDate

/**
 * Homebase-Zeiträume: z. B. Wohnung bei den Eltern (von Anfang an), Wohnung 2 ab 05.02.2018, Wohnung 3 ab 08/2022.
 * Neuester Zeitraum oben.
 */
@Composable
fun HomebaseSection(
    editor: EditorData,
    today: LocalDate,
    onSetSingle: (String) -> Unit,
    onAdd: (String, LocalDate) -> Unit,
    onRemove: (String) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    val homes = editor.homebases
    var pickPlace by remember { mutableStateOf(false) }
    var movePlace by remember { mutableStateOf<String?>(null) }
    var editPeriod by remember { mutableStateOf<Homebases.Period?>(null) }

    Text(
        stringResource(R.string.settings_homebase_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (homes.periods.isEmpty()) {
        TextButton(onClick = { pickPlace = true }) { Text(stringResource(R.string.editor_choose_place)) }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            homes.periods.asReversed().forEach { period ->
                val name = editor.placesById[period.placeId]?.displayName ?: "?"
                Column(Modifier.fillMaxWidth().clickable { editPeriod = period }.padding(vertical = 6.dp)) {
                    Text("🏠 $name", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        periodLabel(period, homes.until(period)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = { pickPlace = true }) { Text(stringResource(R.string.homebase_add_move)) }
    }

    if (pickPlace) {
        PlacePickerDialog(
            places = editor.sortedPlaces,
            onDismiss = { pickPlace = false },
            onSelect = { id ->
                pickPlace = false
                if (homes.periods.isEmpty()) onSetSingle(id) else movePlace = id
            },
            onCreatePlace = onCreatePlace,
        )
    }
    movePlace?.let { id ->
        DatePickerDialogFor(today, onDismiss = { movePlace = null }) { date ->
            onAdd(id, date)
            movePlace = null
        }
    }
    editPeriod?.let { period ->
        HomebasePeriodDialog(
            name = editor.placesById[period.placeId]?.displayName ?: "?",
            period = period,
            canRemove = homes.periods.size > 1,
            onDismiss = { editPeriod = null },
            onChangeDate = { onAdd(period.placeId, it); editPeriod = null },
            onRemove = { onRemove(period.placeId); editPeriod = null },
        )
    }
}

@Composable
private fun periodLabel(period: Homebases.Period, until: LocalDate?): String {
    val fromStart = period.since == UNKNOWN_DATE
    return when {
        until == null && fromStart -> stringResource(R.string.homebase_always)
        until == null -> stringResource(R.string.homebase_since_current, Format.mediumDate(period.since))
        fromStart -> stringResource(R.string.homebase_until, Format.mediumDate(until))
        else -> "${Format.mediumDate(period.since)} – ${Format.mediumDate(until)}"
    }
}

@Composable
private fun HomebasePeriodDialog(
    name: String,
    period: Homebases.Period,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onChangeDate: (LocalDate) -> Unit,
    onRemove: () -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏠 $name") },
        text = {
            Column {
                Text(
                    if (period.since == UNKNOWN_DATE) {
                        stringResource(R.string.homebase_from_start)
                    } else {
                        stringResource(R.string.homebase_since, Format.mediumDate(period.since))
                    },
                )
                TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.homebase_change_date)) }
                if (period.since != UNKNOWN_DATE) {
                    TextButton(onClick = { onChangeDate(UNKNOWN_DATE) }) { Text(stringResource(R.string.homebase_from_start)) }
                }
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.homebase_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
    if (pickDate) {
        DatePickerDialogFor(
            if (period.since == UNKNOWN_DATE) LocalDate.now() else period.since,
            onDismiss = { pickDate = false },
        ) { onChangeDate(it); pickDate = false }
    }
}
