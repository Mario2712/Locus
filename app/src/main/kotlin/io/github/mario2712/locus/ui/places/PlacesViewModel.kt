package io.github.mario2712.locus.ui.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.places.LocationGroup
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceGroups
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.stats.PlaceRecord
import io.github.mario2712.locus.domain.stats.PlaceStats
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.places.PlaceSearchController
import io.github.mario2712.locus.places.SearchResult
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.navigation.PlaceRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlaceListItem(
    val place: Place,
    val totalDays: Int,
    val lastDate: LocalDate?,
    /** Art bei Örtlichkeiten. */
    val group: LocationGroup,
    /** Anzahl zugehöriger Örtlichkeiten (bei Städten). */
    val locationCount: Int,
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
    private val cleanup: io.github.mario2712.locus.places.PlaceCleanup,
    timelineCache: io.github.mario2712.locus.ui.common.TimelineCache,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    /** Orte, die nirgends vorkommen. */
    val unused: StateFlow<Set<String>> = cleanup.observeUnused()
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptySet())

    fun deleteUnused() = viewModelScope.launch { cleanup.deleteUnused() }

    /** Unbenutzte Orte als Listeneinträge – auch reine Länder-Orte, die in den Reitern nicht erscheinen. */
    val unusedItems: StateFlow<List<PlaceListItem>> = combine(cleanup.observeUnused(), editorCatalog.data) { ids, editor ->
        editor.places.filter { it.id in ids }.sortedBy { it.displayName.lowercase() }.map { place ->
            PlaceListItem(place, 0, null, group = PlaceGroups.groupOf(place, emptyList()), locationCount = 0)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    val items: StateFlow<List<PlaceListItem>> = timelineCache.snapshot.map { snap ->
        val v = snap.visits
        val editor = snap.editor
        // Homebase zählt an Tagen mit Start/Ende zuhause mit.
        val resolved = snap.withImplicitHome(Defaults.ME)
        val children = editor.places.filter { it.parentPlaceId != null }.groupBy({ it.parentPlaceId!! }, { it.id })
        val shown = editor.places.filter { it.kind != io.github.mario2712.locus.domain.model.PlaceKind.COUNTRY }
        // Alle Orts-Akten in einem Durchlauf statt pro Ort über alle Einträge
        val records = PlaceStats.records(shown.associate { it.id to children[it.id].orEmpty().toSet() }, Defaults.ME, resolved, v)
        val visitsByPlace = v.groupBy { it.placeId }
        shown.map { place ->
            val record = records.getValue(place.id)
            PlaceListItem(
                place, record.totalDays, record.lastDate,
                group = PlaceGroups.groupOf(place, visitsByPlace[place.id].orEmpty()),
                locationCount = children[place.id].orEmpty().size,
            )
        }.sortedWith(compareByDescending<PlaceListItem> { it.totalDays }.thenBy { it.place.displayName.lowercase() })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())
}

data class PlaceDetailState(
    val place: Place? = null,
    val record: PlaceRecord? = null,
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    private val places: PlaceRepository,
    private val search: PlaceSearchController,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    private val placeId = savedStateHandle.toRoute<PlaceRoute>().id
    private val today = LocalDate.now(clock)

    val state: StateFlow<PlaceDetailState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val place = editor.placesById[placeId]
        val resolved = io.github.mario2712.locus.domain.timeline.ImplicitHome.augment(Timeline.resolve(s, editor.homebases, today), v, Defaults.ME, editor.homebases, today)
        val children = editor.places.filter { it.parentPlaceId == placeId }.map { it.id }.toSet()
        PlaceDetailState(place, place?.let { PlaceStats.record(it.id, Defaults.ME, resolved, v, children) }, editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), PlaceDetailState())

    fun save(place: Place) = viewModelScope.launch { places.upsert(place) }
    /** Ohne bisherige Homebase: von Anfang an; sonst Umzug ab heute (frühere Zeiträume bleiben). */
    fun setHomebase() = viewModelScope.launch {
        val hasHome = state.value.editor.places.any { it.isHomebase || it.homebaseSince != null }
        if (hasHome) places.addHomebase(placeId, java.time.LocalDate.now()) else places.setHomebase(placeId)
    }
    fun delete() = viewModelScope.launch { places.delete(placeId) }

    /** Position auf der Karte gesetzt bzw. korrigiert. */
    fun setPosition(latitude: Double, longitude: Double, hit: io.github.mario2712.locus.domain.places.PlaceCandidate?) =
        viewModelScope.launch { state.value.place?.let { search.setPosition(it, latitude, longitude, hit) } }

    suspend fun searchOnline(query: String): SearchResult<List<PlaceCandidate>> = search.search(query)
    fun link(candidate: PlaceCandidate) = viewModelScope.launch {
        state.value.place?.let { search.link(it, candidate) }
    }
}
