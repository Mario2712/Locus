package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TimelineFilterTest {
    private val me = Defaults.ME
    private val jo = Defaults.JOHANNA
    private fun d(s: String) = LocalDate.parse(s)
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val places = listOf(
        Place("hallein", "Hallein", PlaceKind.CITY, "AT"),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT"),
        Place("arzt", "Dr. Huber", PlaceKind.POI, "AT", parentPlaceId = "hallein"),
        Place("linz", "Linz", PlaceKind.CITY, "AT"),
    ).associateBy { it.id }
    private val days = listOf(
        TimelineDay(d("2026-03-02"), null, listOf(Stay("o", "salzburg", StayTypeIds.OFFICE, d("2026-03-02"), participants = setOf(me))),
            listOf(Visit("a", "arzt", VisitCategoryIds.HEALTH, d("2026-03-02"), participants = setOf(me)))),
        TimelineDay(d("2026-03-03"), null, listOf(Stay("h", "hallein", StayTypeIds.HOMEOFFICE, d("2026-03-03"), participants = setOf(me))), emptyList()),
        TimelineDay(d("2026-03-07"), null, listOf(Stay("x", "linz", StayTypeIds.EXCURSION, d("2026-03-07"), participants = setOf(me, jo))), emptyList()),
    )
    private fun dates(f: TimelineFilter) = f.apply(days, types, places).map { it.date.dayOfMonth }

    @Test
    fun `Arbeit ausblenden oder nur Arbeit`() {
        val noWork = TimelineFilter(work = TimelineWork.NO_WORK).apply(days, types, places)
        assertEquals(listOf(2, 7), noWork.map { it.date.dayOfMonth })
        assertEquals(emptyList<Stay>(), noWork.first().stations) // Büro weg, Arztbesuch bleibt
        assertEquals(listOf(2, 3), dates(TimelineFilter(work = TimelineWork.ONLY_WORK)))
    }

    @Test
    fun `Art, Kategorie, Ort und Person`() {
        assertEquals(listOf(3), dates(TimelineFilter(stayTypeId = StayTypeIds.HOMEOFFICE)))
        assertEquals(listOf(2), dates(TimelineFilter(categoryId = VisitCategoryIds.HEALTH)))
        assertEquals(listOf(2, 3), dates(TimelineFilter(query = "hallein"))) // Arzt liegt in Hallein
        assertEquals(listOf(7), dates(TimelineFilter(person = jo)))
    }
}
