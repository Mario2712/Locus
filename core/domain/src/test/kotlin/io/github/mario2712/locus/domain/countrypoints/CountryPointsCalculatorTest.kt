package io.github.mario2712.locus.domain.countrypoints

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CountryPointsCalculatorTest {

    private val me = PersonId("me")
    private val johanna = PersonId("johanna")

    private val stayTypes = listOf(
        StayType("urlaub", "Urlaub", StayGroup.VACATION, countsForCountryPoints = true),
        StayType("ausflug", "Ausflug", StayGroup.FREE, countsForCountryPoints = true),
        StayType("transit", "Transit", StayGroup.OTHER, countsForCountryPoints = false),
        StayType("anreise", "Anreise/Unterwegs", StayGroup.OTHER, countsForCountryPoints = false),
    ).associateBy { it.id }

    private val visitCategories = listOf(
        VisitCategory("sight", "Sehenswürdigkeit", countsForCountryPoints = true),
        VisitCategory("restaurant", "Restaurant/Café", countsForCountryPoints = true),
        VisitCategory("rast", "Rast/Tankstopp", countsForCountryPoints = false),
    ).associateBy { it.id }

    private val places = listOf(
        Place("yyz", "Flughafen Toronto", PlaceKind.POI, "CA"),
        Place("havana", "Havanna", PlaceKind.CITY, "CU"),
        Place("pek", "Peking", PlaceKind.CITY, "CN"),
        Place("mauer", "Chinesische Mauer", PlaceKind.POI, "CN"),
        Place("tankstelle-si", "Raststätte Slowenien", PlaceKind.POI, "SI"),
        Place("gostilna-si", "Gostilna Slowenien", PlaceKind.POI, "SI"),
        Place("split", "Split", PlaceKind.CITY, "HR"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
        Place("nuuk", "Nuuk", PlaceKind.CITY, "GL"),
        Place("mcmurdo", "McMurdo", PlaceKind.POI, "AQ"),
    ).associateBy { it.id }

    private val calculator = CountryPointsCalculator()

    private fun calc(stays: List<Stay> = emptyList(), visits: List<Visit> = emptyList()) =
        calculator.calculate(stays, visits, places, stayTypes, visitCategories)

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Umstieg in Toronto als Transit gibt keinen Länderpunkt fuer Kanada`() {
        val points = calc(
            stays = listOf(
                Stay("1", "yyz", "transit", d("2019-03-01"), participants = setOf(me)),
                Stay("2", "havana", "urlaub", d("2019-03-02"), d("2019-03-14"), participants = setOf(me)),
            ),
        )
        assertEquals(setOf("CU"), points.countriesOf(me))
    }

    @Test
    fun `Zwischenlandung in Peking mit Besuch der Mauer gibt Laenderpunkt China`() {
        val points = calc(
            stays = listOf(Stay("1", "pek", "ausflug", d("2018-10-10"), participants = setOf(me))),
            visits = listOf(Visit("v", "mauer", "sight", d("2018-10-10"), participants = setOf(me))),
        )
        assertEquals(setOf("CN"), points.countriesOf(me))
    }

    @Test
    fun `Durchfahrt mit Tankstopp gibt keinen Punkt, Essen gehen schon`() {
        val durchfahrt = Stay("1", "tankstelle-si", "anreise", d("2023-08-01"), participants = setOf(me))
        val tankstopp = Visit("v1", "tankstelle-si", "rast", d("2023-08-01"), participants = setOf(me))
        val ziel = Stay("2", "split", "urlaub", d("2023-08-01"), d("2023-08-10"), participants = setOf(me))

        assertEquals(setOf("HR"), calc(listOf(durchfahrt, ziel), listOf(tankstopp)).countriesOf(me))

        val essen = Visit("v2", "gostilna-si", "restaurant", d("2023-08-01"), participants = setOf(me))
        assertEquals(setOf("HR", "SI"), calc(listOf(durchfahrt, ziel), listOf(tankstopp, essen)).countriesOf(me))
    }

    @Test
    fun `Override am Besuch schlaegt Kategorie-Regel`() {
        val visit = Visit(
            "v", "tankstelle-si", "rast", d("2023-08-01"),
            participants = setOf(me), countsForCountryPointsOverride = true,
        )
        assertEquals(setOf("SI"), calc(visits = listOf(visit)).countriesOf(me))
    }

    @Test
    fun `gemeinsam heisst zur selben Zeit im Land`() {
        val points = calc(
            stays = listOf(
                // gemeinsamer Eintrag
                Stay("1", "split", "urlaub", d("2023-08-01"), d("2023-08-10"), participants = setOf(me, johanna)),
                // getrennte, aber überlappende Einträge
                Stay("2", "pek", "urlaub", d("2018-10-01"), d("2018-10-05"), participants = setOf(me)),
                Stay("3", "pek", "urlaub", d("2018-10-05"), d("2018-10-08"), participants = setOf(johanna)),
                // beide in Italien, aber zu unterschiedlichen Zeiten
                Stay("4", "rom", "urlaub", d("2015-05-01"), participants = setOf(me)),
                Stay("5", "rom", "urlaub", d("2016-05-01"), participants = setOf(johanna)),
                // nur ich in Kuba
                Stay("6", "havana", "urlaub", d("2019-03-02"), participants = setOf(me)),
            ),
        )
        assertEquals(setOf("CN", "HR"), points.together(me, johanna))
        assertEquals(setOf("CN", "HR", "IT"), points.bothVisited(me, johanna))
        assertEquals(setOf("CU"), points.onlyBy(me, johanna))
        assertEquals(emptySet<String>(), points.onlyBy(johanna, me))
    }

    @Test
    fun `unscharfe Datumsangabe zaehlt und liefert ersten und letzten Besuch`() {
        val points = calc(
            visits = listOf(
                Visit("v1", "rom", "sight", d("2014-07-15"), precision = DatePrecision.MONTH, participants = setOf(me)),
                Visit("v2", "rom", "sight", d("2020-04-02"), participants = setOf(me)),
            ),
        )
        val stats = points.byPerson.getValue(me).getValue("IT")
        assertEquals(d("2014-07-01"), stats.firstDate)
        assertEquals(d("2020-04-02"), stats.lastDate)
    }

    @Test
    fun `Groenland zaehlt fuer Daenemark, Antarktis gibt keinen Punkt`() {
        val points = calc(
            stays = listOf(
                Stay("1", "nuuk", "urlaub", d("2024-07-01"), participants = setOf(me)),
                Stay("2", "mcmurdo", "urlaub", d("2025-01-01"), participants = setOf(me)),
            ),
        )
        assertEquals(setOf("DK"), points.countriesOf(me))
    }

    @Test
    fun `Besuch mit mehreren Kategorien zaehlt, wenn eine davon zaehlt`() {
        val visit = Visit(
            "v", "tankstelle-si", "rast", d("2023-08-01"), participants = setOf(me),
            extraCategoryIds = setOf("restaurant"),
        )
        assertEquals(setOf("SI"), calc(visits = listOf(visit)).countriesOf(me))
    }
}
