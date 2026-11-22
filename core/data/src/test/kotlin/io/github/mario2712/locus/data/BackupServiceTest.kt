package io.github.mario2712.locus.data

import androidx.room.Room
import io.github.mario2712.locus.data.backup.BackupService
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.RoomDayTemplateRepository
import io.github.mario2712.locus.data.repository.RoomPersonRepository
import io.github.mario2712.locus.data.repository.RoomPlaceRepository
import io.github.mario2712.locus.data.repository.RoomStayRepository
import io.github.mario2712.locus.data.repository.RoomVisitRepository
import io.github.mario2712.locus.data.seed.DatabaseSeeder
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BackupServiceTest : DbTestBase() {

    private var otherNow = 1_000L
    private val other: LocusDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LocusDatabase::class.java)
        .allowMainThreadQueries().build()
    private val otherMeta = SyncMetaFactory({ otherNow }, { "other-device" })

    @After fun closeOther() = other.close()

    private fun d(s: String) = LocalDate.parse(s)

    private suspend fun fill() {
        seeder().seed()
        RoomPersonRepository(db.personDao(), meta).upsert(Defaults.persons.first().copy(birthDate = d("1996-05-10")))
        RoomPlaceRepository(db.placeDao(), meta).apply {
            upsert(Place("hallein", "Hallein", PlaceKind.CITY, "AT", latitude = 47.68, longitude = 13.09, alias = "Zuhause"))
            setHomebase("hallein")
            upsert(Place("museum", "Museum", PlaceKind.POI, "AT", parentPlaceId = "hallein", poiCategory = "museum"))
        }
        RoomStayRepository(db.stayDao(), meta).upsert(
            Stay("s1", "hallein", StayTypeIds.HOMEOFFICE, d("2026-11-16"), startTime = LocalTime.of(8, 5),
                participants = setOf(Defaults.ME, Defaults.JOHANNA), overnight = true, transport = setOf(TransportMode.TRAIN)),
        )
        RoomVisitRepository(db.visitDao(), meta).upsert(
            Visit("v1", "museum", Defaults.VisitCategoryIds.MUSEUM, d("2026-11-17"), participants = setOf(Defaults.ME),
                extraCategoryIds = setOf(Defaults.VisitCategoryIds.RESTAURANT), countsForCountryPointsOverride = true),
        )
        RoomDayTemplateRepository(db.dayTemplateDao(), meta).upsert(
            DayTemplate("t1", "Bürotag", listOf(TemplateStep(StayTypeIds.OFFICE, "hallein", setOf(TransportMode.TRAIN)))),
        )
    }

    @Test
    fun `Export und Import ergeben dieselben Daten auf einem anderen Geraet`() = runTest {
        fill()
        val json = BackupService(db, meta).exportJson()
        val result = BackupService(other, otherMeta).importJson(json)

        assertEquals(0, result.updated)
        assertEquals(BackupService(db, meta).export().copy(exportedAt = 0, device = ""),
            BackupService(other, otherMeta).export().copy(exportedAt = 0, device = ""))
        val stay = RoomStayRepository(other.stayDao(), otherMeta).get("s1")!!
        assertEquals(setOf(TransportMode.TRAIN), stay.transport)
        assertEquals(d("1996-05-10"), RoomPersonRepository(other.personDao(), otherMeta).observeAll().first().first { it.isMe }.birthDate)
        assertEquals("hallein", RoomPlaceRepository(other.placeDao(), otherMeta).observeAll().first().single { it.isHomebase }.id)
    }

    @Test
    fun `Zusammenfuehren - neuerer Stand gewinnt, Loeschungen werden uebernommen`() = runTest {
        fill()
        BackupService(other, otherMeta).importJson(BackupService(db, meta).exportJson())

        // Auf dem anderen Gerät später geändert → bleibt beim erneuten Import des alten Stands
        otherNow = 50_000L
        RoomPlaceRepository(other.placeDao(), otherMeta).upsert(Place("hallein", "Hallein", PlaceKind.CITY, "AT", alias = "Daheim"))
        // Auf dem ersten Gerät noch später gelöscht → wird übernommen
        now = 60_000L
        RoomStayRepository(db.stayDao(), meta).delete("s1")

        val result = BackupService(other, otherMeta).importJson(BackupService(db, meta).exportJson())
        assertEquals(1, result.updated)
        assertEquals("Daheim", RoomPlaceRepository(other.placeDao(), otherMeta).get("hallein")!!.alias)
        assertNull(RoomStayRepository(other.stayDao(), otherMeta).get("s1"))
    }

    @Test
    fun `fremde Datei wird abgelehnt, Schema-Version passt zur Datenbank`() = runTest {
        assertThrows(IllegalArgumentException::class.java) { BackupService(db, meta).parse("""{"hello":"world"}""") }
        assertEquals(BackupService.SCHEMA_VERSION, db.openHelper.readableDatabase.version)
    }
}
