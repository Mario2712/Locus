package io.github.mario2712.locus.places

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mario2712.locus.domain.geo.CountryLocator
import io.github.mario2712.locus.domain.geo.CountryShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Land zu Koordinaten ohne Internet, aus den Länderumrissen der App (assets/countries.geojson). */
@Singleton
class OfflineCountries @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var locator: CountryLocator? = null

    suspend fun locate(latitude: Double, longitude: Double): String? = withContext(Dispatchers.Default) {
        val l = mutex.withLock { locator ?: load().also { locator = it } }
        l.locate(latitude, longitude)
    }

    private fun load(): CountryLocator {
        val json = context.assets.open("countries.geojson").bufferedReader().use { it.readText() }
        val features = JSONObject(json).getJSONArray("features")
        val shapes = (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val iso = feature.optJSONObject("properties")?.optString("iso")?.takeIf { it.length == 2 } ?: return@mapNotNull null
            val geometry = feature.getJSONObject("geometry")
            val coords = geometry.getJSONArray("coordinates")
            val polygons = when (geometry.getString("type")) {
                "Polygon" -> listOf(polygon(coords))
                "MultiPolygon" -> (0 until coords.length()).map { polygon(coords.getJSONArray(it)) }
                else -> emptyList()
            }
            CountryShape(iso, polygons)
        }
        return CountryLocator(shapes)
    }

    private fun polygon(rings: JSONArray): List<List<DoubleArray>> = (0 until rings.length()).map { r ->
        val ring = rings.getJSONArray(r)
        (0 until ring.length()).map { p -> ring.getJSONArray(p).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } }
    }
}
