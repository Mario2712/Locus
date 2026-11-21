package io.github.mario2712.locus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.editor.PlaceField
import io.github.mario2712.locus.ui.editor.TimePickerDialog
import io.github.mario2712.locus.ui.editor.TypeChips
import io.github.mario2712.locus.ui.editor.ValueButtonRow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var pickTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.settings_patterns)) {
                Text(
                    stringResource(R.string.settings_patterns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayOfWeek.entries.forEach { day ->
                    val rule = state.rules[day]
                    Row(
                        Modifier.fillMaxWidth().clickable { editDay = day }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(day.getDisplayName(TextStyle.FULL, Locale.GERMAN), Modifier.weight(0.4f))
                        if (rule != null) {
                            TypeDot(state.editor.typeColor(rule.stayTypeId))
                            Text(
                                "${state.editor.typeName(rule.stayTypeId)} · ${state.editor.placeName(rule.placeId)}",
                                Modifier.weight(0.6f),
                            )
                        } else {
                            Text(
                                stringResource(R.string.settings_no_rule),
                                Modifier.weight(0.6f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.settings_reminder)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_reminder_enabled), Modifier.weight(1f))
                    Switch(checked = state.reminder.enabled, onCheckedChange = viewModel::setReminderEnabled)
                }
                ValueButtonRow(stringResource(R.string.settings_reminder_time), Format.time(state.reminder.time)) {
                    pickTime = true
                }
                Text(
                    stringResource(R.string.settings_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editDay?.let { day ->
        RuleDialog(
            day = day,
            state = state,
            onDismiss = { editDay = null },
            onSave = { typeId, placeId -> viewModel.setRule(day, typeId, placeId); editDay = null },
            onClear = { viewModel.clearRule(day); editDay = null },
            onCreatePlace = viewModel::createPlace,
        )
    }
    if (pickTime) {
        TimePickerDialog(
            state.reminder.time,
            onDismiss = { pickTime = false },
            onConfirm = { viewModel.setReminderTime(it); pickTime = false },
        )
    }
}

@Composable
private fun RuleDialog(
    day: DayOfWeek,
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit,
    onCreatePlace: suspend (io.github.mario2712.locus.ui.common.PlaceDraft) -> io.github.mario2712.locus.domain.model.Place,
) {
    val existing = state.rules[day]
    var typeId by remember { mutableStateOf(existing?.stayTypeId) }
    var placeId by remember { mutableStateOf(existing?.placeId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.getDisplayName(TextStyle.FULL, Locale.GERMAN)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
                TypeChips(state.editor.activeStayTypes, typeId) { typeId = it }
                Text(stringResource(R.string.editor_place), style = MaterialTheme.typography.labelLarge)
                PlaceField(state.editor.sortedPlaces, placeId, { placeId = it }, onCreatePlace)
                if (existing != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.settings_remove_rule), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            val t = typeId
            val p = placeId
            TextButton(enabled = t != null && p != null, onClick = { if (t != null && p != null) onSave(t, p) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
