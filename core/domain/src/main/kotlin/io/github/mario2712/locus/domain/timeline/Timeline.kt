package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate

/**
 * Löst die Stationen-Logik auf:
 *
 * - **Basis:** Wo man übernachtet. Standard ist die Homebase; eine Station mit `overnight`
 *   wird zur neuen Basis (Taschkent, Samarkand, Wien …).
 * - **Laufende Basis:** Eine Übernachtungs-Station ohne explizites Ende gilt bis zur nächsten
 *   Basis-Änderung derselben Person – also bis zur nächsten Übernachtungs-Station („Weiterreise“)
 *   oder einer Station an der Homebase („Heimreise“) – bzw. bis heute.
 * - **Tages-Stationen** (Büro, Homeoffice, Ausflug) enden am Tagesende, man ist zurück an der Basis.
 */
object Timeline {

    val chronological: Comparator<Stay> = compareBy<Stay>({ it.startDate }, { it.sortIndex })

    /** Stationen mit effektivem Enddatum (laufende Basen werden bis zur nächsten Basis verlängert). */
    fun resolve(stays: List<Stay>, homebaseId: String?, today: LocalDate): List<Stay> =
        resolve(stays, Homebases.single(homebaseId), today)

    fun resolve(stays: List<Stay>, homebases: Homebases, today: LocalDate): List<Stay> {
        val sorted = stays.sortedWith(chronological)
        return sorted.mapIndexed { index, stay ->
            if (!stay.isOpenBase()) return@mapIndexed stay
            val next = sorted.asSequence().drop(index + 1).firstOrNull { other ->
                other.precision == DatePrecision.DAY &&
                    other.participants.any { it in stay.participants } &&
                    (other.overnight || homebases.isHome(other.placeId, other.startDate))
            }
            val end = next?.startDate ?: maxOf(stay.startDate, today)
            stay.copy(endDate = end)
        }
    }

    /**
     * Basis-Station der [person] zu Beginn von [date] (wo sie die Nacht davor verbracht hat),
     * oder `null` für die Homebase. Erwartet aufgelöste Stationen ([resolve]).
     */
    fun baseAtStart(date: LocalDate, person: PersonId, resolved: List<Stay>): Stay? =
        resolved
            .filter { it.overnight && person in it.participants && it.precision == DatePrecision.DAY }
            .lastOrNull { it.startDate < date && it.endDate >= date }

    /**
     * Aktuelle Basis der [person] am Ende von [date] (Übernachtungsort), `null` = Homebase.
     * Eine laufende Basis endet in [resolve] „heute“; an [today] gilt sie daher weiterhin als Basis.
     */
    fun baseAtEnd(
        date: LocalDate,
        person: PersonId,
        resolved: List<Stay>,
        homebaseId: String?,
        today: LocalDate? = null,
    ): Stay? = baseAtEnd(date, person, resolved, Homebases.single(homebaseId), today)

    fun baseAtEnd(
        date: LocalDate,
        person: PersonId,
        resolved: List<Stay>,
        homebases: Homebases,
        today: LocalDate? = null,
    ): Stay? {
        val dayStays = resolved
            .filter { person in it.participants && it.precision == DatePrecision.DAY && it.startDate == date }
            .sortedWith(chronological)
        val lastChange = dayStays.lastOrNull { it.overnight || homebases.isHome(it.placeId, date) }
        return when {
            lastChange == null -> baseAtStart(date, person, resolved)?.takeIf { it.endDate > date || date == today }
            lastChange.overnight -> lastChange
            else -> null
        }
    }

    /** Erster Tag der laufenden Reise: zusammenhängende Basen außerhalb der Homebase. */
    fun tripStart(base: Stay, person: PersonId, resolved: List<Stay>): LocalDate {
        val bases = resolved
            .filter { it.overnight && person in it.participants && it.precision == DatePrecision.DAY }
            .sortedWith(chronological)
        var index = bases.indexOfFirst { it.id == base.id }
        if (index < 0) return base.startDate
        var start = bases[index]
        while (index > 0 && bases[index - 1].endDate >= start.startDate) {
            index--
            start = bases[index]
        }
        return start.startDate
    }

    private fun Stay.isOpenBase(): Boolean =
        overnight && precision == DatePrecision.DAY && endDate == startDate
}
