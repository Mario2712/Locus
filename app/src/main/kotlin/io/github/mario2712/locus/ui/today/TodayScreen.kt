package io.github.mario2712.locus.ui.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.common.StayRow
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.common.text
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.StayEditorDialog
import io.github.mario2712.locus.ui.editor.SwitchDialog
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    onOpenDay: (java.time.LocalDate) -> Unit,
    onOpenCalendar: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editorDraft by remember { mutableStateOf<StayDraft?>(null) }
    var showSwitch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(context)) }
    }

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Format.longDate(state.today), style = MaterialTheme.typography.headlineSmall)
            state.holiday?.let {
                Text(stringResource(R.string.today_holiday, it), color = MaterialTheme.colorScheme.tertiary)
            }

            if (state.reminderEnabled && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionCard(stringResource(R.string.today_notifications_title)) {
                    Text(stringResource(R.string.today_notifications_text))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.today_notifications_allow))
                    }
                }
            }

            if (state.todayStays.isEmpty()) {
                SectionCard(stringResource(R.string.today_where)) {
                    val suggestion = state.suggestion
                    if (suggestion != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TypeDot(state.editor.typeColor(suggestion.stayTypeId))
                            Text(
                                "${state.editor.typeName(suggestion.stayTypeId)} · ${state.editor.placeName(suggestion.placeId)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.confirmSuggestion(suggestion) }) {
                                Text(stringResource(R.string.today_confirm))
                            }
                            OutlinedButton(onClick = { editorDraft = viewModel.newDraft(suggestion) }) {
                                Text(stringResource(R.string.today_other))
                            }
                        }
                    } else {
                        Text(stringResource(R.string.today_no_suggestion))
                    }
                    if (state.combos.isNotEmpty()) {
                        Text(stringResource(R.string.today_recent), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.combos.forEach { combo ->
                                AssistChip(
                                    onClick = { viewModel.quickCheckIn(combo) },
                                    label = {
                                        Text("${state.editor.typeName(combo.stayTypeId)} · ${state.editor.placeName(combo.placeId)}")
                                    },
                                    leadingIcon = { TypeDot(state.editor.typeColor(combo.stayTypeId)) },
                                )
                            }
                        }
                    }
                    TextButton(onClick = { editorDraft = viewModel.newDraft() }) {
                        Text(stringResource(R.string.today_new_entry))
                    }
                }
            } else {
                SectionCard(stringResource(R.string.today_title)) {
                    state.todayStays.forEach { StayRow(it, onClick = { onOpenDay(state.today) }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showSwitch = true }) { Text(stringResource(R.string.today_switch)) }
                        OutlinedButton(onClick = { onOpenDay(state.today) }) { Text(stringResource(R.string.today_edit)) }
                    }
                }
            }

            SectionCard(stringResource(R.string.today_week)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    state.week.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (day.colorArgb != null) TypeDot(day.colorArgb, 16.dp)
                                else Text("·", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                if (state.weekCounts.isNotEmpty()) {
                    Text(state.weekCounts.joinToString(" · ") { (name, count) -> "$count× $name" })
                }
                if (state.missingDays > 0) {
                    TextButton(onClick = onOpenCalendar) {
                        Text(
                            context.resources.getQuantityString(R.plurals.today_missing, state.missingDays, state.missingDays),
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.today_country_points, state.countries.size)) {
                if (state.countries.isEmpty()) {
                    Text(stringResource(R.string.today_no_countries))
                } else {
                    Text(state.countries.joinToString(" ") { it.flag }, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }

    editorDraft?.let { draft ->
        StayEditorDialog(
            initial = draft,
            editor = state.editor,
            onDismiss = { editorDraft = null },
            onSave = { viewModel.save(it); editorDraft = null },
            onDelete = null,
            onCreatePlace = viewModel::createPlace,
        )
    }
    if (showSwitch) {
        SwitchDialog(
            date = state.today,
            initialTime = LocalTime.now().withSecond(0).withNano(0).let { it.withMinute(it.minute - it.minute % 5) },
            editor = state.editor,
            onDismiss = { showSwitch = false },
            onConfirm = { viewModel.switchAt(it); showSwitch = false },
            onCreatePlace = viewModel::createPlace,
        )
    }
}
