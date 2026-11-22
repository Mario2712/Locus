package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DetailStatsTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val places = listOf(
        Place("hallein", "Hallein", PlaceKind.CITY, "AT", latitude = 47.683, longitude = 13.097),
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT", parentPlaceId = "hallein"),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT", latitude = 47.800, longitude = 13.045),
        Place("office", "Büro", PlaceKind.POI, "AT", parentPlaceId = "salzburg"),
        Place("lidl", "Lidl", PlaceKind.POI, "AT", parentPlaceId = "hallein"),
        Place("ichi", "Ichi Go Ichi E", PlaceKind.POI, "AT", parentPlaceId = "salzburg"),
        Place("muc", "Flughafen München", PlaceKind.POI, "DE", latitude = 48.354, longitude = 11.786),
        Place("han", "Hanoi", PlaceKind.CITY, "VN", latitude = 21.028, longitude = 105.834),
    ).associateBy { it.id }

    private val stays = listOf(
        Stay("ho1", "home", StayTypeIds.HOMEOFFICE, d("2026-03-02"), participants = setOf(me)),
        Stay("ho2", "home", StayTypeIds.HOMEOFFICE, d("2026-03-03"), participants = setOf(me)),
        Stay("of", "office", StayTypeIds.OFFICE, d("2026-03-04"), participants = setOf(me), transport = setOf(TransportMode.WALK, TransportMode.TRAIN)),
        Stay("ho3", "home", StayTypeIds.HOMEOFFICE, d("2026-03-05"), participants = setOf(me)),
        Stay("ho4", "home", StayTypeIds.HOMEOFFICE, d("2026-03-06"), participants = setOf(me)),
        Stay("ho5", "home", StayTypeIds.HOMEOFFICE, d("2026-03-09"), participants = setOf(me)),
        Stay("t", "muc", StayTypeIds.TRANSIT, d("2026-09-12"), participants = setOf(me), transport = setOf(TransportMode.CAR), sortIndex = 0),
        Stay("v", "han", StayTypeIds.VACATION, d("2026-09-13"), participants = setOf(me), transport = setOf(TransportMode.PLANE), overnight = true),
        Stay("back", "home", StayTypeIds.FREE, d("2026-09-20"), participants = setOf(me), transport = setOf(TransportMode.PLANE)),
    )
    private val visits = listOf(
        Visit("v1", "lidl", VisitCategoryIds.SHOPPING, d("2026-03-07"), participants = setOf(me)),
        Visit("v2", "lidl", VisitCategoryIds.SHOPPING, d("2026-03-14"), participants = setOf(me), extraCategoryIds = setOf(VisitCategoryIds.RESTAURANT)),
        Visit("v3", "ichi", VisitCategoryIds.RESTAURANT, d("2026-03-20"), participants = setOf(me)),
    )

    private fun stats() = YearStats.compute(
        d("2026-01-01"), d("2026-12-31"), me, stays, visits, places, types, Defaults.visitCategories.associateBy { it.id },
        StayPriority.ranker(types), Homebases.single("home"), d("2026-11-22"),
    )

    @Test
    fun `Besuche, Top-Orte und neu entdeckt`() {
        val s = stats().details
        assertEquals(2, s.visitsByCategory[VisitCategoryIds.SHOPPING])
        assertEquals(2, s.visitsByCategory[VisitCategoryIds.RESTAURANT])
        assertEquals(listOf("lidl" to 2, "ichi" to 1), s.topPlaces)
        assertEquals(listOf("ichi" to 1, "lidl" to 1), s.topPlacesByCategory.getValue(VisitCategoryIds.RESTAURANT))
        assertTrue("salzburg" in s.newCityIds && "han" in s.newCityIds)
        assertTrue("home" !in s.newLocationIds && "lidl" in s.newLocationIds)
    }

    @Test
    fun `Kilometer Luftlinie inklusive impliziter Heimkehr`() {
        val s = stats().details
        // Hallein–Salzburg ca. 13,5 km, hin und zurück mit dem Zug
        assertEquals(27.0, s.kmByTransport.getValue(TransportMode.TRAIN), 1.5)
        assertEquals(1, s.transportLegs[TransportMode.WALK])
        assertEquals(2, s.flights)
        // München–Hanoi und zurück nach Hallein: jeweils gut 8.000 km
        assertTrue(s.kmByTransport.getValue(TransportMode.PLANE) > 16_000)
        assertTrue(s.kmByTransport.getValue(TransportMode.CAR) in 100.0..130.0)
    }

    @Test
    fun `Rekorde, Wochentage und Homebase-Land nie neu`() {
        val all = stats()
        val s = all.details
        assertEquals("han", s.records.farthestPlaceId)
        assertEquals(9, s.records.longestTrip?.days) // 12.–20.09.
        assertEquals(3, s.records.homeofficeStreak) // Do, Fr, Mo – Büro am Mi unterbricht
        assertEquals(1, s.workByWeekday[DayOfWeek.WEDNESDAY]?.get(StayTypeIds.OFFICE))
        assertEquals(listOf("VN"), all.newCountries) // München nur Transit
        assertTrue("AT" !in all.newCountries)
        assertTrue("AT" in all.countries)
    }
}
