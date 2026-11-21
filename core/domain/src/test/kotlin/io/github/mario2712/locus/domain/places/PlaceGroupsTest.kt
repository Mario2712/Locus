package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlaceGroupsTest {
    private fun poi(category: String?) = Place("p", "X", PlaceKind.POI, "AT", poiCategory = category)

    @Test
    fun `Stadt oder Oertlichkeit`() {
        assertTrue(PlaceGroups.isLocality(Place("c", "Salzburg", PlaceKind.CITY, "AT")))
        assertFalse(PlaceGroups.isLocality(poi("museum")))
        assertFalse(PlaceGroups.isLocality(Place("a", "Davisstraße 1", PlaceKind.ADDRESS, "AT")))
    }

    @Test
    fun `Art aus OSM-Kategorie`() {
        assertEquals(LocationGroup.FOOD, PlaceGroups.groupOf(poi("restaurant")))
        assertEquals(LocationGroup.CULTURE, PlaceGroups.groupOf(poi("museum")))
        assertEquals(LocationGroup.NATURE, PlaceGroups.groupOf(poi("peak")))
        assertEquals(LocationGroup.LODGING, PlaceGroups.groupOf(poi("alpine_hut")))
        assertEquals(LocationGroup.OTHER, PlaceGroups.groupOf(poi("supermarket")))
    }

    @Test
    fun `ohne OSM-Kategorie zaehlen die Besuchskategorien`() {
        val d = LocalDate.parse("2026-11-21")
        val visits = listOf(
            Visit("1", "p", VisitCategoryIds.SUMMIT, d, participants = setOf(ME)),
            Visit("2", "p", VisitCategoryIds.SUMMIT, d.plusDays(1), participants = setOf(ME), extraCategoryIds = setOf(VisitCategoryIds.RESTAURANT)),
        )
        assertEquals(LocationGroup.NATURE, PlaceGroups.groupOf(poi(null), visits))
    }
}
