package io.github.mario2712.locus.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.stats.YearReview
import io.github.mario2712.locus.domain.stats.YearReviewData
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.navigation.YearReviewRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class YearReviewUiState(
    val year: Int,
    val data: YearReviewData? = null,
    val editor: EditorData = EditorData(),
    val includeWork: Boolean = false,
    /** Länderumrisse für die selbst gezeichneten Karten. */
    val shapes: List<io.github.mario2712.locus.domain.geo.CountryShape> = emptyList(),
    /** Reiserouten als (Breite, Länge)-Folgen: Zuhause → Stationen → Zuhause. */
    val routes: List<List<Pair<Double, Double>>> = emptyList(),
    val homeName: String? = null,
    val homeCoordinates: Pair<Double, Double>? = null,
)

@HiltViewModel
class YearReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
    private val countries: io.github.mario2712.locus.places.OfflineCountries,
) : ViewModel() {
    private val shapes = kotlinx.coroutines.flow.flow { emit(runCatching { countries.shapes() }.getOrDefault(emptyList())) }
    private val year = savedState.toRoute<YearReviewRoute>().year
    private val today = LocalDate.now(clock)
    private val includeWork = MutableStateFlow(false)

    val state: StateFlow<YearReviewUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data, includeWork, shapes) { s, v, editor, work, shp ->
        val me = editor.persons.firstOrNull { it.isMe }
        val partner = editor.persons.firstOrNull { !it.isMe }
        val data = YearReview.build(
            year, me?.id ?: Defaults.ME, partner?.id, me?.birthDate, s, v, editor.placesById, editor.typesById,
            editor.categoriesById, editor.rank, editor.homebases, today, includeWork = work,
        )
        fun coords(id: String?): Pair<Double, Double>? {
            val p = id?.let(editor.placesById::get) ?: return null
            val lat = p.latitude
            val lon = p.longitude
            if (lat != null && lon != null) return lat to lon
            return p.parentPlaceId?.let(editor.placesById::get)?.let { c -> c.latitude?.let { la -> c.longitude?.let { lo -> la to lo } } }
        }
        val routes = data.trips.mapNotNull { trip ->
            val home = coords(editor.homebases.at(trip.start))
            val points = listOfNotNull(home) + trip.stays.mapNotNull { coords(it.placeId) } + listOfNotNull(home.takeIf { !trip.ongoing })
            points.fold(mutableListOf<Pair<Double, Double>>()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }.takeIf { it.size >= 2 }
        }
        val homeId = editor.homebases.at(LocalDate.of(year, 12, 31).coerceAtMost(today))
        val homePlace = homeId?.let(editor.placesById::get)
        val homeCity = homePlace?.parentPlaceId?.let(editor.placesById::get)
        YearReviewUiState(year, data, editor, work, shp, routes, (homeCity ?: homePlace)?.displayName, coords(homeId))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), YearReviewUiState(year))

    fun setIncludeWork(value: Boolean) {
        includeWork.value = value
    }
}
