package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.countrypoints.ForeignDays
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.usecase.FakeStayRepository
import io.github.mario2712.locus.domain.usecase.JourneyLeg
import io.github.mario2712.locus.domain.usecase.StayCommands
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HomebasesTest {
    private fun d(s: String) = LocalDate.parse(s)

    private val places = listOf(
        Place("eltern", "Wohnung Eltern", PlaceKind.ADDRESS, "AT", homebaseSince = UNKNOWN_DATE),
        Place("w2", "Wohnung 2", PlaceKind.ADDRESS, "AT", homebaseSince = d("2018-02-05")),
        Place("w3", "Wohnung 3", PlaceKind.ADDRESS, "AT", homebaseSince = d("2022-08-01"), isHomebase = true),
        Place("muc", "Flughafen München", PlaceKind.POI, "DE"),
        Place("han", "Flughafen Hanoi", PlaceKind.POI, "VN"),
        Place("hotel", "Hotel Hanoi", PlaceKind.POI, "VN"),
        Place("freilassing", "Freilassing", PlaceKind.CITY, "DE"),
    ).associateBy { it.id }
    private val homes = Homebases.of(places.values)

    @Test
    fun `Homebase je Datum mit Umzuegen`() {
        assertEquals("eltern", homes.at(d("2016-06-01")))
        assertEquals("w2", homes.at(d("2018-02-05")))
        assertEquals("w2", homes.at(d("2022-07-31")))
        assertEquals("w3", homes.at(d("2026-11-21")))
        assertEquals(d("2022-07-31"), homes.until(homes.periods[1]))
        assertNull(homes.until(homes.periods[2]))
    }

    @Test
    fun `Heimreise zur damaligen Wohnung beendet die Reise, nicht zur heutigen`() = runTest {
        val repo = FakeStayRepository()
        var n = 0
        val commands = StayCommands(repo).apply { newId = { "s${++n}" } }
        commands.journey(d("2019-07-01"), setOf(ME), listOf(JourneyLeg("hotel", setOf(TransportMode.PLANE))), StayTypeIds.VACATION)
        commands.returnHome(d("2019-07-10"), setOf(ME), "w2")
        val resolved = Timeline.resolve(repo.all, homes, d("2026-11-21"))
        assertEquals(d("2019-07-10"), resolved.first { it.placeId == "hotel" }.endDate)
        assertNull(Timeline.baseAtEnd(d("2019-07-10"), ME, resolved, homes))
    }

    @Test
    fun `Hanoi - Flaggen nur fuer zaehlbare Auslandstage, Transit nicht`() = runTest {
        val repo = FakeStayRepository()
        var n = 0
        val commands = StayCommands(repo).apply { newId = { "s${++n}" } }
        commands.journey(
            d("2026-09-12"), setOf(ME),
            listOf(
                JourneyLeg("muc", setOf(TransportMode.CAR)),
                JourneyLeg("han", setOf(TransportMode.PLANE), dayOffset = 1),
                JourneyLeg("hotel", setOf(TransportMode.TAXI), dayOffset = 1),
            ),
            StayTypeIds.VACATION,
        )
        commands.returnHome(d("2026-09-20"), setOf(ME), "w3")
        val visits = listOf(Visit("v", "freilassing", VisitCategoryIds.SHOPPING, d("2026-10-03"), participants = setOf(ME)))
        val resolved = Timeline.resolve(repo.all, homes, d("2026-11-21"))
        val flags = ForeignDays.of(
            d("2026-09-01"), d("2026-10-31"), ME, resolved, visits, places,
            Defaults.stayTypes.associateBy { it.id }, Defaults.visitCategories.associateBy { it.id }, homes,
        )
        assertNull(flags[d("2026-09-12")]) // nur Transit München
        assertEquals(listOf("VN"), flags[d("2026-09-13")])
        assertEquals(listOf("VN"), flags[d("2026-09-20")]) // Heimreisetag: Basis Hanoi bis zur Heimreise
        assertEquals(listOf("DE"), flags[d("2026-10-03")])
        assertNull(flags[d("2026-09-21")])
    }
}
