package io.github.mario2712.locus.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mario2712.locus.data.backup.BackupService
import io.github.mario2712.locus.data.backup.ImportResult
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sicherungen: Export/Import über eine vom Nutzer gewählte Datei und automatische Sicherungen
 * im öffentlichen Ordner „Download/Locus“ (bleibt auch nach Deinstallation erhalten).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BackupService,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun suggestedFileName(): String =
        "locus-backup-" + LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".json"

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = service.exportJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            ?: error("Datei kann nicht geschrieben werden")
        markBackedUp()
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Datei kann nicht gelesen werden")
        service.importJson(text)
    }

    /** Schreibt eine Sicherung nach „Download/Locus“ und behält die neuesten [keep] Dateien. */
    suspend fun backupToDownloads(keep: Int = 5): String = withContext(Dispatchers.IO) {
        val json = service.exportJson()
        val name = suggestedFileName()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/$FOLDER/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Sicherung kann nicht angelegt werden")
        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        pruneOldBackups(keep)
        markBackedUp()
        name
    }

    private fun pruneOldBackups(keep: Int) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val ids = mutableListOf<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("Download/$FOLDER/", "locus-backup-%"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        ids.drop(keep).forEach { id -> resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null) }
    }

    private suspend fun markBackedUp() {
        val current = settings.observeBackup().first()
        settings.setBackup(current.copy(lastBackupEpochMillis = clock.millis()))
    }

    companion object {
        const val FOLDER = "Locus"
    }
}
