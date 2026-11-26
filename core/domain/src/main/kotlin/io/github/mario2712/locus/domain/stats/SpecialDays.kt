package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.day.DayClassifier
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Homebases
import io.github.mario2712.locus.domain.timeline.TimelineIndex
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** Wo die Person an einem bestimmten Tag war. */
data class DayWhere(
    val date: LocalDate,
    val placeId: String,
    val countryIso2: String?,
    /** Außerhalb des Landes der damaligen Homebase. */
    val abroad: Boolean,
)

/** Alter beim ersten (datierten) Besuch eines Landes. */
data class FirstVisitAge(val countryIso2: String, val firstDate: LocalDate, val age: Int)

/** Jahrestage: Silvester, Weihnachten, Geburtstage und „Alter beim ersten Besuch“. */
object SpecialDays {

    /**
     * Ort am Tag: Station der Hauptart, sonst die Unterkunft, sonst ein Besuch, sonst – innerhalb des erfassten
     * Zeitraums – die damalige Homebase. Vor dem ersten Eintrag: `null`.
     */
    fun whereOn(
        date: LocalDate,
        index: TimelineIndex,
        visitsByDate: Map<LocalDate, List<Visit>>,
        places: Map<String, Place>,
        homebases: Homebases,
        classifier: DayClassifier,
        firstEntry: LocalDate?,
    ): DayWhere? {
        if (firstEntry == null || date < firstEntry) return null
        val dayStays = index.covering(date)
        val summary = classifier.classify(date, dayStays)
        val placeId = summary?.let { s -> dayStays.lastOrNull { it.typeId == s.mainTypeId }?.placeId }
            ?: index.baseAtStart(date)?.placeId
            ?: visitsByDate[date]?.firstOrNull()?.placeId
            ?: homebases.at(date)
            ?: return null
        fun country(id: String?) = id?.let { places[it] }?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
        val iso = country(placeId)
        return DayWhere(date, placeId, iso, iso != null && iso != country(homebases.at(date)))
    }

    /** Ein Jahrestag (z. B. 31.12.) für jedes Jahr ab dem ersten Eintrag bis [today], neueste zuerst. */
    fun annual(
        day: MonthDay,
        person: PersonId,
        resolved: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        homebases: Homebases,
        rank: (String) -> Int,
        today: LocalDate,
    ): List<DayWhere> {
        val index = TimelineIndex(resolved, person)
        val ownVisits = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val visitsByDate = ownVisits.groupBy { it.date }
        val first = (index.own.map { it.startDate } + ownVisits.map { it.date }).minOrNull() ?: return emptyList()
        val classifier = DayClassifier(rank)
        return (today.year downTo first.year).mapNotNull { year ->
            val date = day.atYear(year)
            if (date > today) return@mapNotNull null
            whereOn(date, index, visitsByDate, places, homebases, classifier, first)
        }
    }

    /** Alter beim ersten datierten Besuch je Land (ohne Homebase-Land), jüngstes zuerst. */
    fun firstVisitAges(
        birthDate: LocalDate,
        countryStats: Collection<CountryStatsLike>,
        homeCountries: Set<String>,
    ): List<FirstVisitAge> = countryStats
        .filter { it.dateKnown && it.countryIso2 !in homeCountries && !it.firstDate.isBefore(birthDate) }
        .map { FirstVisitAge(it.countryIso2, it.firstDate, ChronoUnit.YEARS.between(birthDate, it.firstDate).toInt()) }
        .sortedWith(compareBy({ it.age }, { it.firstDate }))
}

/** Schmale Sicht auf Länder-Kennzahlen (entkoppelt von der Länderpunkt-Berechnung). */
interface CountryStatsLike {
    val countryIso2: String
    val firstDate: LocalDate
    val dateKnown: Boolean
}
