package io.github.mario2712.locus.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contentBuilder: ReminderContentBuilder,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val current = settings.observeReminder().first()
        if (current.enabled) {
            contentBuilder.build(LocalDate.now(clock))?.let(notifier::show)
        }
        // Zuletzt: nächste Erinnerung planen (ersetzt diese Arbeit).
        scheduler.apply(current)
        return Result.success()
    }
}
