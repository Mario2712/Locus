package io.github.mario2712.locus.ui.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GERMAN = Locale.GERMAN

object Format {
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
    private val mediumDate = DateTimeFormatter.ofPattern("EE, d. MMM", GERMAN)
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.", GERMAN)
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", GERMAN)
    private val time = DateTimeFormatter.ofPattern("HH:mm", GERMAN)

    fun longDate(date: LocalDate): String = longDate.format(date)
    fun mediumDate(date: LocalDate): String = mediumDate.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun month(date: LocalDate): String = month.format(date).replaceFirstChar { it.titlecase(GERMAN) }
    fun time(value: LocalTime): String = time.format(value)

    /** z. B. „08:00–12:00“, „ab 12:00“, „bis 13:00“ oder leer für ganztägig. */
    fun timeRange(start: LocalTime?, end: LocalTime?): String = when {
        start != null && end != null -> "${time(start)}–${time(end)}"
        start != null -> "ab ${time(start)}"
        end != null -> "bis ${time(end)}"
        else -> ""
    }
}
