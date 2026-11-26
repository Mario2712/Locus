package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.mario2712.locus.BuildConfig
import java.time.LocalDate
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

    /**
     * Führt [block] aus; meldet er eine Änderung, wird die Meldung als rückgängig machbar markiert.
     * Gemerkt wird nur der Stand davor im Bereich [from]..[to]. Der Vergleich mit dem Stand danach
     * passiert erst beim Rückgängigmachen – so bleibt das Speichern selbst schnell.
     */
    suspend fun record(from: LocalDate, to: LocalDate, block: suspend () -> UiMessage): UiMessage = mutex.withLock {
        val started = System.nanoTime()
        val (beforeStays, beforeVisits) = coroutineScope {
            val s = async { stays.observeBetween(from, to).first() }
            val v = async { visits.observeBetween(from, to).first() }
            s.await().associateBy { it.id } to v.await().associateBy { it.id }
        }
        val snapshotDone = System.nanoTime()
        val message = block()
        val changed = when (message) {
            UiMessage.Saved, UiMessage.Deleted, is UiMessage.Conflict -> true
            is UiMessage.Filled -> message.count > 0
            else -> false
        }
        if (!changed) {
            undo = null
            return message
        }
        undo = {
            val afterStays = stays.observeBetween(from, to).first().associateBy { it.id }
            val afterVisits = visits.observeBetween(from, to).first().associateBy { it.id }
            (afterStays.keys - beforeStays.keys).forEach { stays.delete(it) }
            beforeStays.values.filter { afterStays[it.id] != it }.forEach { stays.upsert(it) }
            (afterVisits.keys - beforeVisits.keys).forEach { visits.delete(it) }
            beforeVisits.values.filter { afterVisits[it.id] != it }.forEach { visits.upsert(it) }
        }
        val timing = if (BuildConfig.DEBUG) {
            val ms = { a: Long, b: Long -> (b - a) / 1_000_000 }
            "${ms(started, System.nanoTime())} ms (Vorher-Stand ${ms(started, snapshotDone)} ms, Speichern ${ms(snapshotDone, System.nanoTime())} ms)"
        } else null
        UiMessage.Undoable(message, timing)
    }

    /** Macht die letzte Änderung rückgängig. @return `true`, wenn es etwas rückgängig zu machen gab. */
    suspend fun undo(): Boolean = mutex.withLock {
        val action = undo ?: return false
        undo = null
        action()
        true
    }
}
