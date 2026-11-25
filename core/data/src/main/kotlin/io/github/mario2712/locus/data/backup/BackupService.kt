package io.github.mario2712.locus.data.backup

import androidx.room.withTransaction
import io.github.mario2712.locus.data.db.DayTemplateEntity
import io.github.mario2712.locus.data.db.LocusDatabase
import io.github.mario2712.locus.data.db.PatternRuleEntity
import io.github.mario2712.locus.data.db.PersonEntity
import io.github.mario2712.locus.data.db.PlaceEntity
import io.github.mario2712.locus.data.db.RegionCollectionEntity
import io.github.mario2712.locus.data.db.StayEntity
import io.github.mario2712.locus.data.db.StayPersonEntity
import io.github.mario2712.locus.data.db.StayTypeEntity
import io.github.mario2712.locus.data.db.SyncMeta
import io.github.mario2712.locus.data.db.TripEntity
import io.github.mario2712.locus.data.db.TripPersonEntity
import io.github.mario2712.locus.data.db.VisitCategoryEntity
import io.github.mario2712.locus.data.db.VisitEntity
import io.github.mario2712.locus.data.db.VisitPersonEntity
import io.github.mario2712.locus.data.db.WishEntity
import io.github.mario2712.locus.data.sync.SyncMetaFactory
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.StayGroup
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Export und zusammenführender Import der gesamten Datenbank als JSON. */
class BackupService @Inject constructor(
    private val db: LocusDatabase,
    private val meta: SyncMetaFactory,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dao get() = db.backupDao()

    suspend fun exportJson(settings: SettingsDto? = null): String =
        json.encodeToString(BackupFile.serializer(), export().copy(settings = settings))

    suspend fun export(): BackupFile = db.withTransaction {
        BackupFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = meta.now(),
            device = meta.deviceId(),
            persons = dao.persons().sortedBy { it.sortKey() }.map { PersonDto(it.id, it.name, it.colorArgb, it.isMe, it.birthDate?.toString(), it.meta.dto()) },
            stayTypes = dao.stayTypes().sortedBy { it.sortKey() }.map {
                StayTypeDto(it.id, it.name, it.group.name, it.countsForCountryPoints, it.icon, it.colorArgb, it.sortOrder, it.isArchived, it.meta.dto())
            },
            visitCategories = dao.visitCategories().sortedBy { it.sortKey() }.map {
                VisitCategoryDto(it.id, it.name, it.countsForCountryPoints, it.icon, it.sortOrder, it.isArchived, it.meta.dto())
            },
            regionCollections = dao.regionCollections().sortedBy { it.countryIso2 }.map { RegionCollectionDto(it.countryIso2, it.enabled, it.meta.dto()) },
            places = dao.places().sortedBy { it.sortKey() }.map {
                PlaceDto(
                    it.id, it.name, it.kind.name, it.countryIso2, it.regionCode, it.latitude, it.longitude, it.cityName,
                    it.parentPlaceId, it.osmId, it.poiCategory, it.alias, it.isFavorite, it.note, it.isHomebase, it.meta.dto(),
                    it.address, it.homebaseSince?.toString(),
                )
            },
            stays = dao.stays().sortedBy { it.sortKey() }.map { w ->
                val s = w.stay
                StayDto(
                    s.id, s.placeId, s.typeId, s.startDate.toString(), s.endDate.toString(), s.startTime?.hhmm(), s.endTime?.hhmm(),
                    s.precision.name, s.sortIndex, s.tripId, s.note, s.source.name, s.overnight, s.transport,
                    w.persons.map { it.personId }.sorted(), s.meta.dto(),
                )
            },
            visits = dao.visits().sortedBy { it.sortKey() }.map { w ->
                val v = w.visit
                VisitDto(
                    v.id, v.placeId, v.categoryId, v.date.toString(), v.time?.hhmm(), v.precision.name, v.countsForCountryPointsOverride,
                    v.tripId, v.note, v.rating, v.photoUri, v.source.name, v.extraCategoryIds, w.persons.map { it.personId }.sorted(), v.meta.dto(),
                )
            },
            trips = dao.trips().sortedBy { it.sortKey() }.map { w ->
                val t = w.trip
                TripDto(
                    t.id, t.title, t.startDate.toString(), t.endDate.toString(), t.startTime?.hhmm(), t.endTime?.hhmm(),
                    t.precision.name, t.note, w.persons.map { it.personId }.sorted(), t.meta.dto(),
                )
            },
            wishes = dao.wishes().sortedBy { it.sortKey() }.map {
                WishDto(it.id, it.placeId, it.countryIso2, it.personId, it.note, it.priority, it.wishedAt, it.fulfilledVisitId, it.meta.dto())
            },
            patternRules = dao.patternRules().sortedBy { it.sortKey() }.map {
                PatternRuleDto(
                    it.id, it.personId, it.weekday, it.stayTypeId, it.placeId, it.validFrom?.toString(), it.validTo?.toString(),
                    it.templateId, it.meta.dto(),
                )
            },
            dayTemplates = dao.dayTemplates().sortedBy { it.sortKey() }.map { DayTemplateDto(it.id, it.name, it.steps, it.sortOrder, it.meta.dto()) },
        )
    }

    /** Liest eine Sicherung; wirft [IllegalArgumentException] bei fremden oder kaputten Dateien. */
    fun parse(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Keine gültige Locus-Sicherung", e)
        }
        require(file.format == BackupFile.FORMAT) { "Keine Locus-Sicherung" }
        require(file.schemaVersion <= SCHEMA_VERSION) { "Sicherung stammt aus einer neueren App-Version" }
        return file
    }

    suspend fun importJson(text: String): ImportResult = import(parse(text))

    /** Führt eine Sicherung zusammen: je Datensatz gewinnt der neuere Stand (`updatedAt`). */
    suspend fun import(file: BackupFile): ImportResult = db.withTransaction {
        val counter = Counter()
        file.persons.forEach { d ->
            counter.merge(dao.personUpdated(d.id), d.meta) {
                dao.upsertPerson(PersonEntity(d.id, d.name, d.colorArgb, d.isMe, d.meta.entity(), d.birthDate?.let(LocalDate::parse)))
            }
        }
        file.stayTypes.forEach { d ->
            counter.merge(dao.stayTypeUpdated(d.id), d.meta) {
                dao.upsertStayType(
                    StayTypeEntity(d.id, d.name, StayGroup.valueOf(d.group), d.countsForCountryPoints, d.icon, d.colorArgb, d.sortOrder, d.isArchived, d.meta.entity()),
                )
            }
        }
        file.visitCategories.forEach { d ->
            counter.merge(dao.visitCategoryUpdated(d.id), d.meta) {
                dao.upsertVisitCategory(VisitCategoryEntity(d.id, d.name, d.countsForCountryPoints, d.icon, d.sortOrder, d.isArchived, d.meta.entity()))
            }
        }
        file.regionCollections.forEach { d ->
            counter.merge(dao.regionCollectionUpdated(d.countryIso2), d.meta) {
                dao.upsertRegionCollection(RegionCollectionEntity(d.countryIso2, d.enabled, d.meta.entity()))
            }
        }
        file.places.forEach { d ->
            counter.merge(dao.placeUpdated(d.id), d.meta) {
                dao.upsertPlace(
                    PlaceEntity(
                        d.id, d.name, PlaceKind.valueOf(d.kind), d.countryIso2, d.regionCode, d.latitude, d.longitude, d.cityName,
                        d.parentPlaceId, d.osmId, d.poiCategory, d.alias, d.isFavorite, d.note, d.meta.entity(), d.isHomebase,
                        d.address, d.homebaseSince?.let(LocalDate::parse),
                    ),
                )
            }
        }
        file.stays.forEach { d ->
            counter.merge(dao.stayUpdated(d.id), d.meta) {
                dao.upsertStay(
                    StayEntity(
                        d.id, d.placeId, d.typeId, LocalDate.parse(d.startDate), LocalDate.parse(d.endDate),
                        d.startTime?.let(LocalTime::parse), d.endTime?.let(LocalTime::parse), DatePrecision.valueOf(d.precision),
                        d.sortIndex, d.tripId, d.note, EntrySource.valueOf(d.source), d.meta.entity(), d.overnight, d.transport,
                    ),
                )
                dao.clearStayPersons(d.id)
                dao.insertStayPersons(d.persons.map { StayPersonEntity(d.id, it) })
            }
        }
        file.visits.forEach { d ->
            counter.merge(dao.visitUpdated(d.id), d.meta) {
                dao.upsertVisit(
                    VisitEntity(
                        d.id, d.placeId, d.categoryId, LocalDate.parse(d.date), d.time?.let(LocalTime::parse),
                        DatePrecision.valueOf(d.precision), d.countsOverride, d.tripId, d.note, d.rating, d.photoUri,
                        EntrySource.valueOf(d.source), d.meta.entity(), d.extraCategoryIds,
                    ),
                )
                dao.clearVisitPersons(d.id)
                dao.insertVisitPersons(d.persons.map { VisitPersonEntity(d.id, it) })
            }
        }
        file.trips.forEach { d ->
            counter.merge(dao.tripUpdated(d.id), d.meta) {
                dao.upsertTrip(
                    TripEntity(
                        d.id, d.title, LocalDate.parse(d.startDate), LocalDate.parse(d.endDate), d.startTime?.let(LocalTime::parse),
                        d.endTime?.let(LocalTime::parse), DatePrecision.valueOf(d.precision), d.note, d.meta.entity(),
                    ),
                )
                dao.clearTripPersons(d.id)
                dao.insertTripPersons(d.persons.map { TripPersonEntity(d.id, it) })
            }
        }
        file.wishes.forEach { d ->
            counter.merge(dao.wishUpdated(d.id), d.meta) {
                dao.upsertWish(WishEntity(d.id, d.placeId, d.countryIso2, d.personId, d.note, d.priority, d.wishedAt, d.fulfilledVisitId, d.meta.entity()))
            }
        }
        file.patternRules.forEach { d ->
            counter.merge(dao.patternRuleUpdated(d.id), d.meta) {
                dao.upsertPatternRule(
                    PatternRuleEntity(
                        d.id, d.personId, d.weekday, d.stayTypeId, d.placeId, d.validFrom?.let(LocalDate::parse),
                        d.validTo?.let(LocalDate::parse), d.meta.entity(), d.templateId,
                    ),
                )
            }
        }
        file.dayTemplates.forEach { d ->
            counter.merge(dao.dayTemplateUpdated(d.id), d.meta) {
                dao.upsertDayTemplate(DayTemplateEntity(d.id, d.name, d.steps, d.sortOrder, d.meta.entity()))
            }
        }
        counter.result()
    }

    private class Counter {
        var added = 0
        var updated = 0
        var unchanged = 0

        suspend fun merge(existingUpdatedAt: Long?, incoming: MetaDto, write: suspend () -> Unit) {
            when {
                existingUpdatedAt == null -> { write(); added++ }
                incoming.updatedAt > existingUpdatedAt -> { write(); updated++ }
                else -> unchanged++
            }
        }

        fun result() = ImportResult(added, updated, unchanged)
    }

    /** Stabile Reihenfolge (nach ID), damit Sicherungen vergleichbar bleiben. */
    private fun Any.sortKey(): String = when (this) {
        is PersonEntity -> id
        is StayTypeEntity -> id
        is VisitCategoryEntity -> id
        is PlaceEntity -> id
        is io.github.mario2712.locus.data.db.StayWithPersons -> stay.id
        is io.github.mario2712.locus.data.db.VisitWithPersons -> visit.id
        is io.github.mario2712.locus.data.db.TripWithPersons -> trip.id
        is WishEntity -> id
        is PatternRuleEntity -> id
        is DayTemplateEntity -> id
        else -> toString()
    }

    private fun SyncMeta.dto() = MetaDto(createdAt, updatedAt, deleted, originDevice)
    private fun MetaDto.entity() = SyncMeta(createdAt, updatedAt, deleted, originDevice)
    private fun LocalTime.hhmm(): String = withSecond(0).withNano(0).toString()

    companion object {
        /** Muss zur Room-Version passen. */
        const val SCHEMA_VERSION = 5
    }
}
