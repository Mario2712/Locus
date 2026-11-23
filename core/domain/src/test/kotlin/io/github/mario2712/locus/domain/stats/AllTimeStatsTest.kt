package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AllTimeStatsTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
        Place("us", "USA", PlaceKind.COUNTRY, "US"),
    ).associateBy { it.id }

    @Test
    fun `Gesamt zaehlt auch ohne Datum erfasste Laender`() {
        val stays = listOf(
            Stay("u", "us", StayTypeIds.VACATION, UNKNOWN_DATE, precision = DatePrecision.UNKNOWN, participants = setOf(me)),
            Stay("r", "rom", StayTypeIds.VACATION, d("2024-05-01"), participants = setOf(me)),
            Stay("h", "home", StayTypeIds.HOMEOFFICE, d("2026-03-02"), participants = setOf(me)),
        )
        fun compute(allTime: Boolean) = YearStats.compute(
            d("2024-05-01"), d("2026-11-23"), me, stays, emptyList(), places, types,
            Defaults.visitCategories.associateBy { it.id }, StayPriority.ranker(types), Homebases.single("home"), d("2026-11-23"),
            allTime = allTime,
        )
        assertEquals(listOf("AT", "IT", "US"), compute(true).countries)
        assertEquals(listOf("AT", "IT"), compute(false).countries)
        assertEquals(listOf("IT"), compute(true).newCountries)
    }
}
