package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceCandidatesTest {
    private val hallein = PlaceCandidate(
        osmId = "R34896", name = "Hallein", kind = PlaceKind.CITY, countryIso2 = "AT", countryName = "Österreich",
        stateName = "Salzburg", cityName = "Hallein", latitude = 47.6833, longitude = 13.0956, category = "town",
    )

    @Test
    fun `Region wird ueber den Namen zugeordnet`() {
        assertEquals("AT-5", PlaceCandidates.regionCode("AT", "Salzburg"))
        assertEquals("DE-BY", PlaceCandidates.regionCode("DE", "Bayern"))
        assertNull(PlaceCandidates.regionCode("IT", "Lazio"))
    }

    @Test
    fun `Treffer wird zu Ort mit Koordinaten, OSM-ID und Region`() {
        val place = PlaceCandidates.toPlace(hallein, id = "p")
        assertEquals("AT-5", place.regionCode)
        assertEquals("R34896", place.osmId)
        assertEquals(47.6833, place.latitude)
        assertEquals("Salzburg, Österreich", hallein.subtitle)
    }

    @Test
    fun `manuell angelegtes Hallein wird wiedererkannt und ergaenzt`() {
        val manual = Place("p1", "Hallein", PlaceKind.CITY, "AT", alias = "Zuhause", isHomebase = true)
        val found = PlaceCandidates.findExisting(hallein, listOf(manual))!!
        val enriched = PlaceCandidates.enrich(found, hallein)
        assertEquals("Zuhause", enriched.alias)
        assertTrue(enriched.isHomebase)
        assertEquals("R34896", enriched.osmId)
        assertEquals("AT-5", enriched.regionCode)
        assertEquals(13.0956, enriched.longitude)
    }

    @Test
    fun `gleicher Name weit entfernt ist ein anderer Ort`() {
        val other = Place("p2", "Hallein", PlaceKind.CITY, "AT", latitude = 48.2, longitude = 16.37)
        assertNull(PlaceCandidates.findExisting(hallein, listOf(other)))
        val distance = PlaceCandidates.distanceMeters(47.6833, 13.0956, 47.8095, 13.0550) // Hallein–Salzburg
        assertTrue(distance in 13_000.0..16_000.0)
    }

    @Test
    fun `Stadt zu einem Museum finden oder minimal anlegen`() {
        val museum = hallein.copy(osmId = "N1", name = "Keltenmuseum", kind = PlaceKind.POI, cityName = "Hallein", category = "museum")
        val city = Place("p1", "Hallein", PlaceKind.CITY, "AT")
        assertEquals("p1", PlaceCandidates.findCity("hallein", "AT", listOf(city))?.id)
        assertNull(PlaceCandidates.findCity("Hallein", "DE", listOf(city)))
        val stub = PlaceCandidates.cityStub(museum, "Hallein", id = "c")
        assertEquals(PlaceKind.CITY, stub.kind)
        assertEquals("AT-5", stub.regionCode)
    }
}
