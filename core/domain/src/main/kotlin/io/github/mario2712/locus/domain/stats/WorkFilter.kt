package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds

/** Arbeit in der Statistik ein- oder ausblenden. */
enum class WorkFilter {
    /** Alles zählen. */
    ALL,

    /** Stationen der Gruppe „Arbeit“ (Homeoffice, Büro, Dienstreise, eigene Arbeitsarten) weglassen. */
    NO_WORK,

    /** Wie [NO_WORK], aber Dienstreisen bleiben – inklusive Büro-Stationen an Dienstreise-Tagen (z. B. Bregenz). */
    NO_WORK_EXCEPT_BUSINESS_TRIPS;

    /** Filtert aufgelöste Stationen (laufende Basen bereits verlängert). */
    fun apply(resolved: List<Stay>, stayTypes: Map<String, StayType>): List<Stay> {
        if (this == ALL) return resolved
        fun isWork(stay: Stay) = stayTypes[stay.typeId]?.group == StayGroup.WORK
        if (this == NO_WORK) return resolved.filterNot(::isWork)
        val businessTrips = resolved.filter { it.typeId == StayTypeIds.BUSINESS_TRIP }
        return resolved.filter { stay ->
            !isWork(stay) || businessTrips.any { trip ->
                trip.participants.any { it in stay.participants } &&
                    trip.startDate <= stay.endDate && stay.startDate <= trip.endDate
            }
        }
    }
}
