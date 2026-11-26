package io.github.mario2712.locus.domain.countrypoints

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Homebases
import java.time.LocalDate

/** Länder je Tag, die nicht das Land der damaligen Homebase sind – z. B. für Flaggen im Kalender. */
object ForeignDays {

    /**
     * @param resolved aufgelöste Stationen (laufende Reisen verlängert)
     * @return Datum → zählbare Länder (ISO), nur Einträge, die für Länderpunkte zählen; Transit nicht.
     */
    fun of(
        from: LocalDate,
        to: LocalDate,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        homebases: Homebases,
    ): Map<LocalDate, List<String>> {
        fun countryOf(placeId: String) = places[placeId]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
        fun homeCountry(date: LocalDate) = homebases.at(date)?.let(::countryOf)

        val result = sortedMapOf<LocalDate, LinkedHashSet<String>>()
        resolved.asSequence()
            .filter { person in it.participants && it.precision == DatePrecision.DAY }
            .filter { stayTypes[it.typeId]?.countsForCountryPoints == true }
            .filter { it.startDate <= to && it.endDate >= from }
            .forEach { stay ->
                val iso = countryOf(stay.placeId) ?: return@forEach
                var day = maxOf(stay.startDate, from)
                val end = minOf(stay.endDate, to)
                while (!day.isAfter(end)) {
                    if (iso != homeCountry(day)) result.getOrPut(day) { linkedSetOf() } += iso
                    day = day.plusDays(1)
                }
            }
        visits.asSequence()
            .filter { person in it.participants && it.precision == DatePrecision.DAY && it.date in from..to }
            .filter { v -> v.countsForCountryPointsOverride ?: v.categoryIds.any { visitCategories[it]?.countsForCountryPoints == true } }
            .forEach { v ->
                val iso = countryOf(v.placeId) ?: return@forEach
                if (iso != homeCountry(v.date)) result.getOrPut(v.date) { linkedSetOf() } += iso
            }
        return result.mapValues { it.value.toList() }
    }
}
