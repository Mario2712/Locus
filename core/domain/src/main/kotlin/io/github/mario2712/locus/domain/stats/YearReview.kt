package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.timeline.TimelineIndex
import io.github.mario2712.locus.domain.trips.TripSummary
import io.github.mario2712.locus.domain.trips.Trips
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth

/** Ein Monat im Rückblick: Reisen, die in dem Monat beginnen, und Länder außerhalb des Alltags. */
data class MonthHighlight(val month: YearMonth, val trips: List<TripSummary>, val countries: List<String>)

/** Alles für den Jahresrückblick einer Person. */
data class YearReviewData(
    val year: Int,
    val stats: YearStatsData,
    val previous: YearStatsData?,
    val months: List<MonthHighlight>,
    /** Tage, an denen beide Personen gemeinsam unterwegs bzw. eingetragen waren. */
    val togetherDays: Int,
    val togetherTrips: Int,
    val birthday: DayWhere?,
    val newYearsEve: DayWhere?,
    val includeWork: Boolean,
)

object YearReview {

    fun build(
        year: Int,
        person: PersonId,
        partner: PersonId?,
        birthDate: LocalDate?,
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
        rank: (String) -> Int,
        homebases: Homebases,
        today: LocalDate,
        includeWork: Boolean,
    ): YearReviewData {
        val from = LocalDate.of(year, 1, 1)
        val to = LocalDate.of(year, 12, 31)
        // Ohne Arbeit: Dienstreisen bleiben (Amsterdam, Bregenz …), Büro- und Homeoffice-Alltag nicht.
        val filter = if (includeWork) WorkFilter.ALL else WorkFilter.NO_WORK_EXCEPT_BUSINESS_TRIPS
        fun compute(y: Int) = YearStats.compute(
            LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), person, stays, visits, places, stayTypes, visitCategories,
            rank, homebases, today, filter,
        )
        val stats = compute(year)
        val firstEntry = (stays.filter { it.precision == DatePrecision.DAY }.map { it.startDate } + visits.map { it.date }).minOrNull()
        val previous = if (firstEntry != null && firstEntry.year < year) compute(year - 1) else null

        val resolved = filter.apply(Timeline.resolve(stays, homebases, today), stayTypes)
        val trips = Trips.derive(resolved, visits, places, person, homebases, today)
            .filter { it.precision == DatePrecision.DAY && it.start.year == year }
        val points = CountryPointsCalculator()
        val months = (1..12).map { m ->
            val month = YearMonth.of(year, m)
            val monthStays = resolved.filter { person in it.participants && it.startDate <= month.atEndOfMonth() && it.endDate >= month.atDay(1) }
            val monthVisits = visits.filter { person in it.participants && YearMonth.from(it.date) == month }
            val countries = points.calculate(monthStays, monthVisits, places, stayTypes, visitCategories).countriesOf(person)
                .filter { it !in homeCountries(homebases, places) }.sorted()
            MonthHighlight(month, trips.filter { YearMonth.from(it.start) == month }, countries)
        }

        val together = partner?.let { p ->
            val both = resolved.filter { person in it.participants && p in it.participants }
            val index = TimelineIndex(both, person)
            val days = index.coveredDays.count { it in from..to }
            days to trips.count { p in it.participants }
        } ?: (0 to 0)

        val nye = SpecialDays.annual(MonthDay.of(12, 31), person, Timeline.resolve(stays, homebases, today), visits, places, homebases, rank, today)
            .firstOrNull { it.date.year == year }
        val birthday = birthDate?.let { b ->
            SpecialDays.annual(MonthDay.from(b), person, Timeline.resolve(stays, homebases, today), visits, places, homebases, rank, today)
                .firstOrNull { it.date.year == year }
        }
        return YearReviewData(year, stats, previous, months, together.first, together.second, birthday, nye, includeWork)
    }

    private fun homeCountries(homebases: Homebases, places: Map<String, Place>): Set<String> =
        homebases.placeIds.mapNotNull { id -> places[id]?.let { io.github.mario2712.locus.domain.reference.ReferenceData.countableCountry(it.countryIso2)?.iso2 } }.toSet()
}
