package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import java.time.LocalDate

/** Orts-Akte: alles, was man über einen Ort weiß. */
data class PlaceRecord(
    /** Tage mit einer Station dort (inkl. Tage einer laufenden Basis). */
    val stayDays: Int,
    /** Tage je Aufenthaltsart. */
    val daysByType: Map<String, Int>,
    val visitCount: Int,
    /** Besuche je Kategorie. */
    val visitsByCategory: Map<String, Int>,
    /** Alle Tage mit Station oder Besuch. */
    val totalDays: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    /** Neueste zuerst. */
    val dates: List<LocalDate>,
)

object PlaceStats {
    fun record(placeId: String, person: PersonId, resolved: List<Stay>, visits: List<Visit>): PlaceRecord {
        val stays = resolved.filter { it.placeId == placeId && person in it.participants && it.precision == DatePrecision.DAY }
        val ownVisits = visits.filter { it.placeId == placeId && person in it.participants }
        val stayDayTypes = stays.flatMap { stay ->
            generateSequence(stay.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(stay.endDate) }.map { it to stay.typeId }.toList()
        }
        val stayDates = stayDayTypes.map { it.first }.toSet()
        val allDates = (stayDates + ownVisits.map { it.date }).toSortedSet()
        return PlaceRecord(
            stayDays = stayDates.size,
            daysByType = stayDayTypes.distinct().groupingBy { it.second }.eachCount(),
            visitCount = ownVisits.size,
            visitsByCategory = ownVisits.flatMap { it.categoryIds }.groupingBy { it }.eachCount(),
            totalDays = allDates.size,
            firstDate = allDates.firstOrNull(),
            lastDate = allDates.lastOrNull(),
            dates = allDates.toList().asReversed(),
        )
    }
}
