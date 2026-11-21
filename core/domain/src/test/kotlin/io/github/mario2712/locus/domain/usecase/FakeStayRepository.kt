package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.repository.StayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeStayRepository : StayRepository {
    val data = MutableStateFlow<Map<String, Stay>>(emptyMap())
    val all: List<Stay> get() = data.value.values.sortedWith(compareBy({ it.startDate }, { it.sortIndex }))

    override fun observeAll(): Flow<List<Stay>> = data.map { all }
    override fun observeBetween(from: LocalDate, to: LocalDate) =
        data.map { all.filter { it.startDate <= to && it.endDate >= from } }
    override fun observeByTrip(tripId: String) = data.map { all.filter { it.tripId == tripId } }
    override suspend fun get(id: String) = data.value[id]
    override suspend fun upsert(stay: Stay) { data.value = data.value + (stay.id to stay) }
    override suspend fun delete(id: String) { data.value = data.value - id }
}
