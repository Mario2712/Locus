package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UnusedPlacesTest {
    private val me = Defaults.ME
    private val d = LocalDate.parse("2026-05-01")

    @Test
    fun `nur Orte ohne jeden Bezug sind unbenutzt`() {
        val places = listOf(
            Place("home", "Zuhause", PlaceKind.ADDRESS, "AT", isHomebase = true),
            Place("old", "Wohnung 1", PlaceKind.ADDRESS, "AT", homebaseSince = LocalDate.parse("2010-01-01")),
            Place("hoian", "Hoi An", PlaceKind.CITY, "VN"),
            Place("hotel", "Hotel", PlaceKind.POI, "VN", parentPlaceId = "hoian"),
            Place("ninh", "Ninh Binh", PlaceKind.CITY, "VN"),
            Place("bad", "Unbenutzt", PlaceKind.POI, "VN", parentPlaceId = "ninh"),
            Place("fav", "Lieblingscafé", PlaceKind.POI, "AT", isFavorite = true),
            Place("tpl", "Büro", PlaceKind.POI, "AT"),
            Place("lidl", "Lidl", PlaceKind.POI, "AT"),
        )
        val unused = UnusedPlaces.find(
            places,
            stays = listOf(Stay("s", "hotel", "stay-vacation", d, participants = setOf(me))),
            visits = listOf(Visit("v", "lidl", "visit-shopping", d, participants = setOf(me))),
            templates = listOf(DayTemplate("t", "Bürotag", listOf(TemplateStep("stay-office", "tpl")))),
            rules = emptyList(),
            wishes = emptyList(),
        )
        // Hoi An bleibt wegen des Hotels; Ninh Binh fällt mit seiner unbenutzten Örtlichkeit weg.
        assertEquals(setOf("ninh", "bad"), unused)
    }
}
