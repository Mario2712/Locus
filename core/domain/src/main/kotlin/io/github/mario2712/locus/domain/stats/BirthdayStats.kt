package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryStats
import io.github.mario2712.locus.domain.day.DayClassifier
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.timeline.Timeline
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Wo eine Person an einem Geburtstag war. */
data class BirthdayEntry(
    val date: LocalDate,
    val age: Int,
    val placeId: String,
    /** Zählbares Land (Gebiete → Mutterland), sonst `null`. */
    val countryIso2: String?,
)

/** Anzahl Länder bis zu einem runden Geburtstag. */
data class AgeMilestone(
    val age: Int,
    val date: LocalDate,
    val countries: Int,
    val reached: Boolean,
)

/** Statistiken rund um Geburtsdaten. */
object BirthdayStats {

    /** Runde Geburtstage, für die Länderpunkte ausgewiesen werden. */
    val MILESTONE_AGES = listOf(18, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100)

    fun age(birthDate: LocalDate, today: LocalDate): Int = ChronoUnit.YEARS.between(birthDate, today).toInt()

    /** Datum des Geburtstags im Alter [age] (29. Februar → 28. Februar in Nicht-Schaltjahren). */
    fun birthdayAt(birthDate: LocalDate, age: Int): LocalDate = birthDate.plusYears(age.toLong())

    fun nextBirthday(birthDate: LocalDate, today: LocalDate): LocalDate {
        val age = age(birthDate, today)
        val thisYear = birthdayAt(birthDate, age)
        return if (thisYear == today) today else birthdayAt(birthDate, age + 1)
    }

    /**
     * Länder bis zu den runden Geburtstagen: zählt Länder, deren erster Besuch **vor** dem jeweiligen
     * Geburtstag lag. Enthält alle erreichten Meilensteine und den nächsten noch offenen.
     */
    fun milestones(birthDate: LocalDate, countries: Collection<CountryStats>, today: LocalDate): List<AgeMilestone> {
        val reached = MILESTONE_AGES.map { age ->
            val date = birthdayAt(birthDate, age)
            AgeMilestone(age, date, countries.count { it.firstDate < date }, reached = !date.isAfter(today))
        }
        val done = reached.filter { it.reached }
        val next = reached.firstOrNull { !it.reached }?.copy(countries = countries.count { !it.firstDate.isAfter(today) })
        return done + listOfNotNull(next)
    }

    /**
     * Wo die Person an ihren bisherigen Geburtstagen war – soweit eingetragen.
     * Ort = Hauptstation des Tages (Rangfolge); sonst Basis (laufende Reise); sonst ein Besuch.
     * Tage ohne Einträge werden ausgelassen.
     */
    fun birthdayPlaces(
        birthDate: LocalDate,
        person: PersonId,
        resolvedStays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        rank: (String) -> Int,
        today: LocalDate,
    ): List<BirthdayEntry> {
        val own = resolvedStays.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val ownVisits = visits.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val classifier = DayClassifier(rank)
        return (0..age(birthDate, today)).mapNotNull { age ->
            val date = birthdayAt(birthDate, age)
            val dayStays = own.filter { it.covers(date) }
            val summary = classifier.classify(date, dayStays)
            val placeId = summary?.let { s -> dayStays.firstOrNull { it.typeId == s.mainTypeId }?.placeId }
                ?: Timeline.baseAtStart(date, person, own)?.placeId
                ?: ownVisits.firstOrNull { it.date == date }?.placeId
                ?: return@mapNotNull null
            val country = places[placeId]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
            BirthdayEntry(date, age, placeId, country)
        }.sortedByDescending { it.date }
    }
}
