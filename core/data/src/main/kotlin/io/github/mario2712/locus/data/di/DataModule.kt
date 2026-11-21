package io.github.mario2712.locus.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.repository.RoomCatalogRepository
import io.github.mario2712.locus.data.repository.RoomPatternRuleRepository
import io.github.mario2712.locus.data.repository.RoomPersonRepository
import io.github.mario2712.locus.data.repository.RoomPlaceRepository
import io.github.mario2712.locus.data.repository.RoomStayRepository
import io.github.mario2712.locus.data.repository.RoomTripRepository
import io.github.mario2712.locus.data.repository.RoomVisitRepository
import io.github.mario2712.locus.data.repository.RoomWishRepository
import io.github.mario2712.locus.data.sync.DeviceIdProvider
import io.github.mario2712.locus.data.sync.SharedPrefsDeviceIdProvider
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.data.sync.TimeProvider
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.PatternRuleRepository
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.TripRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.repository.WishRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): LocusDatabase =
        Room.databaseBuilder(context, LocusDatabase::class.java, LocusDatabase.NAME).build()

    @Provides @Singleton
    fun timeProvider(): TimeProvider = TimeProvider(System::currentTimeMillis)

    @Provides @Singleton
    fun deviceIdProvider(@ApplicationContext context: Context): DeviceIdProvider = SharedPrefsDeviceIdProvider(context)

    @Provides @Singleton
    fun syncMetaFactory(time: TimeProvider, device: DeviceIdProvider) = SyncMetaFactory(time, device)

    @Provides fun personDao(db: LocusDatabase) = db.personDao()
    @Provides fun catalogDao(db: LocusDatabase) = db.catalogDao()
    @Provides fun placeDao(db: LocusDatabase) = db.placeDao()
    @Provides fun stayDao(db: LocusDatabase) = db.stayDao()
    @Provides fun visitDao(db: LocusDatabase) = db.visitDao()
    @Provides fun tripDao(db: LocusDatabase) = db.tripDao()
    @Provides fun wishDao(db: LocusDatabase) = db.wishDao()
    @Provides fun patternRuleDao(db: LocusDatabase) = db.patternRuleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun personRepository(impl: RoomPersonRepository): PersonRepository
    @Binds abstract fun catalogRepository(impl: RoomCatalogRepository): CatalogRepository
    @Binds abstract fun placeRepository(impl: RoomPlaceRepository): PlaceRepository
    @Binds abstract fun stayRepository(impl: RoomStayRepository): StayRepository
    @Binds abstract fun visitRepository(impl: RoomVisitRepository): VisitRepository
    @Binds abstract fun tripRepository(impl: RoomTripRepository): TripRepository
    @Binds abstract fun wishRepository(impl: RoomWishRepository): WishRepository
    @Binds abstract fun patternRuleRepository(impl: RoomPatternRuleRepository): PatternRuleRepository
}
