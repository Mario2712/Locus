package io.github.mario2712.locus.ui.editor

import android.content.Context
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceCandidates
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.places.LocalPlaceSearch
import io.github.mario2712.locus.places.SearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/** Detailreiche Karte mit Örtlichkeiten und Hausnummern (OpenFreeMap, ohne Schlüssel). */
private const val PICKER_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Startpunkt der Kartenauswahl. */
data class MapStart(val latitude: Double, val longitude: Double, val zoom: Double)

/** Markierung (z. B. Suchtreffer), antippbar. */
data class MapPin(val id: String, val latitude: Double, val longitude: Double, val label: String)

/** Ohne Anhaltspunkt: Mitteleuropa. */
private val FALLBACK_START = MapStart(47.5, 13.0, 5.0)

/**
 * Startpunkt in der gewünschten Reihenfolge: Ort/Stadt des Tages bzw. Kontext ([preferred], erster mit Koordinaten,
 * sonst dessen Stadt) → zuletzt verwendeter Ort → aktueller Standort → Mitteleuropa.
 */
suspend fun mapStartFor(preferred: List<Place>, recent: List<Place>, allPlaces: List<Place>, current: suspend () -> Pair<Double, Double>?): MapStart {
    val byId = allPlaces.associateBy { it.id }
    fun startOf(place: Place): MapStart? {
        val lat = place.latitude
        val lon = place.longitude
        if (lat != null && lon != null) return MapStart(lat, lon, if (place.kind == PlaceKind.CITY) 13.0 else 16.0)
        return place.parentPlaceId?.let(byId::get)?.let { parent ->
            parent.latitude?.let { la -> parent.longitude?.let { lo -> MapStart(la, lo, 13.0) } }
        }
    }
    preferred.firstNotNullOfOrNull(::startOf)?.let { return it }
    recent.firstNotNullOfOrNull(::startOf)?.let { return it }
    withTimeoutOrNull(5_000) { current() }?.let { (lat, lon) -> return MapStart(lat, lon, 15.0) }
    return FALLBACK_START
}

private sealed interface Lookup {
    data object Loading : Lookup
    data object Offline : Lookup
    data class Found(val hit: PlaceCandidate?) : Lookup
}

/**
 * Vollbild-Kartenauswahl: Karte unter dem festen Fadenkreuz verschieben, unten steht laufend die Adresse.
 * Mit [pins] erscheinen zusätzlich antippbare Markierungen (z. B. Suchtreffer).
 */
@Composable
fun MapPickerDialog(
    title: String,
    start: MapStart?,
    onDismiss: () -> Unit,
    onPick: (latitude: Double, longitude: Double, hit: PlaceCandidate?) -> Unit,
    confirmLabel: String = stringResource(R.string.map_pick_here),
    pins: List<MapPin> = emptyList(),
    onPin: (MapPin) -> Unit = {},
) {
    val controller = LocalPlaceSearch.current
    val context = LocalContext.current
    val mapView = remember { createPickerMapView(context) }
    var center by remember { mutableStateOf<LatLng?>(null) }
    var lookup by remember { mutableStateOf<Lookup>(Lookup.Loading) }
    var styleFailed by remember { mutableStateOf(false) }
    val currentOnPin by rememberUpdatedState(onPin)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(start, pins) {
        val s = start ?: return@LaunchedEffect
        center = LatLng(s.latitude, s.longitude)
        mapView.getMapAsync { map ->
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.cameraPosition = CameraPosition.Builder().target(LatLng(s.latitude, s.longitude)).zoom(s.zoom).build()
            map.addOnCameraIdleListener { center = map.cameraPosition.target }
            mapView.addOnDidFailLoadingMapListener { styleFailed = true }
            map.setStyle(Style.Builder().fromUri(PICKER_STYLE)) { style ->
                if (pins.isEmpty()) return@setStyle
                style.addSource(GeoJsonSource("pins", pinsGeoJson(pins)))
                style.addLayer(
                    CircleLayer("pins-circles", "pins").withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor("#E34948"),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
                style.addLayer(
                    SymbolLayer("pins-labels", "pins").withProperties(
                        PropertyFactory.textField("{label}"),
                        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                        PropertyFactory.textAnchor("top"),
                        PropertyFactory.textHaloColor("#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.5f),
                    ),
                )
                if (pins.size > 1) {
                    val bounds = LatLngBounds.Builder().apply { pins.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
                    runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
                }
                map.addOnMapClickListener { latLng ->
                    val point: PointF = map.projection.toScreenLocation(latLng)
                    val id = map.queryRenderedFeatures(point, "pins-circles").firstOrNull()?.getStringProperty("id")
                    pins.firstOrNull { it.id == id }?.let { currentOnPin(it); true } ?: false
                }
            }
        }
    }

    // Adresse zum Kartenmittelpunkt (verzögert, damit beim Verschieben nicht jede Bewegung abfragt)
    LaunchedEffect(center) {
        val c = center ?: return@LaunchedEffect
        lookup = Lookup.Loading
        delay(400)
        lookup = when (val result = controller?.describe(c.latitude, c.longitude)) {
            is SearchResult.Ok -> Lookup.Found(result.value)
            null -> Lookup.Found(null)
            else -> Lookup.Offline
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    // Fadenkreuz: Spitze des Pins liegt genau in der Kartenmitte
                    Box(Modifier.align(Alignment.Center).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text("📍", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp))
                    if (pins.isNotEmpty()) {
                        Card(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                            Text(stringResource(R.string.map_pins_hint), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val l = lookup) {
                        Lookup.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.search_loading))
                        }
                        Lookup.Offline -> Text(stringResource(R.string.map_pick_offline), color = MaterialTheme.colorScheme.error)
                        is Lookup.Found -> Text(describeHit(l.hit), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (styleFailed) Text(stringResource(R.string.map_pick_offline), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.map_pick_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = center != null && lookup is Lookup.Found,
                        onClick = { center?.let { c -> onPick(c.latitude, c.longitude, (lookup as? Lookup.Found)?.hit) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(confirmLabel) }
                    Text(stringResource(R.string.search_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun describeHit(hit: PlaceCandidate?): String {
    if (hit == null) return stringResource(R.string.map_pick_no_address)
    val country = ReferenceData.countableCountry(hit.countryIso2)
    return listOfNotNull(
        hit.name.takeIf { hit.kind != PlaceKind.ADDRESS },
        hit.address ?: hit.subtitle,
        country?.let { "${it.flag} ${it.nameDe}" },
    ).distinct().joinToString(" · ")
}

private fun createPickerMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context).apply { onCreate(null) }
}

private fun pinsGeoJson(pins: List<MapPin>): String {
    val features = JSONArray()
    pins.forEach { pin ->
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(pin.longitude).put(pin.latitude)))
                .put("properties", JSONObject().put("id", pin.id).put("label", pin.label)),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

/**
 * Neuer Ort aus einem Kartenpunkt: Name vorgeschlagen (überschreibbar), Örtlichkeit oder Stadt/Dorf.
 * Land, Region, Stadt und Koordinaten kommen aus dem Punkt.
 */
@Composable
fun MapPlaceCreateDialog(
    hit: PlaceCandidate,
    latitude: Double,
    longitude: Double,
    initialKind: PlaceKind,
    onDismiss: () -> Unit,
    onCreated: (Place) -> Unit,
) {
    val controller = LocalPlaceSearch.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(if (initialKind == PlaceKind.CITY) PlaceKind.CITY else PlaceKind.POI) }
    var name by remember { mutableStateOf(PlaceCandidates.suggestName(hit, kind)) }
    var nameEdited by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val country = ReferenceData.countableCountry(hit.countryIso2)

    fun switchKind(newKind: PlaceKind) {
        kind = newKind
        if (!nameEdited) name = PlaceCandidates.suggestName(hit, newKind)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_new_place)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind != PlaceKind.CITY, { switchKind(PlaceKind.POI) }, label = { Text(stringResource(R.string.place_kind_location)) })
                    FilterChip(kind == PlaceKind.CITY, { switchKind(PlaceKind.CITY) }, label = { Text(stringResource(R.string.map_kind_village)) })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameEdited = true },
                    singleLine = true,
                    label = { Text(stringResource(if (kind == PlaceKind.CITY) R.string.place_name else R.string.place_name_location)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    listOfNotNull(
                        hit.address.takeIf { kind != PlaceKind.CITY },
                        hit.cityName?.takeIf { kind != PlaceKind.CITY && !it.equals(name.trim(), true) }
                            ?.let { stringResource(R.string.place_in_city, it) },
                        country?.let { "${it.flag} ${it.nameDe}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (failed) Text(stringResource(R.string.search_import_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !saving && controller != null, onClick = {
                val c = controller ?: return@TextButton
                saving = true
                scope.launch {
                    try {
                        onCreated(c.createAt(latitude, longitude, name, kind, hit))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failed = true
                        saving = false
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
