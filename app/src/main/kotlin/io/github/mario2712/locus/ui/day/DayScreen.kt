package io.github.mario2712.locus.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.common.StayRow
import io.github.mario2712.locus.ui.common.text
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.StayEditorDialog
import io.github.mario2712.locus.ui.editor.SwitchDialog
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(onBack: () -> Unit, viewModel: DayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editorDraft by remember { mutableStateOf<StayDraft?>(null) }
    var showSwitch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(context)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Format.longDate(state.date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.holiday?.let {
                Text(stringResource(R.string.today_holiday, it), color = MaterialTheme.colorScheme.tertiary)
            }
            state.suggestion?.let { suggestion ->
                SectionCard(stringResource(R.string.day_suggestion)) {
                    Text("${state.editor.typeName(suggestion.stayTypeId)} · ${state.editor.placeName(suggestion.placeId)}")
                    Button(onClick = { viewModel.confirmSuggestion(suggestion) }) { Text(stringResource(R.string.today_confirm)) }
                }
            }
            SectionCard(stringResource(R.string.day_stays)) {
                if (state.stays.isEmpty()) {
                    Text(stringResource(R.string.day_empty))
                } else {
                    state.stays.forEach { item ->
                        StayRow(item, onClick = { editorDraft = StayDraft.from(item.stay) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editorDraft = viewModel.newDraft() }) { Text(stringResource(R.string.day_add)) }
                    if (state.stays.isNotEmpty()) {
                        OutlinedButton(onClick = { showSwitch = true }) { Text(stringResource(R.string.today_switch)) }
                    }
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
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editorDraft = null } },
            onCreatePlace = viewModel::createPlace,
        )
    }
    if (showSwitch) {
        SwitchDialog(
            date = state.date,
            initialTime = LocalTime.of(12, 0),
            editor = state.editor,
            onDismiss = { showSwitch = false },
            onConfirm = { viewModel.switchAt(it); showSwitch = false },
            onCreatePlace = viewModel::createPlace,
        )
    }
}
