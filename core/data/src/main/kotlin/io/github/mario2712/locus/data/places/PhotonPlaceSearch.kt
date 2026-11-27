package io.github.mario2712.locus.data.places

import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Ortssuche über Photon (OpenStreetMap, https://photon.komoot.io). Treffer werden nur bei Auswahl
 * lokal gespeichert. Faire Nutzung: nur auf ausdrückliche Suche, kleines Limit, eigener User-Agent.
 */
class PhotonPlaceSearch(
    private val userAgent: String,
    private val baseUrl: String = "https://photon.komoot.io",
) : PlaceSearch {

    override suspend fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceCandidate> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = if (nearLatitude != null && nearLongitude != null) "&lat=${fmt(nearLatitude)}&lon=${fmt(nearLongitude)}" else ""
        return PhotonParser.parse(get("$baseUrl/api/?q=$q&lang=de&limit=10$bias"))
    }

    override suspend fun reverse(latitude: Double, longitude: Double): PlaceCandidate? =
        // Mehrere Treffer im Umkreis: der nächste unbenannte Weg o. Ä. wird verworfen, dann zählt der nächste brauchbare.
        PhotonParser.parse(get("$baseUrl/reverse?lat=${fmt(latitude)}&lon=${fmt(longitude)}&lang=de&limit=10&radius=5")).firstOrNull()

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.6f", value)

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw SearchHttpException(code)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Wandelt Photon-GeoJSON in [PlaceCandidate]s um. */
object PhotonParser {

    private val cityValues = setOf("city", "town", "village", "hamlet", "municipality", "suburb", "island")

    fun parse(json: String): List<PlaceCandidate> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        // Jeder Treffer einzeln abgesichert; doppelte Treffer (gleiche OSM-ID) werden entfernt.
        return (0 until features.length()).mapNotNull { index ->
            runCatching { parseFeature(features.optJSONObject(index)) }.getOrNull()
        }.distinctBy { it.osmId }
    }

    private fun parseFeature(feature: JSONObject?): PlaceCandidate? {
        feature ?: return null
        val props = feature.optJSONObject("properties") ?: return null
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val country = props.optStringOrNull("countrycode")?.uppercase() ?: return null
        val osmType = props.optStringOrNull("osm_type") ?: return null
        val osmId = props.optLong("osm_id", -1).takeIf { it >= 0 } ?: return null
        val osmKey = props.optStringOrNull("osm_key")
        val osmValue = props.optStringOrNull("osm_value")
        val type = props.optStringOrNull("type")
        val street = props.optStringOrNull("street")
        val ownName = props.optStringOrNull("name")
        val name = ownName
            ?: street?.let { s -> listOfNotNull(s, props.optStringOrNull("housenumber")).joinToString(" ") }
            ?: return null
        // Photon liefert POIs oft mit type "house" – als Adresse gilt nur, was keinen eigenen Namen hat
        // oder eine Straße ist.
        val kind = when {
            osmKey == "place" && osmValue in cityValues -> PlaceKind.CITY
            osmKey == "boundary" && type == "city" -> PlaceKind.CITY
            osmKey == "highway" || type == "street" || ownName == null -> PlaceKind.ADDRESS
            else -> PlaceKind.POI
        }
        return PlaceCandidate(
            osmId = "$osmType$osmId",
            name = name,
            kind = kind,
            countryIso2 = country,
            countryName = props.optStringOrNull("country"),
            stateName = props.optStringOrNull("state"),
            cityName = props.optStringOrNull("city") ?: if (kind == PlaceKind.CITY) name else props.optStringOrNull("locality"),
            latitude = coordinates.getDouble(1),
            longitude = coordinates.getDouble(0),
            category = osmValue,
            street = street,
            houseNumber = props.optStringOrNull("housenumber"),
            postcode = props.optStringOrNull("postcode"),
            district = props.optStringOrNull("district") ?: props.optStringOrNull("locality"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
