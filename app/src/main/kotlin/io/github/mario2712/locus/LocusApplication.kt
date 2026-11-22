package io.github.mario2712.locus

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.mario2712.locus.backup.AutoBackupWorker
import io.github.mario2712.locus.data.seed.DatabaseSeeder
import io.github.mario2712.locus.di.ApplicationScope
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.reminder.ReminderNotifier
import io.github.mario2712.locus.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LocusApplication : Application(), Configuration.Provider {

    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var placeCleanup: io.github.mario2712.locus.places.PlaceCleanup
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var reminderNotifier: ReminderNotifier
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.createChannel()
        // Standarddaten anlegen; die UI beobachtet Flows und aktualisiert sich danach automatisch.
        appScope.launch {
            seeder.seed()
            // Orte, die mindestens 7 Tage alt sind und nirgends vorkommen, still entfernen (weich gelöscht, sync-fähig).
            runCatching { placeCleanup.deleteUnused(minAgeMillis = io.github.mario2712.locus.places.PlaceCleanup.AUTO_MIN_AGE_MILLIS) }
        }
        // Erinnerung bei jeder Änderung der Einstellungen (und beim Start) neu planen.
        appScope.launch { settings.observeReminder().collect(reminderScheduler::apply) }
        // Wöchentliche Sicherung nach Download/Locus, solange aktiviert.
        appScope.launch {
            settings.observeBackup().collect { AutoBackupWorker.apply(this@LocusApplication, it.autoBackup) }
        }
    }
}
