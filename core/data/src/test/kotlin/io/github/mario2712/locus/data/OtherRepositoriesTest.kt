package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.repository.RoomCatalogRepository
import io.github.mario2712.locus.data.repository.RoomPatternRuleRepository
import io.github.mario2712.locus.data.repository.RoomPlaceRepository
import io.github.mario2712.locus.data.repository.RoomTripRepository
import io.github.mario2712.locus.data.repository.RoomVisitRepository
import io.github.mario2712.locus.data.repository.RoomWishRepository
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.RegionCollection
import io.github.mario2712.locus.domain.model.Trip
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.Wish
import io.github.mario2712.locus.domain.reference.Defaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class OtherRepositoriesTest : DbTestBase() {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Orte - Favoriten, OSM-Suche und Soft-Delete`() = runTest {
        val repo = RoomPlaceRepository(db.placeDao(), meta)
        val home = Place(
            "p1", "Hallein", PlaceKind.CITY, "at", regionCode = "AT-5", latitude = 47.68, longitude = 13.09,
            osmId = "relation/123", alias = "Zuhause", isFavorite = true,
        )
        val office = Place("p2", "Salzburg", PlaceKind.CITY, "AT", alias = "Büro", isFavorite = true)
        val burg = Place("p3", "Ljubljanski grad", PlaceKind.POI, "SI", parentPlaceId = "p4")
        listOf(home, office, burg).forEach { repo.upsert(it) }

        assertEquals("AT", repo.get("p1")!!.countryIso2)
        assertEquals(listOf("Büro", "Zuhause"), repo.observeFavorites().first().map { it.displayName })
        assertEquals("p1", repo.findByOsmId("relation/123")!!.id)

        repo.delete("p1")
        assertNull(repo.findByOsmId("relation/123"))
        assertEquals(listOf("p3", "p2"), repo.observeAll().first().map { it.id })
    }

    @Test
    fun `Besuche - nach Ort, Zeitraum und mit Override`() = runTest {
        val repo = RoomVisitRepository(db.visitDao(), meta)
        val burg = Visit(
            "v1", "burg", Defaults.VisitCategoryIds.SIGHT, d("2026-11-21"), time = LocalTime.of(10, 30),
            participants = setOf(Defaults.ME, Defaults.JOHANNA), rating = 5, tripId = "t1",
        )
        val tank = Visit(
            "v2", "tank", Defaults.VisitCategoryIds.REST_STOP, d("2026-11-20"),
            participants = setOf(Defaults.ME), countsForCountryPointsOverride = true,
        )
        repo.upsert(burg)
        repo.upsert(tank)

        assertEquals(burg, repo.get("v1"))
        assertEquals(true, repo.get("v2")!!.countsForCountryPointsOverride)
        assertEquals(listOf("v2", "v1"), repo.observeBetween(d("2026-11-20"), d("2026-11-22")).first().map { it.id })
        assertEquals(listOf("v1"), repo.observeByPlace("burg").first().map { it.id })
        assertEquals(listOf("v1"), repo.observeByTrip("t1").first().map { it.id })
    }

    @Test
    fun `Reisen, Wuensche und Muster - Rundreise durch die Datenbank`() = runTest {
        val trips = RoomTripRepository(db.tripDao(), meta)
        val trip = Trip(
            "t1", "Ljubljana Kurzurlaub", d("2026-11-20"), d("2026-11-22"), startTime = LocalTime.of(13, 0),
            participants = setOf(Defaults.ME, Defaults.JOHANNA),
        )
        trips.upsert(trip)
        assertEquals(trip, trips.get("t1"))

        val wishes = RoomWishRepository(db.wishDao(), meta)
        val wish = Wish("w1", countryIso2 = "jp", priority = 2, createdAtEpochMillis = 42)
        wishes.upsert(wish)
        assertEquals(wish.copy(countryIso2 = "JP"), wishes.observeAll().first().single())

        val rules = RoomPatternRuleRepository(db.patternRuleDao(), meta)
        val rule = PatternRule("r1", Defaults.ME, DayOfWeek.TUESDAY, Defaults.StayTypeIds.OFFICE, "p2")
        rules.upsert(rule)
        assertEquals(listOf(rule), rules.observeAll().first())
        rules.delete("r1")
        assertEquals(emptyList<PatternRule>(), rules.observeAll().first())
    }

    @Test
    fun `Regionen-Sammlung ein- und ausschalten`() = runTest {
        val catalog = RoomCatalogRepository(db.catalogDao(), meta)
        catalog.setRegionCollection(RegionCollection("AT", enabled = true))
        catalog.setRegionCollection(RegionCollection("AT", enabled = false))
        assertEquals(listOf(RegionCollection("AT", false)), catalog.observeRegionCollections().first())
    }
}
