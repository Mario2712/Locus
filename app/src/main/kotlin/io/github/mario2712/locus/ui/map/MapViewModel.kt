package io.github.mario2712.locus.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.countrypoints.CountryOverview
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.countrypoints.CountryStatus
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.repository.WishRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.trips.Trips
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Wessen Länder die Karte zeigt. */
enum class MapPerson { A, B, BOTH }

/** Einfärbung eines Landes auf der Karte. */
enum class MapCountryState { TOGETHER, BOTH_SEPARATELY, ONLY_A, ONLY_B, VISITED, WISH }

data class MapPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val days: Int,
    /** Örtlichkeit (Lokal, Gipfel …) statt Stadt – erst beim Hineinzoomen sichtbar. */
    val isLocation: Boolean = false,
)

data class MapFilter(
    val person: MapPerson = MapPerson.BOTH,
    val year: Int? = null,
    val showPlaces: Boolean = true,
    val showRoutes: Boolean = true,
    val detailMap: Boolean = false,
)

data class MapUiState(
    val filter: MapFilter = MapFilter(),
    /** Polygon-ISO (inkl. Gebiete wie GL) → Einfärbung. */
    val countries: Map<String, MapCountryState> = emptyMap(),
    val places: List<MapPlace> = emptyList(),
    /** Routen als Koordinatenfolgen (lat, lon). */
    val routes: List<List<Pair<Double, Double>>> = emptyList(),
    val years: List<Int> = emptyList(),
    val nameA: String = "",
    val nameB: String? = null,
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class MapViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    wishes: WishRepository,
    editorCatalog: EditorCatalog,
    private val calculator: CountryPointsCalculator,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val filter = MutableStateFlow(MapFilter())

    val state: StateFlow<MapUiState> = combine(
        stays.observeAll(), visits.observeAll(), wishes.observeAll(), editorCatalog.data, filter,
    ) { s, v, w, editor, f ->
        val a = editor.persons.firstOrNull { it.isMe } ?: return@combine MapUiState(filter = f, editor = editor)
        val b = editor.persons.firstOrNull { !it.isMe }
        val home = editor.homebase?.id
        val resolvedAll = Timeline.resolve(s, home, today)
        val years = (resolvedAll.map { it.startDate.year } + v.map { it.date.year }).distinct().sortedDescending()

        val (resolved, yearVisits) = if (f.year == null) {
            resolvedAll to v
        } else {
            val from = LocalDate.of(f.year, 1, 1)
            val to = LocalDate.of(f.year, 12, 31)
            resolvedAll.filter { it.startDate <= to && it.endDate >= from } to v.filter { it.date.year == f.year }
        }
        val points = calculator.calculate(resolved, yearVisits, editor.placesById, editor.typesById, editor.categoriesById)
        val overview = CountryOverview.build(points, a.id, b?.id, w)

        val countries = mutableMapOf<String, MapCountryState>()
        overview.rows.forEach { row ->
            val state = when (f.person) {
                MapPerson.A -> if (row.visitedByA) MapCountryState.VISITED else null
                MapPerson.B -> if (row.visitedByB) MapCountryState.VISITED else null
                MapPerson.BOTH -> when (row.status) {
                    CountryStatus.TOGETHER -> MapCountryState.TOGETHER
                    CountryStatus.BOTH_SEPARATELY -> MapCountryState.BOTH_SEPARATELY
                    CountryStatus.ONLY_A -> MapCountryState.ONLY_A
                    CountryStatus.ONLY_B -> MapCountryState.ONLY_B
                    CountryStatus.NONE -> null
                }
            } ?: if (row.wished && f.year == null) MapCountryState.WISH else null
            if (state != null) countries[row.country.iso2] = state
        }
        // Gebiete (z. B. Grönland) nur einfärben, wenn man selbst dort war – mit dem Status des Mutterlands.
        val selected = when (f.person) {
            MapPerson.A -> setOf(a.id)
            MapPerson.B -> setOfNotNull(b?.id)
            MapPerson.BOTH -> setOfNotNull(a.id, b?.id)
        }
        rawTerritories(resolved, yearVisits, editor, selected).forEach { territory ->
            val mother = ReferenceData.territoryToCountry[territory]
            countries[mother]?.takeIf { it != MapCountryState.WISH }?.let { countries[territory] = it }
        }

        val places = mapPlaces(resolved, yearVisits, editor, selected)
        val routes = if (f.showRoutes) {
            Trips.derive(s, v, editor.placesById, a.id, home, today)
                .filter { f.year == null || it.start.year == f.year || it.end.year == f.year }
                .mapNotNull { trip ->
                    fun coordsOf(placeId: String) = editor.placesById[placeId]?.let { p ->
                        p.latitude?.let { lat -> p.longitude?.let { lon -> lat to lon } }
                    }
                    val homeCoords = home?.let(::coordsOf)
                    val points = listOfNotNull(homeCoords) + trip.stays.mapNotNull { coordsOf(it.placeId) } +
                        listOfNotNull(homeCoords.takeIf { !trip.ongoing })
                    val line = points.fold(mutableListOf<Pair<Double, Double>>()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }
                    line.takeIf { it.size >= 2 }
                }
        } else {
            emptyList()
        }
        MapUiState(f, countries, places, routes, years, a.name, b?.name, editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), MapUiState())

    fun setPerson(person: MapPerson) = filter.update { it.copy(person = person) }
    fun setYear(year: Int?) = filter.update { it.copy(year = year) }
    fun togglePlaces() = filter.update { it.copy(showPlaces = !it.showPlaces) }
    fun toggleRoutes() = filter.update { it.copy(showRoutes = !it.showRoutes) }
    fun toggleDetailMap() = filter.update { it.copy(detailMap = !it.detailMap) }

    private fun rawTerritories(stays: List<Stay>, visits: List<Visit>, editor: EditorData, persons: Set<io.github.mario2712.locus.domain.model.PersonId>): Set<String> {
        val placeIds = stays.filter { s -> s.participants.any { it in persons } && editor.typesById[s.typeId]?.countsForCountryPoints == true }
            .map { it.placeId } + visits.filter { vi -> vi.participants.any { it in persons } }.map { it.placeId }
        return placeIds.mapNotNull { editor.placesById[it]?.countryIso2?.uppercase() }
            .filter { it in ReferenceData.territoryToCountry }.toSet()
    }

    private fun mapPlaces(stays: List<Stay>, visits: List<Visit>, editor: EditorData, persons: Set<io.github.mario2712.locus.domain.model.PersonId>): List<MapPlace> {
        val days = mutableMapOf<String, MutableSet<LocalDate>>()
        stays.filter { s -> s.precision == DatePrecision.DAY && s.participants.any { it in persons } }.forEach { stay ->
            var d = stay.startDate
            while (!d.isAfter(stay.endDate)) { days.getOrPut(stay.placeId) { mutableSetOf() } += d; d = d.plusDays(1) }
        }
        visits.filter { vi -> vi.participants.any { it in persons } }.forEach { days.getOrPut(it.placeId) { mutableSetOf() } += it.date }
        return days.mapNotNull { (id, dates) ->
            val place = editor.placesById[id] ?: return@mapNotNull null
            val lat = place.latitude ?: return@mapNotNull null
            val lon = place.longitude ?: return@mapNotNull null
            MapPlace(id, place.displayName, lat, lon, dates.size, isLocation = place.kind != io.github.mario2712.locus.domain.model.PlaceKind.CITY)
        }
    }
}
