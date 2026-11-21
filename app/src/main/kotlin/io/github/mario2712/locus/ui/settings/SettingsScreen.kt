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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.ParticipantChips
import io.github.mario2712.locus.ui.editor.PlacePickerDialog
import io.github.mario2712.locus.ui.editor.SwitchRow
import io.github.mario2712.locus.ui.editor.TimePickerDialog
import io.github.mario2712.locus.ui.editor.ValueButtonRow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = state.editor
    var editDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var editTemplate by remember { mutableStateOf<DayTemplate?>(null) }
    var newTemplate by remember { mutableStateOf(false) }
    var pickHomebase by remember { mutableStateOf(false) }
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
            SectionCard(stringResource(R.string.settings_homebase)) {
                Text(
                    stringResource(R.string.settings_homebase_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ValueButtonRow(
                    stringResource(R.string.settings_homebase),
                    editor.homebase?.displayName ?: stringResource(R.string.editor_choose_place),
                ) { pickHomebase = true }
            }

            SectionCard(stringResource(R.string.settings_templates)) {
                Text(
                    stringResource(R.string.settings_templates_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                editor.templates.forEach { template ->
                    Column(Modifier.fillMaxWidth().clickable { editTemplate = template }.padding(vertical = 6.dp)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            editor.stepsLabel(template.steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { newTemplate = true }) { Text(stringResource(R.string.settings_template_new)) }
            }

            SectionCard(stringResource(R.string.settings_patterns)) {
                Text(
                    stringResource(R.string.settings_patterns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayOfWeek.entries.forEach { day ->
                    val rule = state.rules[day]
                    Row(Modifier.fillMaxWidth().clickable { editDay = day }.padding(vertical = 8.dp)) {
                        Text(day.getDisplayName(TextStyle.FULL, Locale.GERMAN), Modifier.weight(0.35f))
                        Text(
                            rule?.let { r ->
                                r.templateId?.let { editor.templatesById[it]?.name } ?: editor.stepsLabel(r.steps(editor.templatesById))
                            } ?: stringResource(R.string.settings_no_rule),
                            Modifier.weight(0.65f),
                            color = if (rule == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            StayTypesCard(editor.stayTypes, viewModel::saveStayType) { viewModel.newCustomId("stay") }
            VisitCategoriesCard(editor.visitCategories, viewModel::saveVisitCategory) { viewModel.newCustomId("visit") }

            SectionCard(stringResource(R.string.settings_defaults)) {
                val defaults = editor.defaults
                SwitchRow(stringResource(R.string.settings_prefill_transport), defaults.prefillTransport) {
                    viewModel.setEntryDefaults(defaults.copy(prefillTransport = it))
                }
                Text(stringResource(R.string.settings_daily_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, defaults.dailyParticipants) {
                    if (it.isNotEmpty()) viewModel.setEntryDefaults(defaults.copy(dailyParticipants = it))
                }
                Text(stringResource(R.string.settings_trip_participants), style = MaterialTheme.typography.labelLarge)
                ParticipantChips(editor.persons, defaults.tripParticipants) {
                    if (it.isNotEmpty()) viewModel.setEntryDefaults(defaults.copy(tripParticipants = it))
                }
            }

            SectionCard(stringResource(R.string.settings_reminder)) {
                SwitchRow(stringResource(R.string.settings_reminder_enabled), state.reminder.enabled, viewModel::setReminderEnabled)
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

    if (pickHomebase) {
        PlacePickerDialog(
            places = editor.sortedPlaces,
            onDismiss = { pickHomebase = false },
            onSelect = { viewModel.setHomebase(it); pickHomebase = false },
            onCreatePlace = viewModel::createPlace,
        )
    }
    editDay?.let { day ->
        RuleDialog(
            day = day,
            state = state,
            onDismiss = { editDay = null },
            onTemplate = { viewModel.setRuleTemplate(day, it); editDay = null },
            onSingle = { typeId, placeId -> viewModel.setRuleSingle(day, typeId, placeId); editDay = null },
            onClear = { viewModel.clearRule(day); editDay = null },
            onCreatePlace = viewModel::createPlace,
        )
    }
    val templateToEdit = editTemplate
    if (templateToEdit != null || newTemplate) {
        TemplateDialog(
            initial = templateToEdit,
            editor = editor,
            newId = viewModel::newTemplateId,
            onDismiss = { editTemplate = null; newTemplate = false },
            onSave = { viewModel.saveTemplate(it); editTemplate = null; newTemplate = false },
            onDelete = templateToEdit?.let { t -> { viewModel.deleteTemplate(t.id); editTemplate = null } },
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
