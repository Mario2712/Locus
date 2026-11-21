package io.github.mario2712.locus.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.reference.ReferenceData
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
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = places.firstOrNull { it.id == selectedId }?.displayName ?: stringResource(R.string.editor_choose_place)
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (showPicker) {
        PlacePickerDialog(
            places = places,
            onDismiss = { showPicker = false },
            onSelect = { onSelect(it); showPicker = false },
            onCreatePlace = onCreatePlace,
        )
    }
}

@Composable
fun PlacePickerDialog(
    places: List<Place>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreatePlace: suspend (PlaceDraft) -> Place,
) {
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    val filtered = places.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
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
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { place ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onSelect(place.id) }.padding(vertical = 10.dp),
                        ) {
                            val country = ReferenceData.countableCountry(place.countryIso2)
                            Text((if (place.isFavorite) "★ " else "") + place.displayName)
                            if (place.alias != null || country != null) {
                                Text(
                                    listOfNotNull(place.alias?.let { place.name }, country?.let { "${it.flag} ${it.nameDe}" })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (places.isEmpty()) Text(stringResource(R.string.editor_no_places))
            }
        },
        confirmButton = { TextButton(onClick = { showCreate = true }) { Text(stringResource(R.string.editor_new_place)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (showCreate) {
        PlaceCreateDialog(
            initialName = query,
            onDismiss = { showCreate = false },
            onCreate = { draft -> val place = onCreatePlace(draft); showCreate = false; onSelect(place.id) },
        )
    }
}

@Composable
fun PlaceCreateDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: suspend (PlaceDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.place_name)) }, modifier = Modifier.fillMaxWidth(),
                )
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
                            onCreate(PlaceDraft(name, country.iso2, regionCode, alias, favorite))
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
