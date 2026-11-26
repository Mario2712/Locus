package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import java.time.LocalDate

/**
 * Homebases über die Zeit (Umzüge): z. B. Wohnung bei den Eltern bis 4.2.2018, dann Wohnung 2,
 * ab August 2022 Wohnung 3. Zu jedem Datum gibt es höchstens eine Homebase.
 */
class Homebases(periods: List<Period>) {

    /** Homebase ab [since]; [UNKNOWN_DATE] = „von Anfang an“. */
    data class Period(val placeId: String, val since: LocalDate)

    val periods: List<Period> = periods.sortedBy { it.since }

    val placeIds: Set<String> get() = periods.mapTo(linkedSetOf()) { it.placeId }

    /** Homebase am [date] oder `null`. */
    fun at(date: LocalDate): String? = periods.lastOrNull { !it.since.isAfter(date) }?.placeId

    fun isHome(placeId: String, date: LocalDate): Boolean = at(date) == placeId

    /** Ende des Zeitraums (letzter Tag), `null` = bis heute. */
    fun until(period: Period): LocalDate? =
        periods.getOrNull(periods.indexOf(period) + 1)?.since?.minusDays(1)

    companion object {
        val NONE = Homebases(emptyList())

        /** Eine einzige Homebase für alle Zeit. */
        fun single(placeId: String?): Homebases =
            Homebases(listOfNotNull(placeId?.let { Period(it, UNKNOWN_DATE) }))

        /** Aus den Orten: [Place.homebaseSince]; eine Homebase ohne Datum gilt „von Anfang an“. */
        fun of(places: Collection<Place>): Homebases = Homebases(
            places.filter { it.homebaseSince != null || it.isHomebase }
                .map { Period(it.id, it.homebaseSince ?: UNKNOWN_DATE) },
        )
    }
}
