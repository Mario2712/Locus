package io.github.mario2712.locus.places

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceCandidates
import io.github.mario2712.locus.domain.places.PlaceSearch
import io.github.mario2712.locus.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis einer Online-Aktion für die UI. */
sealed interface SearchResult<out T> {
    data class Ok<T>(val value: T) : SearchResult<T>
    data object Offline : SearchResult<Nothing>
    data object NoLocation : SearchResult<Nothing>
}

/**
 * Verbindet Online-Ortssuche, Standort und lokale Orte: sucht, übernimmt Treffer (ohne Duplikate,
 * bestehende Orte werden ergänzt) und ermittelt Orte am aktuellen Standort.
 */
@Singleton
class PlaceSearchController @Inject constructor(
    private val search: PlaceSearch,
    private val places: PlaceRepository,
    private val location: DeviceLocation,
    private val offlineCountries: OfflineCountries,
) {
    fun hasLocationPermission(): Boolean = location.hasPermission()

    suspend fun search(query: String): SearchResult<List<PlaceCandidate>> = try {
        val near = location.takeIf { it.hasPermission() }?.let { runCatching { it.current() }.getOrNull() }
        val results = search.search(query, near?.latitude, near?.longitude)
        SearchResult.Ok(
            if (near == null) results
            else results.map { it.copy(distanceMeters = PlaceCandidates.distanceMeters(near.latitude, near.longitude, it.latitude, it.longitude)) },
        )
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        SearchResult.Offline
    }

    /**
     * Orte am aktuellen Standort: der genaue Treffer und – falls verschieden – die Stadt dazu
     * (z. B. „Billa“ und „Oberalm“).
     */
    suspend fun nearby(): SearchResult<List<PlaceCandidate>> {
        val here = runCatching { location.current() }.getOrNull() ?: return SearchResult.NoLocation
        return try {
            val hit = search.reverse(here.latitude, here.longitude) ?: return SearchResult.Ok(emptyList())
            val city = hit.cityName?.takeIf { hit.kind != PlaceKind.CITY && it != hit.name }?.let { name ->
                runCatching { search.search(name, here.latitude, here.longitude) }.getOrNull()
                    ?.firstOrNull { it.kind == PlaceKind.CITY && it.name.equals(name, ignoreCase = true) }
            }
            SearchResult.Ok(
                listOfNotNull(city, hit).distinctBy { it.osmId }.map {
                    it.copy(distanceMeters = PlaceCandidates.distanceMeters(here.latitude, here.longitude, it.latitude, it.longitude))
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SearchResult.Offline
        }
    }

    /**
     * Übernimmt einen Treffer: bestehender Ort wird ergänzt, sonst neu angelegt.
     * Lokale, Museen usw. werden mit ihrer Stadt verknüpft (Stadt wird bei Bedarf angelegt),
     * damit sie in der Orts-Akte der Stadt mitzählen.
     */
    suspend fun import(candidate: PlaceCandidate): Place {
        val all = places.observeAll().first()
        val existing = PlaceCandidates.findExisting(candidate, all)
        var place = existing?.let { PlaceCandidates.enrich(it, candidate) } ?: PlaceCandidates.toPlace(candidate)
        val cityName = candidate.cityName
        if (place.kind != PlaceKind.CITY && place.parentPlaceId == null && cityName != null && !cityName.equals(candidate.name, true)) {
            val city = resolveCity(candidate, cityName, all)
            place = place.copy(parentPlaceId = city.id)
        }
        places.upsert(place)
        return place
    }

    private suspend fun resolveCity(candidate: PlaceCandidate, cityName: String, all: List<Place>): Place {
        PlaceCandidates.findCity(cityName, candidate.countryIso2, all)?.let { return it }
        val online = runCatching { search.search(cityName, candidate.latitude, candidate.longitude) }.getOrNull()
            ?.firstOrNull { it.kind == PlaceKind.CITY && it.name.equals(cityName, true) && it.countryIso2.equals(candidate.countryIso2, true) }
        val city = online?.let { c ->
            PlaceCandidates.findExisting(c, all)?.let { PlaceCandidates.enrich(it, c) } ?: PlaceCandidates.toPlace(c)
        } ?: PlaceCandidates.cityStub(candidate, cityName)
        places.upsert(city)
        return city
    }

    /** Adresse bzw. nächste Örtlichkeit zu einem Kartenpunkt (mit Abstand zum Punkt). */
    suspend fun describe(latitude: Double, longitude: Double): SearchResult<PlaceCandidate?> = try {
        SearchResult.Ok(
            search.reverse(latitude, longitude)?.let {
                it.copy(distanceMeters = PlaceCandidates.distanceMeters(latitude, longitude, it.latitude, it.longitude))
            },
        )
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        SearchResult.Offline
    }

    /** Land zum Punkt ohne Internet (Fallback, wenn die Rückwärtssuche nichts liefert). */
    suspend fun countryAt(latitude: Double, longitude: Double): String? =
        runCatching { offlineCountries.locate(latitude, longitude) }.getOrNull()

    /** Aktueller Standort, falls erlaubt und verfügbar. */
    suspend fun currentPosition(): Pair<Double, Double>? =
        location.takeIf { it.hasPermission() }?.let { runCatching { it.current() }.getOrNull() }?.let { it.latitude to it.longitude }

    /**
     * Neuer Ort aus einem Kartenpunkt. Wird der vorgeschlagene OSM-Treffer unverändert übernommen und gibt es ihn
     * schon, wird der bestehende Ort ergänzt. Örtlichkeiten werden mit ihrer Stadt verknüpft.
     */
    suspend fun createAt(
        latitude: Double,
        longitude: Double,
        name: String,
        kind: PlaceKind,
        hit: PlaceCandidate,
        parentPlaceId: String? = null,
    ): Place {
        val all = places.observeAll().first()
        var place = PlaceCandidates.fromPoint(hit, latitude, longitude, name, kind)
        place.osmId?.let { osm -> all.firstOrNull { it.osmId == osm } }?.let { existing ->
            val enriched = PlaceCandidates.enrich(existing, hit)
            places.upsert(enriched)
            return enriched
        }
        val cityName = hit.cityName
        if (kind != PlaceKind.CITY && parentPlaceId != null) {
            place = place.copy(parentPlaceId = parentPlaceId)
        } else if (kind != PlaceKind.CITY && cityName != null && !cityName.equals(place.name, true)) {
            place = place.copy(parentPlaceId = resolveCity(hit, cityName, all).id)
        }
        places.upsert(place)
        return place
    }

    /** Setzt bzw. korrigiert die Position eines Orts; fehlende Adresse/Region kommen aus der Rückwärtssuche. */
    suspend fun setPosition(place: Place, latitude: Double, longitude: Double, hit: PlaceCandidate?): Place {
        val updated = place.copy(
            latitude = latitude,
            longitude = longitude,
            regionCode = place.regionCode ?: hit?.let { PlaceCandidates.regionCode(place.countryIso2, it.stateName) },
            cityName = place.cityName ?: hit?.cityName,
            address = if (place.kind == PlaceKind.CITY) place.address
            else hit?.takeIf(PlaceCandidates::isAtPoint)?.address ?: place.address,
        )
        places.upsert(updated)
        return updated
    }

    /** Speichert einen bearbeiteten Ort. */
    suspend fun save(place: Place) = places.upsert(place)

    /** Verknüpft einen bestehenden Ort mit einem Treffer (Koordinaten, OSM-ID, Region). */
    suspend fun link(place: Place, candidate: PlaceCandidate): Place {
        val linked = PlaceCandidates.enrich(place.copy(latitude = null, longitude = null, osmId = null), candidate)
            .copy(regionCode = place.regionCode ?: PlaceCandidates.regionCode(place.countryIso2, candidate.stateName))
        places.upsert(linked)
        return linked
    }
}

/** Für Dialoge tief im UI-Baum (Ortsauswahl), bereitgestellt in MainActivity. */
val LocalPlaceSearch = staticCompositionLocalOf<PlaceSearchController?> { null }
