package io.github.mario2712.locus.diagnostics

import android.content.Context
import android.os.Build
import io.github.mario2712.locus.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Lokales Fehlerprotokoll: Abstürze landen in einer Datei im App-Speicher (kein externer Dienst).
 * Enthält nur App-Version, Android-Version, Gerätemodell und den technischen Fehler – keine Einträge oder Orte.
 */
object CrashLog {
    private const val FILE = "crash-log.txt"
    private const val MAX_BYTES = 64 * 1024

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Fängt unbehandelte Ausnahmen ab, schreibt sie ins Protokoll und reicht sie weiter (App beendet sich wie gewohnt). */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, error, "Absturz in Thread ${thread.name}") }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Auch für abgefangene, aber unerwartete Fehler nutzbar. */
    fun record(context: Context, error: Throwable, title: String = "Fehler") {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val entry = buildString {
            appendLine("=== ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} · $title")
            appendLine("Locus ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(trace)
        }
        val f = file(context)
        val old = if (f.exists()) f.readText() else ""
        // Neueste Einträge zuerst; ältere fallen hinten raus, sobald die Datei zu groß wird.
        f.writeText((entry + old).take(MAX_BYTES))
    }

    fun read(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()

    fun count(context: Context): Int = Regex("(?m)^=== ").findAll(read(context)).count()

    fun clear(context: Context) {
        file(context).delete()
    }
}
