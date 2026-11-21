package io.github.mario2712.locus.ui.timeline

import androidx.compose.foundation.clickable
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

data class TimelineUiState(val days: List<TimelineDay> = emptyList(), val editor: EditorData = EditorData())

@HiltViewModel
class TimelineViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    val state: StateFlow<TimelineUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val resolved = Timeline.resolve(s, editor.homebase?.id, today)
        TimelineUiState(TimelineDays.build(resolved, v, Defaults.ME, today), editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TimelineUiState())
}

@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackScaffold(stringResource(R.string.timeline_title), onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (state.days.isEmpty()) item { Text(stringResource(R.string.timeline_empty), Modifier.padding(vertical = 16.dp)) }
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
