package io.github.mario2712.locus.domain.trips

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults.JOHANNA
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.stats.PlaceStats
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.timeline.TimelineDays
import io.github.mario2712.locus.domain.usecase.FakeStayRepository
import io.github.mario2712.locus.domain.usecase.JourneyLeg
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TripsTest {
    private val home = "hallein"
    private val places = listOf(
        Place(home, "Hallein", PlaceKind.CITY, "AT", isHomebase = true),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT"),
        Place("muc", "Flughafen München", PlaceKind.POI, "DE"),
        Place("ist", "Flughafen Istanbul", PlaceKind.POI, "TR"),
        Place("taschkent", "Taschkent", PlaceKind.CITY, "UZ"),
        Place("samarkand", "Samarkand", PlaceKind.CITY, "UZ"),
        Place("rom", "Rom", PlaceKind.CITY, "IT"),
        Place("bologna", "Bologna", PlaceKind.CITY, "IT"),
        Place("ljubljana", "Ljubljana", PlaceKind.CITY, "SI"),
    ).associateBy { it.id }
    private val repo = FakeStayRepository()
    private var counter = 0
    private val commands = StayCommands(repo).apply { newId = { "s${++counter}" } }
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Usbekistan-Reise mit Abflug am Vortag, Weiterreise und Heimreise`() = runTest {
        commands.journey(
            d("2026-04-30"), setOf(ME, JOHANNA),
            listOf(
                JourneyLeg("muc", setOf(TransportMode.CAR)),
                JourneyLeg("ist", setOf(TransportMode.PLANE), dayOffset = 1),
                JourneyLeg("taschkent", setOf(TransportMode.PLANE), dayOffset = 1),
            ),
            StayTypeIds.VACATION,
        )
        commands.journey(d("2026-05-03"), setOf(ME, JOHANNA), listOf(JourneyLeg("samarkand", setOf(TransportMode.TRAIN))), StayTypeIds.VACATION)
        commands.returnHome(d("2026-05-08"), setOf(ME, JOHANNA), home, setOf(TransportMode.PLANE))
        val visits = listOf(Visit("v", "samarkand", VisitCategoryIds.SIGHT, d("2026-05-05"), participants = setOf(ME)))

        val trips = Trips.derive(repo.all, visits, places, ME, home, d("2026-11-21"))
        val trip = trips.single()
        assertEquals(d("2026-04-30"), trip.start)
        assertEquals(d("2026-05-08"), trip.end)
        assertEquals(9, trip.days)
        assertEquals(listOf("taschkent", "samarkand"), trip.basePlaceIds)
        assertEquals(listOf("UZ"), trip.countryIso2s) // Transit DE/TR zählt nicht
        assertFalse(trip.ongoing)
        assertEquals(1, trip.visits.size)
    }

    @Test
    fun `laufende Reise, alter Mehrtages-Eintrag und unscharfe Reise`() = runTest {
        commands.journey(d("2026-11-19"), setOf(ME), listOf(JourneyLeg("bologna")), StayTypeIds.VACATION)
        repo.upsert(Stay("old", "ljubljana", StayTypeIds.VACATION, d("2026-10-02"), d("2026-10-04"), participants = setOf(ME)))
        commands.addPastTrip(listOf("rom"), d("2014-07-01"), d("2014-07-31"), DatePrecision.MONTH, setOf(ME), StayTypeIds.VACATION)

        val trips = Trips.derive(repo.all, emptyList(), places, ME, home, d("2026-11-21"))
        assertEquals(listOf("bologna", "ljubljana", "rom"), trips.map { it.basePlaceIds.single() })
        assertTrue(trips[0].ongoing)
        assertEquals(d("2026-11-21"), trips[0].end)
        assertEquals(DatePrecision.MONTH, trips[2].precision)
    }

    @Test
    fun `vergangene Reise mit genauen Daten hat festes Ende, eintaegig ohne Uebernachtung`() = runTest {
        val multi = commands.addPastTrip(listOf("rom"), d("2019-06-01"), d("2019-06-07"), DatePrecision.DAY, setOf(ME), StayTypeIds.VACATION)
        assertTrue(multi.single().overnight)
        val resolved = Timeline.resolve(repo.all, home, d("2026-11-21"))
        assertEquals(d("2019-06-07"), resolved.single().endDate)
        val single = commands.addPastTrip(listOf("bologna"), d("2018-03-03"), d("2018-03-03"), DatePrecision.DAY, setOf(ME), StayTypeIds.EXCURSION)
        assertFalse(single.single().overnight)
    }

    @Test
    fun `Timeline-Tage und Orts-Akte`() = runTest {
        commands.addStation(d("2026-11-16"), setOf(ME), StayTypeIds.HOMEOFFICE, home)
        commands.addStation(d("2026-11-17"), setOf(ME), StayTypeIds.OFFICE, "salzburg")
        commands.addStation(d("2026-11-18"), setOf(ME), StayTypeIds.OFFICE, "salzburg")
        val visits = listOf(
            Visit("v1", "salzburg", VisitCategoryIds.SHOPPING, d("2026-11-21"), participants = setOf(ME),
                extraCategoryIds = setOf(VisitCategoryIds.RESTAURANT)),
        )
        val resolved = Timeline.resolve(repo.all, home, d("2026-11-21"))
        val days = TimelineDays.build(resolved, visits, ME, d("2026-11-21"))
        assertEquals(listOf(d("2026-11-21"), d("2026-11-18"), d("2026-11-17"), d("2026-11-16")), days.map { it.date })

        val record = PlaceStats.record("salzburg", ME, resolved, visits)
        assertEquals(2, record.stayDays)
        assertEquals(mapOf(StayTypeIds.OFFICE to 2), record.daysByType)
        assertEquals(1, record.visitCount)
        assertEquals(mapOf(VisitCategoryIds.SHOPPING to 1, VisitCategoryIds.RESTAURANT to 1), record.visitsByCategory)
        assertEquals(3, record.totalDays)
        assertEquals(d("2026-11-17"), record.firstDate)
        assertEquals(d("2026-11-21"), record.lastDate)
    }
}
