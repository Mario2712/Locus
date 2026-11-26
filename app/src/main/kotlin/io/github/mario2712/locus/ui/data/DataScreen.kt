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
import kotlinx.coroutines.flow.map
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
    data class Restored(val atMillis: Long) : DataMessage
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val backups: BackupManager,
    private val settings: SettingsRepository,
    private val safety: io.github.mario2712.locus.backup.SafetyBackups,
    trash: io.github.mario2712.locus.data.trash.TrashStore,
) : ViewModel() {
    val snapshots = safety.snapshots
    val trashCount: StateFlow<Int> = trash.observe().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val backup: StateFlow<BackupSettings> = settings.observeBackup()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), BackupSettings())
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
    fun importFrom(uri: Uri, withSettings: Boolean) = run {
        backups.importFrom(uri, withSettings).let { DataMessage.Imported(it.added, it.updated, it.unchanged) }
    }
    fun restore(snapshot: io.github.mario2712.locus.backup.SafetyBackups.Snapshot) = run {
        safety.restore(snapshot)
        DataMessage.Restored(snapshot.createdAtMillis)
    }
    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch { settings.setBackup(backup.value.copy(autoBackup = enabled)) }
    fun dismiss() { message.value = null }
}

@Composable
fun DataScreen(onBack: () -> Unit, onOpenTrash: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<io.github.mario2712.locus.backup.SafetyBackups.Snapshot?>(null) }
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
            SectionCard(stringResource(R.string.trash_title)) {
                Text(
                    if (trashCount == 0) stringResource(R.string.trash_summary_empty)
                    else androidx.compose.ui.res.pluralStringResource(R.plurals.trash_summary, trashCount, trashCount),
                )
                OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trash_open)) }
                Text(stringResource(R.string.trash_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(stringResource(R.string.safety_title)) {
                if (snapshots.isEmpty()) Text(stringResource(R.string.safety_empty))
                snapshots.forEach { snap ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stampText(snap.createdAtMillis), style = MaterialTheme.typography.bodyLarge)
                            Text(reasonText(snap.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(enabled = !busy, onClick = { confirmRestore = snap }) { Text(stringResource(R.string.safety_restore)) }
                    }
                }
                Text(stringResource(R.string.safety_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CrashLogCard()

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

    confirmRestore?.let { snap ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.safety_restore_title)) },
            text = { Text(stringResource(R.string.safety_restore_confirm, stampText(snap.createdAtMillis))) },
            confirmButton = { TextButton(onClick = { viewModel.restore(snap); confirmRestore = null }) { Text(stringResource(R.string.safety_restore)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirmImport?.let { uri ->
        var withSettings by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(stringResource(R.string.data_import)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.data_import_confirm))
                    SwitchRow(stringResource(R.string.data_import_settings), withSettings) { withSettings = it }
                    Text(stringResource(R.string.data_import_settings_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.importFrom(uri, withSettings); confirmImport = null }) { Text(stringResource(R.string.data_import_go)) } },
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
                        is DataMessage.Restored -> stringResource(R.string.safety_restored, stampText(m.atMillis))
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

private fun stampText(millis: Long): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMAN).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun reasonText(reason: io.github.mario2712.locus.backup.SafetyBackups.Reason): String = stringResource(
    when (reason) {
        io.github.mario2712.locus.backup.SafetyBackups.Reason.IMPORT -> R.string.safety_reason_import
        io.github.mario2712.locus.backup.SafetyBackups.Reason.FILL -> R.string.safety_reason_fill
        io.github.mario2712.locus.backup.SafetyBackups.Reason.CLEANUP -> R.string.safety_reason_cleanup
        io.github.mario2712.locus.backup.SafetyBackups.Reason.RESTORE -> R.string.safety_reason_restore
    },
)

/** Fehlerprotokoll: Anzahl, Teilen (als Text, z. B. per Mail) und Löschen. */
@androidx.compose.runtime.Composable
private fun CrashLogCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var count by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(io.github.mario2712.locus.diagnostics.CrashLog.count(context)) }
    SectionCard(stringResource(R.string.crashlog_title)) {
        Text(
            if (count == 0) stringResource(R.string.crashlog_empty) else stringResource(R.string.crashlog_count, count),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.crashlog_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        if (count > 0) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Locus Fehlerprotokoll")
                        putExtra(android.content.Intent.EXTRA_TEXT, io.github.mario2712.locus.diagnostics.CrashLog.read(context))
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.crashlog_share)) }
                TextButton(onClick = { io.github.mario2712.locus.diagnostics.CrashLog.clear(context); count = 0 }) {
                    Text(stringResource(R.string.crashlog_clear))
                }
            }
        }
    }
}
