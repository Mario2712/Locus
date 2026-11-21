package io.github.mario2712.locus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.EntryDefaults
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.DayTemplateRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.PlaceDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
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
    private val templates: DayTemplateRepository,
    private val places: PlaceRepository,
    private val editorCatalog: EditorCatalog,
    private val catalog: io.github.mario2712.locus.domain.repository.CatalogRepository,
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
                .mapValues { (_, list) -> list.maxBy { it.validFrom ?: LocalDate.MIN } },
            reminder = reminder,
            editor = editor,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), SettingsUiState())

    fun setHomebase(placeId: String) = viewModelScope.launch { places.setHomebase(placeId) }

    fun saveTemplate(template: DayTemplate) = viewModelScope.launch { templates.upsert(template) }
    fun deleteTemplate(id: String) = viewModelScope.launch { templates.delete(id) }
    fun newTemplateId(): String = UUID.randomUUID().toString()

    /** Muster: Tagesvorlage für einen Wochentag. */
    fun setRuleTemplate(weekday: DayOfWeek, template: DayTemplate) = upsertRule(weekday) {
        it.copy(stayTypeId = template.steps.first().stayTypeId, placeId = template.steps.first().placeId, templateId = template.id)
    }

    /** Muster: einzelne Station für einen Wochentag. */
    fun setRuleSingle(weekday: DayOfWeek, typeId: String, placeId: String) = upsertRule(weekday) {
        it.copy(stayTypeId = typeId, placeId = placeId, templateId = null)
    }

    private fun upsertRule(weekday: DayOfWeek, change: (PatternRule) -> PatternRule) = viewModelScope.launch {
        val existing = state.value.rules[weekday]
            ?: PatternRule("rule-${me.value}-${weekday.value}", me, weekday, "", "")
        patterns.upsert(change(existing))
    }

    fun clearRule(weekday: DayOfWeek) = viewModelScope.launch {
        state.value.rules[weekday]?.let { patterns.delete(it.id) }
    }

    fun saveStayType(type: io.github.mario2712.locus.domain.model.StayType) = viewModelScope.launch { catalog.upsertStayType(type) }
    fun saveVisitCategory(category: io.github.mario2712.locus.domain.model.VisitCategory) =
        viewModelScope.launch { catalog.upsertVisitCategory(category) }
    fun newCustomId(prefix: String): String = "$prefix-custom-" + UUID.randomUUID().toString()

    fun setEntryDefaults(defaults: EntryDefaults) = viewModelScope.launch { settings.setEntryDefaults(defaults) }

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setReminder(state.value.reminder.copy(enabled = enabled))
    }

    fun setReminderTime(time: LocalTime) = viewModelScope.launch {
        settings.setReminder(state.value.reminder.copy(time = time))
    }

    suspend fun createPlace(draft: PlaceDraft): Place = editorCatalog.createPlace(draft)
}
