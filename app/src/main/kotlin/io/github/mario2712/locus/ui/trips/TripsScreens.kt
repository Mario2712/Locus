package io.github.mario2712.locus.ui.trips

import io.github.mario2712.locus.domain.trips.TripWorkFilter
import io.github.mario2712.locus.domain.trips.TripPersonFilter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.trips.TripSummary
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.DatePickerDialogFor
import io.github.mario2712.locus.ui.editor.ParticipantChips
import io.github.mario2712.locus.ui.editor.PlaceField
import io.github.mario2712.locus.ui.editor.TypeChips
import io.github.mario2712.locus.ui.editor.ValueButtonRow
import io.github.mario2712.locus.ui.places.BackScaffold
import io.github.mario2712.locus.ui.timeline.TimelineDayRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Eigener Name der Reise oder automatischer Name aus den Städten. */
fun tripTitle(trip: TripSummary, editor: EditorData): String =
    editor.tripNames[trip.id] ?: io.github.mario2712.locus.domain.trips.Trips.defaultTitle(trip, editor.placesById)

fun tripDates(trip: TripSummary): String = when (trip.precision) {
    DatePrecision.YEAR -> trip.start.year.toString()
    DatePrecision.UNKNOWN -> "?"
    DatePrecision.MONTH -> Format.month(trip.start)
    DatePrecision.DAY -> if (trip.start == trip.end) Format.fullDate(trip.start) else "${Format.shortDate(trip.start)}–${Format.fullDate(trip.end)}"
}

@Composable
fun TripsScreen(onBack: () -> Unit, onOpenTrip: (String) -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addPast by remember { mutableStateOf(false) }
    BackScaffold(stringResource(R.string.trips_title), onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Button(onClick = { addPast = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.trips_add_past))
                }
            }
            item { TripFilters(state, viewModel) }
            if (state.trips.isEmpty()) item { Text(stringResource(R.string.trips_empty)) }
            if (state.trips.isNotEmpty() && state.visibleTrips.isEmpty()) item { Text(stringResource(R.string.trips_filter_empty)) }
            state.visibleTrips.groupBy { it.start.year }.forEach { (year, trips) ->
                item {
                    Text(
                        stringResource(R.string.trips_year, year, trips.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(trips, key = { it.id }) { trip ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenTrip(trip.id) }.padding(vertical = 10.dp)) {
                        Text(
                            trip.countryIso2s.mapNotNull { ReferenceData.country(it)?.flag }.joinToString("") + " " + tripTitle(trip, state.editor),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            listOfNotNull(
                                tripDates(trip),
                                if (trip.precision == DatePrecision.DAY) stringResource(R.string.trips_days, trip.days) else null,
                                if (trip.ongoing) stringResource(R.string.trips_ongoing) else null,
                                state.editor.participantsLabel(trip.participants),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (addPast) {
        PastTripDialog(state.editor, viewModel, onDismiss = { addPast = false })
    }
}

/** Filter: Arbeit/Privat und Mitreisende. */
@Composable
private fun TripFilters(state: TripsUiState, viewModel: TripsViewModel) {
    val others = state.editor.persons.filterNot { it.isMe }
    var yearMenu by remember { mutableStateOf(false) }
    val years = state.trips.flatMap { listOf(it.start.year, it.end.year) }.distinct().sortedDescending()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box {
                FilterChip(state.yearFilter != null, { yearMenu = true }, label = {
                    Text(state.yearFilter?.toString() ?: stringResource(R.string.map_all_years))
                })
                androidx.compose.material3.DropdownMenu(yearMenu, { yearMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.map_all_years)) },
                        onClick = { viewModel.setYearFilter(null); yearMenu = false })
                    years.forEach { y ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(y.toString()) },
                            onClick = { viewModel.setYearFilter(y); yearMenu = false })
                    }
                }
            }
            listOf(
                TripWorkFilter.ALL to R.string.trips_filter_all,
                TripWorkFilter.PRIVATE to R.string.trips_filter_private,
                TripWorkFilter.WORK to R.string.trips_filter_work,
            ).forEach { (value, label) ->
                FilterChip(state.workFilter == value, { viewModel.setWorkFilter(value) }, label = { Text(stringResource(label)) })
            }
        }
        if (others.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.personFilter == TripPersonFilter.All, { viewModel.setPersonFilter(TripPersonFilter.All) },
                    label = { Text(stringResource(R.string.trips_filter_everyone)) })
                others.forEach { person ->
                    val value = TripPersonFilter.With(person.id)
                    FilterChip(state.personFilter == value, { viewModel.setPersonFilter(value) },
                        label = { Text(stringResource(R.string.trips_filter_with, person.name)) })
                }
                FilterChip(state.personFilter == TripPersonFilter.Alone, { viewModel.setPersonFilter(TripPersonFilter.Alone) },
                    label = { Text(stringResource(R.string.trips_filter_alone)) })
            }
        }
    }
}

@Composable
fun TripDetailScreen(tripId: String, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.trips.firstOrNull { it.id == tripId }
    BackScaffold(trip?.let { tripTitle(it, state.editor) } ?: "", onBack) { modifier ->
        if (trip == null) return@BackScaffold
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var rename by remember { mutableStateOf(false) }
            SectionCard(tripDates(trip)) {
                TextButton(onClick = { rename = true }) { Text(stringResource(R.string.trips_rename)) }
                if (rename) {
                    TripRenameDialog(tripTitle(trip, state.editor), onDismiss = { rename = false }) { title ->
                        viewModel.rename(trip, title); rename = false
                    }
                }
                if (trip.precision == DatePrecision.DAY) Text(stringResource(R.string.trips_days, trip.days))
                Text(state.editor.participantsLabel(trip.participants))
                Text(
                    trip.countryIso2s.mapNotNull { ReferenceData.country(it) }.joinToString(" · ") { "${it.flag} ${it.nameDe}" },
                )
                if (trip.ongoing) Text(stringResource(R.string.trips_ongoing), color = MaterialTheme.colorScheme.primary)
            }
            val days = state.days[trip.id].orEmpty()
            if (days.isEmpty()) {
                SectionCard(stringResource(R.string.trips_entries)) {
                    trip.stays.forEach { Text("• ${state.editor.typeName(it.typeId)} · ${state.editor.placeName(it.placeId)}") }
                }
            } else {
                SectionCard(stringResource(R.string.trips_days_title)) {
                    days.forEachIndexed { index, day ->
                        TimelineDayRow(day, state.editor, label = stringResource(R.string.trips_day_n, index + 1)) { onOpenDay(day.date) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Reise benennen (leer = automatischer Name). */
@Composable
fun TripRenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.trips_name)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.trips_rename_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Vergangene Reise nachtragen – mit genauen Daten oder nur Monat/Jahr. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PastTripDialog(editor: EditorData, viewModel: TripsViewModel, onDismiss: () -> Unit) {
    var placeIds by remember { mutableStateOf(listOf<String?>(null)) }
    var typeId by remember { mutableStateOf(editor.tripTypes.firstOrNull()?.id) }
    var precision by remember { mutableStateOf(DatePrecision.MONTH) }
    var year by remember { mutableStateOf((viewModel.today.year - 1).toString()) }
    var month by remember { mutableStateOf(7) }
    var start by remember { mutableStateOf(viewModel.today.minusYears(1)) }
    var end by remember { mutableStateOf(viewModel.today.minusYears(1).plusDays(6)) }
    var participants by remember { mutableStateOf(editor.defaults.tripParticipants) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val yearValue = year.toIntOrNull()?.takeIf { it in 1900..viewModel.today.year }
    val range: Pair<LocalDate, LocalDate>? = when (precision) {
        DatePrecision.DAY -> start to end
        DatePrecision.MONTH -> yearValue?.let { YearMonth.of(it, month).let { m -> m.atDay(1) to m.atEndOfMonth() } }
        DatePrecision.YEAR -> yearValue?.let { LocalDate.of(it, 1, 1) to LocalDate.of(it, 12, 31) }
        DatePrecision.UNKNOWN -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trips_add_past)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(editor.tripTypes + editor.activeStayTypes.filter { it.id == "stay-excursion" }, typeId) { typeId = it }
                Text(stringResource(R.string.trips_destinations), style = MaterialTheme.typography.labelLarge)
                placeIds.forEachIndexed { index, id ->
                    PlaceField(editor.sortedPlaces, id, { new -> placeIds = placeIds.toMutableList().also { it[index] = new } }, viewModel::createPlace)
                }
                TextButton(onClick = { placeIds = placeIds + null }) { Text(stringResource(R.string.trips_add_destination)) }

                Text(stringResource(R.string.trips_when), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(precision == DatePrecision.DAY, { precision = DatePrecision.DAY }, label = { Text(stringResource(R.string.trips_exact)) })
                    FilterChip(precision == DatePrecision.MONTH, { precision = DatePrecision.MONTH }, label = { Text(stringResource(R.string.trips_month)) })
                    FilterChip(precision == DatePrecision.YEAR, { precision = DatePrecision.YEAR }, label = { Text(stringResource(R.string.trips_year_only)) })
                }
                if (precision == DatePrecision.DAY) {
                    ValueButtonRow(stringResource(R.string.editor_from_date), Format.fullDate(start)) { pickStart = true }
                    ValueButtonRow(stringResource(R.string.editor_to_date), Format.fullDate(end)) { pickEnd = true }
                } else {
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { Text(stringResource(R.string.trips_year_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (precision == DatePrecision.MONTH) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..12).forEach { m ->
                                FilterChip(
                                    month == m, { month = m },
                                    label = { Text(java.time.Month.of(m).getDisplayName(TextStyle.SHORT, io.github.mario2712.locus.ui.common.Format.locale)) },
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.editor_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, participants) { participants = it }
            }
        },
        confirmButton = {
            val ids = placeIds.filterNotNull()
            val type = typeId
            TextButton(
                enabled = ids.isNotEmpty() && type != null && range != null && participants.isNotEmpty(),
                onClick = {
                    if (type != null && range != null) {
                        viewModel.addPastTrip(PastTripDraft(ids, type, range.first, range.second, precision, participants))
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickStart) DatePickerDialogFor(start, { pickStart = false }) { start = it; if (end < it) end = it; pickStart = false }
    if (pickEnd) DatePickerDialogFor(end, { pickEnd = false }) { end = maxOf(it, start); pickEnd = false }
}
