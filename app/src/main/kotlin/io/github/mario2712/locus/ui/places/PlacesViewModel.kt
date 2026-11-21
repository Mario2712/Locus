package io.github.mario2712.locus.ui.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.places.PlaceCandidate
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlaceListItem(val place: Place, val totalDays: Int, val lastDate: LocalDate?)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val items: StateFlow<List<PlaceListItem>> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val resolved = Timeline.resolve(s, editor.homebase?.id, today)
        editor.places.map { place ->
            val record = PlaceStats.record(place.id, Defaults.ME, resolved, v)
            PlaceListItem(place, record.totalDays, record.lastDate)
        }.sortedWith(compareByDescending<PlaceListItem> { it.totalDays }.thenBy { it.place.displayName.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
        val resolved = Timeline.resolve(s, editor.homebase?.id, today)
        PlaceDetailState(place, place?.let { PlaceStats.record(it.id, Defaults.ME, resolved, v) }, editor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailState())

    fun save(place: Place) = viewModelScope.launch { places.upsert(place) }
    fun setHomebase() = viewModelScope.launch { places.setHomebase(placeId) }
    fun delete() = viewModelScope.launch { places.delete(placeId) }

    suspend fun searchOnline(query: String): SearchResult<List<PlaceCandidate>> = search.search(query)
    fun link(candidate: PlaceCandidate) = viewModelScope.launch {
        state.value.place?.let { search.link(it, candidate) }
    }
}
