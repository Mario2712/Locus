package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.usecase.JourneyLeg
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.domain.usecase.VisitCommands
import io.github.mario2712.locus.domain.validation.StayOverlapChecker
import io.github.mario2712.locus.ui.editor.FillDraft
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.VisitDraft
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Rückmeldungen an die UI (werden im Screen in Texte übersetzt). */
sealed interface UiMessage {
    data class Conflict(val date: LocalDate) : UiMessage
    data class Filled(val count: Int) : UiMessage
    data object Saved : UiMessage
    data object Deleted : UiMessage
    data object DayNotEmpty : UiMessage
}

/** Gemeinsame Schreib-Abläufe der Screens (Stationen, Reisen, Besuche) inkl. Warnungen. */
class StationActions @Inject constructor(
    private val stays: StayRepository,
    private val visits: VisitRepository,
    private val commands: StayCommands,
    private val visitCommands: VisitCommands,
    private val overlap: StayOverlapChecker,
    private val trips: io.github.mario2712.locus.domain.repository.TripRepository,
) {
    suspend fun saveStation(draft: StayDraft): UiMessage {
        val prepared = if (draft.isNew) {
            draft.copy(sortIndex = commands.nextSortIndex(draft.startDate, draft.participants.first()))
        } else {
            draft
        }
        val stay = prepared.toStay()
        stays.upsert(stay)
        return conflictOrSaved(stay)
    }

    suspend fun deleteStation(id: String): UiMessage {
        stays.delete(id)
        return UiMessage.Deleted
    }

    suspend fun addStation(
        date: LocalDate,
        participants: Set<PersonId>,
        typeId: String,
        placeId: String,
        transport: Set<TransportMode>,
        overnight: Boolean,
        liveTime: LocalTime?,
    ): UiMessage {
        commands.addStation(date, participants, typeId, placeId, transport, overnight, liveTime)
        return UiMessage.Saved
    }

    suspend fun applyTemplate(date: LocalDate, participants: Set<PersonId>, steps: List<TemplateStep>): UiMessage =
        if (commands.applyTemplate(date, participants, steps).isEmpty()) UiMessage.DayNotEmpty else UiMessage.Saved

    suspend fun journey(
        date: LocalDate,
        participants: Set<PersonId>,
        legs: List<JourneyLeg>,
        typeId: String,
        liveTime: LocalTime?,
    ): UiMessage {
        commands.journey(date, participants, legs, typeId, liveTime)
        return UiMessage.Saved
    }

    suspend fun returnJourney(
        date: LocalDate,
        participants: Set<PersonId>,
        stops: List<JourneyLeg>,
        homebaseId: String,
        homeTransport: Set<TransportMode>,
        homeDayOffset: Int,
        liveTime: LocalTime?,
    ): UiMessage {
        commands.returnJourney(date, participants, stops, homebaseId, homeTransport, homeDayOffset, liveTime)
        return UiMessage.Saved
    }

    suspend fun returnHome(
        date: LocalDate,
        participants: Set<PersonId>,
        homebaseId: String,
        transport: Set<TransportMode>,
        liveTime: LocalTime?,
    ): UiMessage {
        commands.returnHome(date, participants, homebaseId, transport, liveTime)
        return UiMessage.Saved
    }

    suspend fun saveVisit(draft: VisitDraft, liveTime: LocalTime?): UiMessage {
        if (draft.isNew) {
            visitCommands.add(
                date = draft.date, placeId = requireNotNull(draft.placeId), categoryIds = draft.categoryIds,
                participants = draft.participants, countsOverride = draft.countsOverride, liveTime = liveTime,
            )
        } else {
            visits.upsert(draft.toVisit())
        }
        return UiMessage.Saved
    }

    suspend fun deleteVisit(id: String): UiMessage {
        visits.delete(id)
        return UiMessage.Deleted
    }

    suspend fun fill(from: LocalDate, to: LocalDate, person: PersonId, draft: FillDraft): UiMessage {
        val count = commands.fillRange(
            from, to, person, requireNotNull(draft.typeId), requireNotNull(draft.placeId),
            draft.skipWeekendsAndHolidays,
        )
        return UiMessage.Filled(count)
    }

    /** Reise benennen; leerer Name setzt den automatischen Namen zurück. */
    suspend fun renameTrip(trip: io.github.mario2712.locus.domain.trips.TripSummary, title: String): UiMessage {
        if (title.isBlank()) {
            trips.delete(trip.id)
        } else {
            trips.upsert(
                io.github.mario2712.locus.domain.model.Trip(
                    id = trip.id, title = title.trim(), startDate = trip.start, endDate = trip.end,
                    participants = trip.participants.ifEmpty { setOf(io.github.mario2712.locus.domain.reference.Defaults.ME) },
                ),
            )
        }
        return UiMessage.Saved
    }

    private suspend fun conflictOrSaved(stay: Stay): UiMessage {
        val others = stays.observeBetween(stay.startDate, stay.endDate).first()
        return overlap.conflicts(stay, others).firstOrNull()?.let { UiMessage.Conflict(it.date) } ?: UiMessage.Saved
    }
}

/** Aktuelle Uhrzeit (Minuten genau) für Live-Einträge am heutigen Tag, sonst `null`. */
fun liveTimeFor(date: LocalDate, today: LocalDate): LocalTime? =
    if (date == today) LocalTime.now().withSecond(0).withNano(0) else null
