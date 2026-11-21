package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.day.DayClassifier
import io.github.mario2712.locus.domain.day.DaySummary
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import java.time.LocalDate

/** Kombination aus Art und Ort für den Schnell-Check-in, z. B. „Büro · Salzburg“. */
data class StayCombo(val stayTypeId: String, val placeId: String)

/** Auswertungen für Heute- und Kalender-Screen. */
object DayInsights {

    private val classifier = DayClassifier()

    fun staysOf(person: PersonId, stays: List<Stay>): List<Stay> = stays.filter { person in it.participants }

    /** Tage im Zeitraum, an denen die Person keinen (tagesgenauen) Aufenthalt hat. */
    fun missingDays(from: LocalDate, to: LocalDate, person: PersonId, stays: List<Stay>): List<LocalDate> {
        val own = staysOf(person, stays).filter { it.precision == DatePrecision.DAY }
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { day -> own.none { it.covers(day) } }
            .toList()
    }

    /** Zuletzt verwendete, unterschiedliche Kombinationen – neueste zuerst. */
    fun recentCombos(person: PersonId, stays: List<Stay>, limit: Int = 4): List<StayCombo> =
        staysOf(person, stays)
            .filter { it.precision == DatePrecision.DAY }
            .sortedWith(compareByDescending<Stay> { it.endDate }.thenByDescending { it.sortIndex })
            .map { StayCombo(it.typeId, it.placeId) }
            .distinct()
            .take(limit)

    /** Tageszusammenfassungen je Datum für eine Person (nur Tage mit Einträgen). */
    fun summaries(from: LocalDate, to: LocalDate, person: PersonId, stays: List<Stay>): Map<LocalDate, DaySummary> {
        val own = staysOf(person, stays)
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .mapNotNull { day -> classifier.classify(day, own)?.let { day to it } }
            .toMap()
    }
}
