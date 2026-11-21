package io.github.mario2712.locus.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.usecase.DaySuggester
import io.github.mario2712.locus.domain.usecase.StayCommands
import io.github.mario2712.locus.domain.validation.StayOverlapChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope für Arbeiten, die unabhängig von einzelnen Screens laufen (z. B. Seeding). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun clock(): Clock = Clock.systemDefaultZone()

    // Domain-Klassen sind bewusst frei von DI-Annotationen und werden hier bereitgestellt.
    @Provides fun stayCommands(stays: StayRepository) = StayCommands(stays)
    @Provides fun daySuggester() = DaySuggester()
    @Provides fun overlapChecker() = StayOverlapChecker()
    @Provides fun countryPointsCalculator() = CountryPointsCalculator()
}
