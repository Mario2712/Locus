package io.github.mario2712.locus.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.model.Country
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.usecase.AustrianHolidays
import io.github.mario2712.locus.domain.usecase.DayInsights
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.DaySuggestion
import io.github.mario2712.locus.domain.usecase.StayCombo
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.StayEditing
import io.github.mario2712.locus.ui.common.StayItem
import io.github.mario2712.locus.ui.common.UiMessage
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.SwitchDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekDay(val date: LocalDate, val colorArgb: Long?, val isToday: Boolean)

data class TodayUiState(
    val today: LocalDate,
    val holiday: String? = null,
    val todayStays: List<StayItem> = emptyList(),
    val suggestion: DaySuggestion? = null,
    val combos: List<StayCombo> = emptyList(),
    val week: List<WeekDay> = emptyList(),
    val weekCounts: List<Pair<String, Int>> = emptyList(),
    val missingDays: Int = 0,
    val countries: List<Country> = emptyList(),
    val reminderEnabled: Boolean = false,
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    patterns: PatternRuleRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository,
    private val editorCatalog: EditorCatalog,
    private val suggester: DaySuggester,
    private val countryPoints: CountryPointsCalculator,
    private val commands: StayCommands,
    private val editing: StayEditing,
) : ViewModel() {

    val today: LocalDate = LocalDate.now(clock)
    private val me = Defaults.ME

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val inputs = combine(
        stays.observeAll(),
        visits.observeAll(),
        patterns.observeAll(),
        catalog.observeVisitCategories(),
        settings.observeReminder(),
    ) { allStays, allVisits, rules, categories, reminder -> Inputs(allStays, allVisits, rules, categories, reminder.enabled) }

    val state: StateFlow<TodayUiState> = combine(inputs, editorCatalog.data) { input, editor ->
        val own = DayInsights.staysOf(me, input.stays)
        val todayStays = own.filter { it.covers(today) }
            .sortedWith(compareBy({ if (it.startDate < today) 0 else 1 }, { it.sortIndex }))
            .map(editor::stayItem)

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val summaries = DayInsights.summaries(weekStart, weekEnd, me, input.stays)
        val week = (0L..6L).map { offset ->
            val day = weekStart.plusDays(offset)
            WeekDay(day, summaries[day]?.let { editor.typeColor(it.mainTypeId) }, day == today)
        }
        val weekCounts = summaries.values.groupingBy { it.mainTypeId }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { editor.typeName(it.key) to it.value }

        val earliest = own.minOfOrNull { it.startDate }
        val gapFrom = listOfNotNull(earliest, today.minusDays(14)).maxOrNull()
        val missing = if (gapFrom == null || !gapFrom.isBefore(today)) {
            0
        } else {
            DayInsights.missingDays(gapFrom, today.minusDays(1), me, input.stays).size
        }

        val points = countryPoints.calculate(
            stays = input.stays, visits = input.visits, places = editor.placesById,
            stayTypes = editor.typesById, visitCategories = input.categories.associateBy { it.id },
        )
        val countries = points.byPerson[me].orEmpty().values
            .sortedBy { it.firstDate }
            .mapNotNull { ReferenceData.country(it.countryIso2) }

        TodayUiState(
            today = today,
            holiday = AustrianHolidays.nameOf(today),
            todayStays = todayStays,
            suggestion = if (todayStays.isEmpty()) {
                suggester.suggest(today, me, input.rules, editor.typesById)
                    ?.takeIf { it.placeId in editor.placesById }
            } else {
                null
            },
            combos = DayInsights.recentCombos(me, input.stays)
                .filter { it.placeId in editor.placesById && it.stayTypeId in editor.typesById },
            week = week,
            weekCounts = weekCounts,
            missingDays = missing,
            countries = countries,
            reminderEnabled = input.reminderEnabled,
            editor = editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(today))

    fun confirmSuggestion(suggestion: DaySuggestion) = viewModelScope.launch {
        commands.checkIn(today, me, suggestion.stayTypeId, suggestion.placeId, EntrySource.PATTERN)
    }

    fun quickCheckIn(combo: StayCombo) = viewModelScope.launch {
        commands.checkIn(today, me, combo.stayTypeId, combo.placeId)
    }

    fun newDraft(suggestion: DaySuggestion? = null) =
        StayDraft(startDate = today, typeId = suggestion?.stayTypeId, placeId = suggestion?.placeId)

    fun save(draft: StayDraft) = viewModelScope.launch { messageChannel.send(editing.save(draft)) }

    fun switchAt(draft: SwitchDraft) = viewModelScope.launch {
        messageChannel.send(editing.switchAt(today, me, draft))
    }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)

    private data class Inputs(
        val stays: List<io.github.mario2712.locus.domain.model.Stay>,
        val visits: List<io.github.mario2712.locus.domain.model.Visit>,
        val rules: List<io.github.mario2712.locus.domain.model.PatternRule>,
        val categories: List<io.github.mario2712.locus.domain.model.VisitCategory>,
        val reminderEnabled: Boolean,
    )
}
