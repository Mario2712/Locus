package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.reference.ReferenceData
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein Suchtreffer aus OpenStreetMap (noch nicht gespeichert). */
data class PlaceCandidate(
    /** z. B. "R34896" (Typ N/W/R + ID). */
    val osmId: String,
    val name: String,
    val kind: PlaceKind,
    /** ISO-3166-1 alpha-2 wie geliefert (kann ein Gebiet sein, z. B. "GL"). */
    val countryIso2: String,
    val countryName: String?,
    /** Bundesland/Region als Name, wie geliefert (z. B. "Salzburg"). */
    val stateName: String?,
    val cityName: String?,
    val latitude: Double,
    val longitude: Double,
    /** OSM-Kategorie, z. B. "town", "restaurant", "museum". */
    val category: String?,
    val street: String? = null,
    val houseNumber: String? = null,
    val postcode: String? = null,
    /** Stadtteil/Ortsteil, z. B. „Burgfried“. */
    val district: String? = null,
    /** Entfernung zum aktuellen Standort, falls bekannt. */
    val distanceMeters: Double? = null,
) {
    /** Zweite Zeile für Suchergebnisse, z. B. „Salzburg, Österreich“. */
    val subtitle: String
        get() = listOfNotNull(cityName?.takeIf { it != name }, stateName, countryName).distinct().joinToString(", ")

    /** Adresse, z. B. „Davisstraße 12, 5400 Hallein“ – zur Unterscheidung gleichnamiger Treffer. */
    val address: String?
        get() {
            val streetPart = street?.let { listOfNotNull(it, houseNumber).joinToString(" ") }
            val cityPart = listOfNotNull(postcode, cityName ?: district).joinToString(" ").takeIf { it.isNotBlank() }
            return listOfNotNull(streetPart, cityPart).joinToString(", ").takeIf { it.isNotBlank() && streetPart != null }
        }
}

/** Online-Suche nach Orten (Implementierung in :core:data, z. B. Photon/OSM). */
interface PlaceSearch {
    suspend fun search(query: String, nearLatitude: Double? = null, nearLongitude: Double? = null): List<PlaceCandidate>
    suspend fun reverse(latitude: Double, longitude: Double): PlaceCandidate?
}

object PlaceCandidates {

    /** ISO-3166-2-Code für einen Regionsnamen (nur für Länder mit Regionen, z. B. AT, DE). */
    fun regionCode(countryIso2: String, stateName: String?): String? {
        if (stateName == null) return null
        val normalized = stateName.trim().lowercase()
        return ReferenceData.regionsOf(countryIso2)
            .firstOrNull { it.nameDe.lowercase() == normalized }?.code
    }

    /** Wandelt einen Treffer in einen speicherbaren Ort um. */
    fun toPlace(candidate: PlaceCandidate, id: String = UUID.randomUUID().toString()): Place = Place(
        id = id,
        name = candidate.name,
        kind = candidate.kind,
        countryIso2 = candidate.countryIso2.uppercase(),
        regionCode = regionCode(candidate.countryIso2.uppercase(), candidate.stateName),
        latitude = candidate.latitude,
        longitude = candidate.longitude,
        cityName = candidate.cityName,
        osmId = candidate.osmId,
        poiCategory = candidate.category,
        address = candidate.address,
    )

    /**
     * Sucht unter gespeicherten Orten einen, der zum Treffer passt: gleiche OSM-ID, oder gleicher
     * Name im Umkreis von [radiusMeters] (z. B. manuell angelegtes „Hallein“ ohne Koordinaten
     * zählt bei gleichem Namen und Land).
     */
    fun findExisting(candidate: PlaceCandidate, places: List<Place>, radiusMeters: Double = 3_000.0): Place? =
        places.firstOrNull { it.osmId == candidate.osmId }
            ?: places.firstOrNull { place ->
                place.name.equals(candidate.name, ignoreCase = true) &&
                    place.countryIso2.equals(candidate.countryIso2, ignoreCase = true) &&
                    (place.latitude == null || place.longitude == null ||
                        distanceMeters(place.latitude, place.longitude, candidate.latitude, candidate.longitude) <= radiusMeters)
            }

    /**
     * Ergänzt einen bestehenden (z. B. manuell angelegten) Ort um Daten aus dem Treffer:
     * Koordinaten, OSM-ID, Region, Stadt, Kategorie. Name, Kurzname, Favorit und Homebase bleiben.
     */
    fun enrich(existing: Place, candidate: PlaceCandidate): Place = existing.copy(
        latitude = existing.latitude ?: candidate.latitude,
        longitude = existing.longitude ?: candidate.longitude,
        osmId = existing.osmId ?: candidate.osmId,
        regionCode = existing.regionCode ?: regionCode(existing.countryIso2, candidate.stateName),
        cityName = existing.cityName ?: candidate.cityName,
        poiCategory = existing.poiCategory ?: candidate.category,
        address = existing.address ?: candidate.address,
    )

    /** Gespeicherte Stadt mit [cityName] im selben Land (für die Zuordnung von Lokalen, Museen …). */
    fun findCity(cityName: String, countryIso2: String, places: List<Place>): Place? =
        places.firstOrNull {
            it.kind == PlaceKind.CITY && it.name.equals(cityName, ignoreCase = true) &&
                it.countryIso2.equals(countryIso2, ignoreCase = true)
        }

    /** Minimaler Stadt-Ort, falls die Stadt online nicht gefunden wird. */
    fun cityStub(candidate: PlaceCandidate, cityName: String, id: String = UUID.randomUUID().toString()): Place = Place(
        id = id,
        name = cityName,
        kind = PlaceKind.CITY,
        countryIso2 = candidate.countryIso2.uppercase(),
        regionCode = regionCode(candidate.countryIso2.uppercase(), candidate.stateName),
        cityName = cityName,
    )

    /** „850 m“ bzw. „12,3 km“. */
    fun formatDistance(meters: Double): String = when {
        meters < 1_000 -> "${(meters / 10).toInt() * 10} m"
        meters < 10_000 -> String.format(java.util.Locale.GERMAN, "%.1f km", meters / 1_000)
        else -> "${(meters / 1_000).toInt()} km"
    }

    /** Großkreis-Entfernung (Haversine) in Metern. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
