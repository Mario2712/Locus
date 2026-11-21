package io.github.mario2712.locus.reminder

import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.DayTemplateRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.DaySuggestion
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Inhalt der Abend-Erinnerung. */
sealed interface ReminderContent {
    val date: LocalDate

    /** Tag ist leer: Vorschlag aus dem Muster (oder keiner). */
    data class Empty(override val date: LocalDate, val suggestion: DaySuggestion?, val label: String?) : ReminderContent

    /** Reise läuft: „Noch in Samarkand?“ mit Option Heimreise. */
    data class OnTrip(override val date: LocalDate, val baseName: String, val canReturnHome: Boolean) : ReminderContent
}

class ReminderContentBuilder @Inject constructor(
    private val stays: StayRepository,
    private val patterns: PatternRuleRepository,
    private val catalog: CatalogRepository,
    private val places: PlaceRepository,
    private val templates: DayTemplateRepository,
    private val suggester: DaySuggester,
) {
    /** @return `null`, wenn der Tag bereits eingetragen ist (dann keine Erinnerung). */
    suspend fun build(date: LocalDate): ReminderContent? {
        val me = Defaults.ME
        val allStays = stays.observeAll().first()
        if (allStays.any { me in it.participants && it.startDate == date }) return null

        val allPlaces = places.observeAll().first()
        val home = allPlaces.firstOrNull { it.isHomebase }
        val resolved = Timeline.resolve(allStays, home?.id, date)
        Timeline.baseAtStart(date, me, resolved)?.let { base ->
            val name = allPlaces.firstOrNull { it.id == base.placeId }?.displayName ?: "?"
            return ReminderContent.OnTrip(date, name, canReturnHome = home != null)
        }

        val types = catalog.observeStayTypes().first().associateBy { it.id }
        val templateMap = templates.observeAll().first().associateBy { it.id }
        val suggestion = suggester.suggest(date, me, patterns.observeAll().first(), types, templateMap)
        val placesById = allPlaces.associateBy { it.id }
        val label = suggestion?.let { s ->
            s.templateName ?: s.steps.joinToString(" → ") { step ->
                "${types[step.stayTypeId]?.name ?: "?"} · ${placesById[step.placeId]?.displayName ?: "?"}"
            }
        }
        val valid = suggestion?.steps?.all { it.placeId in placesById && it.stayTypeId in types } == true
        return ReminderContent.Empty(date, suggestion?.takeIf { valid }, label?.takeIf { valid })
    }
}

/** Aktionen aus der Benachrichtigung (ohne die App zu öffnen). */
class ReminderActions @Inject constructor(
    private val builder: ReminderContentBuilder,
    private val commands: StayCommands,
    private val settings: SettingsRepository,
    private val places: PlaceRepository,
    private val stays: StayRepository,
) {
    /** „Passt“: Vorschlag des Tages anwenden. */
    suspend fun confirm(date: LocalDate) {
        val content = builder.build(date) as? ReminderContent.Empty ?: return
        val suggestion = content.suggestion ?: return
        val participants = settings.observeEntryDefaults().first().dailyParticipants
        commands.applyTemplate(date, participants, suggestion.steps, EntrySource.PATTERN)
    }

    /** „Heimreise heute“: Station an der Homebase mit den Reisenden. */
    suspend fun returnHome(date: LocalDate) {
        val home = places.observeAll().first().firstOrNull { it.isHomebase } ?: return
        val resolved = Timeline.resolve(stays.observeAll().first(), home.id, date)
        val base = Timeline.baseAtStart(date, Defaults.ME, resolved) ?: return
        commands.returnHome(date, base.participants, home.id)
    }
}
