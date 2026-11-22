package io.github.mario2712.locus.domain.trips

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.domain.timeline.Timeline
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Eine aus der Timeline abgeleitete Reise. */
data class TripSummary(
    /** ID der ersten Basis-Station (stabil, solange die Reise existiert). */
    val id: String,
    val start: LocalDate,
    val end: LocalDate,
    val precision: DatePrecision,
    val ongoing: Boolean,
    /** Basen in Reihenfolge (z. B. Taschkent, Samarkand). */
    val basePlaceIds: List<String>,
    val stays: List<Stay>,
    val visits: List<Visit>,
    /** Zählbare Länder der Reise (nur Stationen/Besuche, die Länderpunkte geben würden, ohne Transit). */
    val countryIso2s: List<String>,
    val participants: Set<PersonId>,
    val typeId: String,
    /** Arten aller Basen (z. B. Dienstreise + Urlaub bei verlängerter Dienstreise). */
    val baseTypeIds: Set<String> = setOf(typeId),
) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
}

/**
 * Leitet Reisen aus den Stationen ab: zusammenhängende Basen außerhalb der Homebase
 * (Übernachtungs-Stationen oder mehrtägige Stationen) samt vorgelagerten Transit-Stopps.
 * Unscharf erfasste Reisen (Monat/Jahr) sind je Zeitraum eine eigene Reise.
 */
object Trips {

    /**
     * Automatischer Name: die Städte der Basen (bei Örtlichkeiten wie Hotels deren Stadt), z. B.
     * „Linz“ oder „Taschkent → Samarkand“.
     */
    fun defaultTitle(trip: TripSummary, places: Map<String, Place>): String =
        trip.basePlaceIds.map { id ->
            val place = places[id]
            val city = place?.parentPlaceId?.let { places[it] }
            city?.displayName ?: place?.cityName?.takeIf { place.kind != io.github.mario2712.locus.domain.model.PlaceKind.CITY }
                ?: place?.displayName ?: "?"
        }.fold(mutableListOf<String>()) { acc, name -> if (acc.lastOrNull() != name) acc += name; acc }
            .joinToString(" → ")

    fun derive(
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        person: PersonId,
        homebaseId: String?,
        today: LocalDate,
    ): List<TripSummary> = derive(stays, visits, places, person, Homebases.single(homebaseId), today)

    fun derive(
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        person: PersonId,
        homebases: Homebases,
        today: LocalDate,
    ): List<TripSummary> {
        val resolved = Timeline.resolve(stays, homebases, today).filter { person in it.participants }
        val ownVisits = visits.filter { person in it.participants }
        val currentBase = Timeline.baseAtEnd(today, person, resolved, homebases, today)

        val exact = resolved.filter { it.precision == DatePrecision.DAY }
        val bases = exact
            .filter { (it.overnight || it.endDate > it.startDate) && !homebases.isHome(it.placeId, it.startDate) }
            .sortedWith(Timeline.chronological)

        val groups = mutableListOf<MutableList<Stay>>()
        for (base in bases) {
            val last = groups.lastOrNull()
            if (last != null && last.maxOf { it.endDate } >= base.startDate) last += base else groups += mutableListOf(base)
        }

        val exactTrips = groups.map { group ->
            val baseStart = group.first().startDate
            val end = group.maxOf { it.endDate }
            // Transit-Stopps direkt davor (z. B. Abflughafen am Vortag) gehören zur Reise.
            val transitBefore = exact.filter {
                it.typeId == Defaults.StayTypeIds.TRANSIT && !it.startDate.isAfter(baseStart) &&
                    !it.startDate.isBefore(baseStart.minusDays(2))
            }
            val start = (transitBefore.map { it.startDate } + baseStart).min()
            val inRange = exact.filter { it.startDate <= end && it.endDate >= start && !homebases.isHome(it.placeId, it.startDate) }
            build(
                group, start, end, DatePrecision.DAY,
                ongoing = currentBase != null && group.any { it.id == currentBase.id },
                stays = inRange,
                visits = ownVisits.filter { it.date in start..end },
                places = places,
            )
        }

        val fuzzyTrips = resolved.filter { it.precision == DatePrecision.MONTH || it.precision == DatePrecision.YEAR }
            .groupBy { Triple(it.startDate, it.endDate, it.precision) }
            .map { (key, group) ->
                build(group, key.first, key.second, key.third, ongoing = false, stays = group, visits = emptyList(), places = places)
            }

        return (exactTrips + fuzzyTrips).sortedByDescending { it.start }
    }

    private fun build(
        bases: List<Stay>,
        start: LocalDate,
        end: LocalDate,
        precision: DatePrecision,
        ongoing: Boolean,
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
    ): TripSummary {
        val countryPlaces = stays.filter { it.typeId != Defaults.StayTypeIds.TRANSIT && it.typeId != Defaults.StayTypeIds.ON_THE_WAY }
            .map { it.placeId } + visits.map { it.placeId }
        val countries = countryPlaces.mapNotNull { places[it]?.let { p -> ReferenceData.countableCountry(p.countryIso2)?.iso2 } }.distinct()
        return TripSummary(
            id = bases.first().id,
            start = start,
            end = end,
            precision = precision,
            ongoing = ongoing,
            basePlaceIds = bases.map { it.placeId }.distinct(),
            stays = stays.sortedWith(Timeline.chronological),
            visits = visits.sortedBy { it.date },
            countryIso2s = countries,
            participants = bases.flatMap { it.participants }.toSet(),
            typeId = bases.first().typeId,
            baseTypeIds = bases.mapTo(linkedSetOf()) { it.typeId },
        )
    }
}
