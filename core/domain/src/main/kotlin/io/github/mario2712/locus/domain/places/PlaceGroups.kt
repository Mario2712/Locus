package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds

/** Grobe Art einer Örtlichkeit für Filter und Karte. */
enum class LocationGroup(val nameDe: String) {
    FOOD("Essen & Trinken"),
    CULTURE("Kultur"),
    NATURE("Natur & Gipfel"),
    LODGING("Unterkunft"),
    OTHER("Sonstiges"),
}

/** Unterscheidung „Stadt/Gemeinde“ vs. „Örtlichkeit“ und Einordnung der Örtlichkeiten. */
object PlaceGroups {

    private val osmGroups: Map<String, LocationGroup> = buildMap {
        listOf("restaurant", "cafe", "bar", "pub", "fast_food", "biergarten", "ice_cream", "food_court", "bakery", "winery", "wine")
            .forEach { put(it, LocationGroup.FOOD) }
        listOf(
            "museum", "gallery", "theatre", "cinema", "arts_centre", "attraction", "artwork", "castle", "monument",
            "memorial", "ruins", "archaeological_site", "place_of_worship", "church", "cathedral", "monastery",
            "palace", "fort", "city_gate", "zoo", "aquarium", "theme_park", "library", "concert_hall", "stadium",
        ).forEach { put(it, LocationGroup.CULTURE) }
        listOf(
            "peak", "viewpoint", "park", "nature_reserve", "national_park", "waterfall", "water", "lake", "beach",
            "cave_entrance", "saddle", "glacier", "volcano", "ridge", "valley", "gorge", "spring", "forest", "island", "garden",
        ).forEach { put(it, LocationGroup.NATURE) }
        listOf("hotel", "hostel", "guest_house", "camp_site", "alpine_hut", "apartment", "chalet", "motel", "wilderness_hut", "caravan_site")
            .forEach { put(it, LocationGroup.LODGING) }
    }

    private val visitGroups: Map<String, LocationGroup> = mapOf(
        VisitCategoryIds.RESTAURANT to LocationGroup.FOOD,
        VisitCategoryIds.BAR to LocationGroup.FOOD,
        VisitCategoryIds.SIGHT to LocationGroup.CULTURE,
        VisitCategoryIds.MUSEUM to LocationGroup.CULTURE,
        VisitCategoryIds.EVENT to LocationGroup.CULTURE,
        VisitCategoryIds.NATURE to LocationGroup.NATURE,
        VisitCategoryIds.SUMMIT to LocationGroup.NATURE,
        VisitCategoryIds.ACCOMMODATION to LocationGroup.LODGING,
    )

    private val labels: Map<String, String> = mapOf(
        "restaurant" to "Restaurant", "cafe" to "Café", "bar" to "Bar", "pub" to "Pub", "fast_food" to "Imbiss",
        "biergarten" to "Biergarten", "ice_cream" to "Eis", "bakery" to "Bäckerei", "supermarket" to "Supermarkt",
        "convenience" to "Nahversorger", "mall" to "Einkaufszentrum", "department_store" to "Kaufhaus",
        "clothes" to "Bekleidung", "shoes" to "Schuhe", "doityourself" to "Baumarkt", "hardware" to "Baumarkt",
        "chemist" to "Drogerie", "pharmacy" to "Apotheke", "fuel" to "Tankstelle", "museum" to "Museum",
        "gallery" to "Galerie", "theatre" to "Theater", "cinema" to "Kino", "attraction" to "Sehenswürdigkeit",
        "castle" to "Burg/Schloss", "monument" to "Denkmal", "place_of_worship" to "Kirche", "church" to "Kirche",
        "viewpoint" to "Aussichtspunkt", "peak" to "Gipfel", "park" to "Park", "zoo" to "Zoo", "hotel" to "Hotel",
        "hostel" to "Hostel", "guest_house" to "Pension", "alpine_hut" to "Hütte", "camp_site" to "Campingplatz",
        "station" to "Bahnhof", "aerodrome" to "Flughafen", "parking" to "Parkplatz", "hospital" to "Krankenhaus",
        "school" to "Schule", "university" to "Universität", "city" to "Stadt", "town" to "Stadt",
        "village" to "Dorf", "hamlet" to "Weiler", "suburb" to "Stadtteil", "residential" to "Straße",
    )

    /** Deutsche Bezeichnung einer OSM-Kategorie, z. B. „supermarket“ → „Supermarkt“. */
    fun categoryLabel(osmValue: String?): String? = osmValue?.let { labels[it.lowercase()] }

    /** Stadt/Gemeinde (sonst Örtlichkeit: Lokal, Museum, Gipfel, Adresse …). */
    fun isLocality(place: Place): Boolean = place.kind == PlaceKind.CITY

    /**
     * Art einer Örtlichkeit: aus der OSM-Kategorie, sonst aus den häufigsten Besuchskategorien dort.
     */
    fun groupOf(place: Place, visitsHere: List<Visit> = emptyList()): LocationGroup {
        place.poiCategory?.lowercase()?.let { osmGroups[it] }?.let { return it }
        return visitsHere.flatMap { it.categoryIds }
            .mapNotNull { visitGroups[it] }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?: LocationGroup.OTHER
    }
}
