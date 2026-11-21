package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.domain.validation.StayOverlapChecker
import io.github.mario2712.locus.ui.editor.FillDraft
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.editor.SwitchDraft
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Rückmeldungen an die UI (werden im Screen in Texte übersetzt). */
sealed interface UiMessage {
    data class Conflict(val date: LocalDate) : UiMessage
    data class Filled(val count: Int) : UiMessage
    data object Saved : UiMessage
    data object Deleted : UiMessage
}

/** Gemeinsame Speicherlogik der Screens inkl. Überschneidungs-Warnung. */
class StayEditing @Inject constructor(
    private val stays: StayRepository,
    private val commands: StayCommands,
    private val overlap: StayOverlapChecker,
) {
    suspend fun save(draft: StayDraft): UiMessage {
        val prepared = if (draft.isNew) {
            draft.copy(sortIndex = commands.nextSortIndex(draft.startDate, draft.participants.first()))
        } else {
            draft
        }
        val stay = prepared.toStay()
        stays.upsert(stay)
        return conflictOrSaved(stay)
    }

    suspend fun delete(id: String): UiMessage {
        stays.delete(id)
        return UiMessage.Deleted
    }

    suspend fun switchAt(date: LocalDate, person: PersonId, draft: SwitchDraft): UiMessage {
        val stay = commands.switchAt(
            date = date, time = draft.time, person = person,
            stayTypeId = requireNotNull(draft.typeId), placeId = requireNotNull(draft.placeId),
            endDate = draft.endDate,
        )
        return conflictOrSaved(stay)
    }

    suspend fun fill(from: LocalDate, to: LocalDate, person: PersonId, draft: FillDraft): UiMessage {
        val count = commands.fillRange(
            from, to, person, requireNotNull(draft.typeId), requireNotNull(draft.placeId),
            draft.skipWeekendsAndHolidays,
        )
        return UiMessage.Filled(count)
    }

    private suspend fun conflictOrSaved(stay: io.github.mario2712.locus.domain.model.Stay): UiMessage {
        val others = stays.observeBetween(stay.startDate, stay.endDate).first()
        return overlap.conflicts(stay, others).firstOrNull()?.let { UiMessage.Conflict(it.date) } ?: UiMessage.Saved
    }
}
