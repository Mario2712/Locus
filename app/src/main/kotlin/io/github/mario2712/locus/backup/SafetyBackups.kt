package io.github.mario2712.locus.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mario2712.locus.data.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatische Sicherheitskopien vor größeren Aktionen (Import, langer Zeitraum füllen,
 * unbenutzte Orte löschen, Wiederherstellen). Liegen im privaten App-Speicher; die neuesten
 * [KEEP] bleiben erhalten. Einstellungen sind nicht enthalten, nur die Daten.
 */
@Singleton
class SafetyBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BackupService,
    private val clock: Clock,
) {
    enum class Reason { IMPORT, FILL, CLEANUP, RESTORE }

    data class Snapshot(val file: File, val createdAtMillis: Long, val reason: Reason)

    private val dir get() = File(context.filesDir, "safety").apply { mkdirs() }
    private val state = MutableStateFlow(scan())
    val snapshots: StateFlow<List<Snapshot>> = state.asStateFlow()

    private fun scan(): List<Snapshot> =
        (File(context.filesDir, "safety").listFiles() ?: emptyArray()).mapNotNull { f ->
            val parts = f.nameWithoutExtension.split('_')
            val millis = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val reason = parts.getOrNull(1)?.let { r -> Reason.entries.firstOrNull { it.name == r } } ?: return@mapNotNull null
            Snapshot(f, millis, reason)
        }.sortedByDescending { it.createdAtMillis }

    /** Legt eine Sicherheitskopie an; Fehler werden nicht weitergereicht, die Aktion soll nicht scheitern. */
    suspend fun create(reason: Reason): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val json = service.exportJson()
            val file = File(dir, "${clock.millis()}_${reason.name}.json")
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
            scan().drop(KEEP).forEach { it.file.delete() }
            state.value = scan()
            Snapshot(file, clock.millis(), reason)
        }.getOrNull()
    }

    /** Setzt alle Daten auf den Stand der Kopie zurück; vorher wird der aktuelle Stand gesichert. */
    suspend fun restore(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val file = service.parse(snapshot.file.readText())
        create(Reason.RESTORE)
        service.restore(file)
    }

    fun delete(snapshot: Snapshot) {
        snapshot.file.delete()
        state.value = scan()
    }

    companion object {
        const val KEEP = 5

        /** Ab dieser Länge legt „Zeitraum füllen“ vorher eine Sicherheitskopie an. */
        const val FILL_MIN_DAYS = 14
    }
}
