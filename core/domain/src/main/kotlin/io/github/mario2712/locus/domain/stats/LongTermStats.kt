package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Homebases
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** Ort, der in vielen Jahren vorkommt. */
data class LoyalPlace(val placeId: String, val years: Int, val ofYears: Int)

/** Ort, an dem man nach langer Pause wieder war. */
data class Rediscovered(val placeId: String, val gapYears: Int, val returnDate: LocalDate)

/** Durchschnittlicher Kalendermonat über mehrere Jahre. */
data class TypicalMonth(val month: Month, val homeoffice: Double, val office: Double, val businessTrip: Double)

/** Länder-Überblick für „Gesamt“. */
data class WorldCoverage(
    val countries: Int,
    val ofCountries: Int,
    /** Davon nur ohne Datum erfasst (vor Beginn der Aufzeichnungen). */
    val undated: Int,
    val continents: List<io.github.mario2712.locus.domain.model.Continent>,
)

/** Kennzahlen über viele Jahre: treue Orte, Wiederentdeckt, typischer Monat, Weltabdeckung. */
object LongTermStats {

    fun worldCoverage(countryStats: Collection<CountryStatsLike>): WorldCoverage {
        val countries = countryStats.mapNotNull { ReferenceData.country(it.countryIso2) }
        return WorldCoverage(
            countries = countries.size,
            ofCountries = ReferenceData.countries.size,
            undated = countryStats.count { !it.dateKnown },
            continents = countries.map { it.continent }.distinct().sortedBy { it.ordinal },
        )
    }

    /** Örtlichkeiten (Besuche), die in mindestens [minYears] Jahren vorkommen, die treuesten zuerst. */
    fun loyalPlaces(person: PersonId, visits: List<Visit>, places: Map<String, Place>, homebases: Homebases, minYears: Int = 3, limit: Int = 5): List<LoyalPlace> {
        val own = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }
        if (own.isEmpty()) return emptyList()
        val ofYears = own.map { it.date.year }.distinct().size
        return own.groupBy { it.placeId }
            .filter { (id, _) -> id !in homebases.placeIds && places[id]?.kind != PlaceKind.CITY }
            .map { (id, list) -> LoyalPlace(id, list.map { it.date.year }.distinct().size, ofYears) }
            .filter { it.years >= minYears }
            .sortedWith(compareByDescending<LoyalPlace> { it.years }.thenBy { places[it.placeId]?.displayName })
            .take(limit)
    }

    /** Orte (Städte und Örtlichkeiten), an denen man nach mindestens [minGapYears] Jahren Pause wieder war. */
    fun rediscovered(
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        homebases: Homebases,
        minGapYears: Int = 5,
        limit: Int = 5,
    ): List<Rediscovered> {
        fun cityOf(id: String) = places[id]?.let { if (it.kind == PlaceKind.CITY) it.id else it.parentPlaceId }
        val dates = mutableMapOf<String, MutableSet<LocalDate>>()
        fun add(id: String, date: LocalDate) {
            if (id in homebases.placeIds) return
            dates.getOrPut(id) { sortedSetOf() } += date
            cityOf(id)?.takeIf { it != id }?.let { dates.getOrPut(it) { sortedSetOf() } += date }
        }
        resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }.forEach { add(it.placeId, it.startDate) }
        visits.filter { person in it.participants && it.precision == DatePrecision.DAY }.forEach { add(it.placeId, it.date) }
        val homeCities = homebases.placeIds.mapNotNull(::cityOf).toSet()
        return dates.filterKeys { it !in homeCities }.mapNotNull { (id, set) ->
            set.toList().zipWithNext()
                .map { (a, b) -> Rediscovered(id, ChronoUnit.YEARS.between(a, b).toInt(), b) }
                .filter { it.gapYears >= minGapYears }
                .maxByOrNull { it.gapYears }
        }.sortedWith(compareByDescending<Rediscovered> { it.gapYears }.thenByDescending { it.returnDate }).take(limit)
    }

    /** Durchschnitt je Kalendermonat über alle Monate mit Arbeitstagen. */
    fun typicalMonths(work: List<MonthWork>): List<TypicalMonth> = work
        .filter { it.homeofficeOnly + it.officeDays + it.businessTripDays > 0 }
        .groupBy { it.month.month }
        .toSortedMap()
        .map { (month, list) ->
            TypicalMonth(month, list.map { it.homeofficeOnly }.average(), list.map { it.officeDays }.average(), list.map { it.businessTripDays }.average())
        }

    /** Homeoffice-Anteil je Jahr: reine Homeoffice-Tage im Verhältnis zu allen Arbeitstagen. */
    fun homeofficeShareByYear(work: List<MonthWork>): Map<Int, Double> = work.groupBy { it.month.year }
        .mapValues { (_, list) ->
            val total = list.sumOf { it.homeofficeOnly + it.officeDays + it.businessTripDays }
            if (total == 0) 0.0 else list.sumOf { it.homeofficeOnly }.toDouble() / total
        }
        .toSortedMap()
}
