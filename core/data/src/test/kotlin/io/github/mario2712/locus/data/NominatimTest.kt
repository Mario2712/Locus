package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.places.FallbackPlaceSearch
import io.github.mario2712.locus.data.places.NominatimParser
import io.github.mario2712.locus.data.places.SearchHttpException
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NominatimTest {

    private val json = """
        [
          {"osm_type":"relation","osm_id":34896,"lat":"47.6833","lon":"13.0956","category":"boundary","type":"administrative",
           "addresstype":"town","name":"Hallein",
           "address":{"town":"Hallein","state":"Salzburg","country":"Österreich","country_code":"at","postcode":"5400"}},
          {"osm_type":"node","osm_id":123,"lat":"35.0975","lon":"129.0106","category":"tourism","type":"attraction",
           "addresstype":"tourism","name":"Gamcheon Culture Village",
           "address":{"tourism":"Gamcheon Culture Village","road":"Gamnae 2-ro","suburb":"Gamcheon-dong","city":"Busan","country":"Südkorea","country_code":"kr"}},
          {"osm_type":"way","osm_id":55,"lat":"47.7","lon":"13.1","category":"highway","type":"residential","name":"",
           "address":{"road":"Davisstraße","house_number":"3","town":"Hallein","country":"Österreich","country_code":"at"}},
          {"osm_type":"node","osm_id":9,"lat":"1","lon":"2","name":"Ohne Land","address":{}}
        ]
    """.trimIndent()

    @Test
    fun `Stadt, POI und Adresse werden erkannt, Treffer ohne Land verworfen`() {
        val r = NominatimParser.parseList(json)
        assertEquals(3, r.size)
        assertEquals("R34896", r[0].osmId)
        assertEquals(PlaceKind.CITY, r[0].kind)
        assertEquals("AT", r[0].countryIso2)
        assertEquals("Hallein", r[0].cityName)

        assertEquals(PlaceKind.POI, r[1].kind)
        assertEquals("N123", r[1].osmId)
        assertEquals("Busan", r[1].cityName)
        assertEquals("KR", r[1].countryIso2)
        assertEquals(35.0975, r[1].latitude, 1e-9)

        assertEquals(PlaceKind.ADDRESS, r[2].kind)
        assertEquals("Davisstraße 3", r[2].name)
    }

    @Test
    fun `Rueckwaertssuche ohne Treffer liefert null`() {
        assertNull(NominatimParser.parseSingle("""{"error":"Unable to geocode"}"""))
    }

    private val hit = NominatimParser.parseList(json).first()

    private class Fake(var fail: Exception? = null, val result: PlaceCandidate? = null) : PlaceSearch {
        var calls = 0
        override suspend fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceCandidate> {
            calls++; fail?.let { throw it }; return listOfNotNull(result)
        }
        override suspend fun reverse(latitude: Double, longitude: Double): PlaceCandidate? { calls++; fail?.let { throw it }; return result }
    }

    @Test
    fun `Photon lehnt ab - Nominatim springt ein, Photon wird eine Weile uebersprungen`() = runTest {
        var now = 0L
        val photon = Fake(fail = SearchHttpException(403))
        val nominatim = Fake(result = hit)
        val search = FallbackPlaceSearch(photon, nominatim, pauseMillis = 1_000, clock = { now })

        assertEquals(listOf(hit), search.search("hallein"))
        assertEquals(listOf(hit), search.search("hallein"))
        assertEquals(1, photon.calls) // zweite Suche direkt bei Nominatim
        now = 2_000
        photon.fail = null
        search.search("hallein")
        assertEquals(2, photon.calls)
    }

    @Test
    fun `scheitern beide, wird der HTTP-Fehler gemeldet`() = runTest {
        val search = FallbackPlaceSearch(Fake(fail = SearchHttpException(403)), Fake(fail = java.io.IOException("offline")))
        val e = assertThrows(SearchHttpException::class.java) { kotlinx.coroutines.runBlocking { search.search("x") } }
        assertEquals(403, e.code)
    }
}
