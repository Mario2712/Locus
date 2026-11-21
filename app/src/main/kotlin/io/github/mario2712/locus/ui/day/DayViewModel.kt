package io.github.mario2712.locus.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.usecase.AustrianHolidays
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.DaySuggestion
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.StayEditing
import io.github.mario2712.locus.ui.common.StayItem
import io.github.mario2712.locus.ui.common.UiMessage
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.SwitchDraft
import io.github.mario2712.locus.ui.navigation.DayRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DayUiState(
    val date: LocalDate,
    val holiday: String? = null,
    val stays: List<StayItem> = emptyList(),
    val suggestion: DaySuggestion? = null,
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class DayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stays: StayRepository,
    patterns: PatternRuleRepository,
    private val editorCatalog: EditorCatalog,
    private val suggester: DaySuggester,
    private val commands: StayCommands,
    private val editing: StayEditing,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<DayRoute>().date)
    private val me = Defaults.ME

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val state: StateFlow<DayUiState> = combine(
        stays.observeBetween(date, date),
        patterns.observeAll(),
        editorCatalog.data,
    ) { dayStays, rules, editor ->
        val items = dayStays
            .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))
            .map(editor::stayItem)
        val ownEmpty = dayStays.none { me in it.participants }
        DayUiState(
            date = date,
            holiday = AustrianHolidays.nameOf(date),
            stays = items,
            suggestion = if (ownEmpty) {
                suggester.suggest(date, me, rules, editor.typesById)?.takeIf { it.first.placeId in editor.placesById }
            } else {
                null
            },
            editor = editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(date))

    fun newDraft() = StayDraft(startDate = date)

    fun confirmSuggestion(suggestion: DaySuggestion) = viewModelScope.launch {
        commands.checkIn(date, me, suggestion.first.stayTypeId, suggestion.first.placeId, EntrySource.PATTERN)
    }

    fun save(draft: StayDraft) = viewModelScope.launch { messageChannel.send(editing.save(draft)) }
    fun delete(id: String) = viewModelScope.launch { messageChannel.send(editing.delete(id)) }
    fun switchAt(draft: SwitchDraft) = viewModelScope.launch { messageChannel.send(editing.switchAt(date, me, draft)) }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
