package io.github.mario2712.locus.domain.countrypoints

import io.github.mario2712.locus.domain.model.Continent
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Wish
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.JOHANNA
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CountryOverviewTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("split", "Split", PlaceKind.CITY, "HR"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
        Place("havanna", "Havanna", PlaceKind.CITY, "CU"),
        Place("linz", "Linz", PlaceKind.CITY, "AT"),
        Place("pek", "Peking", PlaceKind.CITY, "CN"),
    ).associateBy { it.id }
    private val stays = listOf(
        Stay("1", "split", StayTypeIds.VACATION, d("2023-08-01"), d("2023-08-03"), participants = setOf(ME, JOHANNA)),
        Stay("2", "rom", StayTypeIds.VACATION, d("2015-05-01"), participants = setOf(ME)),
        Stay("3", "rom", StayTypeIds.VACATION, d("2016-05-01"), participants = setOf(JOHANNA)),
        Stay("4", "havanna", StayTypeIds.VACATION, d("2019-03-02"), participants = setOf(ME)),
        Stay("5", "linz", StayTypeIds.OFFICE, d("2026-11-19"), participants = setOf(JOHANNA)),
    )
    private val points = CountryPointsCalculator().calculate(
        stays, emptyList(), places, Defaults.stayTypes.associateBy { it.id }, Defaults.visitCategories.associateBy { it.id },
    )

    @Test
    fun `Status je Land und Zaehler`() {
        val data = CountryOverview.build(
            points, ME, JOHANNA,
            wishes = listOf(Wish("w", countryIso2 = "jp", createdAtEpochMillis = 0)),
            countryDaysA = CountryOverview.countryDays(stays, places, ME),
        )
        val byIso = data.rows.associateBy { it.country.iso2 }
        assertEquals(CountryStatus.TOGETHER, byIso.getValue("HR").status)
        assertEquals(CountryStatus.BOTH_SEPARATELY, byIso.getValue("IT").status)
        assertEquals(CountryStatus.ONLY_A, byIso.getValue("CU").status)
        assertEquals(CountryStatus.ONLY_B, byIso.getValue("AT").status)
        assertEquals(CountryStatus.NONE, byIso.getValue("CN").status)
        assertTrue(byIso.getValue("JP").wished)
        assertEquals(3, byIso.getValue("HR").daysA)
        assertEquals(d("2016-05-01"), byIso.getValue("IT").firstVisitB)

        assertEquals(3, data.countA)
        assertEquals(3, data.countB)
        assertEquals(1, data.together)
        assertEquals(197, data.total)
        val europe = data.continents.first { it.continent == Continent.EUROPE }
        assertEquals(2, europe.visitedA)
        assertEquals(3, europe.visitedB)
    }
}
