package io.github.mario2712.locus.data.seed

import androidx.room.withTransaction
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.toEntity
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.reference.Defaults
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
) {
    suspend fun seed() {
        val m = meta.forSave(existingCreatedAt = null)
        db.withTransaction {
            db.personDao().insertIfMissing(Defaults.persons.map { it.toEntity(m) })
            db.catalogDao().apply {
                insertStayTypesIfMissing(Defaults.stayTypes.map { it.toEntity(m) })
                insertVisitCategoriesIfMissing(Defaults.visitCategories.map { it.toEntity(m) })
                insertRegionCollectionsIfMissing(Defaults.regionCollections.map { it.toEntity(m) })
            }
            // Einmalige Übernahme: Ort mit Kurzname „Zuhause“ wird Homebase, falls noch keine gesetzt ist.
            db.placeDao().apply {
                if (homebaseCount() == 0) {
                    findByAlias("Zuhause").singleOrNull()?.let { setHomebase(it.id, meta.now(), meta.deviceId()) }
                }
            }
        }
    }
}
