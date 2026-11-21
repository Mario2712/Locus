package io.github.mario2712.locus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Lesende Abfragen blenden soft-gelöschte Datensätze (deleted = 1) aus.

@Dao
interface PersonDao {
    @Query("SELECT * FROM person WHERE deleted = 0 ORDER BY is_me DESC, name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT created_at FROM person WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entities: List<PersonEntity>)
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM stay_type WHERE deleted = 0 ORDER BY sort_order, name")
    fun observeStayTypes(): Flow<List<StayTypeEntity>>

    @Query("SELECT * FROM visit_category WHERE deleted = 0 ORDER BY sort_order, name")
    fun observeVisitCategories(): Flow<List<VisitCategoryEntity>>

    @Query("SELECT * FROM region_collection WHERE deleted = 0 ORDER BY country_iso2")
    fun observeRegionCollections(): Flow<List<RegionCollectionEntity>>

    @Query("SELECT created_at FROM stay_type WHERE id = :id")
    suspend fun stayTypeCreatedAt(id: String): Long?

    @Query("SELECT created_at FROM visit_category WHERE id = :id")
    suspend fun visitCategoryCreatedAt(id: String): Long?

    @Query("SELECT created_at FROM region_collection WHERE country_iso2 = :iso2")
    suspend fun regionCollectionCreatedAt(iso2: String): Long?

    @Upsert suspend fun upsertStayType(entity: StayTypeEntity)
    @Upsert suspend fun upsertVisitCategory(entity: VisitCategoryEntity)
    @Upsert suspend fun upsertRegionCollection(entity: RegionCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStayTypesIfMissing(entities: List<StayTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVisitCategoriesIfMissing(entities: List<VisitCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRegionCollectionsIfMissing(entities: List<RegionCollectionEntity>)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place WHERE deleted = 0 AND is_favorite = 1 ORDER BY COALESCE(alias, name)")
    fun observeFavorites(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): PlaceEntity?

    @Query("SELECT * FROM place WHERE osm_id = :osmId AND deleted = 0 LIMIT 1")
    suspend fun findByOsmId(osmId: String): PlaceEntity?

    @Query("SELECT created_at FROM place WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: PlaceEntity)

    @Query("UPDATE place SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)

    @Query("UPDATE place SET is_homebase = 0, updated_at = :now, origin_device = :device WHERE is_homebase = 1 AND id != :id")
    suspend fun clearHomebaseExcept(id: String, now: Long, device: String)

    @Query("UPDATE place SET is_homebase = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun markHomebase(id: String, now: Long, device: String)

    @Transaction
    suspend fun setHomebase(id: String, now: Long, device: String) {
        clearHomebaseExcept(id, now, device)
        markHomebase(id, now, device)
    }

    @Query("SELECT COUNT(*) FROM place WHERE is_homebase = 1 AND deleted = 0")
    suspend fun homebaseCount(): Int

    @Query("SELECT * FROM place WHERE deleted = 0 AND LOWER(alias) = LOWER(:alias)")
    suspend fun findByAlias(alias: String): List<PlaceEntity>
}

@Dao
interface StayDao {
    @Transaction
    @Query("SELECT * FROM stay WHERE deleted = 0 ORDER BY start_date, sort_index")
    fun observeAll(): Flow<List<StayWithPersons>>

    @Transaction
    @Query(
        "SELECT * FROM stay WHERE deleted = 0 AND start_date <= :to AND end_date >= :from " +
            "ORDER BY start_date, sort_index",
    )
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<StayWithPersons>>

    @Transaction
    @Query("SELECT * FROM stay WHERE deleted = 0 AND trip_id = :tripId ORDER BY start_date, sort_index")
    fun observeByTrip(tripId: String): Flow<List<StayWithPersons>>

    @Transaction
    @Query("SELECT * FROM stay WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): StayWithPersons?

    @Query("SELECT created_at FROM stay WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: StayEntity)

    @Query("DELETE FROM stay_person WHERE stay_id = :stayId")
    suspend fun clearPersons(stayId: String)

    @Insert suspend fun insertPersons(persons: List<StayPersonEntity>)

    @Transaction
    suspend fun save(entity: StayEntity, persons: List<StayPersonEntity>) {
        upsert(entity)
        clearPersons(entity.id)
        insertPersons(persons)
    }

    @Query("UPDATE stay SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}

@Dao
interface VisitDao {
    @Transaction
    @Query("SELECT * FROM visit WHERE deleted = 0 ORDER BY date, time")
    fun observeAll(): Flow<List<VisitWithPersons>>

    @Transaction
    @Query("SELECT * FROM visit WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date, time")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<VisitWithPersons>>

    @Transaction
    @Query("SELECT * FROM visit WHERE deleted = 0 AND trip_id = :tripId ORDER BY date, time")
    fun observeByTrip(tripId: String): Flow<List<VisitWithPersons>>

    @Transaction
    @Query("SELECT * FROM visit WHERE deleted = 0 AND place_id = :placeId ORDER BY date DESC, time DESC")
    fun observeByPlace(placeId: String): Flow<List<VisitWithPersons>>

    @Transaction
    @Query("SELECT * FROM visit WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): VisitWithPersons?

    @Query("SELECT created_at FROM visit WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: VisitEntity)

    @Query("DELETE FROM visit_person WHERE visit_id = :visitId")
    suspend fun clearPersons(visitId: String)

    @Insert suspend fun insertPersons(persons: List<VisitPersonEntity>)

    @Transaction
    suspend fun save(entity: VisitEntity, persons: List<VisitPersonEntity>) {
        upsert(entity)
        clearPersons(entity.id)
        insertPersons(persons)
    }

    @Query("UPDATE visit SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}

@Dao
interface TripDao {
    @Transaction
    @Query("SELECT * FROM trip WHERE deleted = 0 ORDER BY start_date DESC")
    fun observeAll(): Flow<List<TripWithPersons>>

    @Transaction
    @Query("SELECT * FROM trip WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): TripWithPersons?

    @Query("SELECT created_at FROM trip WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: TripEntity)

    @Query("DELETE FROM trip_person WHERE trip_id = :tripId")
    suspend fun clearPersons(tripId: String)

    @Insert suspend fun insertPersons(persons: List<TripPersonEntity>)

    @Transaction
    suspend fun save(entity: TripEntity, persons: List<TripPersonEntity>) {
        upsert(entity)
        clearPersons(entity.id)
        insertPersons(persons)
    }

    @Query("UPDATE trip SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wish WHERE deleted = 0 ORDER BY priority DESC, wished_at")
    fun observeAll(): Flow<List<WishEntity>>

    @Query("SELECT created_at FROM wish WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: WishEntity)

    @Query("UPDATE wish SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}

@Dao
interface PatternRuleDao {
    @Query("SELECT * FROM pattern_rule WHERE deleted = 0 ORDER BY weekday")
    fun observeAll(): Flow<List<PatternRuleEntity>>

    @Query("SELECT created_at FROM pattern_rule WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: PatternRuleEntity)

    @Query("UPDATE pattern_rule SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}

@Dao
interface DayTemplateDao {
    @Query("SELECT * FROM day_template WHERE deleted = 0 ORDER BY sort_order, name")
    fun observeAll(): Flow<List<DayTemplateEntity>>

    @Query("SELECT created_at FROM day_template WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    @Upsert suspend fun upsert(entity: DayTemplateEntity)

    @Query("UPDATE day_template SET deleted = 1, updated_at = :now, origin_device = :device WHERE id = :id")
    suspend fun softDelete(id: String, now: Long, device: String)
}
