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
    /** Mehrfachauswahl: Tag passt, wenn eine der Arten vorkommt. */
    val stayTypeIds: Set<String> = emptySet(),
    /** Mehrfachauswahl: Tag passt, wenn eine der Kategorien vorkommt. */
    val categoryIds: Set<String> = emptySet(),
    val query: String = "",
    val person: PersonId? = null,
    /** Zeitraum (beide Grenzen inklusive); `null` = offen. */
    val from: java.time.LocalDate? = null,
    val to: java.time.LocalDate? = null,
) {
    val hasPeriod: Boolean get() = from != null || to != null

    val isActive: Boolean
        get() = this != TimelineFilter()

    /** Anzahl gesetzter Filter ohne Ortssuche (für „Filter (n)“). */
    val count: Int
        get() = (if (work != TimelineWork.ALL) 1 else 0) + stayTypeIds.size + categoryIds.size + (if (person != null) 1 else 0) +
            (if (hasPeriod) 1 else 0)

    companion object {
        /** Wie oft (Tage) jede Art bzw. Kategorie vorkommt – nur Verwendetes, häufigstes zuerst. */
        fun usage(days: List<TimelineDay>): Pair<List<Pair<String, Int>>, List<Pair<String, Int>>> {
            val types = days.flatMap { d -> (d.stations + listOfNotNull(d.baseAtStart)).map { it.typeId }.distinct() }
                .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            val categories = days.flatMap { d -> d.visits.flatMap { it.categoryIds }.distinct() }
                .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            return types to categories
        }
    }

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
        fun noteMatches(note: String?) = note != null && note.lowercase().contains(q)
        return days.mapNotNull { day ->
            if (from != null && day.date < from) return@mapNotNull null
            if (to != null && day.date > to) return@mapNotNull null
            val stations = day.stations.filter(::keep)
            val base = day.baseAtStart?.takeIf(::keep)
            val visits = if (work == TimelineWork.ONLY_WORK) emptyList() else day.visits
            val filtered = day.copy(baseAtStart = base, stations = stations, visits = visits)
            val stays = stations + listOfNotNull(base)
            val matches = (stays.isNotEmpty() || visits.isNotEmpty()) &&
                (stayTypeIds.isEmpty() || stays.any { it.typeId in stayTypeIds }) &&
                (categoryIds.isEmpty() || visits.any { v -> v.categoryIds.any { it in categoryIds } }) &&
                // Freitext: Ort (inkl. Stadt) oder Notiz, z. B. der Filmtitel beim Kinobesuch
                (q.isEmpty() || stays.any { placeMatches(it.placeId) || noteMatches(it.note) } ||
                    visits.any { placeMatches(it.placeId) || noteMatches(it.note) }) &&
                (person == null || stays.any { person in it.participants } || visits.any { person in it.participants })
            filtered.takeIf { matches }
        }
    }
}
