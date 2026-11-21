package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.repository.VisitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VisitCommandsTest {
    private val data = MutableStateFlow<Map<String, Visit>>(emptyMap())
    private val repo = object : VisitRepository {
        override fun observeAll() = data.map { it.values.toList() }
        override fun observeBetween(from: LocalDate, to: LocalDate) =
            data.map { m -> m.values.filter { it.date in from..to } }
        override fun observeByTrip(tripId: String) = data.map { m -> m.values.filter { it.tripId == tripId } }
        override fun observeByPlace(placeId: String) = data.map { m -> m.values.filter { it.placeId == placeId } }
        override suspend fun get(id: String) = data.value[id]
        override suspend fun upsert(visit: Visit) { data.value = data.value + (visit.id to visit) }
        override suspend fun delete(id: String) { data.value = data.value - id }
    }
    private var counter = 0
    private val commands = VisitCommands(repo).apply { newId = { "v${++counter}" } }
    private val day = LocalDate.parse("2026-11-21")

    @Test
    fun `Einkaufen in Oberalm, spaeter noch essen - ein Besuch mit zwei Kategorien`() = runTest {
        commands.add(day, "oberalm", setOf(VisitCategoryIds.SHOPPING), setOf(ME))
        commands.add(day, "oberalm", setOf(VisitCategoryIds.RESTAURANT), setOf(ME))
        val visit = data.value.values.single()
        assertEquals(setOf(VisitCategoryIds.SHOPPING, VisitCategoryIds.RESTAURANT), visit.categoryIds)
    }

    @Test
    fun `anderer Ort ergibt eigenen Besuch, Laenderpunkt-Schalter wird gespeichert`() = runTest {
        commands.add(day, "oberalm", setOf(VisitCategoryIds.SHOPPING), setOf(ME))
        commands.add(day, "freilassing", setOf(VisitCategoryIds.SHOPPING), setOf(ME), countsOverride = true)
        assertEquals(2, data.value.size)
        assertEquals(true, data.value.values.first { it.placeId == "freilassing" }.countsForCountryPointsOverride)
    }
}
