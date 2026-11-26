package io.github.mario2712.locus.ui.editor

import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Bearbeitungsstand eines Besuchs. */
data class VisitDraft(
    val id: String? = null,
    val date: LocalDate,
    val placeId: String? = null,
    val categoryIds: Set<String> = emptySet(),
    val participants: Set<PersonId> = setOf(Defaults.ME),
    /** `null` = Regel der Kategorien. */
    val countsOverride: Boolean? = null,
    val note: String = "",
    val time: LocalTime? = null,
    val source: EntrySource = EntrySource.MANUAL,
    /** Ursprünglicher Besuch beim Bearbeiten (übernimmt Felder, die der Dialog nicht zeigt). */
    val original: Visit? = null,
) {
    val isNew: Boolean get() = id == null
    val isValid: Boolean get() = placeId != null && categoryIds.isNotEmpty() && participants.isNotEmpty()

    fun toVisit(): Visit = original?.copy(
        placeId = requireNotNull(placeId),
        categoryId = categoryIds.first(),
        extraCategoryIds = categoryIds - categoryIds.first(),
        date = date,
        participants = participants,
        countsForCountryPointsOverride = countsOverride,
        note = note.trim().takeIf { it.isNotEmpty() },
    ) ?: Visit(
        id = id ?: UUID.randomUUID().toString(),
        placeId = requireNotNull(placeId),
        categoryId = categoryIds.first(),
        extraCategoryIds = categoryIds - categoryIds.first(),
        date = date,
        time = time,
        participants = participants,
        countsForCountryPointsOverride = countsOverride,
        note = note.trim().takeIf { it.isNotEmpty() },
        source = source,
    )

    companion object {
        fun from(visit: Visit) = VisitDraft(
            id = visit.id, date = visit.date, placeId = visit.placeId, categoryIds = visit.categoryIds,
            participants = visit.participants, countsOverride = visit.countsForCountryPointsOverride,
            note = visit.note.orEmpty(), time = visit.time, source = visit.source, original = visit,
        )
    }
}
