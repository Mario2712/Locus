package io.github.mario2712.locus.ui.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.ui.common.BaseDayViewModel
import io.github.mario2712.locus.ui.common.DayState
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.StationActions
import io.github.mario2712.locus.ui.navigation.DayRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    patterns: PatternRuleRepository,
    editorCatalog: EditorCatalog,
    suggester: DaySuggester,
    actions: StationActions,
) : BaseDayViewModel(stays, visits, patterns, editorCatalog, suggester, actions) {

    override val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<DayRoute>().date)
    override val today: LocalDate = LocalDate.now(clock)

    val state: StateFlow<DayState> = inputs.map(::buildDayState)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), DayState(date))
}
