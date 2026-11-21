package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.repository.RoomCatalogRepository
import io.github.mario2712.locus.data.repository.RoomPersonRepository
import io.github.mario2712.locus.data.seed.DatabaseSeeder
import io.github.mario2712.locus.domain.reference.Defaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseSeederTest : DbTestBase() {

    private val seeder by lazy { DatabaseSeeder(db, meta) }
    private val catalog by lazy { RoomCatalogRepository(db.catalogDao(), meta) }
    private val persons by lazy { RoomPersonRepository(db.personDao(), meta) }

    @Test
    fun `legt Standarddaten an`() = runTest {
        seeder.seed()
        assertEquals(Defaults.persons, persons.observeAll().first())
        assertEquals(Defaults.stayTypes, catalog.observeStayTypes().first())
        assertEquals(Defaults.visitCategories, catalog.observeVisitCategories().first())
        assertEquals(Defaults.regionCollections, catalog.observeRegionCollections().first())
    }

    @Test
    fun `ist idempotent und ueberschreibt keine Nutzeraenderungen`() = runTest {
        seeder.seed()
        val renamed = Defaults.stayTypes.first().copy(name = "Telearbeit")
        catalog.upsertStayType(renamed)

        seeder.seed()

        val types = catalog.observeStayTypes().first()
        assertEquals(Defaults.stayTypes.size, types.size)
        assertEquals("Telearbeit", types.first().name)
        assertEquals(Defaults.persons.size, persons.observeAll().first().size)
    }

    @Test
    fun `ergaenzt fehlende Standardwerte`() = runTest {
        seeder.seed()
        db.openHelper.writableDatabase.execSQL("DELETE FROM visit_category WHERE id = 'visit-summit'")

        seeder.seed()

        assertEquals(Defaults.visitCategories, catalog.observeVisitCategories().first())
    }

    @Test
    fun `Geburtsdatum wird gespeichert und vom Seeder nicht ueberschrieben`() = runTest {
        seeder.seed()
        val me = Defaults.persons.first().copy(birthDate = java.time.LocalDate.parse("1996-05-10"))
        persons.upsert(me)
        seeder.seed()
        assertEquals(java.time.LocalDate.parse("1996-05-10"), persons.observeAll().first().first { it.isMe }.birthDate)
    }
}
