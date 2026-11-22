package io.github.mario2712.locus.domain.trips

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType

/** Reisen nach Arbeit filtern. */
enum class TripWorkFilter {
    ALL,

    /** Reisen mit mindestens einer privaten Basis (Urlaub, Ausflug …). */
    PRIVATE,

    /** Reisen mit mindestens einer Arbeits-Basis (Dienstreise). Verlängerte Dienstreisen erscheinen in beiden. */
    WORK,
}

/** Reisen nach Mitreisenden filtern. */
sealed interface TripPersonFilter {
    data object All : TripPersonFilter

    /** Nur ich, ohne Begleitung. */
    data object Alone : TripPersonFilter

    data class With(val person: PersonId) : TripPersonFilter
}

object TripFilter {
    fun apply(
        trips: List<TripSummary>,
        work: TripWorkFilter,
        person: TripPersonFilter,
        me: PersonId,
        stayTypes: Map<String, StayType>,
    ): List<TripSummary> = trips.filter { trip ->
        val workBases = trip.baseTypeIds.count { stayTypes[it]?.group == StayGroup.WORK }
        val workOk = when (work) {
            TripWorkFilter.ALL -> true
            TripWorkFilter.PRIVATE -> workBases < trip.baseTypeIds.size
            TripWorkFilter.WORK -> workBases > 0
        }
        val personOk = when (person) {
            TripPersonFilter.All -> true
            TripPersonFilter.Alone -> trip.participants == setOf(me)
            is TripPersonFilter.With -> person.person in trip.participants
        }
        workOk && personOk
    }
}
