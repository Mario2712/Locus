package io.github.mario2712.locus.domain.trips

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TripVisitsTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT", parentPlaceId = "hallein"),
        Place("hallein", "Hallein", PlaceKind.CITY, "AT"),
        Place("cafe", "Café El'risa", PlaceKind.POI, "AT", parentPlaceId = "hallein"),
        Place("hoian", "Hoi An", PlaceKind.CITY, "VN"),
        Place("hotel", "Grand Sunrise Palace", PlaceKind.POI, "VN", parentPlaceId = "hoian"),
        Place("bridge", "Chùa Cầu", PlaceKind.POI, "VN", parentPlaceId = "hoian"),
        Place("dad", "Flughafen Da Nang", PlaceKind.POI, "VN"),
        Place("muc", "Flughafen München", PlaceKind.POI, "DE"),
        Place("rast", "Raststätte Irschenberg", PlaceKind.POI, "DE"),
    ).associateBy { it.id }

    @Test
    fun `Besuche nach der Heimkehr gehoeren nicht zur Reise, Rast unterwegs schon`() {
        val stays = listOf(
            Stay("h", "hotel", StayTypeIds.VACATION, d("2026-09-20"), participants = setOf(me), overnight = true),
            Stay("t1", "dad", StayTypeIds.TRANSIT, d("2026-09-26"), participants = setOf(me), sortIndex = 0),
            Stay("t2", "muc", StayTypeIds.TRANSIT, d("2026-09-27"), participants = setOf(me), sortIndex = 0),
            Stay("back", "home", StayTypeIds.FREE, d("2026-09-27"), participants = setOf(me), sortIndex = 1),
        )
        val visits = listOf(
            Visit("v1", "bridge", VisitCategoryIds.SIGHT, d("2026-09-24"), participants = setOf(me)),
            Visit("v2", "cafe", VisitCategoryIds.RESTAURANT, d("2026-09-27"), participants = setOf(me)),
            Visit("v3", "rast", VisitCategoryIds.REST_STOP, d("2026-09-27"), participants = setOf(me)),
        )
        val trip = Trips.derive(stays, visits, places, me, "home", d("2026-10-10")).single()
        assertEquals(d("2026-09-27"), trip.end)
        assertEquals(listOf("v1", "v3"), trip.visits.map { it.id })
        assertEquals(listOf("VN", "DE"), trip.countryIso2s)
    }
}
