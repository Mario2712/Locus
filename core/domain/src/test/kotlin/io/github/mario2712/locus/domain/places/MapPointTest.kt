package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.PlaceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapPointTest {
    private val hit = PlaceCandidate(
        osmId = "N1", name = "Bánh Mì Phượng", kind = PlaceKind.POI, countryIso2 = "vn", countryName = "Vietnam",
        stateName = null, cityName = "Hội An", latitude = 15.877, longitude = 108.326, category = "restaurant",
        street = "Phan Châu Trinh", houseNumber = "2B",
    )

    @Test
    fun `Vorschlag uebernommen - OSM-Verknuepfung bleibt`() {
        val place = PlaceCandidates.fromPoint(hit, 15.8771, 108.3262, PlaceCandidates.suggestName(hit, PlaceKind.POI), PlaceKind.POI, id = "x")
        assertEquals("N1", place.osmId)
        assertEquals("VN", place.countryIso2)
        assertEquals("Hội An", place.cityName)
        assertEquals(15.8771, place.latitude)
        assertEquals("Phan Châu Trinh 2B, Hội An", place.address)
    }

    @Test
    fun `eigener Name - ohne OSM-ID, Stadt-Dorf nimmt den Ortsnamen`() {
        val own = PlaceCandidates.fromPoint(hit, 15.8, 108.3, "Garküche am Fluss", PlaceKind.POI)
        assertNull(own.osmId)
        assertNull(own.poiCategory)
        assertEquals("Hội An", PlaceCandidates.suggestName(hit, PlaceKind.CITY))
        val village = PlaceCandidates.fromPoint(hit, 15.8, 108.3, "Hội An", PlaceKind.CITY)
        assertEquals(PlaceKind.CITY, village.kind)
        assertNull(village.address)
        assertEquals("Hội An", village.cityName)
    }
}
