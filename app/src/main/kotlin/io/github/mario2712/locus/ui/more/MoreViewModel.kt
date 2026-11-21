package io.github.mario2712.locus.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DataStatus(
    val persons: List<String> = emptyList(),
    val stayTypes: Int = 0,
    val visitCategories: Int = 0,
    val places: Int = 0,
    val stays: Int = 0,
    val countries: Int = ReferenceData.countries.size,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    persons: PersonRepository,
    catalog: CatalogRepository,
    places: PlaceRepository,
    stays: StayRepository,
) : ViewModel() {

    val status: StateFlow<DataStatus> = combine(
        persons.observeAll(),
        catalog.observeStayTypes(),
        catalog.observeVisitCategories(),
        places.observeAll(),
        stays.observeAll(),
    ) { p, types, categories, pl, st ->
        DataStatus(
            persons = p.map { it.name },
            stayTypes = types.size,
            visitCategories = categories.size,
            places = pl.size,
            stays = st.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataStatus())
}
