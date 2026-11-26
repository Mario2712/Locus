package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryStats
import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.domain.timeline.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.MonthDay

class SpecialDaysTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
    ).associateBy { it.id }
    private val types = Defaults.stayTypes.associateBy { it.id }

    @Test
    fun `Silvester je Jahr - Ausland und zuhause`() {
        val stays = listOf(
            Stay("a", "home", StayTypeIds.FREE, d("2024-06-01"), participants = setOf(me)),
            Stay("r", "rom", StayTypeIds.VACATION, d("2024-12-29"), participants = setOf(me), overnight = true),
            Stay("b", "home", StayTypeIds.FREE, d("2025-01-02"), participants = setOf(me)),
        )
        val resolved = Timeline.resolve(stays, Homebases.single("home"), d("2026-11-23"))
        val nye = SpecialDays.annual(MonthDay.of(12, 31), me, resolved, emptyList(), places, Homebases.single("home"), StayPriority.ranker(types), d("2026-11-23"))
        // 2026 liegt in der Zukunft, 2025 zuhause (kein Eintrag → Homebase), 2024 in Rom
        assertEquals(listOf(2025, 2024), nye.map { it.date.year })
        assertEquals("home", nye[0].placeId)
        assertEquals(true, nye[1].abroad)
        assertEquals("IT", nye[1].countryIso2)
    }

    @Test
    fun `Alter beim ersten Besuch ohne Homebase-Land und ohne undatierte`() {
        val ages = SpecialDays.firstVisitAges(
            d("1996-05-10"),
            listOf(CountryStats("VN", d("2026-09-13"), d("2026-09-27")), CountryStats("AT", d("2014-01-01"), d("2026-11-01")),
                CountryStats("US", UNKNOWN_DATE, UNKNOWN_DATE), CountryStats("IT", d("2015-07-01"), d("2024-12-31"))),
            homeCountries = setOf("AT"),
        )
        assertEquals(listOf("IT" to 19, "VN" to 30), ages.map { it.countryIso2 to it.age })
    }
}
