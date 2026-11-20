package io.github.mario2712.locus.domain.day

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate
import java.time.LocalTime

private const val MINUTES_PER_DAY = 24 * 60

/** Zusammenfassung eines Tages einer Person. */
data class DaySummary(
    val date: LocalDate,
    /** Alle Aufenthaltsarten, die an diesem Tag vorkommen (in chronologischer Reihenfolge). */
    val typeIds: Set<String>,
    /** Hauptart: Art mit der längsten Dauer; bei fehlenden Uhrzeiten die erste des Tages. */
    val mainTypeId: String,
    /** Minuten je Art, sofern sich alle Zeiten ermitteln ließen. */
    val minutesByType: Map<String, Int>?,
) {
    fun contains(typeId: String): Boolean = typeId in typeIds

    /** z. B. für den Homeoffice-Report: Tag besteht ausschließlich aus [typeId]. */
    fun isExclusively(typeId: String): Boolean = typeIds == setOf(typeId)
}

/**
 * Klassifiziert einen Tag anhand der Aufenthalte **einer** Person.
 *
 * Fehlende Uhrzeiten werden aus den Nachbarn abgeleitet (Ende = Beginn des nächsten Aufenthalts).
 * Lässt sich die Dauer nicht vollständig bestimmen, ist die erste Art des Tages die Hauptart.
 * Unscharfe Aufenthalte (Monat/Jahr) werden ignoriert.
 */
class DayClassifier {

    fun classify(date: LocalDate, stays: List<Stay>): DaySummary? {
        val day = stays
            .filter { it.precision == DatePrecision.DAY && it.covers(date) }
            .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))
        if (day.isEmpty()) return null

        val durations = resolveMinutes(date, day)
        val typeIds = day.mapTo(linkedSetOf()) { it.typeId }

        val minutesByType = durations?.let {
            val map = LinkedHashMap<String, Int>()
            day.forEachIndexed { i, stay -> map.merge(stay.typeId, it[i], Int::plus) }
            map
        }
        val main = minutesByType?.maxByOrNull { it.value }?.key ?: day.first().typeId

        return DaySummary(date, typeIds, main, minutesByType)
    }

    private fun resolveMinutes(date: LocalDate, day: List<Stay>): List<Int>? {
        val n = day.size
        val starts = arrayOfNulls<Int>(n)
        val ends = arrayOfNulls<Int>(n)
        day.forEachIndexed { i, s ->
            starts[i] = when {
                s.startDate < date -> 0
                s.startTime != null -> s.startTime.minutes()
                i == 0 -> 0
                else -> null
            }
            ends[i] = when {
                s.endDate > date -> MINUTES_PER_DAY
                s.endTime != null -> s.endTime.minutes()
                i == n - 1 -> MINUTES_PER_DAY
                else -> null
            }
        }
        repeat(n) {
            for (i in 0 until n) {
                if (starts[i] == null && i > 0) starts[i] = ends[i - 1]
                if (ends[i] == null && i < n - 1) ends[i] = starts[i + 1]
            }
        }
        return List(n) { i ->
            val s = starts[i] ?: return null
            val e = ends[i] ?: return null
            (e - s).coerceAtLeast(0)
        }
    }

    private fun LocalTime.minutes(): Int = hour * 60 + minute
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
