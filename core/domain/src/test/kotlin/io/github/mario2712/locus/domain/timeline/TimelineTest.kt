package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.JOHANNA
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.usecase.DayInsights
import io.github.mario2712.locus.domain.usecase.FakeStayRepository
import io.github.mario2712.locus.domain.usecase.JourneyLeg
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Deine Beispieltage als Tests (Stationen-Konzept). */
class TimelineTest {
    private val home = "hallein"
    private val repo = FakeStayRepository()
    private var counter = 0
    private val commands = StayCommands(repo).apply { newId = { "s${++counter}" } }
    private val rank = StayPriority.ranker(Defaults.stayTypes.associateBy { it.id })
    private val both = setOf(ME, JOHANNA)
    private fun d(s: String) = LocalDate.parse(s)
    private fun resolved(today: String) = Timeline.resolve(repo.all, home, d(today))

    @Test
    fun `Bsp 1 - Buerotag ist eine einzige Station, abends wieder zuhause`() = runTest {
        commands.addStation(d("2026-11-17"), setOf(ME), StayTypeIds.OFFICE, "salzburg", setOf(TransportMode.TRAIN))
        val r = resolved("2026-11-17")
        assertNull(Timeline.baseAtEnd(d("2026-11-17"), ME, r, home)) // Homebase
        assertEquals(StayTypeIds.OFFICE, DayInsights.summaries(d("2026-11-17"), d("2026-11-17"), ME, r, rank).values.single().mainTypeId)
    }

    @Test
    fun `Bsp 3 - Buero, Homeoffice, Freizeit implizit - Hauptart Buero`() = runTest {
        commands.addStation(d("2026-11-19"), setOf(ME), StayTypeIds.OFFICE, "salzburg")
        commands.addStation(d("2026-11-19"), setOf(ME), StayTypeIds.HOMEOFFICE, home)
        val summary = DayInsights.summaries(d("2026-11-19"), d("2026-11-19"), ME, resolved("2026-11-19"), rank).values.single()
        assertEquals(StayTypeIds.OFFICE, summary.mainTypeId)
        assertEquals(setOf(StayTypeIds.OFFICE, StayTypeIds.HOMEOFFICE), summary.typeIds)
    }

    @Test
    fun `Bsp 4 - Autofahrt nach Bologna, Basis laeuft weiter bis zur Heimreise`() = runTest {
        commands.journey(d("2026-08-01"), both, listOf(JourneyLeg("bologna", setOf(TransportMode.CAR))), StayTypeIds.VACATION)

        // Noch unterwegs: Basis läuft bis heute
        val ongoing = resolved("2026-08-04")
        assertEquals("bologna", Timeline.baseAtStart(d("2026-08-04"), ME, ongoing)!!.placeId)
        assertEquals(d("2026-08-04"), ongoing.single().endDate)
        // Am heutigen Abend läuft die Reise weiter
        assertEquals("bologna", Timeline.baseAtEnd(d("2026-08-04"), ME, ongoing, home, today = d("2026-08-04"))!!.placeId)

        commands.returnHome(d("2026-08-06"), both, home, setOf(TransportMode.CAR))
        val done = resolved("2026-08-20")
        assertEquals(d("2026-08-06"), done.first { it.placeId == "bologna" }.endDate)
        assertNull(Timeline.baseAtStart(d("2026-08-07"), ME, done))
        assertEquals(
            List(6) { StayTypeIds.VACATION },
            DayInsights.summaries(d("2026-08-01"), d("2026-08-06"), ME, done, rank).values.map { it.mainTypeId },
        )
        // Johanna war dabei
        assertEquals("bologna", Timeline.baseAtStart(d("2026-08-03"), JOHANNA, done)!!.placeId)
    }

    @Test
    fun `Bsp 5 - Flug mit Umstieg am Folgetag, Transit ohne Basis`() = runTest {
        commands.journey(
            d("2026-03-01"), setOf(ME),
            listOf(
                JourneyLeg("muc", setOf(TransportMode.CAR)),
                JourneyLeg("y", setOf(TransportMode.PLANE), dayOffset = 1),
                JourneyLeg("x", setOf(TransportMode.PLANE), dayOffset = 1),
            ),
            StayTypeIds.VACATION,
        )
        val stays = repo.all
        assertEquals(listOf(StayTypeIds.TRANSIT, StayTypeIds.TRANSIT, StayTypeIds.VACATION), stays.map { it.typeId })
        assertEquals(listOf(d("2026-03-01"), d("2026-03-02"), d("2026-03-02")), stays.map { it.startDate })
        assertEquals(listOf(false, false, true), stays.map { it.overnight })
        val r = resolved("2026-03-05")
        assertEquals("x", Timeline.baseAtEnd(d("2026-03-02"), ME, r, home)!!.placeId)
    }

    @Test
    fun `Usbekistan - Weiterreise verschiebt die Basis, Ausflug kehrt zur Basis zurueck`() = runTest {
        commands.journey(d("2026-05-01"), setOf(ME), listOf(JourneyLeg("taschkent", setOf(TransportMode.PLANE))), StayTypeIds.VACATION)
        commands.journey(d("2026-05-03"), setOf(ME), listOf(JourneyLeg("samarkand", setOf(TransportMode.TRAIN))), StayTypeIds.VACATION)
        commands.addStation(d("2026-05-04"), setOf(ME), StayTypeIds.EXCURSION, "shahrisabz", setOf(TransportMode.CAR))

        val r = resolved("2026-05-05")
        assertEquals(d("2026-05-03"), r.first { it.placeId == "taschkent" }.endDate)
        assertEquals("taschkent", Timeline.baseAtStart(d("2026-05-03"), ME, r)!!.placeId)
        assertEquals("samarkand", Timeline.baseAtEnd(d("2026-05-03"), ME, r, home)!!.placeId)
        // Nach dem Ausflug ist Samarkand weiterhin die Basis
        assertEquals("samarkand", Timeline.baseAtEnd(d("2026-05-04"), ME, r, home)!!.placeId)
        val samarkand = Timeline.baseAtStart(d("2026-05-05"), ME, r)!!
        assertEquals(d("2026-05-01"), Timeline.tripStart(samarkand, ME, r))
        // Ausflugstag: Urlaub hat Vorrang vor Ausflug
        assertEquals(
            StayTypeIds.VACATION,
            DayInsights.summaries(d("2026-05-04"), d("2026-05-04"), ME, r, rank).values.single().mainTypeId,
        )
    }

    @Test
    fun `Dienstreise Wien mit Uebernachtung, ohne Uebernachtung zurueck nach Hause`() = runTest {
        commands.addStation(d("2026-11-24"), setOf(ME), StayTypeIds.BUSINESS_TRIP, "wien", setOf(TransportMode.TRAIN), overnight = true)
        commands.addStation(d("2026-11-26"), setOf(ME), StayTypeIds.BUSINESS_TRIP, "linz", setOf(TransportMode.TRAIN))
        val r = resolved("2026-11-25")
        assertEquals("wien", Timeline.baseAtStart(d("2026-11-25"), ME, r)!!.placeId)
        // Linz (ohne Übernachtung) beendet die Wien-Basis nicht – erst Heimreise
        commands.returnHome(d("2026-11-25"), setOf(ME), home)
        val r2 = resolved("2026-11-30")
        assertEquals(d("2026-11-25"), r2.first { it.placeId == "wien" }.endDate)
        assertNull(Timeline.baseAtEnd(d("2026-11-26"), ME, r2, home))
    }

    @Test
    fun `explizites Ende einer Uebernachtungs-Station bleibt erhalten`() {
        val stay = Stay("1", "rom", StayTypeIds.VACATION, d("2025-05-01"), d("2025-05-10"), participants = setOf(ME), overnight = true)
        assertEquals(d("2025-05-10"), Timeline.resolve(listOf(stay), home, d("2026-01-01")).single().endDate)
    }
}
