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

    @Test
    fun `Land ohne Datum zaehlt als Laenderpunkt, aber nicht fuer Meilensteine und Reisen`() {
        val unknown = io.github.mario2712.locus.domain.model.UNKNOWN_DATE
        val placesWithCountry = places + ("place-jp" to Place("place-jp", "Japan", PlaceKind.COUNTRY, "JP"))
        val undated = Stay(
            "u", "place-jp", StayTypeIds.VACATION, unknown, precision = io.github.mario2712.locus.domain.model.DatePrecision.UNKNOWN,
            participants = setOf(ME),
        )
        val all = stays + undated
        val p = CountryPointsCalculator().calculate(
            all, emptyList(), placesWithCountry, Defaults.stayTypes.associateBy { it.id }, Defaults.visitCategories.associateBy { it.id },
        )
        val jp = p.byPerson.getValue(ME).getValue("JP")
        assertEquals(false, jp.dateKnown)
        assertTrue("JP" in p.countriesOf(ME))
        // Meilensteine zählen nur datierte Länder
        val milestones = io.github.mario2712.locus.domain.stats.BirthdayStats.milestones(
            d("1990-01-01"), p.byPerson.getValue(ME).values, d("2026-11-21"),
        )
        assertEquals(2, milestones.first { it.age == 30 }.countries) // IT 2015, CU 2019 (HR erst 2023, JP ohne Datum nicht)
        assertEquals(4, milestones.last().countries) // nächster Meilenstein: Stand heute inkl. JP
        // Keine Reise im Jahr 1
        val trips = io.github.mario2712.locus.domain.trips.Trips.derive(all, emptyList(), placesWithCountry, ME, null, d("2026-11-21"))
        assertTrue(trips.none { it.basePlaceIds.contains("place-jp") })
    }
}
