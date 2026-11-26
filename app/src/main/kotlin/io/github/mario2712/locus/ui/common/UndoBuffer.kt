package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * „Rückgängig“ für die letzte Änderung an Stationen und Besuchen: merkt sich den Stand davor und stellt ihn
 * auf Wunsch wieder her (neue Einträge weg, geänderte bzw. gelöschte wieder wie vorher). Nur ein Schritt.
 */
@Singleton
class UndoBuffer @Inject constructor(
    private val stays: StayRepository,
    private val visits: VisitRepository,
) {
    private val mutex = Mutex()
    private var undo: (suspend () -> Unit)? = null

    /** Führt [block] aus; gab es Änderungen, wird die Meldung als rückgängig machbar markiert. */
    suspend fun record(block: suspend () -> UiMessage): UiMessage = mutex.withLock {
        val beforeStays = stays.observeAll().first().associateBy { it.id }
        val beforeVisits = visits.observeAll().first().associateBy { it.id }
        val message = block()
        val afterStays = stays.observeAll().first().associateBy { it.id }
        val afterVisits = visits.observeAll().first().associateBy { it.id }
        val addedStays = afterStays.keys - beforeStays.keys
        val restoreStays = beforeStays.values.filter { afterStays[it.id] != it }
        val addedVisits = afterVisits.keys - beforeVisits.keys
        val restoreVisits = beforeVisits.values.filter { afterVisits[it.id] != it }
        if (addedStays.isEmpty() && restoreStays.isEmpty() && addedVisits.isEmpty() && restoreVisits.isEmpty()) {
            undo = null
            return message
        }
        undo = {
            addedStays.forEach { stays.delete(it) }
            restoreStays.forEach { stays.upsert(it) }
            addedVisits.forEach { visits.delete(it) }
            restoreVisits.forEach { visits.upsert(it) }
        }
        UiMessage.Undoable(message)
    }

    /** Macht die letzte Änderung rückgängig. @return `true`, wenn es etwas rückgängig zu machen gab. */
    suspend fun undo(): Boolean = mutex.withLock {
        val action = undo ?: return false
        undo = null
        action()
        true
    }
}
