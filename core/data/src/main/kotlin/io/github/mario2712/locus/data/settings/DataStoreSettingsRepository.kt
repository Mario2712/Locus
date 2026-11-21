package io.github.mario2712.locus.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private companion object {
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }
}
