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
)

@HiltViewModel
class YearReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    private val year = savedState.toRoute<YearReviewRoute>().year
    private val today = LocalDate.now(clock)
    private val includeWork = MutableStateFlow(false)

    val state: StateFlow<YearReviewUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data, includeWork) { s, v, editor, work ->
        val me = editor.persons.firstOrNull { it.isMe }
        val partner = editor.persons.firstOrNull { !it.isMe }
        val data = YearReview.build(
            year, me?.id ?: Defaults.ME, partner?.id, me?.birthDate, s, v, editor.placesById, editor.typesById,
            editor.categoriesById, editor.rank, editor.homebases, today, includeWork = work,
        )
        YearReviewUiState(year, data, editor, work)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), YearReviewUiState(year))

    fun setIncludeWork(value: Boolean) {
        includeWork.value = value
    }
}
