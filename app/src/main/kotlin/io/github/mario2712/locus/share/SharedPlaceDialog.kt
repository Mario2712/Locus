package io.github.mario2712.locus.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceCandidates
import io.github.mario2712.locus.domain.places.PlaceGroups
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.places.LocalPlaceSearch
import io.github.mario2712.locus.places.PlaceSearchController
import io.github.mario2712.locus.places.SearchResult
import io.github.mario2712.locus.ui.editor.MapPlaceCreateDialog
import kotlinx.coroutines.launch

/**
 * Aus Google Maps geteilter Ort: passende OSM-Treffer zur Auswahl oder „Diesen Punkt übernehmen“
 * (Name aus Maps vorbelegt). [onSaved] bekommt den gespeicherten Ort.
 */
@Composable
fun SharedPlaceDialog(text: String, cities: List<Place>, onDismiss: () -> Unit, onSaved: (Place) -> Unit) {
    val controller = LocalPlaceSearch.current ?: return
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var resolution by remember { mutableStateOf<PlaceSearchController.SharedResolution?>(null) }
    var point by remember { mutableStateOf<Triple<Double, Double, PlaceCandidate>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        resolution = controller.resolveShared(text)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = resolution
                when {
                    loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.search_loading))
                    }
                    r == null -> Text(stringResource(R.string.share_no_maps_link))
                    else -> {
                        r.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        if (r.offline) Text(stringResource(R.string.search_offline), color = MaterialTheme.colorScheme.error)
                        if (r.candidates.isNotEmpty()) {
                            Text(stringResource(R.string.share_matches), style = MaterialTheme.typography.labelLarge)
                            r.candidates.forEach { candidate ->
                                val country = ReferenceData.countableCountry(candidate.countryIso2)
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            runCatching { controller.import(candidate) }.onSuccess(onSaved).onFailure { failed = true }
                                        }
                                    }.padding(vertical = 8.dp),
                                ) {
                                    Text("${country?.flag ?: "🌐"} ${candidate.name}")
                                    Text(
                                        listOfNotNull(
                                            PlaceGroups.categoryLabel(candidate.category),
                                            candidate.address ?: candidate.subtitle,
                                            candidate.distanceMeters?.let { PlaceCandidates.formatDistance(it) },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        } else if (r.latitude == null) {
                            Text(stringResource(R.string.share_nothing_found))
                        }
                        val lat = r.latitude
                        val lon = r.longitude
                        if (lat != null && lon != null) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val hit = (controller.describe(lat, lon) as? SearchResult.Ok)?.value
                                        ?: controller.countryAt(lat, lon)?.let { PlaceCandidates.countryOnly(it, lat, lon) }
                                    if (hit != null) point = Triple(lat, lon, hit) else failed = true
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_take_point)) }
                        }
                        if (failed) Text(stringResource(R.string.search_import_failed), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    point?.let { (lat, lon, hit) ->
        MapPlaceCreateDialog(
            hit = hit, latitude = lat, longitude = lon, initialKind = PlaceKind.POI, cities = cities,
            initialName = resolution?.name,
            onDismiss = { point = null },
            onCreated = { place -> point = null; onSaved(place) },
        )
    }
}
