package io.github.mario2712.locus.data

import androidx.room.Room
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment

/** In-Memory-Datenbank mit steuerbarer Uhr für Repository-Tests. */
abstract class DbTestBase {
    protected lateinit var db: LocusDatabase
    protected var now = 1_000L
    protected val meta = SyncMetaFactory(time = { now }, device = { "test-device" })

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LocusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    protected val settings = InMemorySettings()

    protected fun seeder() = io.github.mario2712.locus.data.seed.DatabaseSeeder(
        db, meta, settings,
        io.github.mario2712.locus.data.repository.RoomCatalogRepository(db.catalogDao(), meta),
        io.github.mario2712.locus.data.repository.RoomVisitRepository(db.visitDao(), meta),
    )
}

/** Einstellungen im Speicher (nur was der Seeder braucht, Rest mit Standardwerten). */
class InMemorySettings : io.github.mario2712.locus.domain.repository.SettingsRepository {
    private val reminder = kotlinx.coroutines.flow.MutableStateFlow(io.github.mario2712.locus.domain.model.ReminderSettings())
    private val defaults = kotlinx.coroutines.flow.MutableStateFlow(io.github.mario2712.locus.domain.model.EntryDefaults())
    private val backup = kotlinx.coroutines.flow.MutableStateFlow(io.github.mario2712.locus.domain.model.BackupSettings())
    private val done = mutableSetOf<String>()
    override fun observeReminder() = reminder
    override suspend fun setReminder(settings: io.github.mario2712.locus.domain.model.ReminderSettings) { reminder.value = settings }
    override fun observeEntryDefaults() = defaults
    override suspend fun setEntryDefaults(defaults: io.github.mario2712.locus.domain.model.EntryDefaults) { this.defaults.value = defaults }
    override fun observeBackup() = backup
    override suspend fun setBackup(settings: io.github.mario2712.locus.domain.model.BackupSettings) { backup.value = settings }
    override suspend fun isMigrationDone(key: String) = key in done
    override suspend fun setMigrationDone(key: String) { done += key }
}
