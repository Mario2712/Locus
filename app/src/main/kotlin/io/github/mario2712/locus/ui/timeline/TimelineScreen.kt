package io.github.mario2712.locus.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.InputChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.mario2712.locus.domain.timeline.TimelineWork
import io.github.mario2712.locus.domain.timeline.TimelineFilter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.timeline.TimelineDay
import io.github.mario2712.locus.domain.timeline.TimelineDays
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.places.BackScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class TimelineUiState(
    val days: List<TimelineDay> = emptyList(),
    val editor: EditorData = EditorData(),
    val filter: TimelineFilter = TimelineFilter(),
    /** Tage ohne Filter (für „x von y“). */
    val totalDays: Int = 0,
    /** Jahre mit Einträgen, neueste zuerst. */
    val years: List<Int> = emptyList(),
    /** Verwendete Arten bzw. Kategorien mit Anzahl Tage, häufigste zuerst. */
    val typeUsage: List<Pair<String, Int>> = emptyList(),
    val categoryUsage: List<Pair<String, Int>> = emptyList(),
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val filter = MutableStateFlow(TimelineFilter())

    // Tage nur bei Datenänderungen neu aufbauen, nicht bei jeder Filtereingabe.
    private val allDays = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val days = TimelineDays.build(Timeline.resolve(s, editor.homebases, today), v, Defaults.ME, today)
        Triple(days, editor, TimelineFilter.usage(days))
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<TimelineUiState> = combine(allDays, filter) { (days, editor, usage), f ->
        TimelineUiState(
            f.apply(days, editor.typesById, editor.placesById), editor, f, days.size,
            days.map { it.date.year }.distinct().sortedDescending(), usage.first, usage.second,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TimelineUiState())

    fun setFilter(value: TimelineFilter) {
        filter.value = value
    }
}

@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackScaffold(stringResource(R.string.timeline_title), onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item(key = "filters") { TimelineFilters(state, viewModel::setFilter) }
            if (state.days.isEmpty()) {
                item {
                    Text(
                        stringResource(if (state.filter.isActive) R.string.timeline_filter_empty else R.string.timeline_empty),
                        Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            state.days.groupBy { YearMonth.from(it.date) }.forEach { (month, days) ->
                item(key = "m-$month") {
                    Text(
                        Format.month(month.atDay(1)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(days, key = { it.date.toString() }) { day ->
                    TimelineDayRow(day, state.editor, label = Format.mediumDate(day.date)) { onOpenDay(day.date) }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Ortssuche plus „Filter (n)“; aktive Filter als Chips mit ✕. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineFilters(state: TimelineUiState, onChange: (TimelineFilter) -> Unit) {
    val f = state.filter
    val editor = state.editor
    var sheet by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = f.query, onValueChange = { onChange(f.copy(query = it)) }, singleLine = true,
                label = { Text(stringResource(R.string.timeline_filter_place)) }, modifier = Modifier.weight(1f),
            )
            FilterChip(f.count > 0, { sheet = true }, label = {
                Text(if (f.count > 0) stringResource(R.string.timeline_filter_button_n, f.count) else stringResource(R.string.timeline_filter_button))
            })
        }
        if (f.count > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.work != TimelineWork.ALL) {
                    ActiveChip(stringResource(if (f.work == TimelineWork.NO_WORK) R.string.stats_filter_no_work else R.string.timeline_filter_only_work)) {
                        onChange(f.copy(work = TimelineWork.ALL))
                    }
                }
                f.stayTypeIds.forEach { id -> ActiveChip(editor.typeName(id)) { onChange(f.copy(stayTypeIds = f.stayTypeIds - id)) } }
                f.categoryIds.forEach { id -> ActiveChip(editor.categoryName(id)) { onChange(f.copy(categoryIds = f.categoryIds - id)) } }
                f.person?.let { id ->
                    val name = editor.persons.firstOrNull { it.id == id }?.name ?: "?"
                    ActiveChip(stringResource(R.string.trips_filter_with, name)) { onChange(f.copy(person = null)) }
                }
                if (f.hasPeriod) ActiveChip(periodText(f)) { onChange(f.copy(from = null, to = null)) }
            }
        }
        if (f.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.timeline_filter_count, state.days.size, state.totalDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(TimelineFilter()) }) { Text(stringResource(R.string.timeline_filter_reset)) }
            }
        }
    }
    if (sheet) FilterSheet(state, onChange) { sheet = false }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, label = { Text(label) }, trailingIcon = { Text("✕") })
}

/** Filter von unten: Arbeit, Arten, Kategorien (nur verwendete, mit Anzahl), Personen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: TimelineUiState, onChange: (TimelineFilter) -> Unit, onDismiss: () -> Unit) {
    val f = state.filter
    val editor = state.editor
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodSection(state, onChange)
            SheetLabel(stringResource(R.string.timeline_filter_work))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TimelineWork.ALL to R.string.trips_filter_all,
                    TimelineWork.NO_WORK to R.string.stats_filter_no_work,
                    TimelineWork.ONLY_WORK to R.string.timeline_filter_only_work,
                ).forEach { (value, label) ->
                    FilterChip(f.work == value, { onChange(f.copy(work = value)) }, label = { Text(stringResource(label)) })
                }
            }
            if (state.typeUsage.isNotEmpty()) {
                SheetLabel(stringResource(R.string.timeline_filter_types))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.typeUsage.forEach { (id, count) ->
                        val selected = id in f.stayTypeIds
                        FilterChip(selected, {
                            onChange(f.copy(stayTypeIds = if (selected) f.stayTypeIds - id else f.stayTypeIds + id))
                        }, label = { Text("${editor.typeName(id)} ($count)") }, leadingIcon = { TypeDot(editor.typeColor(id), 8.dp) })
                    }
                }
            }
            if (state.categoryUsage.isNotEmpty()) {
                SheetLabel(stringResource(R.string.timeline_filter_categories))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categoryUsage.forEach { (id, count) ->
                        val selected = id in f.categoryIds
                        FilterChip(selected, {
                            onChange(f.copy(categoryIds = if (selected) f.categoryIds - id else f.categoryIds + id))
                        }, label = { Text("${editor.categoryName(id)} ($count)") })
                    }
                }
            }
            val others = editor.persons.filterNot { it.isMe }
            if (others.isNotEmpty()) {
                SheetLabel(stringResource(R.string.timeline_filter_persons))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { person ->
                        FilterChip(f.person == person.id, { onChange(f.copy(person = if (f.person == person.id) null else person.id)) },
                            label = { Text(stringResource(R.string.trips_filter_with, person.name)) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.timeline_filter_count, state.days.size, state.totalDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { onChange(TimelineFilter(query = f.query)) }) { Text(stringResource(R.string.timeline_filter_reset)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

/** „03/2026“, „2026“ oder „01.03.2026 – 15.03.2026“. */
private fun periodText(f: TimelineFilter): String {
    val from = f.from
    val to = f.to
    if (from != null && to != null) {
        if (from.dayOfYear == 1 && to == from.withDayOfYear(from.lengthOfYear())) return from.year.toString()
        if (from.dayOfMonth == 1 && to == YearMonth.from(from).atEndOfMonth()) return Format.month(from)
    }
    return "${from?.let(Format::mediumDate) ?: "…"} – ${to?.let(Format::mediumDate) ?: "…"}"
}

/** Zeitraum: alle, ein Jahr, ein Monat oder von–bis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSection(state: TimelineUiState, onChange: (TimelineFilter) -> Unit) {
    val f = state.filter
    var monthPicker by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    SheetLabel(stringResource(R.string.timeline_filter_period))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!f.hasPeriod, { onChange(f.copy(from = null, to = null)) }, label = { Text(stringResource(R.string.timeline_filter_period_all)) })
        state.years.forEach { year ->
            val from = LocalDate.of(year, 1, 1)
            val to = LocalDate.of(year, 12, 31)
            FilterChip(f.from == from && f.to == to, { onChange(f.copy(from = from, to = to)) }, label = { Text(year.toString()) })
        }
        val from = f.from
        val to = f.to
        val month = if (from != null && to != null && from.dayOfMonth == 1 && to == YearMonth.from(from).atEndOfMonth()) from else null
        FilterChip(month != null, { monthPicker = true }, label = {
            Text(month?.let(Format::month) ?: stringResource(R.string.stats_choose_month))
        })
        FilterChip(false, { pickFrom = true }, label = { Text(stringResource(R.string.stats_period_custom)) })
    }
    if (monthPicker) {
        io.github.mario2712.locus.ui.common.MonthPickerDialog(
            initial = f.from?.let(YearMonth::from) ?: YearMonth.from(today),
            latest = YearMonth.from(today),
            onDismiss = { monthPicker = false },
            onSelect = { m -> onChange(f.copy(from = m.atDay(1), to = m.atEndOfMonth())); monthPicker = false },
        )
    }
    if (pickFrom) {
        io.github.mario2712.locus.ui.editor.DatePickerDialogFor(f.from ?: today.minusMonths(1), { pickFrom = false }) { from ->
            customFrom = from; pickFrom = false
        }
    }
    customFrom?.let { from ->
        io.github.mario2712.locus.ui.editor.DatePickerDialogFor(f.to ?: today, { customFrom = null }) { to ->
            onChange(f.copy(from = minOf(from, to), to = maxOf(from, to))); customFrom = null
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** Eine Zeile pro Tag: Datum, Kette (Basis ▸ Stationen) und Besuche. */
@Composable
fun TimelineDayRow(day: TimelineDay, editor: EditorData, label: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        // Ausgangspunkt des Tages (Unterkunft der laufenden Reise), auch wenn danach Stationen folgen.
        val base = day.baseAtStart
        if (base != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeDot(editor.typeColor(base.typeId), 10.dp)
                Text(
                    "🧳 ${editor.typeName(base.typeId)} · ${editor.placeName(base.placeId)}",
                    color = if (day.stations.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        day.stations.forEach { stay ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeDot(editor.typeColor(stay.typeId), 10.dp)
                Text(
                    listOfNotNull(
                        stay.transport.joinToString("") { it.emoji }.takeIf { it.isNotEmpty() },
                        "${editor.typeName(stay.typeId)} · ${editor.placeName(stay.placeId)}",
                    ).joinToString(" "),
                )
            }
        }
        day.visits.forEach { visit ->
            Text(
                "📍 ${editor.placeName(visit.placeId)} (${visit.categoryIds.joinToString(" + ") { editor.categoryName(it) }})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
