package io.github.mario2712.locus.ui.editor

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import io.github.mario2712.locus.domain.model.Country
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.places.PlaceCandidates
import io.github.mario2712.locus.domain.places.PlaceGroups
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.places.LocalPlaceSearch
import io.github.mario2712.locus.places.SearchResult
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.PlaceDraft
import io.github.mario2712.locus.ui.common.TypeDot
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeChips(types: List<StayType>, selectedId: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            FilterChip(
                selected = type.id == selectedId,
                onClick = { onSelect(type.id) },
                label = { Text(type.name) },
                leadingIcon = { TypeDot(type.colorArgb) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantChips(persons: List<Person>, selected: Set<PersonId>, onChange: (Set<PersonId>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        persons.forEach { person ->
            val isSelected = person.id in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - person.id else selected + person.id) },
                label = { Text(person.name) },
            )
        }
    }
}

/** Feld, das den gewählten Ort zeigt und beim Antippen die Ortsauswahl öffnet. */
@Composable
fun PlaceField(
    places: List<Place>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
    /** Örtlichkeiten (Lokale, Gipfel …) zuerst anzeigen – z. B. bei Besuchen. */
    preferLocations: Boolean = false,
    /** Passende Orte für diesen Eintrag (z. B. Orte in der Stadt des Tages), oben angezeigt. */
    suggestedIds: List<String> = emptyList(),
) {
    var showPicker by remember { mutableStateOf(false) }
    var editPlace by remember { mutableStateOf<Place?>(null) }
    val controller = LocalPlaceSearch.current
    val scope = rememberCoroutineScope()
    val selected = places.firstOrNull { it.id == selectedId }
    val label = selected?.displayName ?: stringResource(R.string.editor_choose_place)
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (selected != null && controller != null) {
        TextButton(onClick = { editPlace = selected }) { Text(stringResource(R.string.place_edit_inline)) }
    }
    editPlace?.let { place ->
        PlaceEditDialog(
            place = place,
            cities = places.filter { it.kind == PlaceKind.CITY && it.id != place.id },
            onDismiss = { editPlace = null },
            onSave = { updated -> scope.launch { controller?.save(updated) }; editPlace = null },
        )
    }
    if (showPicker) {
        PlacePickerDialog(
            places = places,
            onDismiss = { showPicker = false },
            onSelect = { onSelect(it); showPicker = false },
            onCreatePlace = onCreatePlace,
            preferLocations = preferLocations,
            suggestedIds = suggestedIds,
        )
    }
}

/** Ab so vielen gespeicherten Orten zeigt die Auswahl ohne Suchbegriff nur Vorschläge. */
private const val COMPACT_LIMIT = 12

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Results(val items: List<PlaceCandidate>) : OnlineState
    data class Error(val message: Int) : OnlineState
}

/**
 * Ortsauswahl: gespeicherte Orte, Online-Suche (OpenStreetMap) und „Hier“ (aktueller Standort).
 * Ein Online-Treffer wird erst bei Auswahl gespeichert; bestehende Orte werden dabei ergänzt.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlacePickerDialog(
    places: List<Place>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
    preferLocations: Boolean = false,
    suggestedIds: List<String> = emptyList(),
) {
    val controller = LocalPlaceSearch.current
    var showAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var editPlace by remember { mutableStateOf<Place?>(null) }
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
    var mapStart by remember { mutableStateOf<MapStart?>(null) }
    var mapPins by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }
    var picked by remember { mutableStateOf<Triple<Double, Double, PlaceCandidate>?>(null) }
    val filtered = places.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
    }

    fun show(result: SearchResult<List<PlaceCandidate>>) {
        online = when (result) {
            is SearchResult.Ok -> if (result.value.isEmpty()) OnlineState.Error(R.string.search_no_results) else OnlineState.Results(result.value)
            SearchResult.Offline -> OnlineState.Error(R.string.search_offline)
            SearchResult.NoLocation -> OnlineState.Error(R.string.search_no_location)
        }
    }
    fun runNearby() {
        val c = controller ?: return
        online = OnlineState.Loading
        scope.launch { show(runCatching { c.nearby() }.getOrElse { SearchResult.Offline }) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) runNearby() else online = OnlineState.Error(R.string.search_no_permission)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_choose_place)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.editor_search)) }, modifier = Modifier.fillMaxWidth(),
                )
                if (controller != null) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = query.trim().length >= 2 && online != OnlineState.Loading,
                            onClick = {
                                online = OnlineState.Loading
                                scope.launch {
                                    show(runCatching { controller.search(query) }.getOrElse { SearchResult.Offline })
                                }
                            },
                        ) { Text(stringResource(R.string.search_online)) }
                        OutlinedButton(
                            enabled = online != OnlineState.Loading,
                            onClick = {
                                if (controller.hasLocationPermission()) {
                                    runNearby()
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                                    )
                                }
                            },
                        ) { Text(stringResource(R.string.search_here)) }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val byId = places.associateBy { it.id }
                                mapPins = emptyList()
                                mapStart = mapStartFor(suggestedIds.mapNotNull(byId::get), emptyList(), places) { controller.currentPosition() }
                            }
                        }) { Text(stringResource(R.string.map_pick_button)) }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    when (val state = online) {
                        OnlineState.Idle -> Unit
                        OnlineState.Loading -> item {
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.search_loading))
                            }
                        }
                        is OnlineState.Error -> item {
                            Text(stringResource(state.message), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        is OnlineState.Results -> {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.search_online_results)) }
                                    TextButton(onClick = {
                                        mapPins = state.items
                                        state.items.first().let { mapStart = MapStart(it.latitude, it.longitude, 14.0) }
                                    }) { Text(stringResource(R.string.map_show_results)) }
                                }
                            }
                            itemsIndexed(state.items, key = { index, it -> "osm-$index-${it.osmId}" }) { _, candidate ->
                                val country = ReferenceData.countableCountry(candidate.countryIso2)
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        val c = controller ?: return@clickable
                                        scope.launch {
                                            try {
                                                onSelect(c.import(candidate).id)
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                online = OnlineState.Error(R.string.search_import_failed)
                                            }
                                        }
                                    }.padding(vertical = 10.dp),
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
                            item {
                                Text(
                                    stringResource(R.string.search_attribution),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    // Viele Orte: ohne Suchbegriff nur Vorschläge (Kontext, Favoriten, Homebase), Rest auf Wunsch.
                    val compact = query.isBlank() && !showAll && places.size > COMPACT_LIMIT
                    if (compact) {
                        val byId = places.associateBy { it.id }
                        val suggestions = (suggestedIds.mapNotNull { byId[it] } + places.filter { it.isHomebase || it.isFavorite })
                            .distinctBy { it.id }.take(10)
                        if (suggestions.isNotEmpty()) item(key = "label-suggest") { SectionLabel(stringResource(R.string.search_suggestions)) }
                        items(suggestions, key = { "s-" + it.id }) { place ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(onClick = { onSelect(place.id) }, onLongClick = { editPlace = place })
                                    .padding(vertical = 10.dp),
                            ) {
                                Text((if (place.isHomebase) "🏠 " else if (place.isFavorite) "★ " else "") + place.displayName)
                                place.address?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                        item(key = "show-all") {
                            TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.search_show_all, places.size)) }
                        }
                    }
                    val (cities, locations) = (if (compact) emptyList() else filtered).partition { it.kind == PlaceKind.CITY }
                    val sections = listOf(
                        R.string.places_tab_cities to cities,
                        R.string.places_tab_locations to locations,
                    ).let { if (preferLocations) it.reversed() else it }
                    sections.forEach { (label, list) ->
                        if (list.isNotEmpty()) {
                            item(key = "label-$label") { SectionLabel(stringResource(label)) }
                        }
                        items(list, key = { it.id }) { place ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(onClick = { onSelect(place.id) }, onLongClick = { editPlace = place })
                                    .padding(vertical = 10.dp),
                            ) {
                                val country = ReferenceData.countableCountry(place.countryIso2)
                                val parent = place.parentPlaceId?.let { id -> places.firstOrNull { it.id == id }?.displayName }
                                Text((if (place.isHomebase) "🏠 " else if (place.isFavorite) "★ " else "") + place.displayName)
                                Text(
                                    listOfNotNull(
                                        place.alias?.let { place.name },
                                        place.address ?: parent?.let { stringResource(R.string.place_in_city, it) },
                                        country?.let { "${it.flag} ${it.nameDe}" },
                                        if (place.latitude == null) stringResource(R.string.places_no_coordinates) else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
                if (places.isEmpty() && online == OnlineState.Idle) Text(stringResource(R.string.editor_no_places))
                if (places.isNotEmpty() && controller != null) {
                    Text(
                        stringResource(R.string.place_long_press_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { showCreate = true }) { Text(stringResource(R.string.editor_new_place)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    editPlace?.let { place ->
        PlaceEditDialog(
            place = place,
            cities = places.filter { it.kind == PlaceKind.CITY && it.id != place.id },
            onDismiss = { editPlace = null },
            onSave = { updated -> scope.launch { controller?.save(updated) }; editPlace = null },
        )
    }
    mapStart?.let { start ->
        MapPickerDialog(
            title = stringResource(R.string.map_pick_title),
            start = start,
            pins = mapPins.mapIndexed { index, c -> MapPin(index.toString(), c.latitude, c.longitude, c.name) },
            onDismiss = { mapStart = null },
            onPin = { pin ->
                val candidate = mapPins.getOrNull(pin.id.toInt()) ?: return@MapPickerDialog
                val c = controller ?: return@MapPickerDialog
                scope.launch {
                    try {
                        val place = c.import(candidate)
                        mapStart = null
                        onSelect(place.id)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        online = OnlineState.Error(R.string.search_import_failed)
                        mapStart = null
                    }
                }
            },
            onPick = { lat, lon, hit ->
                mapStart = null
                if (hit != null) picked = Triple(lat, lon, hit) else online = OnlineState.Error(R.string.map_pick_no_country)
            },
        )
    }
    picked?.let { (lat, lon, hit) ->
        MapPlaceCreateDialog(
            hit = hit, latitude = lat, longitude = lon,
            initialKind = if (preferLocations || !PlaceCandidates.isAtPoint(hit)) PlaceKind.POI else hit.kind,
            cities = places.filter { it.kind == PlaceKind.CITY },
            onDismiss = { picked = null },
            onCreated = { place -> picked = null; onSelect(place.id) },
        )
    }
    if (showCreate) {
        PlaceCreateDialog(
            initialName = query,
            cities = places.filter { it.kind == PlaceKind.CITY },
            initialKind = if (preferLocations) PlaceKind.POI else PlaceKind.CITY,
            onDismiss = { showCreate = false },
            onCreate = { draft -> val place = onCreatePlace(draft); showCreate = false; onSelect(place.id) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun PlaceCreateDialog(
    initialName: String,
    cities: List<Place>,
    initialKind: PlaceKind,
    onDismiss: () -> Unit,
    onCreate: suspend (PlaceDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    var parentId by remember { mutableStateOf<String?>(null) }
    var showCities by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(ReferenceData.country("AT")!!) }
    var regionCode by remember { mutableStateOf<String?>(null) }
    var favorite by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var showRegions by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val regions = ReferenceData.regionsOf(country.iso2)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_new_place)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == PlaceKind.CITY, { kind = PlaceKind.CITY }, label = { Text(stringResource(R.string.place_kind_city)) })
                    FilterChip(kind != PlaceKind.CITY, { kind = PlaceKind.POI }, label = { Text(stringResource(R.string.place_kind_location)) })
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(if (kind == PlaceKind.CITY) R.string.place_name else R.string.place_name_location)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind != PlaceKind.CITY && cities.isNotEmpty()) {
                    OutlinedButton(onClick = { showCities = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(parentId?.let { id -> cities.firstOrNull { it.id == id }?.let { stringResource(R.string.place_in_city, it.displayName) } }
                            ?: stringResource(R.string.place_parent_optional))
                    }
                }
                OutlinedTextField(
                    value = alias, onValueChange = { alias = it }, singleLine = true,
                    label = { Text(stringResource(R.string.place_alias)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showCountries = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${country.flag} ${country.nameDe}")
                }
                if (regions.isNotEmpty()) {
                    OutlinedButton(onClick = { showRegions = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(regionCode?.let { ReferenceData.region(it)?.nameDe } ?: stringResource(R.string.place_region_optional))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = favorite, onCheckedChange = { favorite = it })
                    Text(stringResource(R.string.place_favorite))
                }
                Text(
                    stringResource(R.string.place_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            onCreate(PlaceDraft(name, country.iso2, regionCode, alias, favorite, kind, parentId))
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (showCities) {
        AlertDialog(
            onDismissRequest = { showCities = false },
            title = { Text(stringResource(R.string.place_parent_optional)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(cities, key = { it.id }) { city ->
                        Text(
                            city.displayName,
                            Modifier.fillMaxWidth().clickable {
                                parentId = city.id
                                ReferenceData.country(city.countryIso2)?.let { country = it }
                                showCities = false
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { parentId = null; showCities = false }) { Text(stringResource(R.string.action_none)) } },
        )
    }
    if (showCountries) {
        CountryPickerDialog(
            onDismiss = { showCountries = false },
            onSelect = { country = it; regionCode = null; showCountries = false },
        )
    }
    if (showRegions) {
        AlertDialog(
            onDismissRequest = { showRegions = false },
            title = { Text(stringResource(R.string.place_region_optional)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(regions, key = { it.code }) { region ->
                        Text(
                            region.nameDe,
                            Modifier.fillMaxWidth().clickable { regionCode = region.code; showRegions = false }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { regionCode = null; showRegions = false }) {
                    Text(stringResource(R.string.action_none))
                }
            },
        )
    }
}

@Composable
fun CountryPickerDialog(onDismiss: () -> Unit, onSelect: (Country) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = ReferenceData.countries.filter {
        query.isBlank() || it.nameDe.contains(query, ignoreCase = true) || it.iso2.equals(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_country)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.editor_search)) }, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.iso2 }) { country ->
                        Text(
                            "${country.flag}  ${country.nameDe}",
                            Modifier.fillMaxWidth().clickable { onSelect(country) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Uhrzeit wählen; „Entfernen“ setzt sie zurück (Uhrzeiten sind optional). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val start = initial ?: LocalTime.of(12, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogFor(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Zeile mit Beschriftung und einem Button, der Datum oder Uhrzeit zeigt. */
@Composable
fun ValueButtonRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

fun formatOptionalTime(time: LocalTime?, placeholder: String): String = time?.let(Format::time) ?: placeholder
