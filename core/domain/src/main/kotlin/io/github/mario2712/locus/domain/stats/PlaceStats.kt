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
    /** Übernachtungs-Aufenthalte (ohne Zuhause), neueste zuerst; aneinander anschließende zusammengefasst. */
    val stayPeriods: List<StayPeriod> = emptyList(),
    /** Tage, an denen eine andere Person dabei war (Person → Anzahl Tage). */
    val daysWith: Map<PersonId, Int> = emptyMap(),
) {
    /** Durchschnittlicher Abstand zwischen zwei Tagen dort, `null` bei weniger als zwei Tagen. */
    val averageIntervalDays: Double?
        get() = if (totalDays < 2 || firstDate == null || lastDate == null) null
        else java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate).toDouble() / (totalDays - 1)

    /** Tage seit (einschließlich) [from]. */
    fun daysSince(from: LocalDate): Int = dates.count { !it.isBefore(from) }

    /** Tage je Wochentag (Mo..So, immer 7 Einträge). */
    val byWeekday: Map<java.time.DayOfWeek, Int>
        get() = java.time.DayOfWeek.entries.associateWith { d -> dates.count { it.dayOfWeek == d } }

    /** Tage je Jahr, neuestes zuerst. */
    val byYear: List<Pair<Int, Int>>
        get() = dates.groupingBy { it.year }.eachCount().entries.sortedByDescending { it.key }.map { it.key to it.value }

    /** Summe der Nächte aller Aufenthalte. */
    val nights: Int get() = stayPeriods.sumOf { it.nights }
}

/** Ein Aufenthalt mit Übernachtung: von der Ankunft bis zum Wechsel der Basis. */
data class StayPeriod(val start: LocalDate, val end: LocalDate) {
    val nights: Int get() = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
}

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
        homebases: io.github.mario2712.locus.domain.timeline.Homebases? = null,
    ): PlaceRecord = records(mapOf(placeId to childPlaceIds), person, resolved, visits, homebases).getValue(placeId)

    /**
     * Orts-Akten für viele Orte auf einmal ([targets]: Ort → zugehörige Örtlichkeiten). Stationen und Besuche
     * werden nur einmal nach Ort gruppiert – bei hunderten Orten um Größenordnungen schneller als einzeln.
     */
    fun records(
        targets: Map<String, Set<String>>,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        homebases: io.github.mario2712.locus.domain.timeline.Homebases? = null,
    ): Map<String, PlaceRecord> {
        val staysByPlace = resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }.groupBy { it.placeId }
        val visitsByPlace = visits.filter { person in it.participants }.groupBy { it.placeId }
        return targets.mapValues { (placeId, children) ->
            val ids = children + placeId
            build(placeId, person, ids.flatMap { staysByPlace[it].orEmpty() }, ids.flatMap { visitsByPlace[it].orEmpty() }, homebases)
        }
    }

    private fun build(
        placeId: String,
        person: PersonId,
        stays: List<Stay>,
        ownVisits: List<Visit>,
        homebases: io.github.mario2712.locus.domain.timeline.Homebases?,
    ): PlaceRecord {
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
            stayPeriods = periods(stays, homebases),
            daysWith = daysWith(person, stays, ownVisits),
        )
    }

    /** Übernachtungs-Basen (nicht Zuhause) als Zeiträume; schließt einer direkt an, wird zusammengefasst. */
    private fun periods(stays: List<Stay>, homebases: io.github.mario2712.locus.domain.timeline.Homebases?): List<StayPeriod> {
        val raw = stays.filter { s ->
            s.overnight && s.endDate.isAfter(s.startDate) &&
                s.typeId != io.github.mario2712.locus.domain.timeline.ImplicitHome.TYPE_ID &&
                homebases?.isHome(s.placeId, s.startDate) != true
        }.map { StayPeriod(it.startDate, it.endDate) }.sortedBy { it.start }
        val merged = mutableListOf<StayPeriod>()
        raw.forEach { p ->
            val last = merged.lastOrNull()
            if (last != null && !p.start.isAfter(last.end)) merged[merged.lastIndex] = StayPeriod(last.start, maxOf(last.end, p.end))
            else merged += p
        }
        return merged.asReversed()
    }

    private fun daysWith(person: PersonId, stays: List<Stay>, visits: List<Visit>): Map<PersonId, Int> {
        val pairs = mutableSetOf<Pair<PersonId, LocalDate>>()
        stays.forEach { s ->
            val others = s.participants - person
            if (others.isEmpty()) return@forEach
            var d = s.startDate
            while (!d.isAfter(s.endDate)) { others.forEach { pairs += it to d }; d = d.plusDays(1) }
        }
        visits.forEach { v -> (v.participants - person).forEach { pairs += it to v.date } }
        return pairs.groupingBy { it.first }.eachCount()
    }
}
