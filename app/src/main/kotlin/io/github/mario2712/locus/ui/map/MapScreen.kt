package io.github.mario2712.locus.ui.map

import android.content.Context
import android.graphics.PointF
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.ui.common.TypeDot
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.URI

/** Farben der Karte (hell/dunkel). */
private data class MapColors(
    val water: Int, val land: Int, val border: Int,
    val together: Int, val bothSeparately: Int, val onlyA: Int, val onlyB: Int, val visited: Int, val wish: Int,
    val place: Int, val location: Int, val route: Int,
)

private fun mapColors(dark: Boolean) = if (dark) {
    MapColors(
        water = 0xFF1B2A33.toInt(), land = 0xFF2B2F31.toInt(), border = 0xFF5A5F62.toInt(),
        together = 0xFF2E9E7E.toInt(), bothSeparately = 0xFF7FB8A4.toInt(), onlyA = 0xFF4F7FD1.toInt(),
        onlyB = 0xFFC95C8A.toInt(), visited = 0xFF2E9E7E.toInt(), wish = 0xFF8C7A3C.toInt(),
        place = 0xFFFFD166.toInt(), location = 0xFF9BC4FF.toInt(), route = 0xFFFFD166.toInt(),
    )
} else {
    MapColors(
        water = 0xFFCFE3EA.toInt(), land = 0xFFF2EFE9.toInt(), border = 0xFFA8A8A8.toInt(),
        together = 0xFF1F6F5C.toInt(), bothSeparately = 0xFF7FB8A4.toInt(), onlyA = 0xFF3F6FC4.toInt(),
        onlyB = 0xFFB4446C.toInt(), visited = 0xFF1F6F5C.toInt(), wish = 0xFFE9C46A.toInt(),
        place = 0xFFE76F51.toInt(), location = 0xFF3F6FC4.toInt(), route = 0xFFE76F51.toInt(),
    )
}

private const val DETAIL_STYLE = "https://tiles.openfreemap.org/styles/positron"

private fun hex(color: Int) = String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and color)

/** Offline-Stil: Meer, Länderflächen und Grenzen aus der App-eigenen Natural-Earth-Datei. */
private fun offlineStyle(c: MapColors): String = """
{"version":8,"name":"Locus offline",
 "sources":{"countries":{"type":"geojson","data":"asset://countries.geojson"}},
 "layers":[
  {"id":"water","type":"background","paint":{"background-color":"${hex(c.water)}"}},
  {"id":"land","type":"fill","source":"countries","paint":{"fill-color":"${hex(c.land)}"}}
 ]}
""".trimIndent()

private class MapHolder(val view: MapView) {
    var map: MapLibreMap? = null
    var style: Style? = null
    var styleKey: String? = null
    var clickInstalled = false
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = remember(dark) { mapColors(dark) }
    val holder = remember { MapHolder(createMapView(context)) }
    var info by remember { mutableStateOf<String?>(null) }
    val daysLabel = stringResource(R.string.map_days)
    var styleVersion by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.view.onStart()
                Lifecycle.Event.ON_RESUME -> holder.view.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view.onPause()
                Lifecycle.Event.ON_STOP -> holder.view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.view.onStop()
            holder.view.onDestroy()
        }
    }

    // Stil laden bzw. wechseln (offline ↔ Detailkarte, hell ↔ dunkel)
    val styleKey = "${state.filter.detailMap}-$dark"
    LaunchedEffect(styleKey) {
        holder.view.getMapAsync { map ->
            holder.map = map
            if (holder.styleKey == styleKey) return@getMapAsync
            holder.styleKey = styleKey
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            if (map.cameraPosition.zoom < 0.1) {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(35.0, 15.0)).zoom(1.0).build()
            }
            holder.style = null
            val builder = if (state.filter.detailMap) Style.Builder().fromUri(DETAIL_STYLE) else Style.Builder().fromJson(offlineStyle(colors))
            map.setStyle(builder) { style ->
                addLocusLayers(style, colors, detail = state.filter.detailMap)
                holder.style = style
                styleVersion++
            }
            if (holder.clickInstalled) return@getMapAsync
            holder.clickInstalled = true
            map.addOnMapClickListener { latLng ->
                val point: PointF = map.projection.toScreenLocation(latLng)
                val place = map.queryRenderedFeatures(point, "pois-circles", "places-circles").firstOrNull()
                val country = map.queryRenderedFeatures(point, "visited", "land").firstOrNull()
                info = when {
                    place != null -> place.getStringProperty("name") + " · " + place.getNumberProperty("days")?.toInt() + " " + daysLabel
                    country != null -> country.getStringProperty("iso")?.let { iso ->
                        val c = ReferenceData.country(iso) ?: ReferenceData.countableCountry(iso)
                        c?.let { "${it.flag} ${it.nameDe}" } ?: iso
                    }
                    else -> null
                }
                true
            }
        }
    }

    // Daten in die Karte übernehmen
    LaunchedEffect(state, styleVersion) {
        val style = holder.style ?: return@LaunchedEffect
        updateLocusLayers(style, state, colors)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MapControls(state, viewModel)
            Legend(state, colors)
        }
        info?.let {
            Card(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text(it, Modifier.padding(12.dp))
            }
        }
    }
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context).apply { onCreate(null) }
}

@Composable
private fun MapControls(state: MapUiState, viewModel: MapViewModel) {
    var yearMenu by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(state.filter.person == MapPerson.A, { viewModel.setPerson(MapPerson.A) }, label = { Text(state.nameA) })
        state.nameB?.let { b ->
            FilterChip(state.filter.person == MapPerson.B, { viewModel.setPerson(MapPerson.B) }, label = { Text(b) })
            FilterChip(state.filter.person == MapPerson.BOTH, { viewModel.setPerson(MapPerson.BOTH) }, label = { Text(stringResource(R.string.map_both)) })
        }
        Box {
            FilterChip(state.filter.year != null, { yearMenu = true }, label = {
                Text(state.filter.year?.toString() ?: stringResource(R.string.map_all_years))
            })
            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.map_all_years)) }, onClick = { viewModel.setYear(null); yearMenu = false })
                state.years.forEach { year ->
                    DropdownMenuItem(text = { Text(year.toString()) }, onClick = { viewModel.setYear(year); yearMenu = false })
                }
            }
        }
        FilterChip(state.filter.showPlaces, viewModel::togglePlaces, label = { Text(stringResource(R.string.map_places)) })
        FilterChip(state.filter.showRoutes, viewModel::toggleRoutes, label = { Text(stringResource(R.string.map_routes)) })
        FilterChip(state.filter.detailMap, viewModel::toggleDetailMap, label = { Text(stringResource(R.string.map_detail)) })
    }
}

@Composable
private fun Legend(state: MapUiState, c: MapColors) {
    val entries = buildList {
        if (state.filter.person == MapPerson.BOTH && state.nameB != null) {
            add(stringResource(R.string.countries_status_together) to c.together)
            add(stringResource(R.string.countries_status_both) to c.bothSeparately)
            add(stringResource(R.string.countries_status_only, state.nameA) to c.onlyA)
            add(stringResource(R.string.countries_status_only, state.nameB) to c.onlyB)
        } else {
            add(stringResource(R.string.map_visited) to c.visited)
        }
        if (state.filter.year == null) add(stringResource(R.string.countries_wishes) to c.wish)
    }
    Card {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            entries.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeDot(color.toLong() and 0xFFFFFFFFL, 10.dp)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun addLocusLayers(style: Style, c: MapColors, detail: Boolean) {
    if (style.getSource("countries") == null) style.addSource(GeoJsonSource("countries", URI("asset://countries.geojson")))
    style.addSource(GeoJsonSource("routes", """{"type":"FeatureCollection","features":[]}"""))
    style.addSource(GeoJsonSource("places", """{"type":"FeatureCollection","features":[]}"""))
    style.addSource(GeoJsonSource("pois", """{"type":"FeatureCollection","features":[]}"""))
    style.addLayer(
        FillLayer("visited", "countries").withProperties(
            PropertyFactory.fillColor(Color.Transparent.toArgb()),
            PropertyFactory.fillOpacity(if (detail) 0.45f else 0.85f),
        ),
    )
    style.addLayer(
        LineLayer("borders", "countries").withProperties(
            PropertyFactory.lineColor(c.border),
            PropertyFactory.lineWidth(0.5f),
        ),
    )
    style.addLayer(
        LineLayer("routes-lines", "routes").withProperties(
            PropertyFactory.lineColor(c.route),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineOpacity(0.8f),
        ),
    )
    style.addLayer(
        CircleLayer("places-circles", "places").withProperties(
            PropertyFactory.circleColor(c.place),
            PropertyFactory.circleRadius(Expression.get("r")),
            PropertyFactory.circleStrokeColor(0xFFFFFFFF.toInt()),
            PropertyFactory.circleStrokeWidth(1f),
        ),
    )
    // Örtlichkeiten erst ab Regional-Zoom, damit die Weltansicht übersichtlich bleibt.
    style.addLayer(
        CircleLayer("pois-circles", "pois").withProperties(
            PropertyFactory.circleColor(c.location),
            PropertyFactory.circleRadius(Expression.get("r")),
            PropertyFactory.circleStrokeColor(0xFFFFFFFF.toInt()),
            PropertyFactory.circleStrokeWidth(1f),
        ).apply { minZoom = 7f },
    )
}

private fun updateLocusLayers(style: Style, state: MapUiState, c: MapColors) {
    val colorOf = mapOf(
        MapCountryState.TOGETHER to c.together,
        MapCountryState.BOTH_SEPARATELY to c.bothSeparately,
        MapCountryState.ONLY_A to c.onlyA,
        MapCountryState.ONLY_B to c.onlyB,
        MapCountryState.VISITED to c.visited,
        MapCountryState.WISH to c.wish,
    )
    val fill = if (state.countries.isEmpty()) {
        Expression.color(Color.Transparent.toArgb())
    } else {
        Expression.match(
            Expression.get("iso"),
            Expression.color(Color.Transparent.toArgb()),
            *state.countries.map { (iso, s) -> Expression.stop(iso, Expression.color(colorOf.getValue(s))) }.toTypedArray(),
        )
    }
    style.getLayerAs<FillLayer>("visited")?.setProperties(PropertyFactory.fillColor(fill))

    val places = JSONArray()
    val pois = JSONArray()
    if (state.filter.showPlaces) {
        state.places.forEach { p ->
            (if (p.isLocation) pois else places).put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.longitude).put(p.latitude)))
                val radius = if (p.isLocation) 4.0 else 4.0 + kotlin.math.min(10.0, kotlin.math.sqrt(p.days.toDouble()) * 1.5)
                put("properties", JSONObject().put("name", p.name).put("days", p.days).put("r", radius))
            })
        }
    }
    style.getSourceAs<GeoJsonSource>("places")?.setGeoJson(JSONObject().put("type", "FeatureCollection").put("features", places).toString())
    style.getSourceAs<GeoJsonSource>("pois")?.setGeoJson(JSONObject().put("type", "FeatureCollection").put("features", pois).toString())

    val routes = JSONArray()
    state.routes.forEach { line ->
        routes.put(JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray().apply {
                line.forEach { (lat, lon) -> put(JSONArray().put(lon).put(lat)) }
            }))
            put("properties", JSONObject())
        })
    }
    style.getSourceAs<GeoJsonSource>("routes")?.setGeoJson(JSONObject().put("type", "FeatureCollection").put("features", routes).toString())
}
