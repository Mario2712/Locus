package io.github.mario2712.locus.domain.reference

import io.github.mario2712.locus.domain.model.Continent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReferenceDataTest {

    @Test
    fun `197 zaehlbare Laender mit eindeutigen Codes`() {
        val countries = ReferenceData.countries
        assertEquals(197, countries.size)
        assertEquals(197, countries.map { it.iso2 }.toSet().size)
        assertEquals(197, countries.map { it.iso3 }.toSet().size)
        assertTrue(countries.all { it.nameDe.isNotBlank() })
    }

    @Test
    fun `deutsche Namen, Kontinente und Flaggen`() {
        val at = ReferenceData.country("at")!!
        assertEquals("Österreich", at.nameDe)
        assertEquals(Continent.EUROPE, at.continent)
        assertEquals("🇦🇹", at.flag)
        assertEquals("Slowenien", ReferenceData.country("SI")!!.nameDe)
        assertEquals(Continent.ASIA, ReferenceData.country("CN")!!.continent)
        assertEquals(Continent.NORTH_AMERICA, ReferenceData.country("CU")!!.continent)
    }

    @Test
    fun `Kosovo, Vatikan, Taiwan und Palaestina zaehlen zusaetzlich`() {
        assertEquals("Palästina", ReferenceData.country("PS")?.nameDe)
        listOf("XK", "VA", "TW", "PS").forEach { assertTrue(ReferenceData.country(it) != null, it) }
    }

    @Test
    fun `abhaengige Gebiete zaehlen fuer das Mutterland`() {
        assertEquals("DK", ReferenceData.countableCountry("GL")?.iso2)
        assertEquals("CN", ReferenceData.countableCountry("HK")?.iso2)
        assertEquals("FR", ReferenceData.countableCountry("RE")?.iso2)
        assertEquals("AT", ReferenceData.countableCountry("AT")?.iso2)
        assertNull(ReferenceData.countableCountry("AQ"))
    }

    @Test
    fun `jedes Gebiet zeigt auf ein zaehlbares Land und ist selbst keines`() {
        ReferenceData.territoryToCountry.forEach { (territory, mother) ->
            assertNull(ReferenceData.country(territory), territory)
            assertTrue(ReferenceData.country(mother) != null, mother)
        }
    }

    @Test
    fun `Bundeslaender fuer Oesterreich und Deutschland`() {
        assertEquals(9, ReferenceData.regionsOf("AT").size)
        assertEquals(16, ReferenceData.regionsOf("DE").size)
        assertEquals("Salzburg", ReferenceData.region("AT-5")?.nameDe)
    }
}
