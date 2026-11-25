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
    /** Längste Serie von Arbeitstagen mit Büro als Hauptart. */
    val officeStreak: Int = 0,
    /** Himmelsrichtungen: nördlichster, südlichster, östlichster, westlichster Ort. */
    val northmost: String? = null,
    val southmost: String? = null,
    val eastmost: String? = null,
    val westmost: String? = null,
    /** Längste Abwesenheit: Tage am Stück nicht zuhause (über Reisen hinweg). */
    val longestAbsenceDays: Int = 0,
    val longestAbsenceFrom: java.time.LocalDate? = null,
    /** Längste Pause zwischen zwei Reisen (Tage). */
    val longestPauseDays: Int = 0,
    val longestPauseFrom: java.time.LocalDate? = null,
    /** Längste Strecke an einem Tag (Luftlinie, Meter) und der Tag. */
    val longestDayMeters: Double = 0.0,
    val longestDay: java.time.LocalDate? = null,
    /** Weitester Tagesausflug (ohne Übernachtung auswärts). */
    val farthestDayTripPlaceId: String? = null,
    val farthestDayTripMeters: Double = 0.0,
    /** Land außerhalb des Homebase-Landes mit den meisten Tagen. */
    val favoriteCountry: String? = null,
    val favoriteCountryDays: Int = 0,
    /** Meiste Nächte am Stück in derselben Unterkunft. */
    val longestStayPlaceId: String? = null,
    val longestStayNights: Int = 0,
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
        val dayKm = mutableMapOf<LocalDate, Double>()
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
                if (mode != null) {
                    val leg = km(previous, stay.placeId)
                    kms[mode] = (kms[mode] ?: 0.0) + leg
                    dayKm[date] = (dayKm[date] ?: 0.0) + leg
                }
                lastMode = mode
                previous = stay.placeId
            }
            // Implizite Heimkehr (z. B. nach dem Büro) mit demselben Verkehrsmittel wie der Hinweg
            val home = homebases.at(date)
            val endsAtHome = index.baseAtEnd(date, homebases, today) == null
            val mode = lastMode
            val endsInTransit = isTransit(chain.last())
            if (endsAtHome && !endsInTransit && home != null && previous != home && mode != null) {
                val leg = km(previous, home)
                kms[mode] = (kms[mode] ?: 0.0) + leg
                dayKm[date] = (dayKm[date] ?: 0.0) + leg
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
        var officeRun = 0
        var officeBest = 0
        summaries.toSortedMap().filterKeys { it in from..to }.values.forEach { day ->
            if (stayTypes[day.mainTypeId]?.group != StayGroup.WORK) return@forEach
            if (day.mainTypeId == StayTypeIds.HOMEOFFICE) { streak++; best = maxOf(best, streak) } else streak = 0
            if (day.mainTypeId == StayTypeIds.OFFICE) { officeRun++; officeBest = maxOf(officeBest, officeRun) } else officeRun = 0
        }

        // Himmelsrichtungen (alle Orte mit Koordinaten im Zeitraum)
        val located = candidates.map { it.first }.distinct().mapNotNull { id -> coords(id)?.let { id to it } }
        // Längste Abwesenheit: Nächte am Stück nicht an der Homebase
        var absenceRun = 0
        var absenceBest = 0
        var absenceStart: LocalDate? = null
        var absenceBestStart: LocalDate? = null
        var day = from
        while (!day.isAfter(minOf(to, today))) {
            if (index.baseAtEnd(day, homebases, today) != null) {
                if (absenceRun == 0) absenceStart = day
                absenceRun++
                if (absenceRun > absenceBest) { absenceBest = absenceRun; absenceBestStart = absenceStart }
            } else {
                absenceRun = 0
            }
            day = day.plusDays(1)
        }
        // Längste Reisepause: zwischen Reisen bzw. von der letzten Reise bis heute
        val sortedTrips = rangeTrips.sortedBy { it.start }
        var pauseBest = 0L
        var pauseFrom: LocalDate? = null
        sortedTrips.zipWithNext().forEach { (a, b) ->
            val gap = java.time.temporal.ChronoUnit.DAYS.between(a.end, b.start) - 1
            if (gap > pauseBest) { pauseBest = gap; pauseFrom = a.end.plusDays(1) }
        }
        sortedTrips.lastOrNull()?.takeIf { !it.ongoing }?.let { last ->
            val gap = java.time.temporal.ChronoUnit.DAYS.between(last.end, minOf(to, today))
            if (gap > pauseBest) { pauseBest = gap; pauseFrom = last.end.plusDays(1) }
        }
        // Weitester Tagesausflug: Tag beginnt und endet zuhause
        var dayTripBest = 0.0
        var dayTripPlace: String? = null
        byStart.forEach { (date, dayStays) ->
            if (index.baseAtStart(date) != null || index.baseAtEnd(date, homebases, today) != null) return@forEach
            // Endet der Tag an einem Transit-Halt (z. B. Abflughafen), ist es kein Tagesausflug, sondern Reisebeginn.
            if (isTransit(dayStays.sortedWith(Timeline.chronological).last())) return@forEach
            (dayStays.map { it.placeId } + rangeVisits.filter { it.date == date }.map { it.placeId }).forEach { id ->
                val d = km(homebases.at(date), id) * 1000
                if (d > dayTripBest) { dayTripBest = d; dayTripPlace = id }
            }
        }
        // Lieblingsland im Ausland (Tage mit zählenden Stationen)
        fun countryOf(id: String) = places[id]?.let { io.github.mario2712.locus.domain.reference.ReferenceData.countableCountry(it.countryIso2)?.iso2 }
        val countryDays = mutableMapOf<String, MutableSet<LocalDate>>()
        own.filter { stayTypes[it.typeId]?.countsForCountryPoints == true && it.startDate <= to && it.endDate >= from }.forEach { s ->
            val iso = countryOf(s.placeId) ?: return@forEach
            var d = maxOf(s.startDate, from)
            while (!d.isAfter(minOf(s.endDate, to))) {
                if (iso != homebases.at(d)?.let(::countryOf)) countryDays.getOrPut(iso) { mutableSetOf() } += d
                d = d.plusDays(1)
            }
        }
        val favorite = countryDays.maxByOrNull { it.value.size }
        // Längster Aufenthalt am selben Ort (Übernachtungs-Basen, Nächte bis zum nächsten Wechsel)
        val longestStay = own.filter { it.overnight && it.startDate in from..to && !homebases.isHome(it.placeId, it.startDate) }
            .maxByOrNull { java.time.temporal.ChronoUnit.DAYS.between(it.startDate, it.endDate) }

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
                officeStreak = officeBest,
                northmost = located.maxByOrNull { it.second.first }?.first,
                southmost = located.minByOrNull { it.second.first }?.first,
                eastmost = located.maxByOrNull { it.second.second }?.first,
                westmost = located.minByOrNull { it.second.second }?.first,
                longestAbsenceDays = if (absenceBest > 0) absenceBest + 1 else 0,
                longestAbsenceFrom = absenceBestStart,
                longestPauseDays = pauseBest.toInt(),
                longestPauseFrom = pauseFrom,
                longestDayMeters = (dayKm.maxByOrNull { it.value }?.value ?: 0.0) * 1000,
                longestDay = dayKm.maxByOrNull { it.value }?.key,
                farthestDayTripPlaceId = dayTripPlace,
                farthestDayTripMeters = dayTripBest,
                favoriteCountry = favorite?.key,
                favoriteCountryDays = favorite?.value?.size ?: 0,
                longestStayPlaceId = longestStay?.placeId,
                longestStayNights = longestStay?.let { java.time.temporal.ChronoUnit.DAYS.between(it.startDate, it.endDate).toInt() } ?: 0,
            ),
            workByWeekday = weekday,
        )
    }
}
