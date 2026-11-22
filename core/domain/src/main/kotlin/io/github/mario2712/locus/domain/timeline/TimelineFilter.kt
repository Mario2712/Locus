package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType

/** Arbeit in der Timeline. */
enum class TimelineWork { ALL, NO_WORK, ONLY_WORK }

/**
 * Filter für die Timeline: Arbeit, Aufenthaltsart, Besuchskategorie, Ort (Text) und Person.
 * Arbeit blendet Stationen aus; die übrigen Filter wählen Tage aus, die passen.
 */
data class TimelineFilter(
    val work: TimelineWork = TimelineWork.ALL,
    val stayTypeId: String? = null,
    val categoryId: String? = null,
    val query: String = "",
    val person: PersonId? = null,
) {
    val isActive: Boolean
        get() = this != TimelineFilter()

    fun apply(days: List<TimelineDay>, stayTypes: Map<String, StayType>, places: Map<String, Place>): List<TimelineDay> {
        if (!isActive) return days
        fun isWork(stay: Stay) = stayTypes[stay.typeId]?.group == StayGroup.WORK
        fun keep(stay: Stay) = when (work) {
            TimelineWork.ALL -> true
            TimelineWork.NO_WORK -> !isWork(stay)
            TimelineWork.ONLY_WORK -> isWork(stay)
        }
        val q = query.trim().lowercase()
        fun placeMatches(id: String): Boolean {
            val place = places[id] ?: return false
            val parent = place.parentPlaceId?.let(places::get)
            return listOfNotNull(place.name, place.alias, place.cityName, parent?.name, parent?.alias).any { it.lowercase().contains(q) }
        }
        return days.mapNotNull { day ->
            val stations = day.stations.filter(::keep)
            val base = day.baseAtStart?.takeIf(::keep)
            val visits = if (work == TimelineWork.ONLY_WORK) emptyList() else day.visits
            val filtered = day.copy(baseAtStart = base, stations = stations, visits = visits)
            val stays = stations + listOfNotNull(base)
            val matches = (stays.isNotEmpty() || visits.isNotEmpty()) &&
                (stayTypeId == null || stays.any { it.typeId == stayTypeId }) &&
                (categoryId == null || visits.any { categoryId in it.categoryIds }) &&
                (q.isEmpty() || stays.any { placeMatches(it.placeId) } || visits.any { placeMatches(it.placeId) }) &&
                (person == null || stays.any { person in it.participants } || visits.any { person in it.participants })
            filtered.takeIf { matches }
        }
    }
}
