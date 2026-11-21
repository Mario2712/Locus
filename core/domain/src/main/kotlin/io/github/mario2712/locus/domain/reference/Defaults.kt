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

    /**
     * Farben der Arten: geprüfte kategoriale Palette (Farbsehschwäche-tauglich, in fester Reihenfolge).
     * Transit ist bewusst neutral grau.
     */
    val stayTypes: List<StayType> = listOf(
        StayType(StayTypeIds.HOMEOFFICE, "Homeoffice", StayGroup.WORK, true, "home_work", 0xFF2A78D6, 0),
        StayType(StayTypeIds.OFFICE, "Büro", StayGroup.WORK, true, "business", 0xFFEB6834, 1),
        StayType(StayTypeIds.BUSINESS_TRIP, "Dienstreise", StayGroup.WORK, true, "work_travel", 0xFF1BAF7A, 2),
        StayType(StayTypeIds.FREE, "Frei / Zuhause", StayGroup.FREE, true, "weekend", 0xFFEDA100, 3),
        StayType(StayTypeIds.EXCURSION, "Ausflug", StayGroup.FREE, true, "hiking", 0xFFE87BA4, 4),
        StayType(StayTypeIds.VACATION, "Urlaub", StayGroup.VACATION, true, "beach", 0xFF008300, 5),
        StayType(StayTypeIds.ON_THE_WAY, "Anreise / Unterwegs", StayGroup.OTHER, false, "commute", 0xFF6250D6, 6),
        StayType(StayTypeIds.TRANSIT, "Transit", StayGroup.OTHER, false, "connecting_airports", 0xFF8A8A85, 7),
        StayType(StayTypeIds.SICK, "Krank", StayGroup.OTHER, true, "sick", 0xFFE34948, 8),
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

    /** Frühere Standardfarben (bis M4) → neue, für die einmalige Umstellung bestehender Installationen. */
    val legacyStayTypeColors: Map<String, Pair<Long, Long>> = mapOf(
        StayTypeIds.HOMEOFFICE to (0xFF5C6BC0 to 0xFF2A78D6),
        StayTypeIds.OFFICE to (0xFF3949AB to 0xFFEB6834),
        StayTypeIds.BUSINESS_TRIP to (0xFF00897B to 0xFF1BAF7A),
        StayTypeIds.FREE to (0xFF7CB342 to 0xFFEDA100),
        StayTypeIds.EXCURSION to (0xFFF9A825 to 0xFFE87BA4),
        StayTypeIds.VACATION to (0xFFEF6C00 to 0xFF008300),
        StayTypeIds.ON_THE_WAY to (0xFF8D6E63 to 0xFF6250D6),
        StayTypeIds.TRANSIT to (0xFF90A4AE to 0xFF8A8A85),
        StayTypeIds.SICK to (0xFFE53935 to 0xFFE34948),
    )

    val regionCollections: List<RegionCollection> = listOf(
        RegionCollection("AT", enabled = true),
        RegionCollection("DE", enabled = true),
    )
}
