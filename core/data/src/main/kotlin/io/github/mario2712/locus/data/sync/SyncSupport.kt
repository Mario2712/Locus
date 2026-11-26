package io.github.mario2712.locus.data.sync

import android.content.Context
import io.github.mario2712.locus.data.db.SyncMeta
import java.util.UUID

/** Zeitquelle, in Tests austauschbar. */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/** Eindeutige, zufällige ID dieser Installation – Basis für `originDevice` im Sync. */
fun interface DeviceIdProvider {
    fun deviceId(): String
}

class SharedPrefsDeviceIdProvider(context: Context) : DeviceIdProvider {
    private val prefs = context.getSharedPreferences("locus_device", Context.MODE_PRIVATE)

    @Synchronized
    override fun deviceId(): String =
        prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }

    private companion object {
        const val KEY = "device_id"
    }
}

/** Erzeugt Sync-Metadaten beim Speichern; `createdAt` bleibt bei Updates erhalten. */
class SyncMetaFactory(
    private val time: TimeProvider,
    private val device: DeviceIdProvider,
) {
    fun forSave(existingCreatedAt: Long?): SyncMeta {
        val now = time.nowMillis()
        return SyncMeta(
            createdAt = existingCreatedAt ?: now,
            updatedAt = now,
            deleted = false,
            originDevice = device.deviceId(),
        )
    }

    fun now(): Long = time.nowMillis()

    fun deviceId(): String = device.deviceId()
}
