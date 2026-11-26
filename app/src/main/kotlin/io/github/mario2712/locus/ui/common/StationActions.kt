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
    data object Undone : UiMessage

    /** Meldung zu einer Änderung, die sich per „Rückgängig“ zurücknehmen lässt. */
    data class Undoable(val inner: UiMessage) : UiMessage
}

/** Gemeinsame Schreib-Abläufe der Screens (Stationen, Reisen, Besuche) inkl. Warnungen. */
class StationActions @Inject constructor(
    private val stays: StayRepository,
    private val visits: VisitRepository,
    private val commands: StayCommands,
    private val visitCommands: VisitCommands,
    private val overlap: StayOverlapChecker,
    private val trips: io.github.mario2712.locus.domain.repository.TripRepository,
    /** Letzte Änderung zurücknehmen (siehe [UndoBuffer]). */
    val undo: UndoBuffer,
) {
    suspend fun saveStation(draft: StayDraft): UiMessage {
        val person = draft.participants.first()
        val anchor = draft.insertAnchor
        val prepared = when {
            // Einfügen davor/danach: am Anker-Tag an der gewählten Stelle, am Vortag hinten, am Folgetag vorne.
            draft.isNew && anchor != null -> {
                val index = when {
                    draft.startDate == anchor -> draft.insertIndex ?: commands.nextSortIndex(draft.startDate, person)
                    draft.startDate < anchor -> commands.nextSortIndex(draft.startDate, person)
                    else -> 0
                }
                commands.makeRoomAt(draft.startDate, person, index)
                draft.copy(sortIndex = index)
            }
            draft.isNew -> draft.copy(sortIndex = commands.nextSortIndex(draft.startDate, person))
            else -> {
                // Auf einen anderen Tag verschoben: dort hinten anhängen.
                val old = draft.id?.let { stays.get(it) }
                if (old != null && old.startDate != draft.startDate) {
                    draft.copy(sortIndex = commands.nextSortIndex(draft.startDate, person))
                } else {
                    draft
                }
            }
        }
        val stay = prepared.toStay()
        stays.upsert(stay)
        return conflictOrSaved(stay)
    }

    suspend fun reorderStations(orderedIds: List<String>): UiMessage {
        commands.reorder(orderedIds)
        return UiMessage.Saved
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
