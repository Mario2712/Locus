package io.github.mario2712.locus.domain.model

import io.github.mario2712.locus.domain.util.flagEmoji
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@JvmInline
value class PersonId(val value: String)

data class Person(
    val id: PersonId,
    val name: String,
    val isMe: Boolean,
    /** Farbe als ARGB, z. B. für Karte und Statistik. */
    val colorArgb: Long = 0xFF1F6F5C,
)

enum class Continent(val nameDe: String) {
    EUROPE("Europa"),
    ASIA("Asien"),
    AFRICA("Afrika"),
    NORTH_AMERICA("Nordamerika"),
    SOUTH_AMERICA("Südamerika"),
    OCEANIA("Ozeanien"),
}

/** Ein zählbares Land (Stammdaten, siehe [io.github.mario2712.locus.domain.reference.ReferenceData]). */
data class Country(
    val iso2: String,
    val iso3: String,
    val nameDe: String,
    val continent: Continent,
) {
    val flag: String get() = flagEmoji(iso2)
}

/** Region nach ISO 3166-2, z. B. "AT-5" Salzburg. */
data class Region(
    val code: String,
    val countryIso2: String,
    val nameDe: String,
)

/** Ob für ein Land Regionen (Bundesländer) gesammelt werden. */
data class RegionCollection(
    val countryIso2: String,
    val enabled: Boolean,
)

/** Genauigkeit einer Datumsangabe. Unscharfe Angaben zählen nicht für Tagesstatistiken. */
enum class DatePrecision { DAY, MONTH, YEAR }

/** Fachliche Gruppe einer Aufenthaltsart – Basis für Statistiken. */
enum class StayGroup { WORK, FREE, VACATION, OTHER }

/** Woher ein Eintrag stammt. */
enum class EntrySource { MANUAL, PATTERN, LINK, PHOTO, GPS, IMPORT }

data class StayType(
    val id: String,
    val name: String,
    val group: StayGroup,
    /** z. B. `false` für „Transit“ und „Anreise/Unterwegs“. */
    val countsForCountryPoints: Boolean,
    /** Schlüssel für das Icon in der UI. */
    val icon: String = "",
    val colorArgb: Long = 0xFF888888,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

data class VisitCategory(
    val id: String,
    val name: String,
    /** z. B. `false` für „Rast/Tankstopp“. */
    val countsForCountryPoints: Boolean,
    val icon: String = "",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cityName: String? = null,
    /** POI → zugehörige Stadt. */
    val parentPlaceId: String? = null,
    /** OSM-Referenz, z. B. "node/123", zur Wiedererkennung bei erneuter Suche. */
    val osmId: String? = null,
    val poiCategory: String? = null,
    /** Eigener Kurzname, z. B. „Zuhause“, „Büro“. */
    val alias: String? = null,
    val isFavorite: Boolean = false,
    val note: String? = null,
) {
    val displayName: String get() = alias ?: name
}

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
    val tripId: String? = null,
    val note: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate darf nicht vor startDate liegen" }
        require(participants.isNotEmpty()) { "Ein Aufenthalt braucht mindestens eine Person" }
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
    val tripId: String? = null,
    val note: String? = null,
    /** 1–5 Sterne, optional. */
    val rating: Int? = null,
    /** Verweis auf ein Foto in der Galerie (content://…), keine Kopie. */
    val photoUri: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
) {
    init {
        require(participants.isNotEmpty()) { "Ein Besuch braucht mindestens eine Person" }
        require(rating == null || rating in 1..5) { "Bewertung muss zwischen 1 und 5 liegen" }
    }

    val dateRange: ClosedRange<LocalDate>
        get() = when (precision) {
            DatePrecision.DAY -> date..date
            DatePrecision.MONTH -> YearMonth.from(date).let { it.atDay(1)..it.atEndOfMonth() }
            DatePrecision.YEAR -> LocalDate.of(date.year, 1, 1)..LocalDate.of(date.year, 12, 31)
        }
}

/** Klammer um mehrere Tage, z. B. „Ljubljana Kurzurlaub“, optional mit Uhrzeit für Start/Ende. */
data class Trip(
    val id: String,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val precision: DatePrecision = DatePrecision.DAY,
    val participants: Set<PersonId>,
    val note: String? = null,
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate darf nicht vor startDate liegen" }
        require(title.isNotBlank()) { "Eine Reise braucht einen Titel" }
    }
}

/** Merkliste: ein Ort oder ein ganzes Land, das man noch besuchen möchte. */
data class Wish(
    val id: String,
    val placeId: String? = null,
    val countryIso2: String? = null,
    /** `null` = für beide. */
    val personId: PersonId? = null,
    val note: String? = null,
    val priority: Int = 0,
    val createdAtEpochMillis: Long,
    val fulfilledVisitId: String? = null,
) {
    init {
        require((placeId == null) != (countryIso2 == null)) { "Wunsch braucht genau einen Ort oder ein Land" }
    }

    val isFulfilled: Boolean get() = fulfilledVisitId != null
}

/** Wochentag-Muster, z. B. „Dienstag: Büro Salzburg“. */
data class PatternRule(
    val id: String,
    val personId: PersonId,
    val weekday: DayOfWeek,
    val stayTypeId: String,
    val placeId: String,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
) {
    fun appliesTo(date: LocalDate): Boolean =
        date.dayOfWeek == weekday &&
            (validFrom == null || !date.isBefore(validFrom)) &&
            (validTo == null || !date.isAfter(validTo))
}
