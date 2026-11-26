package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.reference.ReferenceData
import java.time.LocalDate

/** Ein besonderer Tag aus einem früheren Jahr. */
data class Memory(
    val yearsAgo: Int,
    val date: LocalDate,
    /** Wo: Unterkunft bzw. Stationen außerhalb des Alltags. */
    val placeIds: List<String>,
    /** Besondere Besuche (Sehenswürdigkeit, Restaurant …). */
    val visitPlaceIds: List<String>,
    /** Länder außerhalb des damaligen Homebase-Landes. */
    val countries: List<String>,
)

/**
 * „Heute vor X Jahren“: nur besondere Tage – auf Reise, im Ausland oder mit Besuchen, die mehr als Alltag sind
 * (nicht Einkaufen, Arzt, Rast, Sonstiges).
 */
object OnThisDay {
    private val everydayCategories = setOf(
        VisitCategoryIds.SHOPPING, VisitCategoryIds.HEALTH, VisitCategoryIds.REST_STOP, VisitCategoryIds.OTHER,
    )
    private val everydayTypes = setOf(StayTypeIds.HOMEOFFICE, StayTypeIds.OFFICE, StayTypeIds.FREE, StayTypeIds.TRANSIT, StayTypeIds.SICK)

    fun find(
        today: LocalDate,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        homebases: Homebases,
    ): List<Memory> {
        val index = TimelineIndex(resolved, person)
        val ownVisits = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }.groupBy { it.date }
        val first = (index.own.map { it.startDate } + ownVisits.keys).minOrNull() ?: return emptyList()
        fun country(id: String) = places[id]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
        return (1..(today.year - first.year)).mapNotNull { years ->
            val date = today.minusYears(years.toLong())
            if (date < first) return@mapNotNull null
            val homeCountry = homebases.at(date)?.let(::country)
            val base = index.baseAtStart(date) ?: index.baseAtEnd(date, homebases, today)
            val stays = index.covering(date)
            val special = stays.filter { s ->
                s.typeId !in everydayTypes || (stayTypes[s.typeId]?.countsForCountryPoints == true && country(s.placeId) != homeCountry)
            }
            val dayVisits = ownVisits[date].orEmpty().filter { v -> v.categoryIds.any { it !in everydayCategories } }
            val placeIds = (listOfNotNull(base?.placeId) + special.map { it.placeId }).distinct()
            val countries = (stays.filter { stayTypes[it.typeId]?.countsForCountryPoints == true }.map { it.placeId } +
                ownVisits[date].orEmpty().filter { v -> v.countsForCountryPointsOverride ?: v.categoryIds.any { visitCategories[it]?.countsForCountryPoints == true } }
                    .map { it.placeId })
                .mapNotNull(::country).distinct().filter { it != homeCountry }
            if (placeIds.isEmpty() && dayVisits.isEmpty() && countries.isEmpty()) return@mapNotNull null
            Memory(years, date, placeIds, dayVisits.map { it.placeId }.distinct(), countries)
        }
    }
}
