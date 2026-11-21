package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import java.time.LocalDate

/** Vorschlag für einen Tag: Art und Ort, z. B. „Homeoffice · Hallein“. */
data class DaySuggestion(
    val date: LocalDate,
    val stayTypeId: String,
    val placeId: String,
    val ruleId: String,
)

/**
 * Schlägt anhand der Wochentag-Muster vor, wo man an einem Tag war.
 * An österreichischen Feiertagen werden Arbeits-Muster (Gruppe WORK) übersprungen.
 */
class DaySuggester(
    private val isHoliday: (LocalDate) -> Boolean = AustrianHolidays::isHoliday,
) {
    fun suggest(
        date: LocalDate,
        person: PersonId,
        rules: List<PatternRule>,
        stayTypes: Map<String, StayType>,
    ): DaySuggestion? {
        val holiday = isHoliday(date)
        return rules
            .asSequence()
            .filter { it.personId == person && it.appliesTo(date) }
            .filter { rule -> !(holiday && stayTypes[rule.stayTypeId]?.group == StayGroup.WORK) }
            .filter { rule -> stayTypes[rule.stayTypeId]?.isArchived != true }
            // Bei mehreren passenden Regeln gewinnt die mit dem spätesten Gültigkeitsbeginn.
            .maxByOrNull { it.validFrom ?: LocalDate.MIN }
            ?.let { DaySuggestion(date, it.stayTypeId, it.placeId, it.id) }
    }
}
