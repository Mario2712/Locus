package io.github.mario2712.locus.data

import androidx.room.Room
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.RoomPersonRepository
import io.github.mario2712.locus.data.repository.RoomPlaceRepository
import io.github.mario2712.locus.data.repository.RoomStayRepository
import io.github.mario2712.locus.data.repository.RoomVisitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Baut eine Datenbank im Schema v1 (aus der eingecheckten Schema-Datei) mit echten Einträgen auf,
 * öffnet sie mit der aktuellen Room-Version und prüft, dass die Migration Daten erhält.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val schemaDir = File("schemas/io.github.mario2712.locus.data.db.LocusDatabase")

    @Test
    fun `v1 bis aktuell behaelt Aufenthalte, Orte, Besuche und Personen`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        val schema = JSONObject(File(schemaDir, "1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))

            db.execSQL(
                "INSERT INTO place (id, name, kind, country_iso2, region_code, latitude, longitude, city_name, " +
                    "parent_place_id, osm_id, poi_category, alias, is_favorite, note, created_at, updated_at, deleted, origin_device) " +
                    "VALUES ('p1', 'Hallein', 'CITY', 'AT', 'AT-5', NULL, NULL, NULL, NULL, NULL, NULL, 'Zuhause', 1, NULL, 1, 1, 0, 'dev')",
            )
            db.execSQL(
                "INSERT INTO stay (id, place_id, type_id, start_date, end_date, start_time, end_time, precision, " +
                    "sort_index, trip_id, note, source, created_at, updated_at, deleted, origin_device) " +
                    "VALUES ('s1', 'p1', 'stay-homeoffice', '2026-11-16', '2026-11-16', NULL, NULL, 'DAY', 0, NULL, NULL, 'MANUAL', 1, 1, 0, 'dev')",
            )
            db.execSQL("INSERT INTO stay_person (stay_id, person_id) VALUES ('s1', 'person-me')")
            db.execSQL(
                "INSERT INTO visit (id, place_id, category_id, date, time, precision, counts_override, trip_id, note, " +
                    "rating, photo_uri, source, created_at, updated_at, deleted, origin_device) " +
                    "VALUES ('v1', 'p1', 'visit-shopping', '2026-11-16', NULL, 'DAY', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 1, 1, 0, 'dev')",
            )
            db.execSQL("INSERT INTO visit_person (visit_id, person_id) VALUES ('v1', 'person-me')")
            db.execSQL(
                "INSERT INTO person (id, name, color_argb, is_me, created_at, updated_at, deleted, origin_device) " +
                    "VALUES ('person-me', 'Ich', 4280250204, 1, 1, 1, 0, 'dev')",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, LocusDatabase::class.java, name).allowMainThreadQueries().build()
        val meta = io.github.mario2712.locus.data.sync.SyncMetaFactory({ 2L }, { "dev" })
        try {
            val stay = RoomStayRepository(room.stayDao(), meta).get("s1")!!
            assertEquals("p1", stay.placeId)
            assertFalse(stay.overnight)
            assertTrue(stay.transport.isEmpty())

            val place = RoomPlaceRepository(room.placeDao(), meta).get("p1")!!
            assertEquals("Zuhause", place.alias)
            assertFalse(place.isHomebase)

            val visit = RoomVisitRepository(room.visitDao(), meta).get("v1")!!
            assertEquals(setOf("visit-shopping"), visit.categoryIds)

            assertTrue(room.dayTemplateDao().observeAll().first().isEmpty())

            val person = RoomPersonRepository(room.personDao(), meta).observeAll().first().single()
            assertEquals("Ich", person.name)
            assertEquals(null, person.birthDate)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
