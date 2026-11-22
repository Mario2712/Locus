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

    private val seeder by lazy { seeder() }
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
    fun `Gipfel wird archiviert und Besuche auf Natur umgestellt - nur einmal`() = runTest {
        seeder.seed()
        val visits = io.github.mario2712.locus.data.repository.RoomVisitRepository(db.visitDao(), meta)
        val places = io.github.mario2712.locus.data.repository.RoomPlaceRepository(db.placeDao(), meta)
        places.upsert(io.github.mario2712.locus.domain.model.Place("p", "Untersberg", io.github.mario2712.locus.domain.model.PlaceKind.POI, "AT"))
        val summit = Defaults.visitCategories.first { it.id == Defaults.VisitCategoryIds.SUMMIT }
        catalog.upsertVisitCategory(summit.copy(isArchived = false))
        visits.upsert(io.github.mario2712.locus.domain.model.Visit("v", "p", summit.id, java.time.LocalDate.parse("2024-06-01"), participants = setOf(Defaults.ME)))
        // Bestehende Installation vor dem Update: Umstellung noch nicht gelaufen.
        DatabaseSeeder(db, meta, InMemorySettings(), catalog, visits).seed()
        assertEquals(Defaults.VisitCategoryIds.NATURE, visits.get("v")!!.categoryId)
        assertEquals(true, catalog.observeVisitCategories().first().first { it.id == summit.id }.isArchived)
    }

    @Test
    fun `Geburtsdatum wird gespeichert und vom Seeder nicht ueberschrieben`() = runTest {
        seeder.seed()
        val me = Defaults.persons.first().copy(birthDate = java.time.LocalDate.parse("1996-05-10"))
        persons.upsert(me)
        seeder.seed()
        assertEquals(java.time.LocalDate.parse("1996-05-10"), persons.observeAll().first().first { it.isMe }.birthDate)
    }

    @Test
    fun `alte Standardfarben werden umgestellt, eigene Farben bleiben`() = runTest {
        seeder.seed()
        val types = Defaults.stayTypes.associateBy { it.id }
        catalog.upsertStayType(types.getValue(Defaults.StayTypeIds.HOMEOFFICE).copy(colorArgb = 0xFF5C6BC0))
        catalog.upsertStayType(types.getValue(Defaults.StayTypeIds.OFFICE).copy(colorArgb = 0xFF123456))
        seeder.seed()
        val after = catalog.observeStayTypes().first().associateBy { it.id }
        assertEquals(0xFF2A78D6, after.getValue(Defaults.StayTypeIds.HOMEOFFICE).colorArgb)
        assertEquals(0xFF123456, after.getValue(Defaults.StayTypeIds.OFFICE).colorArgb)
    }
}
