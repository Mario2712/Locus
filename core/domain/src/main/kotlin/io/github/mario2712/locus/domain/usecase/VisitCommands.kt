package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.repository.VisitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Besuche erfassen: „Einkaufen in Oberalm“, später „+ Restaurant“. */
class VisitCommands(private val visits: VisitRepository) {

    var newId: () -> String = { UUID.randomUUID().toString() }

    /**
     * Erfasst einen Besuch. Gibt es am selben Tag schon einen Besuch am selben Ort mit denselben
     * Personen, werden die Kategorien dort ergänzt statt einen zweiten Besuch anzulegen.
     */
    suspend fun add(
        date: LocalDate,
        placeId: String,
        categoryIds: Set<String>,
        participants: Set<PersonId>,
        countsOverride: Boolean? = null,
        liveTime: LocalTime? = null,
    ): Visit {
        require(categoryIds.isNotEmpty()) { "Ein Besuch braucht mindestens eine Kategorie" }
        val existing = visits.observeBetween(date, date).first()
            .firstOrNull { it.placeId == placeId && it.participants == participants }
        val visit = if (existing != null) {
            val all = existing.categoryIds + categoryIds
            existing.copy(
                extraCategoryIds = all - existing.categoryId,
                countsForCountryPointsOverride = countsOverride ?: existing.countsForCountryPointsOverride,
            )
        } else {
            Visit(
                id = newId(), placeId = placeId, categoryId = categoryIds.first(), date = date, time = liveTime,
                participants = participants, countsForCountryPointsOverride = countsOverride,
                extraCategoryIds = categoryIds - categoryIds.first(), source = EntrySource.MANUAL,
            )
        }
        visits.upsert(visit)
        return visit
    }
}
