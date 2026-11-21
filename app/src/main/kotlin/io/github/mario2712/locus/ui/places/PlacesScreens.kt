package io.github.mario2712.locus.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import io.github.mario2712.locus.domain.places.PlaceCandidate
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.places.SearchResult
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.CountryPickerDialog
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

@Composable
fun PlacesScreen(onBack: () -> Unit, onOpenPlace: (String) -> Unit, viewModel: PlacesViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    BackScaffold(stringResource(R.string.places_title), onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.editor_search)) }, modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn {
                items(items.filter { query.isBlank() || it.place.displayName.contains(query, true) || it.place.name.contains(query, true) }, key = { it.place.id }) { item ->
                    val place = item.place
                    val country = ReferenceData.countableCountry(place.countryIso2)
                    Column(Modifier.fillMaxWidth().clickable { onOpenPlace(place.id) }.padding(vertical = 10.dp)) {
                        Text((if (place.isHomebase) "🏠 " else if (place.isFavorite) "★ " else "") + "${country?.flag ?: ""} ${place.displayName}")
                        Text(
                            listOfNotNull(
                                stringResource(R.string.places_days, item.totalDays),
                                item.lastDate?.let { stringResource(R.string.places_last, Format.fullDate(it)) },
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
    }
}

@Composable
fun PlaceDetailScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, viewModel: PlaceDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val place = state.place
    var edit by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackScaffold(place?.displayName ?: "", onBack) { modifier ->
        if (place == null) return@BackScaffold
        val record = state.record
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val country = ReferenceData.countableCountry(place.countryIso2)
            SectionCard(place.displayName) {
                Text(listOfNotNull(place.alias?.let { place.name }, country?.let { "${it.flag} ${it.nameDe}" },
                    place.regionCode?.let { ReferenceData.region(it)?.nameDe }).joinToString(" · "))
                Text(
                    if (place.latitude != null && place.longitude != null) {
                        stringResource(R.string.place_coordinates, "%.4f".format(place.latitude), "%.4f".format(place.longitude))
                    } else {
                        stringResource(R.string.places_no_coordinates)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { edit = true }) { Text(stringResource(R.string.place_edit)) }
                    OutlinedButton(onClick = { link = true }) { Text(stringResource(R.string.place_link_osm)) }
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
        PlaceEditDialog(place, onDismiss = { edit = false }, onSave = { viewModel.save(it); edit = false })
    }
    if (place != null && link) {
        LinkDialog(place, viewModel, onDismiss = { link = false })
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

@Composable
private fun PlaceEditDialog(place: Place, onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    var draft by remember { mutableStateOf(place) }
    var pickCountry by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, singleLine = true,
                    label = { Text(stringResource(R.string.place_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.alias.orEmpty(), { draft = draft.copy(alias = it.takeIf { a -> a.isNotBlank() }) }, singleLine = true,
                    label = { Text(stringResource(R.string.place_alias)) }, modifier = Modifier.fillMaxWidth())
                val country = ReferenceData.countableCountry(draft.countryIso2)
                OutlinedButton(onClick = { pickCountry = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${country?.flag ?: ""} ${country?.nameDe ?: draft.countryIso2}")
                }
                SwitchRow(stringResource(R.string.place_favorite), draft.isFavorite) { draft = draft.copy(isFavorite = it) }
            }
        },
        confirmButton = { TextButton(enabled = draft.name.isNotBlank(), onClick = { onSave(draft.copy(name = draft.name.trim())) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickCountry) {
        CountryPickerDialog(onDismiss = { pickCountry = false }, onSelect = {
            draft = draft.copy(countryIso2 = it.iso2, regionCode = null); pickCountry = false
        })
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
