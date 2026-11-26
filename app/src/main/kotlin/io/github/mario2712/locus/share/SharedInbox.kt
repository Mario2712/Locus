package io.github.mario2712.locus.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import javax.inject.Singleton

/** Von anderen Apps geteilter Text (z. B. Google-Maps-Link), bis die Oberfläche ihn verarbeitet hat. */
@Singleton
class SharedInbox @Inject constructor() {
    private val pending = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = pending

    fun offer(text: String) {
        pending.value = text
    }

    fun consume() {
        pending.value = null
    }

    /** Aus einer Benachrichtigung angeforderter Jahresrückblick. */
    private val review = MutableStateFlow<Int?>(null)
    val reviewYear: StateFlow<Int?> = review

    fun openReview(year: Int) {
        review.value = year
    }

    fun reviewOpened() {
        review.value = null
    }
}

/** Stellt der Oberfläche den geteilten Text und die Städte (für die Zuordnung) bereit. */
@dagger.hilt.android.lifecycle.HiltViewModel
class ShareViewModel @Inject constructor(
    private val inbox: SharedInbox,
    editorCatalog: io.github.mario2712.locus.ui.common.EditorCatalog,
) : androidx.lifecycle.ViewModel() {
    val text: StateFlow<String?> = inbox.text
    val cities: StateFlow<List<io.github.mario2712.locus.domain.model.Place>> = kotlinx.coroutines.flow.combine(editorCatalog.data, inbox.text) { e, _ -> e.cities }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun done() = inbox.consume()
    val reviewYear: StateFlow<Int?> = inbox.reviewYear
    fun reviewOpened() = inbox.reviewOpened()
}
