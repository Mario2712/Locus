package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WorkFilterTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("hallein", "Hallein", PlaceKind.CITY, "AT"),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT"),
        Place("bregenz", "Bregenz", PlaceKind.CITY, "AT"),
        Place("hotel", "Hotel Bregenz", PlaceKind.POI, "AT", parentPlaceId = "bregenz"),
        Place("ams", "Amsterdam", PlaceKind.CITY, "NL"),
    ).associateBy { it.id }
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val stays = listOf(
        // Bürotag in Salzburg
        Stay("o", "salzburg", StayTypeIds.OFFICE, d("2026-11-10"), participants = setOf(me), sortIndex = 0),
        // Dienstreise Bregenz: Büro, Hotel (Basis), nächster Tag Büro, Heimreise
        Stay("b1", "bregenz", StayTypeIds.OFFICE, d("2026-11-11"), participants = setOf(me), sortIndex = 0),
        Stay("b2", "hotel", StayTypeIds.BUSINESS_TRIP, d("2026-11-11"), participants = setOf(me), sortIndex = 1, overnight = true),
        Stay("b3", "bregenz", StayTypeIds.OFFICE, d("2026-11-12"), participants = setOf(me), sortIndex = 0),
        Stay("b4", "hallein", StayTypeIds.FREE, d("2026-11-12"), participants = setOf(me), sortIndex = 1),
        // Freier Tag mit Ausflug
        Stay("a", "ams", StayTypeIds.EXCURSION, d("2026-11-14"), participants = setOf(me)),
    )

    private fun stats(filter: WorkFilter) = YearStats.compute(
        d("2026-11-01"), d("2026-11-30"), me, stays, emptyList(), places, types,
        Defaults.visitCategories.associateBy { it.id }, io.github.mario2712.locus.domain.day.StayPriority.ranker(types),
        Homebases.single("hallein"), d("2026-11-20"), filter,
    )

    @Test
    fun `ohne Arbeit faellt Buero und Dienstreise weg`() {
        val s = stats(WorkFilter.NO_WORK)
        assertEquals(setOf(StayTypeIds.FREE, StayTypeIds.EXCURSION), s.daysByMainType.keys)
        assertEquals(0, s.nightsAway)
        assertEquals(listOf("NL"), s.countries.filter { it != "AT" })
        assertEquals(false, s.topCities.any { it.first == "bregenz" || it.first == "salzburg" })
    }

    @Test
    fun `Dienstreisen bleiben auf Wunsch - samt Buero vor Ort`() {
        val s = stats(WorkFilter.NO_WORK_EXCEPT_BUSINESS_TRIPS)
        assertEquals(1, s.nightsAway)
        assertEquals(true, s.topCities.any { it.first == "bregenz" })
        assertEquals(false, s.topCities.any { it.first == "salzburg" })
        assertEquals(1, s.trips)
    }

    @Test
    fun `alles zaehlt wie bisher`() {
        val s = stats(WorkFilter.ALL)
        assertEquals(true, s.topCities.any { it.first == "salzburg" })
    }
}
