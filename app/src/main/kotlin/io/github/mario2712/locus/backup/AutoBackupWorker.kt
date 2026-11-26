package io.github.mario2712.locus.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Wöchentliche automatische Sicherung nach „Download/Locus“. */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.observeBackup().first().autoBackup) return Result.success()
        return runCatching { backups.backupToDownloads() }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "auto-backup"

        fun apply(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (enabled) {
                manager.enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build(),
                )
            } else {
                manager.cancelUniqueWork(NAME)
            }
        }
    }
}
