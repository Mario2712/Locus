package io.github.mario2712.locus.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.mario2712.locus.data.settings.DataStoreSettingsRepository
import io.github.mario2712.locus.domain.model.EntryDefaults
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.reference.Defaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `Standard ist 20 Uhr 30 aktiv, Aenderungen werden gespeichert`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(tmp.root, "settings.preferences_pb")
        }
        val repo = DataStoreSettingsRepository(store)

        assertEquals(ReminderSettings(true, LocalTime.of(20, 30)), repo.observeReminder().first())

        repo.setReminder(ReminderSettings(enabled = false, time = LocalTime.of(21, 15)))
        assertEquals(ReminderSettings(false, LocalTime.of(21, 15)), repo.observeReminder().first())

        assertEquals(EntryDefaults(), repo.observeEntryDefaults().first())
        val custom = EntryDefaults(prefillTransport = false, dailyParticipants = setOf(Defaults.ME, Defaults.JOHANNA))
        repo.setEntryDefaults(custom)
        assertEquals(custom, repo.observeEntryDefaults().first())
    }
}
