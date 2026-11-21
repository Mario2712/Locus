package io.github.mario2712.locus.ui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.R
import io.github.mario2712.locus.backup.BackupManager
import io.github.mario2712.locus.domain.model.BackupSettings
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.SwitchRow
import io.github.mario2712.locus.ui.places.BackScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Ergebnis einer Aktion für den Dialog. */
sealed interface DataMessage {
    data class Saved(val fileName: String?) : DataMessage
    data class Imported(val added: Int, val updated: Int, val unchanged: Int) : DataMessage
    data class Failed(val reason: String) : DataMessage
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val backups: BackupManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    val backup: StateFlow<BackupSettings> = settings.observeBackup()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSettings())
    val message = MutableStateFlow<DataMessage?>(null)
    val busy = MutableStateFlow(false)

    fun fileName() = backups.suggestedFileName()

    private fun run(block: suspend () -> DataMessage) = viewModelScope.launch {
        busy.value = true
        message.value = try { block() } catch (e: Exception) { DataMessage.Failed(e.message ?: e.toString()) }
        busy.value = false
    }

    fun backupNow() = run { DataMessage.Saved(backups.backupToDownloads()) }
    fun exportTo(uri: Uri) = run { backups.exportTo(uri); DataMessage.Saved(null) }
    fun importFrom(uri: Uri) = run { backups.importFrom(uri).let { DataMessage.Imported(it.added, it.updated, it.unchanged) } }
    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch { settings.setBackup(backup.value.copy(autoBackup = enabled)) }
    fun dismiss() { message.value = null }
}

@Composable
fun DataScreen(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    BackScaffold(stringResource(R.string.data_title), onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.data_backup)) {
                Text(
                    backup.lastBackupEpochMillis?.let {
                        stringResource(R.string.data_last_backup, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
                            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())))
                    } ?: stringResource(R.string.data_never),
                )
                SwitchRow(stringResource(R.string.data_auto_backup), backup.autoBackup, viewModel::setAutoBackup)
                Button(enabled = !busy, onClick = viewModel::backupNow, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.data_backup_now))
                }
                Text(stringResource(R.string.data_backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(stringResource(R.string.data_transfer)) {
                OutlinedButton(enabled = !busy, onClick = { exportLauncher.launch(viewModel.fileName()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.data_export))
                }
                OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.data_import))
                }
                Text(stringResource(R.string.data_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(stringResource(R.string.data_import)) },
            text = { Text(stringResource(R.string.data_import_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.importFrom(uri); confirmImport = null }) { Text(stringResource(R.string.data_import_go)) } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            text = {
                Text(
                    when (m) {
                        is DataMessage.Saved -> m.fileName?.let { stringResource(R.string.data_saved_downloads, it) } ?: stringResource(R.string.data_saved)
                        is DataMessage.Imported -> stringResource(R.string.data_imported, m.added, m.updated, m.unchanged)
                        is DataMessage.Failed -> stringResource(R.string.data_failed, m.reason)
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
