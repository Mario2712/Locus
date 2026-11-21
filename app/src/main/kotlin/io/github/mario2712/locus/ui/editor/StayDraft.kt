package io.github.mario2712.locus.ui.editor

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Bearbeitungsstand eines Aufenthalts im Dialog. */
data class StayDraft(
    val id: String? = null,
    val typeId: String? = null,
    val placeId: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val participants: Set<PersonId> = setOf(Defaults.ME),
    val note: String = "",
    val sortIndex: Int = 0,
    val tripId: String? = null,
    val precision: DatePrecision = DatePrecision.DAY,
    val source: EntrySource = EntrySource.MANUAL,
) {
    val isNew: Boolean get() = id == null

    val timesValid: Boolean
        get() = endDate.isAfter(startDate) || startTime == null || endTime == null || endTime.isAfter(startTime)

    val isValid: Boolean
        get() = typeId != null && placeId != null && participants.isNotEmpty() &&
            !endDate.isBefore(startDate) && timesValid

    fun toStay(): Stay = Stay(
        id = id ?: UUID.randomUUID().toString(),
        placeId = requireNotNull(placeId),
        typeId = requireNotNull(typeId),
        startDate = startDate,
        endDate = endDate,
        startTime = startTime,
        endTime = endTime,
        precision = precision,
        sortIndex = sortIndex,
        participants = participants,
        tripId = tripId,
        note = note.trim().takeIf { it.isNotEmpty() },
        source = source,
    )

    companion object {
        fun from(stay: Stay) = StayDraft(
            id = stay.id, typeId = stay.typeId, placeId = stay.placeId,
            startDate = stay.startDate, endDate = stay.endDate,
            startTime = stay.startTime, endTime = stay.endTime,
            participants = stay.participants, note = stay.note.orEmpty(),
            sortIndex = stay.sortIndex, tripId = stay.tripId, precision = stay.precision,
            source = stay.source,
        )
    }
}
