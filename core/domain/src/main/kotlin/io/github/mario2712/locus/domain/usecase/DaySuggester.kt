package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.TemplateStep
import java.time.LocalDate

/** Vorschlag für einen Tag: eine oder mehrere Stationen, z. B. „Büro → Homeoffice“. */
data class DaySuggestion(
    val date: LocalDate,
    val steps: List<TemplateStep>,
    val ruleId: String,
    /** Name der Vorlage, falls das Muster eine benannte Vorlage nutzt. */
    val templateName: String? = null,
) {
    val first: TemplateStep get() = steps.first()
}

/**
 * Schlägt anhand der Wochentag-Muster vor, wie ein Tag aussah.
 * An österreichischen Feiertagen werden Muster mit Arbeits-Stationen (Gruppe WORK) übersprungen.
 */
class DaySuggester(
    private val isHoliday: (LocalDate) -> Boolean = AustrianHolidays::isHoliday,
) {
    fun suggest(
        date: LocalDate,
        person: PersonId,
        rules: List<PatternRule>,
        stayTypes: Map<String, StayType>,
        templates: Map<String, DayTemplate> = emptyMap(),
    ): DaySuggestion? {
        val holiday = isHoliday(date)
        return rules
            .asSequence()
            .filter { it.personId == person && it.appliesTo(date) }
            .map { rule -> rule to rule.steps(templates) }
            .filter { (_, steps) -> !(holiday && steps.any { stayTypes[it.stayTypeId]?.group == StayGroup.WORK }) }
            .filter { (_, steps) -> steps.none { stayTypes[it.stayTypeId]?.isArchived == true } }
            // Bei mehreren passenden Regeln gewinnt die mit dem spätesten Gültigkeitsbeginn.
            .maxByOrNull { (rule, _) -> rule.validFrom ?: LocalDate.MIN }
            ?.let { (rule, steps) -> DaySuggestion(date, steps, rule.id, rule.templateId?.let { templates[it]?.name }) }
    }
}
