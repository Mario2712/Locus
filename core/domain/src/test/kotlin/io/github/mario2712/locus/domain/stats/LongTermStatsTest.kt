package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryStats
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

class LongTermStatsTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT", parentPlaceId = "hallein"),
        Place("hallein", "Hallein", PlaceKind.CITY, "AT"),
        Place("lidl", "Lidl", PlaceKind.POI, "AT", parentPlaceId = "hallein"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
        Place("kolosseum", "Kolosseum", PlaceKind.POI, "IT", parentPlaceId = "rom"),
    ).associateBy { it.id }
    private val homes = Homebases.single("home")

    @Test
    fun `treue Orte und Wiederentdeckt ab 5 Jahren`() {
        val visits = (2014..2026).map { y -> Visit("l$y", "lidl", VisitCategoryIds.SHOPPING, d("$y-03-01"), participants = setOf(me)) } +
            Visit("k1", "kolosseum", VisitCategoryIds.SIGHT, d("2015-07-01"), participants = setOf(me)) +
            Visit("k2", "kolosseum", VisitCategoryIds.SIGHT, d("2022-08-01"), participants = setOf(me))
        val stays = listOf(Stay("r", "rom", StayTypeIds.VACATION, d("2019-05-01"), participants = setOf(me)))
        assertEquals(listOf(LoyalPlace("lidl", 13, 13)), LongTermStats.loyalPlaces(me, visits, places, homes))
        val again = LongTermStats.rediscovered(me, stays, visits, places, homes)
        // Kolosseum: 2015 → 2022 (7 Jahre); Rom: 2015 → 2019 (4) → 2022 (3) → nicht dabei; Hallein ist Heimat
        assertEquals(listOf("kolosseum" to 7), again.map { it.placeId to it.gapYears })
    }

    @Test
    fun `typischer Monat, Homeoffice-Anteil und Weltabdeckung`() {
        val work = listOf(
            MonthWork(YearMonth.of(2025, 1), 10, 8, 1, 2), MonthWork(YearMonth.of(2026, 1), 8, 10, 0, 0),
            MonthWork(YearMonth.of(2026, 2), 0, 0, 0, 0),
        )
        val typical = LongTermStats.typicalMonths(work)
        assertEquals(listOf(Month.JANUARY), typical.map { it.month })
        assertEquals(9.0, typical[0].homeoffice)
        assertEquals(0.5, LongTermStats.homeofficeShareByYear(work).getValue(2025), 0.01)
        val cov = LongTermStats.worldCoverage(listOf(CountryStats("AT", d("2014-01-01"), d("2026-01-01")), CountryStats("US", UNKNOWN_DATE, UNKNOWN_DATE)))
        assertEquals(2, cov.countries)
        assertEquals(1, cov.undated)
        assertEquals(2, cov.continents.size)
    }
}
