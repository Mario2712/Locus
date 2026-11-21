package io.github.mario2712.locus.domain.validation

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate
import java.time.LocalTime

/** Zwei Aufenthalte einer Person, die sich zeitlich widersprechen. */
data class StayConflict(
    val person: PersonId,
    val date: LocalDate,
    val other: Stay,
)

/**
 * Prüft, ob sich ein Aufenthalt mit bestehenden Aufenthalten derselben Person überschneidet.
 * Ergebnis ist eine **Warnung**, keine Sperre.
 *
 * Bewusst nachsichtig: Fehlt an einem Tag eine Uhrzeit, gilt der Wechsel als „irgendwann“ und
 * erzeugt keinen Konflikt (z. B. „Büro“ und „Homeoffice“ am selben Tag ohne Uhrzeiten).
 * Konflikte entstehen nur bei eindeutig bestimmbaren, sich überschneidenden Zeitfenstern,
 * etwa bei zwei mehrtägigen Urlauben an verschiedenen Orten.
 */
class StayOverlapChecker {

    fun conflicts(candidate: Stay, existing: List<Stay>): List<StayConflict> {
        if (candidate.precision != DatePrecision.DAY) return emptyList()
        return existing
            .asSequence()
            .filter { it.id != candidate.id && it.precision == DatePrecision.DAY }
            .flatMap { other ->
                val shared = candidate.participants intersect other.participants
                if (shared.isEmpty()) return@flatMap emptySequence()
                val from = maxOf(candidate.startDate, other.startDate)
                val to = minOf(candidate.endDate, other.endDate)
                if (from > to) return@flatMap emptySequence()
                val firstConflict = generateSequence(from) { it.plusDays(1) }
                    .takeWhile { it <= to }
                    .firstOrNull { day -> windowsOverlap(candidate.window(day), other.window(day)) }
                    ?: return@flatMap emptySequence()
                shared.asSequence().map { StayConflict(it, firstConflict, other) }
            }
            .toList()
    }

    /** Zeitfenster in Minuten an [day], oder `null`, wenn eine Grenze unbekannt ist. */
    private fun Stay.window(day: LocalDate): IntRange? {
        val start = if (startDate < day) 0 else startTime?.minutes() ?: return null
        val end = if (endDate > day) MINUTES_PER_DAY else endTime?.minutes() ?: return null
        return start until end
    }

    private fun windowsOverlap(a: IntRange?, b: IntRange?): Boolean =
        a != null && b != null && !a.isEmpty() && !b.isEmpty() && a.first <= b.last && b.first <= a.last

    private fun LocalTime.minutes(): Int = hour * 60 + minute

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
