package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.DaySummary
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.places.PlaceCandidates
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.trips.TripSummary
import java.time.DayOfWeek
import java.time.LocalDate

/** Rekorde im Zeitraum. */
data class Records(
    val longestTrip: TripSummary? = null,
    val mostCountriesTrip: TripSummary? = null,
    /** Weitester Ort von der (damaligen) Homebase, Luftlinie in Metern. */
    val farthestPlaceId: String? = null,
    val farthestMeters: Double = 0.0,
    /** Längste Serie von Arbeitstagen im Homeoffice (freie Tage unterbrechen nicht). */
    val homeofficeStreak: Int = 0,
)

/** Detail-Statistiken für die Reiter „Besuche“, „Unterwegs“, „Arbeit“ und „Rekorde“. */
data class DetailStats(
    /** Besuche je Kategorie (mehrere Kategorien zählen jeweils). */
    val visitsByCategory: Map<String, Int> = emptyMap(),
    /** Meistbesuchte Örtlichkeiten (Anzahl Besuche), höchstens 5. */
    val topPlaces: List<Pair<String, Int>> = emptyList(),
    /** Meistbesuchte Örtlichkeiten je Kategorie, höchstens 5. */
    val topPlacesByCategory: Map<String, List<Pair<String, Int>>> = emptyMap(),
    /** Städte bzw. Örtlichkeiten, die im Zeitraum zum ersten Mal vorkommen. */
    val newCityIds: List<String> = emptyList(),
    val newLocationIds: List<String> = emptyList(),
    /** Etappen je Verkehrsmittel (Stationen mit diesem Verkehrsmittel). */
    val transportLegs: Map<TransportMode, Int> = emptyMap(),
    /** Kilometer (Luftlinie) je Verkehrsmittel; bei mehreren zählt das „größte“ (Flug vor Zug vor Auto …). */
    val kmByTransport: Map<TransportMode, Double> = emptyMap(),
    val records: Records = Records(),
    /** Arbeitsarten je Wochentag (Tage, an denen die Art vorkommt). */
    val workByWeekday: Map<DayOfWeek, Map<String, Int>> = emptyMap(),
) {
    val flights: Int get() = transportLegs[TransportMode.PLANE] ?: 0
    val totalKm: Double get() = kmByTransport.values.sum()
}

object DetailStatsCalculator {

    /** Reihenfolge, welches Verkehrsmittel einer Etappe die Kilometer bekommt. */
    private val dominance = listOf(
        TransportMode.PLANE, TransportMode.TRAIN, TransportMode.CAR, TransportMode.BUS, TransportMode.FERRY,
        TransportMode.TAXI, TransportMode.TRAM, TransportMode.CABLE_CAR, TransportMode.BIKE, TransportMode.WALK,
    )

    fun dominant(modes: Set<TransportMode>): TransportMode? = dominance.firstOrNull { it in modes }

    fun compute(
        from: LocalDate,
        to: LocalDate,
        person: PersonId,
        /** Aufgelöste (und ggf. gefilterte) Stationen. */
        resolved: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        homebases: Homebases,
        summaries: Map<LocalDate, DaySummary>,
        trips: List<TripSummary>,
        today: LocalDate,
    ): DetailStats {
        val own = resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val index = io.github.mario2712.locus.domain.timeline.TimelineIndex(own, person)
        val ownVisits = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val rangeVisits = ownVisits.filter { it.date in from..to }

        // Besuche
        val visitsByCategory = rangeVisits.flatMap { it.categoryIds }.groupingBy { it }.eachCount()
        fun top(list: List<Visit>) = list.groupingBy { it.placeId }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { places[it.first]?.displayName })
            .take(5)
        val topPlaces = top(rangeVisits)
        val topByCategory = visitsByCategory.keys.associateWith { cat -> top(rangeVisits.filter { cat in it.categoryIds }) }

        // Neu entdeckt (erster Eintrag überhaupt im Zeitraum)
        fun cityOf(id: String): String? = places[id]?.let { if (it.kind == PlaceKind.CITY) it.id else it.parentPlaceId }
        val firstSeen = mutableMapOf<String, LocalDate>()
        fun seen(id: String, date: LocalDate) {
            val current = firstSeen[id]
            if (current == null || date < current) firstSeen[id] = date
        }
        own.forEach { s -> seen(s.placeId, s.startDate); cityOf(s.placeId)?.let { seen(it, s.startDate) } }
        ownVisits.forEach { v -> seen(v.placeId, v.date); cityOf(v.placeId)?.let { seen(it, v.date) } }
        val homeIds = homebases.placeIds
        val newIds = firstSeen.filter { it.value in from..to }.keys.filter { it !in homeIds }
        val newCities = newIds.filter { places[it]?.kind == PlaceKind.CITY }.sortedBy { firstSeen[it] }
        val newLocations = newIds.filter { places[it]?.kind == PlaceKind.POI }.sortedBy { firstSeen[it] }

        // Verkehrsmittel und Kilometer
        fun coords(id: String?): Pair<Double, Double>? {
            val place = id?.let(places::get) ?: return null
            val lat = place.latitude
            val lon = place.longitude
            if (lat != null && lon != null) return lat to lon
            return place.parentPlaceId?.let(places::get)?.let { p -> p.latitude?.let { la -> p.longitude?.let { lo -> la to lo } } }
        }
        fun km(a: String?, b: String?): Double {
            val ca = coords(a) ?: return 0.0
            val cb = coords(b) ?: return 0.0
            return PlaceCandidates.distanceMeters(ca.first, ca.second, cb.first, cb.second) / 1000.0
        }
        val legs = mutableMapOf<TransportMode, Int>()
        val kms = mutableMapOf<TransportMode, Double>()
        val byStart = own.filter { it.startDate in from..to }.groupBy { it.startDate }
        fun isTransit(stay: Stay) = stay.typeId == StayTypeIds.TRANSIT || stay.typeId == StayTypeIds.ON_THE_WAY
        // Endet ein Tag an einem Transit-Halt (z. B. Abflughafen), geht es am nächsten Tag von dort weiter.
        var carry: Pair<LocalDate, String>? = null
        byStart.toSortedMap().forEach { (date, dayStays) ->
            val chain = dayStays.sortedWith(Timeline.chronological)
            var previous: String? = index.baseAtStart(date)?.placeId
                ?: carry?.takeIf { it.first == date.minusDays(1) }?.second
                ?: homebases.at(date)
            var lastMode: TransportMode? = null
            chain.forEach { stay ->
                stay.transport.forEach { legs[it] = (legs[it] ?: 0) + 1 }
                val mode = dominant(stay.transport)
                if (mode != null) kms[mode] = (kms[mode] ?: 0.0) + km(previous, stay.placeId)
                lastMode = mode
                previous = stay.placeId
            }
            // Implizite Heimkehr (z. B. nach dem Büro) mit demselben Verkehrsmittel wie der Hinweg
            val home = homebases.at(date)
            val endsAtHome = index.baseAtEnd(date, homebases, today) == null
            val mode = lastMode
            val endsInTransit = isTransit(chain.last())
            if (endsAtHome && !endsInTransit && home != null && previous != home && mode != null) {
                kms[mode] = (kms[mode] ?: 0.0) + km(previous, home)
            }
            carry = if (endsInTransit) date to chain.last().placeId else null
        }

        // Rekorde
        val rangeTrips = trips.filter { it.precision == DatePrecision.DAY && it.start <= to && it.end >= from }
        var farthestId: String? = null
        var farthest = 0.0
        val candidates = own.filter { it.startDate <= to && it.endDate >= from }.map { it.placeId to maxOf(it.startDate, from) } +
            rangeVisits.map { it.placeId to it.date }
        candidates.distinct().forEach { (placeId, date) ->
            val d = km(homebases.at(date), placeId) * 1000
            if (d > farthest) { farthest = d; farthestId = placeId }
        }
        var streak = 0
        var best = 0
        summaries.toSortedMap().filterKeys { it in from..to }.values.forEach { day ->
            if (stayTypes[day.mainTypeId]?.group != StayGroup.WORK) return@forEach
            if (day.mainTypeId == StayTypeIds.HOMEOFFICE) { streak++; best = maxOf(best, streak) } else streak = 0
        }

        // Arbeit je Wochentag
        val weekday = mutableMapOf<DayOfWeek, MutableMap<String, Int>>()
        summaries.filterKeys { it in from..to }.forEach { (date, day) ->
            day.typeIds.filter { stayTypes[it]?.group == StayGroup.WORK }.forEach { type ->
                val m = weekday.getOrPut(date.dayOfWeek) { mutableMapOf() }
                m[type] = (m[type] ?: 0) + 1
            }
        }

        return DetailStats(
            visitsByCategory = visitsByCategory,
            topPlaces = topPlaces,
            topPlacesByCategory = topByCategory,
            newCityIds = newCities,
            newLocationIds = newLocations,
            transportLegs = legs,
            kmByTransport = kms,
            records = Records(
                longestTrip = rangeTrips.maxByOrNull { it.days },
                mostCountriesTrip = rangeTrips.filter { it.countryIso2s.size > 1 }.maxByOrNull { it.countryIso2s.size },
                farthestPlaceId = farthestId,
                farthestMeters = farthest,
                homeofficeStreak = best,
            ),
            workByWeekday = weekday,
        )
    }
}
