package io.github.mario2712.locus.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PersonEntity::class,
        StayTypeEntity::class,
        VisitCategoryEntity::class,
        RegionCollectionEntity::class,
        PlaceEntity::class,
        StayEntity::class,
        StayPersonEntity::class,
        VisitEntity::class,
        VisitPersonEntity::class,
        TripEntity::class,
        TripPersonEntity::class,
        WishEntity::class,
        PatternRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LocusDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun catalogDao(): CatalogDao
    abstract fun placeDao(): PlaceDao
    abstract fun stayDao(): StayDao
    abstract fun visitDao(): VisitDao
    abstract fun tripDao(): TripDao
    abstract fun wishDao(): WishDao
    abstract fun patternRuleDao(): PatternRuleDao

    companion object {
        const val NAME = "locus.db"
    }
}
