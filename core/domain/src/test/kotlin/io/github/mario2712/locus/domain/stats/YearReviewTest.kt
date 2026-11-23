package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class YearReviewTest {
    private val me = Defaults.ME
    private val jo = Defaults.JOHANNA
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT"),
        Place("salzburg", "Salzburg", PlaceKind.CITY, "AT"),
        Place("han", "Hanoi", PlaceKind.CITY, "VN"),
    ).associateBy { it.id }
    private val types = Defaults.stayTypes.associateBy { it.id }

    @Test
    fun `Rueckblick ohne Arbeit, mit Monaten, gemeinsam und Geburtstag`() {
        val stays = listOf(
            Stay("o", "salzburg", StayTypeIds.OFFICE, d("2026-03-04"), participants = setOf(me)),
            Stay("v", "han", StayTypeIds.VACATION, d("2026-09-13"), participants = setOf(me, jo), overnight = true),
            Stay("b", "home", StayTypeIds.FREE, d("2026-09-27"), participants = setOf(me, jo)),
        )
        val r = YearReview.build(
            2026, me, jo, d("1996-09-20"), stays, emptyList(), places, types, Defaults.visitCategories.associateBy { it.id },
            StayPriority.ranker(types), Homebases.single("home"), d("2026-11-23"), includeWork = false,
        )
        assertEquals(false, r.stats.topCities.any { it.first == "salzburg" })
        assertEquals(listOf("VN"), r.months[8].countries)
        assertEquals(1, r.months[8].trips.size)
        assertEquals(15, r.togetherDays) // 13.–27.09.
        assertEquals("han", r.birthday?.placeId)
        assertEquals(null, r.previous)
    }
}
