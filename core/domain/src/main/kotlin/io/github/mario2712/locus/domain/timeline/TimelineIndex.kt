package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate

/**
 * Tages-Index über die aufgelösten Stationen **einer** Person: beantwortet „was gilt an Tag X“ ohne bei jedem
 * Tag alle Stationen zu durchsuchen. Liefert dieselben Ergebnisse wie [Timeline.baseAtStart] und
 * [Timeline.baseAtEnd], ist aber bei vielen Jahren um Größenordnungen schneller.
 */
class TimelineIndex(resolved: List<Stay>, val person: PersonId) {

    /** Eigene, taggenaue Stationen in chronologischer Reihenfolge. */
    val own: List<Stay> = resolved
        .filter { person in it.participants && it.precision == DatePrecision.DAY }
        .sortedWith(Timeline.chronological)

    private val byStart: Map<LocalDate, List<Stay>> = own.groupBy { it.startDate }

    /** Datum → Stationen, die den Tag abdecken (chronologisch). */
    private val covering: Map<LocalDate, List<Stay>> = HashMap<LocalDate, MutableList<Stay>>().also { map ->
        own.forEach { stay ->
            var d = stay.startDate
            while (!d.isAfter(stay.endDate)) {
                map.getOrPut(d) { mutableListOf() } += stay
                d = d.plusDays(1)
            }
        }
    }

    /** Alle Tage mit mindestens einer Station. */
    val coveredDays: Set<LocalDate> get() = covering.keys

    fun startingOn(date: LocalDate): List<Stay> = byStart[date].orEmpty()

    fun covering(date: LocalDate): List<Stay> = covering[date].orEmpty()

    /** Wie [Timeline.baseAtStart]. */
    fun baseAtStart(date: LocalDate): Stay? =
        covering[date]?.lastOrNull { it.overnight && it.startDate < date && it.endDate >= date }

    /** Wie [Timeline.baseAtEnd]. */
    fun baseAtEnd(date: LocalDate, homebases: Homebases, today: LocalDate? = null): Stay? {
        val lastChange = startingOn(date).lastOrNull { it.overnight || homebases.isHome(it.placeId, date) }
        return when {
            lastChange == null -> baseAtStart(date)?.takeIf { it.endDate > date || date == today }
            lastChange.overnight -> lastChange
            else -> null
        }
    }
}
