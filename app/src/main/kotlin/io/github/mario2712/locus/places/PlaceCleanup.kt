package io.github.mario2712.locus.places

import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.places.UnusedPlaces
import io.github.mario2712.locus.domain.repository.DayTemplateRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.repository.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Findet und entfernt Orte, die nirgends vorkommen (siehe [UnusedPlaces]). */
class PlaceCleanup @Inject constructor(
    private val places: PlaceRepository,
    stays: StayRepository,
    visits: VisitRepository,
    templates: DayTemplateRepository,
    rules: PatternRuleRepository,
    private val wishes: WishRepository,
) {
    private data class Inputs(
        val places: List<Place>,
        val stays: List<Stay>,
        val visits: List<Visit>,
        val templates: List<DayTemplate>,
        val rules: List<PatternRule>,
    )

    private val inputs = combine(places.observeAll(), stays.observeAll(), visits.observeAll(), templates.observeAll(), rules.observeAll()) {
            p, s, v, t, r -> Inputs(p, s, v, t, r)
    }

    fun observeUnused(): Flow<Set<String>> = combine(inputs, wishes.observeAll()) { i, w ->
        UnusedPlaces.find(i.places, i.stays, i.visits, i.templates, i.rules, w)
    }

    /** Löscht unbenutzte Orte; mit [minAgeMillis] nur solche, die mindestens so lange existieren. @return Anzahl */
    suspend fun deleteUnused(minAgeMillis: Long? = null, now: Long = System.currentTimeMillis()): Int {
        val toDelete = observeUnused().first().filter { id ->
            minAgeMillis == null || (places.createdAt(id)?.let { now - it >= minAgeMillis } ?: false)
        }
        toDelete.forEach { places.delete(it) }
        return toDelete.size
    }

    companion object {
        /** Automatisches Aufräumen nur für Orte, die seit mindestens 7 Tagen unbenutzt sind. */
        const val AUTO_MIN_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
