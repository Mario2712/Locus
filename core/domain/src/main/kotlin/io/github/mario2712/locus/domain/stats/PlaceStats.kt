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
    /** Besuche je zugehörigem Ort (z. B. Lokale/Museen einer Stadt), nur bei Städten. */
    val childVisits: Map<String, Int> = emptyMap(),
)

object PlaceStats {
    /**
     * Orts-Akte für [placeId]. Mit [childPlaceIds] (z. B. Lokale und Museen in einer Stadt) zählen
     * deren Stationen und Besuche mit – „Tage in Salzburg“ inklusive Museumsbesuch.
     */
    fun record(
        placeId: String,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        childPlaceIds: Set<String> = emptySet(),
    ): PlaceRecord = records(mapOf(placeId to childPlaceIds), person, resolved, visits).getValue(placeId)

    /**
     * Orts-Akten für viele Orte auf einmal ([targets]: Ort → zugehörige Örtlichkeiten). Stationen und Besuche
     * werden nur einmal nach Ort gruppiert – bei hunderten Orten um Größenordnungen schneller als einzeln.
     */
    fun records(
        targets: Map<String, Set<String>>,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
    ): Map<String, PlaceRecord> {
        val staysByPlace = resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }.groupBy { it.placeId }
        val visitsByPlace = visits.filter { person in it.participants }.groupBy { it.placeId }
        return targets.mapValues { (placeId, children) ->
            val ids = children + placeId
            build(placeId, ids.flatMap { staysByPlace[it].orEmpty() }, ids.flatMap { visitsByPlace[it].orEmpty() })
        }
    }

    private fun build(placeId: String, stays: List<Stay>, ownVisits: List<Visit>): PlaceRecord {
        val stayDayTypes = stays.flatMap { stay ->
            generateSequence(stay.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(stay.endDate) }.map { it to stay.typeId }.toList()
        }
        val stayDates = stayDayTypes.map { it.first }.toSet()
        val allDates = (stayDates + ownVisits.map { it.date }).toSortedSet()
        return PlaceRecord(
            stayDays = stayDates.size,
            daysByType = stayDayTypes.distinct().filter { it.second != io.github.mario2712.locus.domain.timeline.ImplicitHome.TYPE_ID }
                .groupingBy { it.second }.eachCount(),
            visitCount = ownVisits.size,
            visitsByCategory = ownVisits.flatMap { it.categoryIds }.groupingBy { it }.eachCount(),
            totalDays = allDates.size,
            firstDate = allDates.firstOrNull(),
            lastDate = allDates.lastOrNull(),
            dates = allDates.toList().asReversed(),
            childVisits = (stays.map { it.placeId } + ownVisits.map { it.placeId })
                .filter { it != placeId }.groupingBy { it }.eachCount(),
        )
    }
}
