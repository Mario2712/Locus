package io.github.mario2712.locus.data.trash

import androidx.room.withTransaction
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.toDomain
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Ein gelöschter Eintrag im Papierkorb. */
sealed interface TrashItem {
    val id: String
    val placeId: String
    val deletedAtMillis: Long

    data class StayItem(val stay: Stay, override val deletedAtMillis: Long) : TrashItem {
        override val id get() = stay.id
        override val placeId get() = stay.placeId
    }

    data class VisitItem(val visit: Visit, override val deletedAtMillis: Long) : TrashItem {
        override val id get() = visit.id
        override val placeId get() = visit.placeId
    }
}

/**
 * Papierkorb: Stationen und Besuche werden nur als gelöscht markiert (Soft-Delete für den Sync)
 * und lassen sich daher wiederherstellen.
 */
class TrashStore @Inject constructor(
    private val db: LocusDatabase,
    private val meta: SyncMetaFactory,
) {
    private val dao get() = db.backupDao()

    /** Gelöschte Einträge der letzten [days] Tage, zuletzt gelöschte zuerst. */
    fun observe(days: Int = DAYS): Flow<List<TrashItem>> {
        val since = meta.now() - days * DAY_MILLIS
        return combine(dao.observeDeletedStays(since), dao.observeDeletedVisits(since)) { stays, visits ->
            (stays.map { TrashItem.StayItem(it.toDomain(), it.stay.meta.updatedAt) } +
                visits.map { TrashItem.VisitItem(it.toDomain(), it.visit.meta.updatedAt) })
                .sortedByDescending { it.deletedAtMillis }
        }
    }

    /** Stellt einen Eintrag wieder her – samt seinem Ort, falls der inzwischen aufgeräumt wurde. */
    suspend fun restore(item: TrashItem) = db.withTransaction {
        val now = meta.now()
        val device = meta.deviceId()
        when (item) {
            is TrashItem.StayItem -> dao.undeleteStay(item.id, now, device)
            is TrashItem.VisitItem -> dao.undeleteVisit(item.id, now, device)
        }
        dao.undeletePlace(item.placeId, now, device)
    }

    companion object {
        const val DAYS = 30
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
