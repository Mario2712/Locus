package io.github.mario2712.locus.data.seed

import androidx.room.withTransaction
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.toEntity
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.usecase.CatalogOrder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Legt die vorbelegten Daten an (Personen, Aufenthaltsarten, Besuchskategorien, Regionen).
 *
 * Idempotent und schonend: Es werden nur **fehlende** Datensätze eingefügt. Vom Nutzer geänderte
 * oder gelöschte Einträge bleiben unangetastet; neue Standardwerte aus App-Updates kommen hinzu.
 */
class DatabaseSeeder @Inject constructor(
    private val db: LocusDatabase,
    private val meta: SyncMetaFactory,
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val visits: VisitRepository,
) {
    suspend fun seed() {
        val m = meta.forSave(existingCreatedAt = null)
        db.withTransaction {
            db.personDao().insertIfMissing(Defaults.persons.map { it.toEntity(m) })
            db.catalogDao().apply {
                insertStayTypesIfMissing(Defaults.stayTypes.map { it.toEntity(m) })
                insertVisitCategoriesIfMissing(Defaults.visitCategories.map { it.toEntity(m) })
                insertRegionCollectionsIfMissing(Defaults.regionCollections.map { it.toEntity(m) })
                // Alte Standardfarben auf die geprüfte Palette umstellen (selbst gewählte Farben bleiben).
                Defaults.legacyStayTypeColors.forEach { (id, colors) ->
                    recolorStayType(id, colors.first, colors.second, meta.now(), meta.deviceId())
                }
            }
            // Einmalige Übernahme: Ort mit Kurzname „Zuhause“ wird Homebase, falls noch keine gesetzt ist.
            db.placeDao().apply {
                if (homebaseCount() == 0) {
                    findByAlias("Zuhause").singleOrNull()?.let { setHomebase(it.id, meta.now(), meta.deviceId()) }
                }
            }
        }
        migrateSummitToNature()
    }

    /** Einmalig: „Gipfel“ archivieren, Besuche auf „Natur / Wandern“ umstellen. */
    private suspend fun migrateSummitToNature() {
        if (settings.isMigrationDone(MIGRATION_SUMMIT)) return
        val from = Defaults.VisitCategoryIds.SUMMIT
        val to = Defaults.VisitCategoryIds.NATURE
        visits.observeAll().first().forEach { visit ->
            CatalogOrder.replaceCategory(visit, from, to)?.let { visits.upsert(it) }
        }
        catalog.observeVisitCategories().first().firstOrNull { it.id == from && !it.isArchived }
            ?.let { catalog.upsertVisitCategory(it.copy(isArchived = true)) }
        settings.setMigrationDone(MIGRATION_SUMMIT)
    }

    private companion object {
        const val MIGRATION_SUMMIT = "summit-to-nature"
    }
}
