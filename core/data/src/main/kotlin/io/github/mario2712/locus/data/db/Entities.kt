package io.github.mario2712.locus.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.StayGroup
import java.time.LocalDate
import java.time.LocalTime

/**
 * Metadaten für den späteren Sync (Last-Write-Wins je Datensatz).
 * Löschungen sind Soft-Deletes, damit sie sich übertragen lassen.
 */
data class SyncMeta(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "origin_device") val originDevice: String,
)

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "is_me") val isMe: Boolean,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "stay_type")
data class StayTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "stay_group") val group: StayGroup,
    @ColumnInfo(name = "counts_for_country_points") val countsForCountryPoints: Boolean,
    val icon: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "visit_category")
data class VisitCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "counts_for_country_points") val countsForCountryPoints: Boolean,
    val icon: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "region_collection")
data class RegionCollectionEntity(
    @PrimaryKey @ColumnInfo(name = "country_iso2") val countryIso2: String,
    val enabled: Boolean,
    @Embedded val meta: SyncMeta,
)

@Entity(
    tableName = "place",
    indices = [Index("osm_id"), Index("country_iso2"), Index("is_favorite")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: PlaceKind,
    @ColumnInfo(name = "country_iso2") val countryIso2: String,
    @ColumnInfo(name = "region_code") val regionCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "city_name") val cityName: String?,
    @ColumnInfo(name = "parent_place_id") val parentPlaceId: String?,
    @ColumnInfo(name = "osm_id") val osmId: String?,
    @ColumnInfo(name = "poi_category") val poiCategory: String?,
    val alias: String?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    val note: String?,
    @Embedded val meta: SyncMeta,
)

// Bewusst ohne Foreign Keys: Soft-Deletes und ein späterer Import in beliebiger Reihenfolge
// sollen nicht an Constraints scheitern. Referenzen werden in der Domain/UI aufgelöst.

@Entity(
    tableName = "stay",
    indices = [Index("start_date"), Index("end_date"), Index("trip_id"), Index("place_id")],
)
data class StayEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "type_id") val typeId: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    @ColumnInfo(name = "end_time") val endTime: LocalTime?,
    val precision: DatePrecision,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "trip_id") val tripId: String?,
    val note: String?,
    val source: EntrySource,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "stay_person", primaryKeys = ["stay_id", "person_id"], indices = [Index("person_id")])
data class StayPersonEntity(
    @ColumnInfo(name = "stay_id") val stayId: String,
    @ColumnInfo(name = "person_id") val personId: String,
)

data class StayWithPersons(
    @Embedded val stay: StayEntity,
    @Relation(parentColumn = "id", entityColumn = "stay_id")
    val persons: List<StayPersonEntity>,
)

@Entity(
    tableName = "visit",
    indices = [Index("date"), Index("trip_id"), Index("place_id")],
)
data class VisitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val date: LocalDate,
    val time: LocalTime?,
    val precision: DatePrecision,
    @ColumnInfo(name = "counts_override") val countsForCountryPointsOverride: Boolean?,
    @ColumnInfo(name = "trip_id") val tripId: String?,
    val note: String?,
    val rating: Int?,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    val source: EntrySource,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "visit_person", primaryKeys = ["visit_id", "person_id"], indices = [Index("person_id")])
data class VisitPersonEntity(
    @ColumnInfo(name = "visit_id") val visitId: String,
    @ColumnInfo(name = "person_id") val personId: String,
)

data class VisitWithPersons(
    @Embedded val visit: VisitEntity,
    @Relation(parentColumn = "id", entityColumn = "visit_id")
    val persons: List<VisitPersonEntity>,
)

@Entity(tableName = "trip", indices = [Index("start_date")])
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    @ColumnInfo(name = "end_time") val endTime: LocalTime?,
    val precision: DatePrecision,
    val note: String?,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "trip_person", primaryKeys = ["trip_id", "person_id"], indices = [Index("person_id")])
data class TripPersonEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "person_id") val personId: String,
)

data class TripWithPersons(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "trip_id")
    val persons: List<TripPersonEntity>,
)

@Entity(tableName = "wish")
data class WishEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "place_id") val placeId: String?,
    @ColumnInfo(name = "country_iso2") val countryIso2: String?,
    @ColumnInfo(name = "person_id") val personId: String?,
    val note: String?,
    val priority: Int,
    @ColumnInfo(name = "wished_at") val wishedAt: Long,
    @ColumnInfo(name = "fulfilled_visit_id") val fulfilledVisitId: String?,
    @Embedded val meta: SyncMeta,
)

@Entity(tableName = "pattern_rule")
data class PatternRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    /** ISO-Wochentag 1 = Montag … 7 = Sonntag. */
    val weekday: Int,
    @ColumnInfo(name = "stay_type_id") val stayTypeId: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "valid_from") val validFrom: LocalDate?,
    @ColumnInfo(name = "valid_to") val validTo: LocalDate?,
    @Embedded val meta: SyncMeta,
)
