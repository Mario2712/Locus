package io.github.mario2712.locus.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.mario2712.locus.domain.model.BackupSettings
import io.github.mario2712.locus.domain.model.EntryDefaults
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject

/** Gerätelokale Einstellungen (nicht Teil des Syncs). */
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeReminder(): Flow<ReminderSettings> = store.data
        .map { prefs ->
            val defaults = ReminderSettings()
            ReminderSettings(
                enabled = prefs[REMINDER_ENABLED] ?: defaults.enabled,
                time = prefs[REMINDER_MINUTES]?.let { LocalTime.of(it / 60, it % 60) } ?: defaults.time,
            )
        }
        .distinctUntilChanged()

    override suspend fun setReminder(settings: ReminderSettings) {
        store.edit { prefs ->
            prefs[REMINDER_ENABLED] = settings.enabled
            prefs[REMINDER_MINUTES] = settings.time.hour * 60 + settings.time.minute
        }
    }

    override fun observeEntryDefaults(): Flow<EntryDefaults> = store.data
        .map { prefs ->
            val defaults = EntryDefaults()
            EntryDefaults(
                prefillTransport = prefs[PREFILL_TRANSPORT] ?: defaults.prefillTransport,
                dailyParticipants = prefs[DAILY_PARTICIPANTS]?.mapTo(linkedSetOf(), ::PersonId)
                    ?.takeIf { it.isNotEmpty() } ?: defaults.dailyParticipants,
                tripParticipants = prefs[TRIP_PARTICIPANTS]?.mapTo(linkedSetOf(), ::PersonId)
                    ?.takeIf { it.isNotEmpty() } ?: defaults.tripParticipants,
                documentedSinceYear = prefs[DOCUMENTED_SINCE] ?: defaults.documentedSinceYear,
                sortByUsage = prefs[SORT_BY_USAGE] ?: defaults.sortByUsage,
                sharedStayTypeIds = prefs[SHARED_STAY_TYPES] ?: defaults.sharedStayTypeIds,
            )
        }
        .distinctUntilChanged()

    override suspend fun setEntryDefaults(defaults: EntryDefaults) {
        store.edit { prefs ->
            prefs[PREFILL_TRANSPORT] = defaults.prefillTransport
            prefs[DAILY_PARTICIPANTS] = defaults.dailyParticipants.mapTo(linkedSetOf()) { it.value }
            prefs[TRIP_PARTICIPANTS] = defaults.tripParticipants.mapTo(linkedSetOf()) { it.value }
            prefs[DOCUMENTED_SINCE] = defaults.documentedSinceYear
            prefs[SORT_BY_USAGE] = defaults.sortByUsage
            prefs[SHARED_STAY_TYPES] = defaults.sharedStayTypeIds
        }
    }

    override suspend fun isMigrationDone(key: String): Boolean =
        store.data.first()[MIGRATIONS_DONE]?.contains(key) == true

    override suspend fun setMigrationDone(key: String) {
        store.edit { prefs -> prefs[MIGRATIONS_DONE] = prefs[MIGRATIONS_DONE].orEmpty() + key }
    }

    override fun observeBackup(): Flow<BackupSettings> = store.data
        .map { prefs -> BackupSettings(prefs[AUTO_BACKUP] ?: true, prefs[LAST_BACKUP]) }
        .distinctUntilChanged()

    override suspend fun setBackup(settings: BackupSettings) {
        store.edit { prefs ->
            prefs[AUTO_BACKUP] = settings.autoBackup
            settings.lastBackupEpochMillis?.let { prefs[LAST_BACKUP] = it } ?: prefs.remove(LAST_BACKUP)
        }
    }

    private companion object {
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_BACKUP = longPreferencesKey("last_backup")
        val PREFILL_TRANSPORT = booleanPreferencesKey("prefill_transport")
        val DAILY_PARTICIPANTS = stringSetPreferencesKey("daily_participants")
        val TRIP_PARTICIPANTS = stringSetPreferencesKey("trip_participants")
        val DOCUMENTED_SINCE = intPreferencesKey("documented_since_year")
        val SORT_BY_USAGE = booleanPreferencesKey("sort_by_usage")
        val SHARED_STAY_TYPES = stringSetPreferencesKey("shared_stay_types")
        val MIGRATIONS_DONE = stringSetPreferencesKey("migrations_done")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }
}
