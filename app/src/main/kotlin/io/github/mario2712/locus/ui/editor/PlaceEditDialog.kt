package io.github.mario2712.locus.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.reference.ReferenceData

/** Ort bearbeiten: Art, zugehörige Stadt, Name, Kurzname, Land, Favorit. */
@Composable
fun PlaceEditDialog(place: Place, cities: List<Place>, onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    var draft by remember { mutableStateOf(place) }
    var pickCountry by remember { mutableStateOf(false) }
    var pickCity by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.kind == PlaceKind.CITY, { draft = draft.copy(kind = PlaceKind.CITY, parentPlaceId = null) },
                        label = { Text(stringResource(R.string.place_kind_city)) })
                    FilterChip(draft.kind != PlaceKind.CITY, { if (draft.kind == PlaceKind.CITY) draft = draft.copy(kind = PlaceKind.POI) },
                        label = { Text(stringResource(R.string.place_kind_location)) })
                }
                if (draft.kind != PlaceKind.CITY) {
                    OutlinedButton(onClick = { pickCity = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(draft.parentPlaceId?.let { id -> cities.firstOrNull { it.id == id }?.let { stringResource(R.string.place_in_city, it.displayName) } }
                            ?: stringResource(R.string.place_parent_optional))
                    }
                }
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
    if (pickCity) {
        AlertDialog(
            onDismissRequest = { pickCity = false },
            title = { Text(stringResource(R.string.place_parent_optional)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(cities, key = { it.id }) { city ->
                        Text(city.displayName, Modifier.fillMaxWidth().clickable { draft = draft.copy(parentPlaceId = city.id); pickCity = false }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { draft = draft.copy(parentPlaceId = null); pickCity = false }) { Text(stringResource(R.string.action_none)) } },
        )
    }
    if (pickCountry) {
        CountryPickerDialog(onDismiss = { pickCountry = false }, onSelect = {
            draft = draft.copy(countryIso2 = it.iso2, regionCode = null); pickCountry = false
        })
    }
}

