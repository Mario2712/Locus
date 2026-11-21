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
import io.github.mario2712.locus.domain.trips.TripSummary
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
    val trips: List<TripSummary> = emptyList(),
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
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)

    val state: StateFlow<TripsUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val home = editor.homebase?.id
        val trips = Trips.derive(s, v, editor.placesById, Defaults.ME, home, today)
        val resolved = Timeline.resolve(s, home, today)
        val allDays = TimelineDays.build(resolved, v, Defaults.ME, today)
        TripsUiState(
            trips = trips,
            days = trips.associate { trip -> trip.id to allDays.filter { it.date in trip.start..trip.end }.reversed() },
            editor = editor,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TripsUiState())

    fun addPastTrip(draft: PastTripDraft) = viewModelScope.launch {
        commands.addPastTrip(draft.placeIds, draft.start, draft.end, draft.precision, draft.participants, draft.typeId)
    }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
