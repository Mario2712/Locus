package io.github.mario2712.locus.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@JvmInline
value class PersonId(val value: String)

data class Person(
    val id: PersonId,
    val name: String,
    val isMe: Boolean,
)

/** Genauigkeit einer Datumsangabe. Unscharfe Angaben zählen nicht für Tagesstatistiken. */
enum class DatePrecision { DAY, MONTH, YEAR }

/** Fachliche Gruppe einer Aufenthaltsart – Basis für Statistiken. */
enum class StayGroup { WORK, FREE, VACATION, OTHER }

data class StayType(
    val id: String,
    val name: String,
    val group: StayGroup,
    /** z. B. `false` für „Transit“ und „Anreise/Unterwegs“. */
    val countsForCountryPoints: Boolean,
)

data class VisitCategory(
    val id: String,
    val name: String,
    /** z. B. `false` für „Rast/Tankstopp“. */
    val countsForCountryPoints: Boolean,
)

enum class PlaceKind { CITY, POI, ADDRESS }

data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    /** ISO-3166-1 alpha-2, z. B. "AT", "SI". */
    val countryIso2: String,
    /** ISO-3166-2, z. B. "AT-5" (Salzburg); nur wenn bekannt. */
    val regionCode: String? = null,
)

/**
 * Ein Aufenthalt: an Ort X, Art Y, von–bis, mit Personen Z.
 *
 * Bei unscharfer Genauigkeit (MONTH/YEAR) decken [startDate]..[endDate] den gesamten
 * Zeitraum ab (z. B. 2014-07-01..2014-07-31 für „Juli 2014“).
 * [sortIndex] legt die chronologische Reihenfolge innerhalb eines Tages fest,
 * wenn Uhrzeiten fehlen.
 */
data class Stay(
    val id: String,
    val placeId: String,
    val typeId: String,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val precision: DatePrecision = DatePrecision.DAY,
    val sortIndex: Int = 0,
    val participants: Set<PersonId>,
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate darf nicht vor startDate liegen" }
    }

    val dateRange: ClosedRange<LocalDate> get() = startDate..endDate

    fun covers(date: LocalDate): Boolean = date in dateRange
}

/** Ein konkreter, bemerkenswerter Ort an einem Datum (Sehenswürdigkeit, Restaurant, Gipfel …). */
data class Visit(
    val id: String,
    val placeId: String,
    val categoryId: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val precision: DatePrecision = DatePrecision.DAY,
    val participants: Set<PersonId>,
    /** Überschreibt die Regel der Kategorie, falls gesetzt. */
    val countsForCountryPointsOverride: Boolean? = null,
) {
    val dateRange: ClosedRange<LocalDate>
        get() = when (precision) {
            DatePrecision.DAY -> date..date
            DatePrecision.MONTH -> YearMonth.from(date).let { it.atDay(1)..it.atEndOfMonth() }
            DatePrecision.YEAR -> LocalDate.of(date.year, 1, 1)..LocalDate.of(date.year, 12, 31)
        }
}
