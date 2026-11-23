package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import java.time.LocalDate

/** Ein Tag in der Timeline: Basis am Morgen, Stationen des Tages, Besuche. */
data class TimelineDay(
    val date: LocalDate,
    val baseAtStart: Stay?,
    val stations: List<Stay>,
    val visits: List<Visit>,
)

object TimelineDays {
    /**
     * Alle Tage mit Einträgen (Stationen, Besuche oder laufende Reise) der [person], neueste zuerst.
     * Erwartet aufgelöste Stationen. Unscharfe Einträge erscheinen nicht als Tage.
     */
    fun build(resolved: List<Stay>, visits: List<Visit>, person: PersonId, today: LocalDate): List<TimelineDay> {
        val own = resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val ownVisits = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val first = (own.map { it.startDate } + ownVisits.map { it.date }).minOrNull() ?: return emptyList()
        val last = maxOf(today, (own.map { it.startDate } + ownVisits.map { it.date }).max())
        val visitsByDate = ownVisits.groupBy { it.date }
        val index = TimelineIndex(own, person)
        return generateSequence(last) { it.minusDays(1) }
            .takeWhile { !it.isBefore(first) }
            .mapNotNull { date ->
                val stations = index.covering(date).filter { it.startDate == date || !it.overnight }
                    .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))
                val base = index.baseAtStart(date)
                val dayVisits = visitsByDate[date].orEmpty()
                if (stations.isEmpty() && dayVisits.isEmpty() && base == null) null
                else TimelineDay(date, base, stations, dayVisits)
            }
            .toList()
    }
}
