package io.github.mario2712.locus.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.timeline.TimelineDay
import io.github.mario2712.locus.domain.timeline.TimelineDays
import io.github.mario2712.locus.domain.trips.TripFilter
import io.github.mario2712.locus.domain.trips.TripPersonFilter
import io.github.mario2712.locus.domain.trips.TripSummary
import io.github.mario2712.locus.domain.trips.TripWorkFilter
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.mario2712.locus.domain.trips.Trips
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TripsUiState(
    /** Alle Reisen (für die Detailansicht). */
    val trips: List<TripSummary> = emptyList(),
    /** Reisen nach den gewählten Filtern (für die Liste). */
    val visibleTrips: List<TripSummary> = emptyList(),
    val workFilter: TripWorkFilter = TripWorkFilter.ALL,
    val personFilter: TripPersonFilter = TripPersonFilter.All,
    val yearFilter: Int? = null,
    val days: Map<String, List<TimelineDay>> = emptyMap(),
    val editor: EditorData = EditorData(),
)

/** Eingaben für „Alte Reise nachtragen“. */
data class PastTripDraft(
    val placeIds: List<String>,
    val typeId: String,
    val start: LocalDate,
    val end: LocalDate,
    val precision: DatePrecision,
    val participants: Set<PersonId>,
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    private val editorCatalog: EditorCatalog,
    private val commands: StayCommands,
    private val actions: io.github.mario2712.locus.ui.common.StationActions,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)

    private val workFilter = MutableStateFlow(TripWorkFilter.ALL)
    private val personFilter = MutableStateFlow<TripPersonFilter>(TripPersonFilter.All)
    private val yearFilter = MutableStateFlow<Int?>(null)
    private val filters = combine(workFilter, personFilter, yearFilter) { w, p, y -> Triple(w, p, y) }

    val state: StateFlow<TripsUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data, filters) { s, v, editor, (wf, pf, year) ->
        val home = editor.homebases
        val trips = Trips.derive(s, v, editor.placesById, Defaults.ME, home, today)
        val resolved = Timeline.resolve(s, home, today)
        val allDays = TimelineDays.build(resolved, v, Defaults.ME, today)
        TripsUiState(
            trips = trips,
            visibleTrips = TripFilter.apply(trips, wf, pf, Defaults.ME, editor.typesById)
                .filter { year == null || it.start.year == year || it.end.year == year },
            yearFilter = year,
            workFilter = wf,
            personFilter = pf,
            days = trips.associate { trip ->
                // Nur Besuche, die zur Reise gehören (nicht das Café nach der Heimkehr).
                val tripVisitIds = trip.visits.mapTo(hashSetOf()) { it.id }
                trip.id to allDays.filter { it.date in trip.start..trip.end }
                    .map { day -> day.copy(visits = day.visits.filter { it.id in tripVisitIds }) }
                    .reversed()
            },
            editor = editor,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TripsUiState())

    fun setWorkFilter(value: TripWorkFilter) {
        workFilter.value = value
    }

    fun setYearFilter(value: Int?) {
        yearFilter.value = value
    }

    fun setPersonFilter(value: TripPersonFilter) {
        personFilter.value = value
    }

    fun addPastTrip(draft: PastTripDraft) = viewModelScope.launch {
        commands.addPastTrip(draft.placeIds, draft.start, draft.end, draft.precision, draft.participants, draft.typeId)
    }

    fun rename(trip: TripSummary, title: String) = viewModelScope.launch { actions.renameTrip(trip, title) }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
