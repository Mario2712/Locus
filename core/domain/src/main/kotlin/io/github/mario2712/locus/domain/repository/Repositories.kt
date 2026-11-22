package io.github.mario2712.locus.domain.repository

import io.github.mario2712.locus.domain.model.BackupSettings
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.EntryDefaults
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.RegionCollection
import io.github.mario2712.locus.domain.model.ReminderSettings
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Trip
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.model.Wish
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Repository-Schnittstellen der Domain. Implementierung in :core:data (Room).
// Löschen ist überall ein Soft-Delete, damit der spätere Sync Löschungen übertragen kann.

interface PersonRepository {
    fun observeAll(): Flow<List<Person>>
    suspend fun upsert(person: Person)
}

interface CatalogRepository {
    /** Inkl. archivierter Arten, sortiert nach sortOrder. */
    fun observeStayTypes(): Flow<List<StayType>>
    fun observeVisitCategories(): Flow<List<VisitCategory>>
    fun observeRegionCollections(): Flow<List<RegionCollection>>
    suspend fun upsertStayType(type: StayType)
    suspend fun upsertVisitCategory(category: VisitCategory)
    suspend fun setRegionCollection(collection: RegionCollection)
}

interface PlaceRepository {
    fun observeAll(): Flow<List<Place>>
    fun observeFavorites(): Flow<List<Place>>
    suspend fun get(id: String): Place?
    suspend fun findByOsmId(osmId: String): Place?
    suspend fun upsert(place: Place)
    suspend fun delete(id: String)
    /** Setzt [id] als einzige Homebase für alle Zeit (ohne Umzüge). */
    suspend fun setHomebase(id: String)

    /** Umzug: [id] ist Homebase ab [since] (früher: [io.github.mario2712.locus.domain.model.UNKNOWN_DATE] = von Anfang an). */
    suspend fun addHomebase(id: String, since: java.time.LocalDate)

    /** [id] ist keine Homebase mehr (Zeitraum entfernen). */
    suspend fun removeHomebase(id: String)
}

interface StayRepository {
    fun observeAll(): Flow<List<Stay>>
    /** Alle Aufenthalte, die den Zeitraum [from]..[to] berühren. */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Stay>>
    fun observeByTrip(tripId: String): Flow<List<Stay>>
    suspend fun get(id: String): Stay?
    suspend fun upsert(stay: Stay)
    suspend fun delete(id: String)
}

interface VisitRepository {
    fun observeAll(): Flow<List<Visit>>
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Visit>>
    fun observeByTrip(tripId: String): Flow<List<Visit>>
    fun observeByPlace(placeId: String): Flow<List<Visit>>
    suspend fun get(id: String): Visit?
    suspend fun upsert(visit: Visit)
    suspend fun delete(id: String)
}

interface TripRepository {
    fun observeAll(): Flow<List<Trip>>
    suspend fun get(id: String): Trip?
    suspend fun upsert(trip: Trip)
    suspend fun delete(id: String)
}

interface WishRepository {
    fun observeAll(): Flow<List<Wish>>
    suspend fun upsert(wish: Wish)
    suspend fun delete(id: String)
}

interface PatternRuleRepository {
    fun observeAll(): Flow<List<PatternRule>>
    suspend fun upsert(rule: PatternRule)
    suspend fun delete(id: String)
}

interface DayTemplateRepository {
    fun observeAll(): Flow<List<DayTemplate>>
    suspend fun upsert(template: DayTemplate)
    suspend fun delete(id: String)
}

interface SettingsRepository {
    fun observeReminder(): Flow<ReminderSettings>
    suspend fun setReminder(settings: ReminderSettings)
    fun observeEntryDefaults(): Flow<EntryDefaults>
    suspend fun setEntryDefaults(defaults: EntryDefaults)
    fun observeBackup(): Flow<BackupSettings>
    suspend fun setBackup(settings: BackupSettings)

    /** Einmalige Datenumstellungen (z. B. „Gipfel“ → „Natur / Wandern“). */
    suspend fun isMigrationDone(key: String): Boolean
    suspend fun setMigrationDone(key: String)
}
