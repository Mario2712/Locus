package io.github.mario2712.locus.domain.reference

import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.RegionCollection
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.VisitCategory

/**
 * Vorbelegte, vom Nutzer änderbare Daten, die beim ersten Start angelegt werden.
 * Die IDs sind stabil, damit Muster, Tests und spätere Sync-Partner sie eindeutig zuordnen.
 */
object Defaults {

    val ME = PersonId("person-me")
    val JOHANNA = PersonId("person-johanna")

    val persons: List<Person> = listOf(
        Person(ME, "Ich", isMe = true, colorArgb = 0xFF1F6F5C),
        Person(JOHANNA, "Johanna", isMe = false, colorArgb = 0xFFB4446C),
    )

    object StayTypeIds {
        const val HOMEOFFICE = "stay-homeoffice"
        const val OFFICE = "stay-office"
        const val BUSINESS_TRIP = "stay-business-trip"
        const val FREE = "stay-free"
        const val EXCURSION = "stay-excursion"
        const val VACATION = "stay-vacation"
        const val ON_THE_WAY = "stay-on-the-way"
        const val TRANSIT = "stay-transit"
        const val SICK = "stay-sick"
    }

    val stayTypes: List<StayType> = listOf(
        StayType(StayTypeIds.HOMEOFFICE, "Homeoffice", StayGroup.WORK, true, "home_work", 0xFF5C6BC0, 0),
        StayType(StayTypeIds.OFFICE, "Büro", StayGroup.WORK, true, "business", 0xFF3949AB, 1),
        StayType(StayTypeIds.BUSINESS_TRIP, "Dienstreise", StayGroup.WORK, true, "work_travel", 0xFF00897B, 2),
        StayType(StayTypeIds.FREE, "Frei / Zuhause", StayGroup.FREE, true, "weekend", 0xFF7CB342, 3),
        StayType(StayTypeIds.EXCURSION, "Ausflug", StayGroup.FREE, true, "hiking", 0xFFF9A825, 4),
        StayType(StayTypeIds.VACATION, "Urlaub", StayGroup.VACATION, true, "beach", 0xFFEF6C00, 5),
        StayType(StayTypeIds.ON_THE_WAY, "Anreise / Unterwegs", StayGroup.OTHER, false, "commute", 0xFF8D6E63, 6),
        StayType(StayTypeIds.TRANSIT, "Transit", StayGroup.OTHER, false, "connecting_airports", 0xFF90A4AE, 7),
        StayType(StayTypeIds.SICK, "Krank", StayGroup.OTHER, true, "sick", 0xFFE53935, 8),
    )

    object VisitCategoryIds {
        const val SIGHT = "visit-sight"
        const val RESTAURANT = "visit-restaurant"
        const val BAR = "visit-bar"
        const val NATURE = "visit-nature"
        const val SUMMIT = "visit-summit"
        const val EVENT = "visit-event"
        const val MUSEUM = "visit-museum"
        const val SHOPPING = "visit-shopping"
        const val SPORT = "visit-sport"
        const val ACCOMMODATION = "visit-accommodation"
        const val REST_STOP = "visit-rest-stop"
        const val OTHER = "visit-other"
    }

    val visitCategories: List<VisitCategory> = listOf(
        VisitCategory(VisitCategoryIds.SIGHT, "Sehenswürdigkeit", true, "attractions", 0),
        VisitCategory(VisitCategoryIds.RESTAURANT, "Restaurant / Café", true, "restaurant", 1),
        VisitCategory(VisitCategoryIds.BAR, "Bar", true, "local_bar", 2),
        VisitCategory(VisitCategoryIds.NATURE, "Natur / Wandern", true, "hiking", 3),
        VisitCategory(VisitCategoryIds.SUMMIT, "Gipfel", true, "landscape", 4),
        VisitCategory(VisitCategoryIds.EVENT, "Konzert / Event", true, "music_note", 5),
        VisitCategory(VisitCategoryIds.MUSEUM, "Museum", true, "museum", 6),
        VisitCategory(VisitCategoryIds.SHOPPING, "Einkaufen", true, "shopping_bag", 7),
        VisitCategory(VisitCategoryIds.SPORT, "Sport", true, "sports", 8),
        VisitCategory(VisitCategoryIds.ACCOMMODATION, "Unterkunft", true, "hotel", 9),
        VisitCategory(VisitCategoryIds.REST_STOP, "Rast / Tankstopp", false, "local_gas_station", 10),
        VisitCategory(VisitCategoryIds.OTHER, "Sonstiges", true, "place", 11),
    )

    val regionCollections: List<RegionCollection> = listOf(
        RegionCollection("AT", enabled = true),
        RegionCollection("DE", enabled = true),
    )
}
