package io.github.mario2712.locus.domain.day

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate

/** Zusammenfassung eines Tages einer Person. */
data class DaySummary(
    val date: LocalDate,
    /** Alle Aufenthaltsarten, die an diesem Tag vorkommen (in chronologischer Reihenfolge). */
    val typeIds: Set<String>,
    /** Hauptart nach [StayPriority] (z. B. Büro → Homeoffice ergibt Büro). */
    val mainTypeId: String,
) {
    fun contains(typeId: String): Boolean = typeId in typeIds

    /** z. B. für den Homeoffice-Report: Tag besteht ausschließlich aus [typeId]. */
    fun isExclusively(typeId: String): Boolean = typeIds == setOf(typeId)
}

/**
 * Klassifiziert einen Tag anhand der (aufgelösten) Stationen **einer** Person.
 * Die Hauptart ergibt sich aus der Rangfolge [rank] (kleiner = wichtiger), bei Gleichstand
 * gewinnt die frühere Station. Unscharfe Aufenthalte (Monat/Jahr) werden ignoriert.
 */
class DayClassifier(private val rank: (String) -> Int = { 0 }) {

    fun classify(date: LocalDate, stays: List<Stay>): DaySummary? {
        val day = stays
            .filter { it.precision == DatePrecision.DAY && it.covers(date) }
            .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))
        if (day.isEmpty()) return null
        val typeIds = day.mapTo(linkedSetOf()) { it.typeId }
        val main = typeIds.minBy(rank)
        return DaySummary(date, typeIds, main)
    }
}

/** Einfache Tagesstatistik über eine Menge von Tageszusammenfassungen. */
object DayStatistics {
    /** Anzahl Tage, an denen eine Art vorkommt („Tage mit Büro“). Ein geteilter Tag zählt in beiden Arten. */
    fun daysWithType(days: Iterable<DaySummary>): Map<String, Int> =
        days.flatMap { it.typeIds }.groupingBy { it }.eachCount()

    /** Anzahl Tage je Hauptart. Jeder Tag zählt genau einmal. */
    fun daysByMainType(days: Iterable<DaySummary>): Map<String, Int> =
        days.groupingBy { it.mainTypeId }.eachCount()

    /** Anzahl Tage, die ausschließlich aus [typeId] bestehen (z. B. reine Homeoffice-Tage). */
    fun exclusiveDays(days: Iterable<DaySummary>, typeId: String): Int =
        days.count { it.isExclusively(typeId) }
}
