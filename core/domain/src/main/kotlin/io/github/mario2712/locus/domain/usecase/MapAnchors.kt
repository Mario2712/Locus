package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import java.time.LocalDate

/**
 * Wo Karte und Ortssuche für einen neuen Eintrag ansetzen: zuerst die zuletzt erfassten Einträge des Tages
 * (Station oder Besuch), dann der Ausgangspunkt (Unterkunft bzw. Homebase).
 */
object MapAnchors {
    fun forDay(
        date: LocalDate,
        person: PersonId,
        stays: List<Stay>,
        visits: List<Visit>,
        basePlaceId: String?,
        homePlaceId: String?,
    ): List<String> {
        data class Entry(val placeId: String, val created: Long?, val order: Int)
        val entries = stays.filter { person in it.participants && it.startDate == date }.map { Entry(it.placeId, it.createdAtMillis, it.sortIndex) } +
            visits.filter { person in it.participants && it.date == date }.mapIndexed { i, v -> Entry(v.placeId, v.createdAtMillis, 1_000 + i) }
        val newestFirst = entries.sortedWith(compareByDescending<Entry> { it.created ?: Long.MIN_VALUE }.thenByDescending { it.order })
        return (newestFirst.map { it.placeId } + listOfNotNull(basePlaceId, homePlaceId)).distinct()
    }
}
