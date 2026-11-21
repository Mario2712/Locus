package io.github.mario2712.locus.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.mario2712.locus.data.db.DayTemplateEntity
import io.github.mario2712.locus.data.db.PatternRuleEntity
import io.github.mario2712.locus.data.db.PersonEntity
import io.github.mario2712.locus.data.db.PlaceEntity
import io.github.mario2712.locus.data.db.RegionCollectionEntity
import io.github.mario2712.locus.data.db.StayEntity
import io.github.mario2712.locus.data.db.StayPersonEntity
import io.github.mario2712.locus.data.db.StayTypeEntity
import io.github.mario2712.locus.data.db.StayWithPersons
import io.github.mario2712.locus.data.db.TripEntity
import io.github.mario2712.locus.data.db.TripPersonEntity
import io.github.mario2712.locus.data.db.TripWithPersons
import io.github.mario2712.locus.data.db.VisitCategoryEntity
import io.github.mario2712.locus.data.db.VisitEntity
import io.github.mario2712.locus.data.db.VisitPersonEntity
import io.github.mario2712.locus.data.db.VisitWithPersons
import io.github.mario2712.locus.data.db.WishEntity

/** Vollzugriff auf alle Tabellen inkl. Soft-Deletes – für Export, Import und später Sync. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM person") suspend fun persons(): List<PersonEntity>
    @Query("SELECT * FROM stay_type") suspend fun stayTypes(): List<StayTypeEntity>
    @Query("SELECT * FROM visit_category") suspend fun visitCategories(): List<VisitCategoryEntity>
    @Query("SELECT * FROM region_collection") suspend fun regionCollections(): List<RegionCollectionEntity>
    @Query("SELECT * FROM place") suspend fun places(): List<PlaceEntity>
    @Transaction @Query("SELECT * FROM stay") suspend fun stays(): List<StayWithPersons>
    @Transaction @Query("SELECT * FROM visit") suspend fun visits(): List<VisitWithPersons>
    @Transaction @Query("SELECT * FROM trip") suspend fun trips(): List<TripWithPersons>
    @Query("SELECT * FROM wish") suspend fun wishes(): List<WishEntity>
    @Query("SELECT * FROM pattern_rule") suspend fun patternRules(): List<PatternRuleEntity>
    @Query("SELECT * FROM day_template") suspend fun dayTemplates(): List<DayTemplateEntity>

    @Query("SELECT updated_at FROM person WHERE id = :id") suspend fun personUpdated(id: String): Long?
    @Query("SELECT updated_at FROM stay_type WHERE id = :id") suspend fun stayTypeUpdated(id: String): Long?
    @Query("SELECT updated_at FROM visit_category WHERE id = :id") suspend fun visitCategoryUpdated(id: String): Long?
    @Query("SELECT updated_at FROM region_collection WHERE country_iso2 = :id") suspend fun regionCollectionUpdated(id: String): Long?
    @Query("SELECT updated_at FROM place WHERE id = :id") suspend fun placeUpdated(id: String): Long?
    @Query("SELECT updated_at FROM stay WHERE id = :id") suspend fun stayUpdated(id: String): Long?
    @Query("SELECT updated_at FROM visit WHERE id = :id") suspend fun visitUpdated(id: String): Long?
    @Query("SELECT updated_at FROM trip WHERE id = :id") suspend fun tripUpdated(id: String): Long?
    @Query("SELECT updated_at FROM wish WHERE id = :id") suspend fun wishUpdated(id: String): Long?
    @Query("SELECT updated_at FROM pattern_rule WHERE id = :id") suspend fun patternRuleUpdated(id: String): Long?
    @Query("SELECT updated_at FROM day_template WHERE id = :id") suspend fun dayTemplateUpdated(id: String): Long?

    @Upsert suspend fun upsertPerson(e: PersonEntity)
    @Upsert suspend fun upsertStayType(e: StayTypeEntity)
    @Upsert suspend fun upsertVisitCategory(e: VisitCategoryEntity)
    @Upsert suspend fun upsertRegionCollection(e: RegionCollectionEntity)
    @Upsert suspend fun upsertPlace(e: PlaceEntity)
    @Upsert suspend fun upsertWish(e: WishEntity)
    @Upsert suspend fun upsertPatternRule(e: PatternRuleEntity)
    @Upsert suspend fun upsertDayTemplate(e: DayTemplateEntity)

    @Upsert suspend fun upsertStay(e: StayEntity)
    @Query("DELETE FROM stay_person WHERE stay_id = :id") suspend fun clearStayPersons(id: String)
    @Insert suspend fun insertStayPersons(p: List<StayPersonEntity>)

    @Upsert suspend fun upsertVisit(e: VisitEntity)
    @Query("DELETE FROM visit_person WHERE visit_id = :id") suspend fun clearVisitPersons(id: String)
    @Insert suspend fun insertVisitPersons(p: List<VisitPersonEntity>)

    @Upsert suspend fun upsertTrip(e: TripEntity)
    @Query("DELETE FROM trip_person WHERE trip_id = :id") suspend fun clearTripPersons(id: String)
    @Insert suspend fun insertTripPersons(p: List<TripPersonEntity>)
}
