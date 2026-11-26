package io.github.mario2712.locus.data.repository

import io.github.mario2712.locus.data.db.CatalogDao
import io.github.mario2712.locus.data.db.DayTemplateDao
import io.github.mario2712.locus.data.db.PatternRuleDao
import io.github.mario2712.locus.data.db.PersonDao
import io.github.mario2712.locus.data.db.PlaceDao
import io.github.mario2712.locus.data.db.StayDao
import io.github.mario2712.locus.data.db.TripDao
import io.github.mario2712.locus.data.db.VisitDao
import io.github.mario2712.locus.data.db.WishDao
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.RegionCollection
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Trip
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.model.Wish
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.DayTemplateRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.TripRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.repository.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> = map { it.map(transform) }

class RoomPersonRepository @Inject constructor(
    private val dao: PersonDao,
    private val meta: SyncMetaFactory,
) : PersonRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override suspend fun upsert(person: Person) =
        dao.upsert(person.toEntity(meta.forSave(dao.createdAt(person.id.value))))
}

class RoomCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val meta: SyncMetaFactory,
) : CatalogRepository {
    override fun observeStayTypes() = dao.observeStayTypes().mapList { it.toDomain() }
    override fun observeVisitCategories() = dao.observeVisitCategories().mapList { it.toDomain() }
    override fun observeRegionCollections() = dao.observeRegionCollections().mapList { it.toDomain() }

    override suspend fun upsertStayType(type: StayType) =
        dao.upsertStayType(type.toEntity(meta.forSave(dao.stayTypeCreatedAt(type.id))))

    override suspend fun upsertVisitCategory(category: VisitCategory) =
        dao.upsertVisitCategory(category.toEntity(meta.forSave(dao.visitCategoryCreatedAt(category.id))))

    override suspend fun setRegionCollection(collection: RegionCollection) =
        dao.upsertRegionCollection(
            collection.toEntity(meta.forSave(dao.regionCollectionCreatedAt(collection.countryIso2))),
        )
}

class RoomPlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val meta: SyncMetaFactory,
) : PlaceRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override fun observeFavorites() = dao.observeFavorites().mapList { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun findByOsmId(osmId: String) = dao.findByOsmId(osmId)?.toDomain()
    override suspend fun upsert(place: Place) = dao.upsert(place.toEntity(meta.forSave(dao.createdAt(place.id))))
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
    override suspend fun createdAt(id: String): Long? = dao.createdAt(id)

    override suspend fun setHomebase(id: String) {
        dao.setHomebase(id, meta.now(), meta.deviceId())
        updateHomebases { places -> places.map { it.copy(homebaseSince = if (it.id == id) io.github.mario2712.locus.domain.model.UNKNOWN_DATE else null) } }
    }

    override suspend fun addHomebase(id: String, since: LocalDate) = updateHomebases { places ->
        places.map { p ->
            when {
                p.id == id -> p.copy(homebaseSince = since)
                // Bisherige Homebase ohne Datum gilt ab jetzt „von Anfang an“, damit sie als früherer Zeitraum erhalten bleibt.
                p.isHomebase && p.homebaseSince == null -> p.copy(homebaseSince = io.github.mario2712.locus.domain.model.UNKNOWN_DATE)
                else -> p
            }
        }
    }

    override suspend fun removeHomebase(id: String) = updateHomebases { places ->
        places.map { if (it.id == id) it.copy(homebaseSince = null, isHomebase = false) else it }
    }

    /** Wendet [change] an und setzt `isHomebase` auf die Homebase mit dem spätesten Beginn (= aktuelle). */
    private suspend fun updateHomebases(change: (List<Place>) -> List<Place>) {
        val before = dao.observeAll().first().map { it.toDomain() }
        val changed = change(before)
        val current = changed.filter { it.homebaseSince != null }.maxByOrNull { it.homebaseSince!! }?.id
        val after = changed.map { it.copy(isHomebase = it.id == current) }
        after.zip(before).filter { (new, old) -> new != old }.forEach { (new, _) ->
            dao.upsert(new.toEntity(meta.forSave(dao.createdAt(new.id))))
        }
    }
}

class RoomStayRepository @Inject constructor(
    private val dao: StayDao,
    private val meta: SyncMetaFactory,
) : StayRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override fun observeBetween(from: LocalDate, to: LocalDate) = dao.observeBetween(from, to).mapList { it.toDomain() }
    override fun observeByTrip(tripId: String) = dao.observeByTrip(tripId).mapList { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(stay: Stay) =
        dao.save(stay.toEntity(meta.forSave(dao.createdAt(stay.id))), stay.personEntities())
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}

class RoomVisitRepository @Inject constructor(
    private val dao: VisitDao,
    private val meta: SyncMetaFactory,
) : VisitRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override fun observeBetween(from: LocalDate, to: LocalDate) = dao.observeBetween(from, to).mapList { it.toDomain() }
    override fun observeByTrip(tripId: String) = dao.observeByTrip(tripId).mapList { it.toDomain() }
    override fun observeByPlace(placeId: String) = dao.observeByPlace(placeId).mapList { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(visit: Visit) =
        dao.save(visit.toEntity(meta.forSave(dao.createdAt(visit.id))), visit.personEntities())
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}

class RoomTripRepository @Inject constructor(
    private val dao: TripDao,
    private val meta: SyncMetaFactory,
) : TripRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(trip: Trip) =
        dao.save(trip.toEntity(meta.forSave(dao.createdAt(trip.id))), trip.personEntities())
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}

class RoomWishRepository @Inject constructor(
    private val dao: WishDao,
    private val meta: SyncMetaFactory,
) : WishRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override suspend fun upsert(wish: Wish) = dao.upsert(wish.toEntity(meta.forSave(dao.createdAt(wish.id))))
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}

class RoomPatternRuleRepository @Inject constructor(
    private val dao: PatternRuleDao,
    private val meta: SyncMetaFactory,
) : PatternRuleRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override suspend fun upsert(rule: PatternRule) = dao.upsert(rule.toEntity(meta.forSave(dao.createdAt(rule.id))))
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}

class RoomDayTemplateRepository @Inject constructor(
    private val dao: DayTemplateDao,
    private val meta: SyncMetaFactory,
) : DayTemplateRepository {
    override fun observeAll() = dao.observeAll().mapList { it.toDomain() }
    override suspend fun upsert(template: DayTemplate) =
        dao.upsert(template.toEntity(meta.forSave(dao.createdAt(template.id))))
    override suspend fun delete(id: String) = dao.softDelete(id, meta.now(), meta.deviceId())
}
