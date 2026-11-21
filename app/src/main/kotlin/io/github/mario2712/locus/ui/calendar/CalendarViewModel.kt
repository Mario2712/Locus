package io.github.mario2712.locus.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.usecase.AustrianHolidays
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.usecase.DayInsights
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.StationActions
import io.github.mario2712.locus.ui.common.UiMessage
import io.github.mario2712.locus.ui.editor.FillDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarDay(
    val date: LocalDate,
    /** Farbe der Hauptart, `null` = kein Eintrag. */
    val mainColor: Long?,
    /** Farben weiterer Arten an geteilten Tagen. */
    val extraColors: List<Long>,
    val holiday: String?,
    /** Vergangener Tag ohne Eintrag (nach dem ersten Eintrag überhaupt). */
    val isGap: Boolean,
    val isToday: Boolean,
)

data class CalendarUiState(
    val month: YearMonth,
    val days: List<CalendarDay> = emptyList(),
    val selection: ClosedRange<LocalDate>? = null,
    val selectionStart: LocalDate? = null,
    val legend: List<Pair<String, Long>> = emptyList(),
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    clock: Clock,
    private val stays: StayRepository,
    private val editorCatalog: EditorCatalog,
    private val editing: StationActions,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val me = Defaults.ME
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selection = MutableStateFlow<Pair<LocalDate?, ClosedRange<LocalDate>?>>(null to null)

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val state: StateFlow<CalendarUiState> = combine(
        month, stays.observeAll(), editorCatalog.data, selection,
    ) { m, all, editor, sel ->
        // Laufende Reisen (Basis ohne Ende) werden bis zur nächsten Basis bzw. bis heute aufgelöst.
        val resolved = Timeline.resolve(all, editor.homebase?.id, today)
        val summaries = DayInsights.summaries(m.atDay(1), m.atEndOfMonth(), me, resolved, editor.rank)
        val earliest = DayInsights.staysOf(me, all).minOfOrNull { it.startDate }
        val days = (1..m.lengthOfMonth()).map { dayOfMonth ->
            val date = m.atDay(dayOfMonth)
            val summary = summaries[date]
            CalendarDay(
                date = date,
                mainColor = summary?.let { editor.typeColor(it.mainTypeId) },
                extraColors = summary?.typeIds?.filter { it != summary.mainTypeId }?.map(editor::typeColor).orEmpty(),
                holiday = AustrianHolidays.nameOf(date),
                isGap = summary == null && earliest != null && !date.isBefore(earliest) && date.isBefore(today),
                isToday = date == today,
            )
        }
        val legend = summaries.values.flatMap { it.typeIds }.distinct()
            .mapNotNull { id -> editor.typesById[id] }
            .sortedBy { it.sortOrder }
            .map { it.name to it.colorArgb }
        CalendarUiState(m, days, sel.second, sel.first, legend, editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), CalendarUiState(YearMonth.from(today)))

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    /** Langes Drücken startet eine Mehrfachauswahl. */
    fun startSelection(date: LocalDate) {
        selection.value = date to (date..date)
    }

    /** Antippen während einer Auswahl legt das Ende fest. @return `true`, wenn der Tap verbraucht wurde. */
    fun extendSelection(date: LocalDate): Boolean {
        val start = selection.value.first ?: return false
        selection.value = start to (minOf(start, date)..maxOf(start, date))
        return true
    }

    fun clearSelection() {
        selection.value = null to null
    }

    fun fill(range: ClosedRange<LocalDate>, draft: FillDraft) = viewModelScope.launch {
        messageChannel.send(editing.fill(range.start, range.endInclusive, me, draft))
        clearSelection()
    }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
