package io.github.mario2712.locus.domain.model

import io.github.mario2712.locus.domain.reference.Defaults
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
    /** Für Geburtstags-Statistiken (optional). */
    val birthDate: LocalDate? = null,
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

/**
 * Genauigkeit einer Datumsangabe. Unscharfe Angaben zählen nicht für Tagesstatistiken.
 * [UNKNOWN]: „schon dort gewesen, wann ist unbekannt“ – zählt nur für Länderpunkte und Karte;
 * das gespeicherte Datum ist dann [UNKNOWN_DATE].
 */
enum class DatePrecision { DAY, MONTH, YEAR, UNKNOWN }

/** Platzhalter-Datum für Einträge mit unbekanntem Datum. */
val UNKNOWN_DATE: LocalDate = LocalDate.of(1, 1, 1)

/** Fachliche Gruppe einer Aufenthaltsart – Basis für Statistiken. */
enum class StayGroup { WORK, FREE, VACATION, EDUCATION, OTHER }

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

/** COUNTRY: nur das Land als Ganzes (für Länderpunkte ohne bekannten Ort/Datum). */
enum class PlaceKind { CITY, POI, ADDRESS, COUNTRY }

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
    /** Zuhause; Tage beginnen und enden hier, sofern keine Reise läuft. Höchstens ein Ort. */
    val isHomebase: Boolean = false,
    /** Adresse zur Unterscheidung gleichnamiger Orte, z. B. „Davisstraße 12, 5400 Hallein“. */
    val address: String? = null,
    /** Homebase ab diesem Datum (Umzüge); [UNKNOWN_DATE] = von Anfang an; `null` = keine Homebase. */
    val homebaseSince: LocalDate? = null,
) {
    val displayName: String get() = alias ?: name
}

/** Verkehrsmittel für den Weg zu einer Station; mehrere möglich, ohne Reihenfolge. */
enum class TransportMode(val emoji: String, val nameDe: String) {
    WALK("🚶", "zu Fuß"),
    BIKE("🚲", "Rad"),
    CAR("🚗", "Auto"),
    BUS("🚌", "Bus"),
    TRAM("🚊", "U-Bahn/Tram"),
    TRAIN("🚆", "Zug"),
    TAXI("🚕", "Taxi"),
    CABLE_CAR("🚡", "Seilbahn"),
    PLANE("✈️", "Flug"),
    FERRY("⛴️", "Fähre"),
}

/**
 * Eine Station (Aufenthalt): an Ort X, Art Y, mit Personen Z.
 *
 * Stationen eines Tages bilden eine Kette in der Reihenfolge von [sortIndex]; Uhrzeiten sind
 * optional (bei Live-Erfassung automatisch). Eine Station mit [overnight] wird zur neuen Basis
 * (Übernachtungsort) und läuft weiter, bis eine andere Basis beginnt – siehe
 * [io.github.mario2712.locus.domain.timeline.Timeline]. Ohne [overnight] endet sie am Tagesende
 * und man ist zurück an der aktuellen Basis.
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
    /** Übernachtung hier → neue Basis (Urlaub, Dienstreise mit Übernachtung). */
    val overnight: Boolean = false,
    /** Verkehrsmittel auf dem Weg zu dieser Station. */
    val transport: Set<TransportMode> = emptySet(),
) {
    /** Zeitpunkt der Erfassung (Epoch-Millis, aus der Datenbank); nicht Teil von equals/copy. */
    var createdAtMillis: Long? = null

    init {
        require(!endDate.isBefore(startDate)) { "endDate darf nicht vor startDate liegen" }
        require(participants.isNotEmpty()) { "Ein Aufenthalt braucht mindestens eine Person" }
    }

    val dateRange: ClosedRange<LocalDate> get() = startDate..endDate

    fun covers(date: LocalDate): Boolean = date in dateRange
}

/**
 * Ein Besuch: an einem Tag kurz an einem Ort gewesen (Einkaufen in Oberalm, Restaurant, Museum …).
 * Unterbricht die Stationen-Kette nicht, zählt aber für Orts-Statistik, Karte und – je nach
 * Kategorie bzw. [countsForCountryPointsOverride] – für Länderpunkte.
 */
data class Visit(
    val id: String,
    val placeId: String,
    /** Hauptkategorie; weitere in [extraCategoryIds] (z. B. Einkaufen + Restaurant). */
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
    val extraCategoryIds: Set<String> = emptySet(),
) {
    /** Zeitpunkt der Erfassung (Epoch-Millis, aus der Datenbank); nicht Teil von equals/copy. */
    var createdAtMillis: Long? = null

    val categoryIds: Set<String> get() = linkedSetOf(categoryId) + extraCategoryIds

    init {
        require(participants.isNotEmpty()) { "Ein Besuch braucht mindestens eine Person" }
        require(rating == null || rating in 1..5) { "Bewertung muss zwischen 1 und 5 liegen" }
    }

    val dateRange: ClosedRange<LocalDate>
        get() = when (precision) {
            DatePrecision.DAY -> date..date
            DatePrecision.MONTH -> YearMonth.from(date).let { it.atDay(1)..it.atEndOfMonth() }
            DatePrecision.YEAR -> LocalDate.of(date.year, 1, 1)..LocalDate.of(date.year, 12, 31)
            DatePrecision.UNKNOWN -> UNKNOWN_DATE..UNKNOWN_DATE
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

/** Ein Schritt einer Tagesvorlage, z. B. „Büro · Salzburg mit 🚆“. */
data class TemplateStep(
    val stayTypeId: String,
    val placeId: String,
    val transport: Set<TransportMode> = emptySet(),
    val overnight: Boolean = false,
)

/** Benannte Tagesvorlage, z. B. „Bürotag“ oder „Hybrid: Büro → Homeoffice“. */
data class DayTemplate(
    val id: String,
    val name: String,
    val steps: List<TemplateStep>,
    val sortOrder: Int = 0,
) {
    init {
        require(steps.isNotEmpty()) { "Eine Vorlage braucht mindestens einen Schritt" }
    }
}

/**
 * Wochentag-Muster: entweder eine [DayTemplate] ([templateId]) oder eine einzelne Station
 * ([stayTypeId] · [placeId]).
 */
data class PatternRule(
    val id: String,
    val personId: PersonId,
    val weekday: DayOfWeek,
    val stayTypeId: String,
    val placeId: String,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val templateId: String? = null,
) {
    /** Schritte des Musters; nutzt die Vorlage, falls vorhanden. */
    fun steps(templates: Map<String, DayTemplate>): List<TemplateStep> =
        templateId?.let { templates[it]?.steps } ?: listOf(TemplateStep(stayTypeId, placeId))

    fun appliesTo(date: LocalDate): Boolean =
        date.dayOfWeek == weekday &&
            (validFrom == null || !date.isBefore(validFrom)) &&
            (validTo == null || !date.isAfter(validTo))
}

/** Standardwerte für neue Einträge (gerätelokal, konfigurierbar). */
data class EntryDefaults(
    /** Verkehrsmittel aus Vorlage bzw. letzter gleicher Strecke vorbelegen. */
    val prefillTransport: Boolean = true,
    val dailyParticipants: Set<PersonId> = setOf(Defaults.ME),
    val tripParticipants: Set<PersonId> = setOf(Defaults.ME, Defaults.JOHANNA),
    /** Ab diesem Jahr ist alles lückenlos erfasst; Länder ohne Datum gelten als davor besucht. */
    val documentedSinceYear: Int = 2014,
    /** Arten und Kategorien in Auswahllisten nach Häufigkeit (letzte 12 Monate) statt fester Reihenfolge. */
    val sortByUsage: Boolean = false,
)

/** Einstellungen der Abend-Erinnerung (gerätelokal, nicht synchronisiert). */
data class ReminderSettings(
    val enabled: Boolean = true,
    val time: LocalTime = LocalTime.of(20, 30),
)

/** Automatische Sicherung (gerätelokal). */
data class BackupSettings(
    val autoBackup: Boolean = true,
    val lastBackupEpochMillis: Long? = null,
)
