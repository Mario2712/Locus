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

class OnThisDayTest {
    private val me = Defaults.ME
    private fun d(s: String) = LocalDate.parse(s)
    private val places = listOf(
        Place("home", "Zuhause", PlaceKind.ADDRESS, "AT"),
        Place("tas", "Taschkent", PlaceKind.CITY, "UZ"),
        Place("lidl", "Lidl", PlaceKind.POI, "AT"),
        Place("dom", "Salzburger Dom", PlaceKind.POI, "AT"),
    ).associateBy { it.id }

    @Test
    fun `nur besondere Tage aus Vorjahren`() {
        val stays = listOf(
            Stay("t", "tas", StayTypeIds.VACATION, d("2023-11-20"), participants = setOf(me), overnight = true),
            Stay("back", "home", StayTypeIds.FREE, d("2023-11-28"), participants = setOf(me)),
            Stay("ho", "home", StayTypeIds.HOMEOFFICE, d("2024-11-23"), participants = setOf(me)),
            Stay("ho2", "home", StayTypeIds.HOMEOFFICE, d("2025-11-23"), participants = setOf(me)),
        )
        val visits = listOf(
            Visit("shop", "lidl", VisitCategoryIds.SHOPPING, d("2024-11-23"), participants = setOf(me)),
            Visit("dom", "dom", VisitCategoryIds.SIGHT, d("2025-11-23"), participants = setOf(me)),
        )
        val resolved = Timeline.resolve(stays, Homebases.single("home"), d("2026-11-23"))
        val memories = OnThisDay.find(
            d("2026-11-23"), me, resolved, visits, places, Defaults.stayTypes.associateBy { it.id },
            Defaults.visitCategories.associateBy { it.id }, Homebases.single("home"),
        )
        // 2025: Dombesuch; 2024: nur Homeoffice + Einkauf → nichts; 2023: Taschkent
        assertEquals(listOf(1, 3), memories.map { it.yearsAgo })
        assertEquals(listOf("dom"), memories[0].visitPlaceIds)
        assertEquals(listOf("tas"), memories[1].placeIds)
        assertEquals(listOf("UZ"), memories[1].countries)
    }
}
