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
}
