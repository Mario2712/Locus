package io.github.mario2712.locus.data.db

import androidx.room.AutoMigration
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
        DayTemplateEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2: Stationen-Konzept (Homebase, Übernachtung, Verkehrsmittel, Besuchs-Kategorien, Vorlagen)
        AutoMigration(from = 1, to = 2),
        // v3: Geburtsdatum für Profil-Statistiken
        AutoMigration(from = 2, to = 3),
        // v4: Adresse von Orten (gleichnamige Orte unterscheiden)
        AutoMigration(from = 3, to = 4),
    ],
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
    abstract fun dayTemplateDao(): DayTemplateDao
    abstract fun backupDao(): io.github.mario2712.locus.data.backup.BackupDao

    companion object {
        const val NAME = "locus.db"
    }
}
