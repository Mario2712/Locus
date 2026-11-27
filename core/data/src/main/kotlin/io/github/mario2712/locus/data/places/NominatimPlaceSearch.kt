package io.github.mario2712.locus.data.places

import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Der Suchdienst hat mit einem HTTP-Fehler geantwortet (z. B. 403 = abgelehnt, 429 = zu viele Anfragen). */
class SearchHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Ortssuche über Nominatim (OpenStreetMap, https://nominatim.openstreetmap.org) – Ersatz, wenn Photon
 * nicht antwortet. Nutzungsregeln: höchstens eine Anfrage pro Sekunde, aussagekräftiger User-Agent,
 * nur auf ausdrückliche Suche (keine Vorschläge beim Tippen).
 */
class NominatimPlaceSearch(
    private val userAgent: String,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
) : PlaceSearch {
    private val mutex = Mutex()
    private var lastRequest = 0L

    override suspend fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceCandidate> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        // Nähe bevorzugen (nicht erzwingen): Umgebung von etwa ±0,5° als Vorzugsbereich
        val bias = if (nearLatitude != null && nearLongitude != null) {
            "&viewbox=${fmt(nearLongitude - 0.5)},${fmt(nearLatitude + 0.5)},${fmt(nearLongitude + 0.5)},${fmt(nearLatitude - 0.5)}"
        } else ""
        return NominatimParser.parseList(get("$baseUrl/search?format=jsonv2&addressdetails=1&accept-language=de&limit=10&q=$q$bias"))
    }

    override suspend fun reverse(latitude: Double, longitude: Double): PlaceCandidate? =
        NominatimParser.parseSingle(get("$baseUrl/reverse?format=jsonv2&addressdetails=1&accept-language=de&zoom=18&lat=${fmt(latitude)}&lon=${fmt(longitude)}"))

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.6f", value)

    private suspend fun get(url: String): String = mutex.withLock {
        val wait = lastRequest + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
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
        } finally {
            lastRequest = System.currentTimeMillis()
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_100L
    }
}

/** Wandelt Nominatim-JSON (format=jsonv2, addressdetails=1) in [PlaceCandidate]s um. */
object NominatimParser {
    private val cityTypes = setOf("city", "town", "village", "hamlet", "municipality", "suburb", "island")

    fun parseList(json: String): List<PlaceCandidate> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { parse(array.optJSONObject(it)) }.getOrNull() }
            .distinctBy { it.osmId }
    }

    fun parseSingle(json: String): PlaceCandidate? = runCatching { parse(JSONObject(json)) }.getOrNull()

    private fun parse(obj: JSONObject?): PlaceCandidate? {
        obj ?: return null
        if (obj.has("error")) return null
        val address = obj.optJSONObject("address") ?: JSONObject()
        val country = address.str("country_code")?.uppercase() ?: return null
        val osmType = obj.str("osm_type")?.firstOrNull()?.uppercaseChar() ?: return null
        val osmId = obj.optLong("osm_id", -1).takeIf { it >= 0 } ?: return null
        val category = obj.str("category")
        val type = obj.str("type")
        val addressType = obj.str("addresstype")
        val street = address.str("road") ?: address.str("pedestrian") ?: address.str("footway")
        val ownName = obj.str("name")
        val name = ownName ?: street?.let { s -> listOfNotNull(s, address.str("house_number")).joinToString(" ") } ?: return null
        val kind = when {
            (category == "place" || category == "boundary") && (type in cityTypes || addressType in cityTypes) -> PlaceKind.CITY
            category == "highway" || ownName == null -> PlaceKind.ADDRESS
            else -> PlaceKind.POI
        }
        val city = address.str("city") ?: address.str("town") ?: address.str("village") ?: address.str("municipality") ?: address.str("hamlet")
        return PlaceCandidate(
            osmId = "$osmType$osmId",
            name = name,
            kind = kind,
            countryIso2 = country,
            countryName = address.str("country"),
            stateName = address.str("state"),
            cityName = city ?: if (kind == PlaceKind.CITY) name else null,
            latitude = obj.getString("lat").toDouble(),
            longitude = obj.getString("lon").toDouble(),
            category = type,
            street = street,
            houseNumber = address.str("house_number"),
            postcode = address.str("postcode"),
            district = address.str("suburb") ?: address.str("city_district") ?: address.str("quarter"),
        )
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

/**
 * Erst Photon, bei Fehler Nominatim. Lehnt Photon ab (HTTP-Fehler), wird es für [pauseMillis] übersprungen,
 * damit nicht jede Suche erst ins Leere läuft. Scheitern beide, gewinnt ein HTTP-Fehler (aussagekräftiger
 * als „keine Verbindung“).
 */
class FallbackPlaceSearch(
    private val primary: PlaceSearch,
    private val secondary: PlaceSearch,
    private val pauseMillis: Long = 10 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaceSearch {
    @Volatile private var primaryPausedUntil = 0L

    override suspend fun search(query: String, nearLatitude: Double?, nearLongitude: Double?) =
        run { it.search(query, nearLatitude, nearLongitude) }

    override suspend fun reverse(latitude: Double, longitude: Double) = run { it.reverse(latitude, longitude) }

    private suspend fun <T> run(call: suspend (PlaceSearch) -> T): T {
        var first: Exception? = null
        if (clock() >= primaryPausedUntil) {
            try {
                return call(primary)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                first = e
                if (e is SearchHttpException) primaryPausedUntil = clock() + pauseMillis
            }
        }
        return try {
            call(secondary)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw (e as? SearchHttpException) ?: (first as? SearchHttpException) ?: e
        }
    }
}
