package io.github.mario2712.locus.ui.timeline

import androidx.compose.foundation.clickable
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.mario2712.locus.domain.timeline.TimelineWork
import io.github.mario2712.locus.domain.timeline.TimelineFilter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
        TimelineDays.build(Timeline.resolve(s, editor.homebases, today), v, Defaults.ME, today) to editor
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<TimelineUiState> = combine(allDays, filter) { (days, editor), f ->
        TimelineUiState(f.apply(days, editor.typesById, editor.placesById), editor, f, days.size)
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

/** Ortssuche, Arbeit, Art, Kategorie und Person. */
@Composable
private fun TimelineFilters(state: TimelineUiState, onChange: (TimelineFilter) -> Unit) {
    val f = state.filter
    val editor = state.editor
    var typeMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = f.query, onValueChange = { onChange(f.copy(query = it)) }, singleLine = true,
            label = { Text(stringResource(R.string.timeline_filter_place)) }, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TimelineWork.ALL to R.string.trips_filter_all,
                TimelineWork.NO_WORK to R.string.stats_filter_no_work,
                TimelineWork.ONLY_WORK to R.string.timeline_filter_only_work,
            ).forEach { (value, label) ->
                FilterChip(f.work == value, { onChange(f.copy(work = value)) }, label = { Text(stringResource(label)) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                FilterChip(f.stayTypeId != null, { typeMenu = true }, label = {
                    Text(f.stayTypeId?.let(editor::typeName) ?: stringResource(R.string.timeline_filter_type))
                })
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.timeline_filter_type_all)) },
                        onClick = { onChange(f.copy(stayTypeId = null)); typeMenu = false })
                    editor.stayTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, onClick = { onChange(f.copy(stayTypeId = type.id)); typeMenu = false })
                    }
                }
            }
            Box {
                FilterChip(f.categoryId != null, { categoryMenu = true }, label = {
                    Text(f.categoryId?.let(editor::categoryName) ?: stringResource(R.string.timeline_filter_category))
                })
                DropdownMenu(categoryMenu, { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.timeline_filter_category_all)) },
                        onClick = { onChange(f.copy(categoryId = null)); categoryMenu = false })
                    editor.visitCategories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = { onChange(f.copy(categoryId = category.id)); categoryMenu = false })
                    }
                }
            }
            editor.persons.filterNot { it.isMe }.forEach { person ->
                FilterChip(f.person == person.id, { onChange(f.copy(person = if (f.person == person.id) null else person.id)) },
                    label = { Text(stringResource(R.string.trips_filter_with, person.name)) })
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
}

/** Eine Zeile pro Tag: Datum, Kette (Basis ▸ Stationen) und Besuche. */
@Composable
fun TimelineDayRow(day: TimelineDay, editor: EditorData, label: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        val base = day.baseAtStart
        if (base != null && day.stations.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeDot(editor.typeColor(base.typeId), 10.dp)
                Text("🧳 ${editor.typeName(base.typeId)} · ${editor.placeName(base.placeId)}")
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
