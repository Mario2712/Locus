package io.github.mario2712.locus.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.usecase.AustrianHolidays
import io.github.mario2712.locus.domain.usecase.DayInsights
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.DaySuggestion
import io.github.mario2712.locus.domain.usecase.StayCombo
import io.github.mario2712.locus.ui.editor.JourneyDraft
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.VisitDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Zustand eines Tages für das [DayPanel]. */
data class DayState(
    val date: LocalDate,
    val isToday: Boolean = false,
    val holiday: String? = null,
    /** Basis am Morgen (Übernachtungsort der Vornacht), `null` = Homebase. */
    val baseAtStart: Stay? = null,
    /** Basis am Abend, `null` = Homebase. */
    val baseAtEnd: Stay? = null,
    /** Tag der laufenden Reise (1 = erster Tag), sonst `null`. */
    val tripDay: Int? = null,
    /** Reise, zu der der Tag gehört (für Name und Umbenennen). */
    val trip: io.github.mario2712.locus.domain.trips.TripSummary? = null,
    val stations: List<StayItem> = emptyList(),
    /** Einträge, an denen „Ich“ nicht beteiligt ist (z. B. nur Johanna). */
    val otherStations: List<StayItem> = emptyList(),
    val visits: List<VisitItem> = emptyList(),
    val suggestion: DaySuggestion? = null,
    val combos: List<StayCombo> = emptyList(),
    val recentVisitPlaces: List<String> = emptyList(),
    /** Vorschläge für Besuche: Örtlichkeiten in den Städten des Tages, dann zuletzt besuchte Orte. */
    val visitSuggestions: List<String> = emptyList(),
    val editor: EditorData = EditorData(),
) {
    val onTrip: Boolean get() = baseAtEnd != null
}

/** Rohdaten, aus denen Tageszustände berechnet werden. */
data class TimelineInputs(
    val stays: List<Stay> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val rules: List<PatternRule> = emptyList(),
    val editor: EditorData = EditorData(),
)

/**
 * Gemeinsame Logik für Heute-Screen und Tagesansicht: Stationen-Kette, Basis/Reise,
 * Vorschlag, Schnellaktionen, Besuche.
 */
abstract class BaseDayViewModel(
    stays: StayRepository,
    visits: VisitRepository,
    patterns: PatternRuleRepository,
    private val editorCatalog: EditorCatalog,
    private val suggester: DaySuggester,
    protected val actions: StationActions,
) : ViewModel() {

    abstract val date: LocalDate
    abstract val today: LocalDate
    protected val me = Defaults.ME

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    protected val inputs: Flow<TimelineInputs> = combine(
        stays.observeAll(), visits.observeAll(), patterns.observeAll(), editorCatalog.data,
    ) { s, v, r, e -> TimelineInputs(s, v, r, e) }

    protected fun buildDayState(input: TimelineInputs): DayState {
        lastInputs = input
        val editor = input.editor
        val homes = editor.homebases
        val homeId = homes.at(date)
        val resolved = Timeline.resolve(input.stays, homes, today)
        val raw = input.stays.filter { it.covers(date) && (it.startDate == date || !it.overnight) }
            .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))
        val own = raw.filter { me in it.participants }
        val baseAtStart = Timeline.baseAtStart(date, me, resolved)
        val baseAtEnd = Timeline.baseAtEnd(date, me, resolved, homes, today)
        val tripBase = baseAtEnd ?: baseAtStart
        val dayVisits = input.visits.filter { it.date == date }.sortedBy { it.time }

        val currentPlace = own.lastOrNull()?.placeId ?: baseAtStart?.placeId ?: homeId
        return DayState(
            date = date,
            isToday = date == today,
            holiday = AustrianHolidays.nameOf(date),
            baseAtStart = baseAtStart,
            baseAtEnd = baseAtEnd,
            tripDay = tripBase?.let { ChronoUnit.DAYS.between(Timeline.tripStart(it, me, resolved), date).toInt() + 1 },
            trip = tripBase?.let {
                io.github.mario2712.locus.domain.trips.Trips.derive(input.stays, input.visits, editor.placesById, me, homes, today)
                    .firstOrNull { t -> t.precision == io.github.mario2712.locus.domain.model.DatePrecision.DAY && date in t.start..t.end }
            },
            stations = own.map(editor::stayItem),
            otherStations = raw.filterNot { me in it.participants }.map(editor::stayItem),
            visits = dayVisits.map(editor::visitItem),
            suggestion = if (own.isEmpty() && baseAtStart == null) {
                suggester.suggest(date, me, input.rules, editor.typesById, editor.templatesById)
                    ?.takeIf { s -> s.steps.all { it.placeId in editor.placesById } }
            } else {
                null
            },
            combos = DayInsights.recentCombos(me, input.stays, limit = 5, excludePlaceId = currentPlace)
                .filter { it.placeId in editor.placesById && it.stayTypeId in editor.typesById },
            recentVisitPlaces = DayInsights.recentVisitPlaces(me, input.visits).filter { it in editor.placesById },
            visitSuggestions = run {
                val dayCities = (own.map { it.placeId } + listOfNotNull(baseAtStart?.placeId, homeId))
                    .mapNotNull { id -> editor.placesById[id]?.let { p -> if (p.kind == io.github.mario2712.locus.domain.model.PlaceKind.CITY) p.id else p.parentPlaceId } }
                    .toSet()
                val inDayCities = editor.places.filter { it.parentPlaceId in dayCities }.map { it.id }
                (dayCities.toList() + inDayCities + DayInsights.recentVisitPlaces(me, input.visits, limit = 8))
                    .filter { it in editor.placesById }.distinct()
            },
            editor = editor,
        )
    }

    private var lastInputs = TimelineInputs()

    private fun send(message: UiMessage) = messageChannel.trySend(message)
    private fun live() = liveTimeFor(date, today)

    /** Teilnehmer für neue Einträge: während einer Reise die Reisenden, sonst Alltags-Standard. */
    fun defaultParticipants(state: DayState): Set<io.github.mario2712.locus.domain.model.PersonId> =
        (state.baseAtEnd ?: state.baseAtStart)?.participants ?: state.editor.defaults.dailyParticipants

    fun prefillTransport(placeId: String?): Set<TransportMode> =
        if (placeId == null || !lastInputs.editor.defaults.prefillTransport) emptySet()
        else DayInsights.lastTransportTo(placeId, lastInputs.stays)

    fun confirmSuggestion(suggestion: DaySuggestion, state: DayState) = viewModelScope.launch {
        send(actions.applyTemplate(date, state.editor.defaults.dailyParticipants, suggestion.steps))
    }

    fun quickStation(combo: StayCombo, state: DayState) = viewModelScope.launch {
        send(
            actions.addStation(
                date, defaultParticipants(state), combo.stayTypeId, combo.placeId,
                prefillTransport(combo.placeId), overnight = false, liveTime = live(),
            ),
        )
    }

    fun newStationDraft(state: DayState, typeId: String? = null) = StayDraft(
        startDate = date,
        typeId = typeId,
        participants = defaultParticipants(state),
        overnight = state.editor.defaultOvernight(typeId),
        startTime = live(),
    )

    fun saveStation(draft: StayDraft) = viewModelScope.launch { send(actions.saveStation(draft)) }
    fun deleteStation(id: String) = viewModelScope.launch { send(actions.deleteStation(id)) }

    fun journey(draft: JourneyDraft) = viewModelScope.launch {
        send(actions.journey(date, draft.participants, draft.legs, draft.typeId, live()))
    }

    fun returnHome(state: DayState, draft: io.github.mario2712.locus.ui.editor.ReturnDraft) = viewModelScope.launch {
        val arrival = date.plusDays(draft.homeDayOffset.toLong())
        val home = state.editor.homebaseAt(arrival) ?: state.editor.homebase ?: return@launch
        send(actions.returnJourney(date, defaultParticipants(state), draft.stops, home.id, draft.homeTransport, draft.homeDayOffset, live()))
    }

    fun newVisitDraft(state: DayState, placeId: String? = null) =
        VisitDraft(date = date, placeId = placeId, participants = defaultParticipants(state))

    fun renameTrip(trip: io.github.mario2712.locus.domain.trips.TripSummary, title: String) =
        viewModelScope.launch { send(actions.renameTrip(trip, title)) }

    fun saveVisit(draft: VisitDraft) = viewModelScope.launch { send(actions.saveVisit(draft, live())) }
    fun deleteVisit(id: String) = viewModelScope.launch { send(actions.deleteVisit(id)) }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
