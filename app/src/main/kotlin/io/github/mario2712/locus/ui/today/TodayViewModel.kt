package io.github.mario2712.locus.ui.today

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.model.Country
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.usecase.DayInsights
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.ui.common.BaseDayViewModel
import io.github.mario2712.locus.ui.common.DayState
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.StationActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekDay(val date: LocalDate, val colorArgb: Long?, val isToday: Boolean)

data class TodayUiState(
    val day: DayState,
    val week: List<WeekDay> = emptyList(),
    val weekCounts: List<Pair<String, Int>> = emptyList(),
    val missingDays: Int = 0,
    val countries: List<Country> = emptyList(),
    val reminderEnabled: Boolean = false,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    patterns: PatternRuleRepository,
    settings: SettingsRepository,
    editorCatalog: EditorCatalog,
    suggester: DaySuggester,
    actions: StationActions,
    private val countryPoints: CountryPointsCalculator,
) : BaseDayViewModel(stays, visits, patterns, editorCatalog, suggester, actions) {

    override val today: LocalDate = LocalDate.now(clock)
    override val date: LocalDate = today

    val state: StateFlow<TodayUiState> = combine(inputs, settings.observeReminder()) { input, reminder ->
        val editor = input.editor
        val resolved = Timeline.resolve(input.stays, editor.homebases, today)

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val summaries = DayInsights.summaries(weekStart, weekStart.plusDays(6), me, resolved, editor.rank)
        val week = (0L..6L).map { offset ->
            val day = weekStart.plusDays(offset)
            WeekDay(day, summaries[day]?.let { editor.typeColor(it.mainTypeId) }, day == today)
        }
        val weekCounts = summaries.values.groupingBy { it.mainTypeId }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { editor.typeName(it.key) to it.value }

        val earliest = DayInsights.staysOf(me, input.stays).minOfOrNull { it.startDate }
        val gapFrom = listOfNotNull(earliest, today.minusDays(14)).maxOrNull()
        val missing = if (gapFrom == null || !gapFrom.isBefore(today)) {
            0
        } else {
            DayInsights.missingDays(gapFrom, today.minusDays(1), me, resolved).size
        }

        val points = countryPoints.calculate(
            stays = resolved, visits = input.visits, places = editor.placesById,
            stayTypes = editor.typesById, visitCategories = editor.categoriesById,
        )
        val countries = points.byPerson[me].orEmpty().values
            .sortedBy { it.firstDate }
            .mapNotNull { ReferenceData.country(it.countryIso2) }

        TodayUiState(
            day = buildDayState(input),
            week = week,
            weekCounts = weekCounts,
            missingDays = missing,
            countries = countries,
            reminderEnabled = reminder.enabled,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TodayUiState(DayState(today)))
}
