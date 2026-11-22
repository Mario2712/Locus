package io.github.mario2712.locus.domain.trips

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TripFilterTest {
    private val me = Defaults.ME
    private val jo = Defaults.JOHANNA
    private val types = Defaults.stayTypes.associateBy { it.id }
    private fun trip(id: String, people: Set<io.github.mario2712.locus.domain.model.PersonId>, vararg typeIds: String) = TripSummary(
        id, LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-03"), DatePrecision.DAY, false,
        emptyList(), emptyList(), emptyList(), emptyList(), people, typeIds.first(), typeIds.toSet(),
    )
    private val amsterdam = trip("ams", setOf(me), StayTypeIds.BUSINESS_TRIP)
    private val hanoi = trip("han", setOf(me, jo), StayTypeIds.VACATION)
    private val linzPlus = trip("linz", setOf(me), StayTypeIds.BUSINESS_TRIP, StayTypeIds.VACATION)
    private val all = listOf(amsterdam, hanoi, linzPlus)
    private fun ids(work: TripWorkFilter, person: TripPersonFilter) =
        TripFilter.apply(all, work, person, me, types).map { it.id }

    @Test
    fun `Arbeit und Privat - verlaengerte Dienstreise in beiden`() {
        assertEquals(listOf("ams", "linz"), ids(TripWorkFilter.WORK, TripPersonFilter.All))
        assertEquals(listOf("han", "linz"), ids(TripWorkFilter.PRIVATE, TripPersonFilter.All))
    }

    @Test
    fun `Personen - mit Johanna oder allein`() {
        assertEquals(listOf("han"), ids(TripWorkFilter.ALL, TripPersonFilter.With(jo)))
        assertEquals(listOf("ams", "linz"), ids(TripWorkFilter.ALL, TripPersonFilter.Alone))
        assertEquals(emptyList<String>(), ids(TripWorkFilter.WORK, TripPersonFilter.With(jo)))
    }
}
