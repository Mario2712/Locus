package io.github.mario2712.locus.ui.countries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.countrypoints.CountryOverview
import io.github.mario2712.locus.domain.countrypoints.CountryOverviewData
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.flow.first
import io.github.mario2712.locus.domain.model.Wish
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.repository.WishRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.trips.TripSummary
import io.github.mario2712.locus.domain.trips.Trips
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Stadt in der Länderansicht: Tage dort (inkl. Örtlichkeiten) und Anzahl Örtlichkeiten. */
data class CityRow(val placeId: String, val days: Int, val locations: Int)

data class CountriesUiState(
    val overview: CountryOverviewData? = null,
    val personA: Person? = null,
    val personB: Person? = null,
    /** Städte je Land (Person A), nach Tagen absteigend. */
    val citiesByCountry: Map<String, List<CityRow>> = emptyMap(),
    /** Örtlichkeiten ohne zugeordnete Stadt je Land. */
    val unassignedByCountry: Map<String, Int> = emptyMap(),
    val tripsByCountry: Map<String, List<TripSummary>> = emptyMap(),
    /** Einträge „Datum unbekannt“ je Land. */
    val undatedByCountry: Map<String, List<Stay>> = emptyMap(),
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class CountriesViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    private val wishes: WishRepository,
    editorCatalog: EditorCatalog,
    private val calculator: CountryPointsCalculator,
    private val places: PlaceRepository,
    private val stayRepository: StayRepository,
    private val commands: StayCommands,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val clockRef = clock

    val state: StateFlow<CountriesUiState> = combine(
        stays.observeAll(), visits.observeAll(), wishes.observeAll(), editorCatalog.data,
    ) { s, v, w, editor ->
        val a = editor.persons.firstOrNull { it.isMe } ?: return@combine CountriesUiState(editor = editor)
        val b = editor.persons.firstOrNull { !it.isMe }
        val resolved = Timeline.resolve(s, editor.homebases, today)
        val points = calculator.calculate(resolved, v, editor.placesById, editor.typesById, editor.categoriesById)
        // Für Tage-Zählungen ist die Homebase an Tagen mit Start/Ende zuhause implizit dabei.
        val withHome = io.github.mario2712.locus.domain.timeline.ImplicitHome.augment(resolved, v, a.id, editor.homebases, today)
        val overview = CountryOverview.build(points, a.id, b?.id, w, CountryOverview.countryDays(withHome, editor.placesById, a.id))

        // Tage je Ort (Stationen tagesgenau + Besuche), dann je Stadt zusammengefasst.
        val datesByPlace = mutableMapOf<String, MutableSet<LocalDate>>()
        withHome.filter { a.id in it.participants && it.precision == DatePrecision.DAY }.forEach { stay ->
            var day = stay.startDate
            while (!day.isAfter(stay.endDate)) { datesByPlace.getOrPut(stay.placeId) { mutableSetOf() } += day; day = day.plusDays(1) }
        }
        v.filter { a.id in it.participants && it.precision == DatePrecision.DAY }
            .forEach { datesByPlace.getOrPut(it.placeId) { mutableSetOf() } += it.date }
        val cityDates = mutableMapOf<String, MutableSet<LocalDate>>()
        val cityLocations = mutableMapOf<String, MutableSet<String>>()
        val unassigned = mutableMapOf<String, Int>()
        datesByPlace.forEach { (placeId, dates) ->
            val place = editor.placesById[placeId] ?: return@forEach
            if (place.kind == PlaceKind.COUNTRY) return@forEach
            val cityId = if (place.kind == PlaceKind.CITY) place.id else place.parentPlaceId
            if (cityId == null) {
                val iso = ReferenceData.countableCountry(place.countryIso2)?.iso2 ?: return@forEach
                unassigned[iso] = (unassigned[iso] ?: 0) + 1
                return@forEach
            }
            cityDates.getOrPut(cityId) { mutableSetOf() } += dates
            if (cityId != place.id) cityLocations.getOrPut(cityId) { mutableSetOf() } += place.id
        }
        val citiesByCountry = cityDates.mapNotNull { (cityId, dates) ->
            val city = editor.placesById[cityId] ?: return@mapNotNull null
            val iso = ReferenceData.countableCountry(city.countryIso2)?.iso2 ?: return@mapNotNull null
            iso to CityRow(cityId, dates.size, cityLocations[cityId]?.size ?: 0)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, rows) -> rows.sortedByDescending { it.days } }
        val trips = Trips.derive(s, v, editor.placesById, a.id, editor.homebases, today)
        val tripsByCountry = trips.flatMap { t -> t.countryIso2s.map { it to t } }.groupBy({ it.first }, { it.second })

        val undated = s.filter { it.precision == DatePrecision.UNKNOWN }.mapNotNull { stay ->
            editor.placesById[stay.placeId]?.let { p -> ReferenceData.countableCountry(p.countryIso2)?.iso2?.let { it to stay } }
        }.groupBy({ it.first }, { it.second })
        CountriesUiState(overview, a, b, citiesByCountry, unassigned, tripsByCountry, undated, editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), CountriesUiState())

    fun toggleWish(iso2: String, existing: List<Wish>) = viewModelScope.launch {
        val wish = existing.firstOrNull { it.countryIso2.equals(iso2, true) }
        if (wish != null) wishes.delete(wish.id)
        else wishes.upsert(Wish(UUID.randomUUID().toString(), countryIso2 = iso2, createdAtEpochMillis = clockRef.millis()))
    }

    /** „Schon dort gewesen – Datum unbekannt“ für ein Land. */
    fun markUndated(iso2: String, participants: Set<PersonId>) = viewModelScope.launch {
        val country = ReferenceData.country(iso2) ?: return@launch
        val all = places.observeAll().first()
        val place = all.firstOrNull { it.kind == PlaceKind.COUNTRY && it.countryIso2 == iso2 }
            ?: Place(UUID.randomUUID().toString(), country.nameDe, PlaceKind.COUNTRY, iso2).also { places.upsert(it) }
        commands.markVisitedUndated(place.id, participants)
    }

    fun removeUndated(stays: List<Stay>) = viewModelScope.launch {
        stays.forEach { stayRepository.delete(it.id) }
    }

    val wishList: StateFlow<List<Wish>> = wishes.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())
}
