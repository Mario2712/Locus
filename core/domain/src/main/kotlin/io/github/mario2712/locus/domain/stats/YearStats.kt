package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.day.DayStatistics
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.trips.Trips
import io.github.mario2712.locus.domain.usecase.DayInsights
import java.time.LocalDate
import java.time.YearMonth

/** Arbeitsorte eines Monats. */
data class MonthWork(
    val month: YearMonth,
    /** Tage ausschließlich im Homeoffice (Basis für die Homeoffice-Pauschale). */
    val homeofficeOnly: Int,
    /** Tage mit Büro (inkl. Hybrid-Tagen). */
    val officeDays: Int,
    /** Tage mit Büro und Homeoffice. */
    val hybridDays: Int,
    val businessTripDays: Int,
)

/** Statistik einer Person für einen Zeitraum (Jahr, Monat, letzte 30 Tage, frei gewählt). */
data class YearStatsData(
    val from: LocalDate,
    val to: LocalDate,
    /** Tage mit mindestens einem Eintrag. */
    val recordedDays: Int,
    /** Tage je Hauptart (jeder Tag genau einmal). */
    val daysByMainType: Map<String, Int>,
    /** Tage, an denen eine Art vorkommt (geteilte Tage zählen mehrfach). */
    val daysWithType: Map<String, Int>,
    val work: List<MonthWork>,
    val vacationDaysDomestic: Int,
    val vacationDaysAbroad: Int,
    /** Länder, die im Zeitraum zum ersten Mal besucht wurden. */
    val newCountries: List<String>,
    /** Alle in diesem Jahr besuchten Länder. */
    val countries: List<String>,
    /** Städte/Gemeinden, die im Zeitraum zum ersten Mal vorkommen. */
    val newCities: List<String>,
    /** Nächte nicht an der Homebase. */
    val nightsAway: Int,
    val trips: Int,
    /** Städte nach Tagen (inkl. ihrer Örtlichkeiten), absteigend. */
    val topCities: List<Pair<String, Int>>,
    /** Hauptart je Tag – für die Kalender-Heatmap. */
    val mainTypeByDate: Map<LocalDate, String>,
    val details: DetailStats = DetailStats(),
) {
    /** Monate des Zeitraums (für Tabelle und Heatmap). */
    val months: List<YearMonth>
        get() = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(to)) }.toList()
}

object YearStats {

    fun compute(
        year: Int,
        person: PersonId,
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        rank: (String) -> Int,
        homebaseId: String?,
        today: LocalDate,
    ): YearStatsData = compute(
        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), person, stays, visits, places, stayTypes, visitCategories,
        rank, io.github.mario2712.locus.domain.timeline.Homebases.single(homebaseId), today,
    )

    fun compute(
        rangeFrom: LocalDate,
        rangeTo: LocalDate,
        person: PersonId,
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        rank: (String) -> Int,
        homebaseId: String?,
        today: LocalDate,
    ): YearStatsData = compute(
        rangeFrom, rangeTo, person, stays, visits, places, stayTypes, visitCategories, rank,
        io.github.mario2712.locus.domain.timeline.Homebases.single(homebaseId), today,
    )

    fun compute(
        rangeFrom: LocalDate,
        rangeTo: LocalDate,
        person: PersonId,
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        rank: (String) -> Int,
        homebases: io.github.mario2712.locus.domain.timeline.Homebases,
        today: LocalDate,
        workFilter: WorkFilter = WorkFilter.ALL,
        /** Gesamtstatistik: Länder inklusive ohne Datum erfasster (vor Beginn der Aufzeichnungen). */
        allTime: Boolean = false,
    ): YearStatsData {
        val from = rangeFrom
        val to = minOf(rangeTo, today)
        // Filter nach dem Auflösen, damit laufende Basen nicht über ausgeblendete Stationen hinaus verlängert werden.
        val resolved = workFilter.apply(Timeline.resolve(stays, homebases, today), stayTypes)
        val own = resolved.filter { person in it.participants }
        val summaries = DayInsights.summaries(from, to, person, resolved, rank)

        // Arbeitsorte je Monat
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(rangeTo)) }
        val work = months.toList().map { month ->
            val days = summaries.filterKeys { YearMonth.from(it) == month }.values
            MonthWork(
                month = month,
                homeofficeOnly = DayStatistics.exclusiveDays(days, StayTypeIds.HOMEOFFICE),
                officeDays = days.count { it.contains(StayTypeIds.OFFICE) },
                hybridDays = days.count { it.contains(StayTypeIds.OFFICE) && it.contains(StayTypeIds.HOMEOFFICE) },
                businessTripDays = days.count { it.contains(StayTypeIds.BUSINESS_TRIP) },
            )
        }

        // Urlaub Inland/Ausland (Inland = Land der damaligen Homebase)
        fun homeCountry(date: LocalDate) = homebases.at(date)?.let { places[it]?.let { p -> ReferenceData.countableCountry(p.countryIso2)?.iso2 } }
        var domestic = 0
        var abroad = 0
        summaries.forEach { (date, summary) ->
            if (stayTypes[summary.mainTypeId]?.group != StayGroup.VACATION) return@forEach
            val vacationAbroad = own.any { stay ->
                stay.precision == DatePrecision.DAY && stay.covers(date) && stayTypes[stay.typeId]?.group == StayGroup.VACATION &&
                    places[stay.placeId]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 } != homeCountry(date)
            }
            if (vacationAbroad) abroad++ else domestic++
        }

        // Länder
        val points = CountryPointsCalculator().calculate(resolved, visits, places, stayTypes, visitCategories)
        val stats = points.byPerson[person].orEmpty().values
        // Das Land einer (früheren) Homebase gilt als schon immer bekannt: zählt, ist aber nie „neu“.
        val homeCountries = homebases.placeIds.mapNotNull { id -> places[id]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 } }.toSet()
        val newCountries = stats.filter { it.firstDate in from..to && it.countryIso2 !in homeCountries }
            .sortedBy { it.firstDate }.map { it.countryIso2 }
        val yearStays = resolved.filter { it.startDate <= to && it.endDate >= from }
        val yearVisits = visits.filter { it.date in from..to }
        val yearPoints = CountryPointsCalculator().calculate(yearStays, yearVisits, places, stayTypes, visitCategories)
        val countries = (if (allTime) points.countriesOf(person) else yearPoints.countriesOf(person)).sorted()

        // Städte: Ort selbst oder Stadt der Örtlichkeit
        fun cityOf(placeId: String): String? {
            val place = places[placeId] ?: return null
            return if (place.kind == PlaceKind.CITY) place.id else place.parentPlaceId
        }
        val cityDates = mutableMapOf<String, MutableSet<LocalDate>>()
        // Homebase zählt an Tagen mit Start/Ende zuhause mit (z. B. Bürotag).
        io.github.mario2712.locus.domain.timeline.ImplicitHome.augment(own, visits, person, homebases, today)
            .filter { it.precision == DatePrecision.DAY }.forEach { stay ->
            val city = cityOf(stay.placeId) ?: return@forEach
            var d = stay.startDate
            while (!d.isAfter(stay.endDate)) { cityDates.getOrPut(city) { mutableSetOf() } += d; d = d.plusDays(1) }
        }
        visits.filter { person in it.participants }.forEach { v -> cityOf(v.placeId)?.let { cityDates.getOrPut(it) { mutableSetOf() } += v.date } }
        val newCities = cityDates.filter { (_, dates) -> dates.min() in from..to }.keys.toList()
        val topCities = cityDates.mapValues { (_, dates) -> dates.count { it in from..to } }
            .filter { it.value > 0 }.toList().sortedByDescending { it.second }.take(5)

        // Nächte auswärts: Basis am Abend ist nicht die Homebase
        val index = io.github.mario2712.locus.domain.timeline.TimelineIndex(own, person)
        var nights = 0
        var d = from
        while (!d.isAfter(to)) {
            if (index.baseAtEnd(d, homebases, today) != null) nights++
            d = d.plusDays(1)
        }

        val tripList = Trips.derive(if (workFilter == WorkFilter.ALL) stays else resolved, visits, places, person, homebases, today)
        val trips = tripList.count { it.start <= to && it.end >= from }

        return YearStatsData(
            from = rangeFrom,
            to = rangeTo,
            recordedDays = summaries.size,
            daysByMainType = DayStatistics.daysByMainType(summaries.values),
            daysWithType = DayStatistics.daysWithType(summaries.values),
            work = work,
            vacationDaysDomestic = domestic,
            vacationDaysAbroad = abroad,
            newCountries = newCountries,
            countries = countries,
            newCities = newCities,
            nightsAway = nights,
            trips = trips,
            topCities = topCities,
            mainTypeByDate = summaries.mapValues { it.value.mainTypeId },
            details = DetailStatsCalculator.compute(
                from, to, person, resolved, visits, places, stayTypes, homebases, summaries, tripList, today,
            ),
        )
    }
}
