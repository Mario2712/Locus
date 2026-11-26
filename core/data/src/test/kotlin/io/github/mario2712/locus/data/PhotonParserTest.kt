package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.places.PhotonParser
import io.github.mario2712.locus.domain.model.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotonParserTest {

    // Beispielantwort im Photon-Format (GeoJSON, Koordinaten als [lon, lat]).
    private val json = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[13.0956,47.6833]},
           "properties":{"osm_type":"R","osm_id":34896,"osm_key":"place","osm_value":"town","type":"city",
             "name":"Hallein","state":"Salzburg","country":"Österreich","countrycode":"AT","postcode":"5400"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[13.0436,47.8003]},
           "properties":{"osm_type":"N","osm_id":123,"osm_key":"tourism","osm_value":"museum","type":"house",
             "name":"Museum der Moderne","city":"Salzburg","state":"Salzburg","country":"Österreich","countrycode":"at"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[13.1,47.7]},
           "properties":{"osm_type":"W","osm_id":55,"osm_key":"highway","osm_value":"residential","type":"street",
             "street":"Davisstraße","city":"Hallein","country":"Österreich","countrycode":"AT"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[1.0,2.0]},
           "properties":{"osm_type":"N","osm_id":9,"name":"Ohne Land"}}
        ]}
    """.trimIndent()

    @Test
    fun `Stadt, POI und Strasse werden erkannt, Treffer ohne Land verworfen`() {
        val results = PhotonParser.parse(json)
        assertEquals(3, results.size)

        val hallein = results[0]
        assertEquals("R34896", hallein.osmId)
        assertEquals(PlaceKind.CITY, hallein.kind)
        assertEquals(47.6833, hallein.latitude, 1e-9)
        assertEquals(13.0956, hallein.longitude, 1e-9)
        assertEquals("Hallein", hallein.cityName)
        assertEquals("Salzburg", hallein.stateName)

        val museum = results[1]
        assertEquals(PlaceKind.POI, museum.kind)
        assertEquals("AT", museum.countryIso2)
        assertEquals("museum", museum.category)
        assertEquals("Salzburg, Österreich", museum.subtitle)

        assertEquals("Davisstraße, Hallein", results[2].address)
        assertEquals(PlaceKind.ADDRESS, results[2].kind)
        assertEquals("Davisstraße", results[2].name)
    }

    @Test
    fun `leere Antwort ergibt keine Treffer`() {
        assertTrue(PhotonParser.parse("""{"type":"FeatureCollection","features":[]}""").isEmpty())
    }

    @Test
    fun `doppelte und kaputte Treffer werden aussortiert`() {
        val hotel = """{"type":"Feature","geometry":{"type":"Point","coordinates":[9.7418,47.5031]},
            "properties":{"osm_type":"W","osm_id":77,"osm_key":"tourism","osm_value":"hotel","type":"house",
            "name":"Hotel Ma Ma","street":"Am Steinenbach","housenumber":"8","postcode":"6900","city":"Bregenz",
            "state":"Vorarlberg","country":"Österreich","countrycode":"AT"}}"""
        val broken = """{"type":"Feature","geometry":{"type":"Point","coordinates":[]},
            "properties":{"osm_type":"N","osm_id":1,"name":"Kaputt","countrycode":"AT"}}"""
        val results = PhotonParser.parse("""{"type":"FeatureCollection","features":[$hotel,$hotel,$broken]}""")
        assertEquals(1, results.size)
        assertEquals("Am Steinenbach 8, 6900 Bregenz", results.single().address)
    }
}
