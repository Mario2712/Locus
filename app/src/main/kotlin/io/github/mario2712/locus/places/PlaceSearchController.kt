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
) {
    fun hasLocationPermission(): Boolean = location.hasPermission()

    suspend fun search(query: String): SearchResult<List<PlaceCandidate>> = try {
        val near = location.takeIf { it.hasPermission() }?.let { runCatching { it.current() }.getOrNull() }
        SearchResult.Ok(search.search(query, near?.latitude, near?.longitude))
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
            SearchResult.Ok(listOfNotNull(city, hit))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SearchResult.Offline
        }
    }

    /** Übernimmt einen Treffer: bestehender Ort wird ergänzt, sonst neu angelegt. */
    suspend fun import(candidate: PlaceCandidate): Place {
        val existing = PlaceCandidates.findExisting(candidate, places.observeAll().first())
        val place = existing?.let { PlaceCandidates.enrich(it, candidate) } ?: PlaceCandidates.toPlace(candidate)
        places.upsert(place)
        return place
    }

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
