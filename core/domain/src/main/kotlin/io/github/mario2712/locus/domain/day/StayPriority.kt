package io.github.mario2712.locus.domain.day

import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds

/**
 * Rangfolge für die Hauptart eines Tages mit mehreren Stationen:
 * Urlaub > Dienstreise > Büro > Homeoffice > Ausflug > Frei > Krank > Anreise > Transit.
 * Eigene Arten werden nach ihrer Gruppe eingeordnet. Kleinerer Wert = wichtiger.
 */
object StayPriority {
    private val defaults = mapOf(
        StayTypeIds.VACATION to 0,
        StayTypeIds.BUSINESS_TRIP to 10,
        StayTypeIds.OFFICE to 20,
        StayTypeIds.HOMEOFFICE to 30,
        StayTypeIds.EXCURSION to 40,
        StayTypeIds.FREE to 50,
        StayTypeIds.SICK to 60,
        StayTypeIds.ON_THE_WAY to 70,
        StayTypeIds.TRANSIT to 80,
    )

    fun rank(type: StayType?): Int {
        if (type == null) return Int.MAX_VALUE
        defaults[type.id]?.let { return it }
        return when (type.group) {
            StayGroup.VACATION -> 5
            StayGroup.WORK -> 25
            StayGroup.FREE -> 45
            StayGroup.OTHER -> 65
        }
    }

    fun ranker(types: Map<String, StayType>): (String) -> Int = { id -> rank(types[id]) }
}
