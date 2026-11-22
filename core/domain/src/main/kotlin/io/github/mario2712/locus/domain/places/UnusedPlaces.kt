package io.github.mario2712.locus.domain.places

import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.Wish

/**
 * Orte, die nirgends vorkommen: keine Station, kein Besuch, keine Vorlage, kein Wochenmuster, kein Wunsch,
 * keine Homebase (auch frühere), kein Favorit und keine verwendete Örtlichkeit darin.
 */
object UnusedPlaces {
    fun find(
        places: List<Place>,
        stays: List<Stay>,
        visits: List<Visit>,
        templates: List<DayTemplate>,
        rules: List<PatternRule>,
        wishes: List<Wish>,
    ): Set<String> {
        val referenced = buildSet {
            stays.forEach { add(it.placeId) }
            visits.forEach { add(it.placeId) }
            templates.forEach { t -> t.steps.forEach { add(it.placeId) } }
            rules.forEach { add(it.placeId) }
            wishes.forEach { w -> w.placeId?.let(::add) }
        }
        val unused = places.filter { p ->
            p.id !in referenced && !p.isHomebase && p.homebaseSince == null && !p.isFavorite
        }.mapTo(mutableSetOf()) { it.id }
        // Eine Stadt bleibt, solange eine ihrer Örtlichkeiten verwendet wird (auch über mehrere Ebenen).
        do {
            val usedParents = places.filter { it.id !in unused }.mapNotNull { it.parentPlaceId }.toSet()
            val changed = unused.removeAll(usedParents)
        } while (changed)
        return unused
    }
}
