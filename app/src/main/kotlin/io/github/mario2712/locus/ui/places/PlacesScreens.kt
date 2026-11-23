package io.github.mario2712.locus.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.places.LocationGroup
import io.github.mario2712.locus.domain.places.PlaceGroups
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.places.SearchResult
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.PlaceEditDialog
import io.github.mario2712.locus.ui.editor.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

private enum class PlacesTab { CITIES, LOCATIONS }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpenPlace: (String) -> Unit, viewModel: PlacesViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val unused by viewModel.unused.collectAsStateWithLifecycle()
    val unusedItems by viewModel.unusedItems.collectAsStateWithLifecycle()
    var unusedOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmCleanup by remember { mutableStateOf(false) }
    // rememberSaveable: Reiter und Filter bleiben beim Zurückkommen aus der Detailansicht erhalten.
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(PlacesTab.CITIES) }
    var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<LocationGroup?>(null) }
    var countryFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var countryMenu by remember { mutableStateOf(false) }
    // Auf-/zugeklappte Gruppen (Schlüssel → offen), ebenfalls über die Detailansicht hinweg gemerkt.
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }

    val byId = items.associateBy { it.place.id }
    val matching = items.filter { item ->
        (query.isBlank() || item.place.displayName.contains(query, true) || item.place.name.contains(query, true)) &&
            (countryFilter == null || ReferenceData.countableCountry(item.place.countryIso2)?.iso2 == countryFilter)
    }
    val (cities, locations) = matching.partition { PlaceGroups.isLocality(it.place) }
    val countries = items.mapNotNull { ReferenceData.countableCountry(it.place.countryIso2) }.distinct().sortedBy { it.nameDe }

    BackScaffold(stringResource(R.string.places_title), onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tab == PlacesTab.CITIES, { tab = PlacesTab.CITIES },
                            label = { Text(stringResource(R.string.places_tab_cities) + " (${cities.size})") })
                        FilterChip(tab == PlacesTab.LOCATIONS, { tab = PlacesTab.LOCATIONS },
                            label = { Text(stringResource(R.string.places_tab_locations) + " (${locations.size})") })
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.editor_search)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box {
                            FilterChip(countryFilter != null, { countryMenu = true }, label = {
                                Text(countryFilter?.let { iso -> ReferenceData.country(iso)?.let { "${it.flag} ${it.nameDe}" } }
                                    ?: stringResource(R.string.places_all_countries))
                            })
                            DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.places_all_countries)) },
                                    onClick = { countryFilter = null; countryMenu = false })
                                countries.forEach { c ->
                                    DropdownMenuItem(text = { Text("${c.flag} ${c.nameDe}") }, onClick = { countryFilter = c.iso2; countryMenu = false })
                                }
                            }
                        }
                        if (unused.isNotEmpty() || unusedOnly) {
                            FilterChip(unusedOnly, { unusedOnly = !unusedOnly },
                                label = { Text(stringResource(R.string.places_unused_n, unused.size)) })
                        }
                        if (tab == PlacesTab.LOCATIONS && !unusedOnly) {
                            LocationGroup.entries.forEach { g ->
                                FilterChip(group == g, { group = if (group == g) null else g }, label = { Text(g.nameDe) })
                            }
                        }
                    }
                }
            }
            if (unusedOnly) {
                val list = unusedItems
                item {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.places_unused_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (list.isNotEmpty()) {
                            androidx.compose.material3.OutlinedButton(onClick = { confirmCleanup = true }) {
                                Text(stringResource(R.string.places_unused_delete_all, list.size))
                            }
                        } else {
                            Text(stringResource(R.string.places_unused_none))
                        }
                    }
                }
                items(list, key = { "u-" + it.place.id }) { item -> PlaceRow(item, extra = null) { onOpenPlace(item.place.id) } }
            } else when (tab) {
                PlacesTab.CITIES -> {
                    if (cities.isEmpty()) item { Text(stringResource(R.string.places_empty), Modifier.padding(vertical = 12.dp)) }
                    items(cities, key = { it.place.id }) { item ->
                        PlaceRow(item, extra = if (item.locationCount > 0) stringResource(R.string.places_locations_count, item.locationCount) else null) {
                            onOpenPlace(item.place.id)
                        }
                    }
                }
                PlacesTab.LOCATIONS -> {
                    val shown = locations.filter { group == null || it.group == group }
                    if (shown.isEmpty()) item { Text(stringResource(R.string.places_empty), Modifier.padding(vertical = 12.dp)) }
                    // Gruppiert nach Stadt; Örtlichkeiten ohne Stadt am Ende.
                    val grouped = shown.groupBy { it.place.parentPlaceId }
                        .toList()
                        .sortedWith(compareBy({ it.first == null }, { byId[it.first]?.place?.displayName?.lowercase() ?: "" }))
                    grouped.forEach { (parentId, list) ->
                        val key = parentId ?: "none"
                        val open = expanded[key] ?: (grouped.size <= 3 || query.isNotBlank())
                        item(key = "g-$key") {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = expanded + (key to !open) }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    (parentId?.let { byId[it]?.place?.displayName } ?: stringResource(R.string.places_without_city)) + " (${list.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(if (open) "▾" else "▸")
                            }
                        }
                        if (open) {
                            items(list, key = { it.place.id }) { item ->
                                PlaceRow(item, extra = item.group.nameDe, indent = true) { onOpenPlace(item.place.id) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            text = { Text(stringResource(R.string.places_unused_confirm, unused.size)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteUnused(); confirmCleanup = false }) {
                    Text(stringResource(R.string.action_delete), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCleanup = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PlaceRow(item: PlaceListItem, extra: String?, indent: Boolean = false, onClick: () -> Unit) {
    val place = item.place
    val country = ReferenceData.countableCountry(place.countryIso2)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (indent) 16.dp else 0.dp, top = 10.dp, bottom = 10.dp)) {
        Text((if (place.isHomebase) "🏠 " else if (place.isFavorite) "★ " else "") + "${country?.flag ?: ""} ${place.displayName}")
        Text(
            listOfNotNull(
                stringResource(R.string.places_days, item.totalDays),
                item.lastDate?.let { stringResource(R.string.places_last, Format.fullDate(it)) },
                extra,
                if (place.latitude == null) stringResource(R.string.places_no_coordinates) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
fun PlaceDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPlace: (String) -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val place = state.place
    var edit by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var positionStart by remember { mutableStateOf<io.github.mario2712.locus.ui.editor.MapStart?>(null) }
    val positionScope = androidx.compose.runtime.rememberCoroutineScope()
    val controller = io.github.mario2712.locus.places.LocalPlaceSearch.current

    BackScaffold(place?.displayName ?: "", onBack) { modifier ->
        if (place == null) return@BackScaffold
        val record = state.record
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val country = ReferenceData.countableCountry(place.countryIso2)
            SectionCard(place.displayName) {
                Text(
                    stringResource(if (place.kind == PlaceKind.CITY) R.string.place_kind_city else R.string.place_kind_location),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                place.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                place.parentPlaceId?.let { parent ->
                    TextButton(onClick = { onOpenPlace(parent) }) {
                        Text(stringResource(R.string.place_in_city, state.editor.placeName(parent)))
                    }
                }
                Text(listOfNotNull(place.alias?.let { place.name }, country?.let { "${it.flag} ${it.nameDe}" },
                    place.regionCode?.let { ReferenceData.region(it)?.nameDe }).joinToString(" · "))
                Text(
                    if (place.latitude != null && place.longitude != null) {
                        stringResource(R.string.place_coordinates, "%.4f".format(java.util.Locale.ROOT, place.latitude), "%.4f".format(java.util.Locale.ROOT, place.longitude))
                    } else {
                        stringResource(R.string.places_no_coordinates)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { edit = true }) { Text(stringResource(R.string.place_edit)) }
                    OutlinedButton(onClick = { link = true }) { Text(stringResource(R.string.place_link_osm)) }
                }
                if (controller != null) {
                    TextButton(onClick = {
                        positionScope.launch {
                            positionStart = io.github.mario2712.locus.ui.editor.mapStartFor(
                                listOf(place), emptyList(), state.editor.places,
                            ) { controller.currentPosition() }
                        }
                    }) { Text(stringResource(R.string.place_set_position)) }
                }
                if (!place.isHomebase) {
                    TextButton(onClick = viewModel::setHomebase) { Text(stringResource(R.string.place_make_homebase)) }
                }
            }
            if (record != null) {
                SectionCard(stringResource(R.string.place_record)) {
                    Text(stringResource(R.string.place_record_days, record.totalDays))
                    record.firstDate?.let { first ->
                        Text(stringResource(R.string.place_record_range, Format.fullDate(first), Format.fullDate(record.lastDate ?: first)))
                    }
                    record.daysByType.entries.sortedByDescending { it.value }.forEach { (typeId, count) ->
                        Text("• ${state.editor.typeName(typeId)}: $count")
                    }
                    val children = state.editor.places.filter { it.parentPlaceId == place.id }
                    if (children.isNotEmpty()) {
                        Text(stringResource(R.string.place_record_children), style = MaterialTheme.typography.labelLarge)
                        children.sortedByDescending { record.childVisits[it.id] ?: 0 }.forEach { child ->
                            Text(
                                "• ${child.displayName}: ${record.childVisits[child.id] ?: 0}",
                                Modifier.fillMaxWidth().clickable { onOpenPlace(child.id) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                    if (record.visitCount > 0) {
                        Text(stringResource(R.string.place_record_visits, record.visitCount))
                        record.visitsByCategory.entries.sortedByDescending { it.value }.forEach { (categoryId, count) ->
                            Text("• ${state.editor.categoryName(categoryId)}: $count")
                        }
                    }
                }
                if (record.dates.isNotEmpty()) {
                    SectionCard(stringResource(R.string.place_record_dates)) {
                        record.dates.take(50).forEach { date ->
                            Text(Format.longDate(date), Modifier.fillMaxWidth().clickable { onOpenDay(date) }.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (place != null && edit) {
        PlaceEditDialog(place, state.editor.cities.filter { it.id != place.id }, onDismiss = { edit = false }, onSave = { viewModel.save(it); edit = false })
    }
    if (place != null && link) {
        LinkDialog(place, viewModel, onDismiss = { link = false })
    }
    if (place != null) {
        positionStart?.let { start ->
            io.github.mario2712.locus.ui.editor.MapPickerDialog(
                title = stringResource(R.string.place_set_position_title, place.displayName),
                start = start,
                confirmLabel = stringResource(R.string.place_set_position_confirm),
                onDismiss = { positionStart = null },
                onPick = { lat, lon, hit -> viewModel.setPosition(lat, lon, hit); positionStart = null },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.place_delete_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(); confirmDelete = false; onBack() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Ort mit OpenStreetMap verknüpfen (Koordinaten, Region) – z. B. für manuell angelegte Orte. */
@Composable
private fun LinkDialog(place: Place, viewModel: PlaceDetailViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(place.name) }
    var results by remember { mutableStateOf<List<PlaceCandidate>?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_link_osm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    scope.launch {
                        when (val r = viewModel.searchOnline(query)) {
                            is SearchResult.Ok -> { results = r.value; error = if (r.value.isEmpty()) R.string.search_no_results else null }
                            else -> error = R.string.search_offline
                        }
                    }
                }) { Text(stringResource(R.string.search_online)) }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results.orEmpty(), key = { it.osmId }) { candidate ->
                        Column(Modifier.fillMaxWidth().clickable { viewModel.link(candidate); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(candidate.name)
                            Text(candidate.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
                Text(stringResource(R.string.search_attribution), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
