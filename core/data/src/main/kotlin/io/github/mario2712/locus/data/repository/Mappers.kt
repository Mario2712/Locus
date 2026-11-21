package io.github.mario2712.locus.data.repository

import io.github.mario2712.locus.data.db.PatternRuleEntity
import io.github.mario2712.locus.data.db.PersonEntity
import io.github.mario2712.locus.data.db.PlaceEntity
import io.github.mario2712.locus.data.db.RegionCollectionEntity
import io.github.mario2712.locus.data.db.StayEntity
import io.github.mario2712.locus.data.db.StayPersonEntity
import io.github.mario2712.locus.data.db.StayTypeEntity
import io.github.mario2712.locus.data.db.StayWithPersons
import io.github.mario2712.locus.data.db.SyncMeta
import io.github.mario2712.locus.data.db.TripEntity
import io.github.mario2712.locus.data.db.TripPersonEntity
import io.github.mario2712.locus.data.db.TripWithPersons
import io.github.mario2712.locus.data.db.VisitCategoryEntity
import io.github.mario2712.locus.data.db.VisitEntity
import io.github.mario2712.locus.data.db.VisitPersonEntity
import io.github.mario2712.locus.data.db.VisitWithPersons
import io.github.mario2712.locus.data.db.WishEntity
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.RegionCollection
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Trip
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.model.Wish
import java.time.DayOfWeek

internal fun PersonEntity.toDomain() = Person(PersonId(id), name, isMe, colorArgb)
internal fun Person.toEntity(meta: SyncMeta) = PersonEntity(id.value, name, colorArgb, isMe, meta)

internal fun StayTypeEntity.toDomain() =
    StayType(id, name, group, countsForCountryPoints, icon, colorArgb, sortOrder, isArchived)

internal fun StayType.toEntity(meta: SyncMeta) =
    StayTypeEntity(id, name, group, countsForCountryPoints, icon, colorArgb, sortOrder, isArchived, meta)

internal fun VisitCategoryEntity.toDomain() =
    VisitCategory(id, name, countsForCountryPoints, icon, sortOrder, isArchived)

internal fun VisitCategory.toEntity(meta: SyncMeta) =
    VisitCategoryEntity(id, name, countsForCountryPoints, icon, sortOrder, isArchived, meta)

internal fun RegionCollectionEntity.toDomain() = RegionCollection(countryIso2, enabled)
internal fun RegionCollection.toEntity(meta: SyncMeta) = RegionCollectionEntity(countryIso2, enabled, meta)

internal fun PlaceEntity.toDomain() = Place(
    id = id, name = name, kind = kind, countryIso2 = countryIso2, regionCode = regionCode,
    latitude = latitude, longitude = longitude, cityName = cityName, parentPlaceId = parentPlaceId,
    osmId = osmId, poiCategory = poiCategory, alias = alias, isFavorite = isFavorite, note = note,
)

internal fun Place.toEntity(meta: SyncMeta) = PlaceEntity(
    id = id, name = name, kind = kind, countryIso2 = countryIso2.uppercase(), regionCode = regionCode,
    latitude = latitude, longitude = longitude, cityName = cityName, parentPlaceId = parentPlaceId,
    osmId = osmId, poiCategory = poiCategory, alias = alias, isFavorite = isFavorite, note = note, meta = meta,
)

internal fun StayWithPersons.toDomain() = Stay(
    id = stay.id, placeId = stay.placeId, typeId = stay.typeId,
    startDate = stay.startDate, endDate = stay.endDate, startTime = stay.startTime, endTime = stay.endTime,
    precision = stay.precision, sortIndex = stay.sortIndex,
    participants = persons.mapTo(linkedSetOf()) { PersonId(it.personId) },
    tripId = stay.tripId, note = stay.note, source = stay.source,
)

internal fun Stay.toEntity(meta: SyncMeta) = StayEntity(
    id = id, placeId = placeId, typeId = typeId, startDate = startDate, endDate = endDate,
    startTime = startTime, endTime = endTime, precision = precision, sortIndex = sortIndex,
    tripId = tripId, note = note, source = source, meta = meta,
)

internal fun Stay.personEntities() = participants.map { StayPersonEntity(id, it.value) }

internal fun VisitWithPersons.toDomain() = Visit(
    id = visit.id, placeId = visit.placeId, categoryId = visit.categoryId, date = visit.date,
    time = visit.time, precision = visit.precision,
    participants = persons.mapTo(linkedSetOf()) { PersonId(it.personId) },
    countsForCountryPointsOverride = visit.countsForCountryPointsOverride, tripId = visit.tripId,
    note = visit.note, rating = visit.rating, photoUri = visit.photoUri, source = visit.source,
)

internal fun Visit.toEntity(meta: SyncMeta) = VisitEntity(
    id = id, placeId = placeId, categoryId = categoryId, date = date, time = time, precision = precision,
    countsForCountryPointsOverride = countsForCountryPointsOverride, tripId = tripId, note = note,
    rating = rating, photoUri = photoUri, source = source, meta = meta,
)

internal fun Visit.personEntities() = participants.map { VisitPersonEntity(id, it.value) }

internal fun TripWithPersons.toDomain() = Trip(
    id = trip.id, title = trip.title, startDate = trip.startDate, endDate = trip.endDate,
    startTime = trip.startTime, endTime = trip.endTime, precision = trip.precision,
    participants = persons.mapTo(linkedSetOf()) { PersonId(it.personId) }, note = trip.note,
)

internal fun Trip.toEntity(meta: SyncMeta) = TripEntity(
    id = id, title = title, startDate = startDate, endDate = endDate, startTime = startTime,
    endTime = endTime, precision = precision, note = note, meta = meta,
)

internal fun Trip.personEntities() = participants.map { TripPersonEntity(id, it.value) }

internal fun WishEntity.toDomain() = Wish(
    id = id, placeId = placeId, countryIso2 = countryIso2, personId = personId?.let(::PersonId),
    note = note, priority = priority, createdAtEpochMillis = wishedAt, fulfilledVisitId = fulfilledVisitId,
)

internal fun Wish.toEntity(meta: SyncMeta) = WishEntity(
    id = id, placeId = placeId, countryIso2 = countryIso2?.uppercase(), personId = personId?.value,
    note = note, priority = priority, wishedAt = createdAtEpochMillis, fulfilledVisitId = fulfilledVisitId,
    meta = meta,
)

internal fun PatternRuleEntity.toDomain() = PatternRule(
    id = id, personId = PersonId(personId), weekday = DayOfWeek.of(weekday), stayTypeId = stayTypeId,
    placeId = placeId, validFrom = validFrom, validTo = validTo,
)

internal fun PatternRule.toEntity(meta: SyncMeta) = PatternRuleEntity(
    id = id, personId = personId.value, weekday = weekday.value, stayTypeId = stayTypeId,
    placeId = placeId, validFrom = validFrom, validTo = validTo, meta = meta,
)
