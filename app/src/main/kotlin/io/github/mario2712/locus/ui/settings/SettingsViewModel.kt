package io.github.mario2712.locus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val rules: Map<DayOfWeek, PatternRule> = emptyMap(),
    val reminder: ReminderSettings = ReminderSettings(),
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val patterns: PatternRuleRepository,
    private val settings: SettingsRepository,
    private val editorCatalog: EditorCatalog,
) : ViewModel() {

    private val me = Defaults.ME

    val state: StateFlow<SettingsUiState> = combine(
        patterns.observeAll(),
        settings.observeReminder(),
        editorCatalog.data,
    ) { rules, reminder, editor ->
        SettingsUiState(
            // Eine Regel je Wochentag (die zuletzt gültige), für „Ich“.
            rules = rules.filter { it.personId == me }
                .groupBy { it.weekday }
                .mapValues { (_, list) -> list.maxBy { it.validFrom ?: java.time.LocalDate.MIN } },
            reminder = reminder,
            editor = editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setRule(weekday: DayOfWeek, typeId: String, placeId: String) = viewModelScope.launch {
        val existing = state.value.rules[weekday]
        patterns.upsert(
            PatternRule(
                id = existing?.id ?: "rule-${me.value}-${weekday.value}",
                personId = me, weekday = weekday, stayTypeId = typeId, placeId = placeId,
            ),
        )
    }

    fun clearRule(weekday: DayOfWeek) = viewModelScope.launch {
        state.value.rules[weekday]?.let { patterns.delete(it.id) }
    }

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setReminder(state.value.reminder.copy(enabled = enabled))
    }

    fun setReminderTime(time: LocalTime) = viewModelScope.launch {
        settings.setReminder(state.value.reminder.copy(time = time))
    }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
