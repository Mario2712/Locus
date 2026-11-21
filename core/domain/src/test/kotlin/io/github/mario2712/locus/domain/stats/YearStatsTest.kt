package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class YearStatsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("hallein", "Hallein", PlaceKind.CITY, "AT", isHomebase = true),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT"),
        Place("museum", "Museum der Moderne", PlaceKind.POI, "AT", parentPlaceId = "salzburg"),
        Place("bologna", "Bologna", PlaceKind.CITY, "IT"),
        Place("zell", "Zell am See", PlaceKind.CITY, "AT"),
    ).associateBy { it.id }
    private val types = Defaults.stayTypes.associateBy { it.id }

    @Test
    fun `Jahresstatistik mit Arbeit, Urlaub, Laendern und Staedten`() {
        val stays = listOf(
            Stay("old", "salzburg", StayTypeIds.OFFICE, d("2025-03-03"), participants = setOf(ME)),
            Stay("1", "hallein", StayTypeIds.HOMEOFFICE, d("2026-03-02"), participants = setOf(ME)),
            Stay("2", "salzburg", StayTypeIds.OFFICE, d("2026-03-03"), participants = setOf(ME)),
            Stay("3", "salzburg", StayTypeIds.OFFICE, d("2026-03-05"), participants = setOf(ME)),
            Stay("4", "hallein", StayTypeIds.HOMEOFFICE, d("2026-03-05"), sortIndex = 1, participants = setOf(ME)),
            Stay("5", "bologna", StayTypeIds.VACATION, d("2026-08-01"), participants = setOf(ME), overnight = true),
            Stay("6", "hallein", StayTypeIds.FREE, d("2026-08-04"), participants = setOf(ME)),
            Stay("7", "zell", StayTypeIds.VACATION, d("2026-09-10"), d("2026-09-11"), participants = setOf(ME)),
        )
        val visits = listOf(Visit("v", "museum", VisitCategoryIds.MUSEUM, d("2026-06-01"), participants = setOf(ME)))
        val stats = YearStats.compute(
            2026, ME, stays, visits, places, types, Defaults.visitCategories.associateBy { it.id },
            StayPriority.ranker(types), "hallein", d("2026-11-21"),
        )

        val march = stats.work.first { it.month == YearMonth.of(2026, 3) }
        assertEquals(1, march.homeofficeOnly)
        assertEquals(2, march.officeDays)
        assertEquals(1, march.hybridDays)

        assertEquals(4, stats.vacationDaysAbroad)   // Bologna 1.–4.8.
        assertEquals(2, stats.vacationDaysDomestic) // Zell am See
        assertEquals(listOf("IT"), stats.newCountries)
        assertEquals(listOf("AT", "IT"), stats.countries)
        assertEquals(setOf("hallein", "bologna", "zell"), stats.newCities.toSet()) // Salzburg schon 2025
        assertEquals(3, stats.nightsAway) // Nächte 1.–3.8. in Bologna
        assertEquals("salzburg" to 3, stats.topCities.first { it.first == "salzburg" }) // 2 Bürotage + Museum
        assertEquals(StayTypeIds.OFFICE, stats.mainTypeByDate[d("2026-03-05")])
        assertEquals(9, stats.recordedDays) // Tage mit Station (Besuche allein zählen nicht)
    }
}
