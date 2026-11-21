package io.github.mario2712.locus.reminder

import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.DaySuggestion
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Inhalt der Abend-Erinnerung. */
data class ReminderContent(
    val date: LocalDate,
    val suggestion: DaySuggestion?,
    /** z. B. „Homeoffice · Hallein“. */
    val suggestionLabel: String?,
)

class ReminderContentBuilder @Inject constructor(
    private val stays: StayRepository,
    private val patterns: PatternRuleRepository,
    private val catalog: CatalogRepository,
    private val places: PlaceRepository,
    private val suggester: DaySuggester,
) {
    /** @return `null`, wenn der Tag bereits eingetragen ist (dann keine Erinnerung). */
    suspend fun build(date: LocalDate): ReminderContent? {
        val me = Defaults.ME
        val hasEntry = stays.observeBetween(date, date).first().any { me in it.participants }
        if (hasEntry) return null

        val types = catalog.observeStayTypes().first().associateBy { it.id }
        val suggestion = suggester.suggest(date, me, patterns.observeAll().first(), types)
        val place = suggestion?.let { places.get(it.first.placeId) }
        val type = suggestion?.let { types[it.first.stayTypeId] }
        return if (suggestion != null && place != null && type != null) {
            ReminderContent(date, suggestion, "${type.name} · ${place.displayName}")
        } else {
            ReminderContent(date, null, null)
        }
    }
}
