package io.github.mario2712.locus.domain.countrypoints

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.reference.ReferenceData
import java.time.LocalDate

/** Anwesenheit einer Person in einem Land über einen Zeitraum, abgeleitet aus einem Eintrag. */
data class CountryPresence(
    val countryIso2: String,
    val person: PersonId,
    val range: ClosedRange<LocalDate>,
)

/** Kennzahlen eines Landes für eine Person. */
data class CountryStats(
    val countryIso2: String,
    /** Erster bekannter Besuch; [io.github.mario2712.locus.domain.model.UNKNOWN_DATE], wenn nur ohne Datum erfasst. */
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val dateKnown: Boolean get() = firstDate != io.github.mario2712.locus.domain.model.UNKNOWN_DATE
}

class CountryPoints internal constructor(
    private val presences: List<CountryPresence>,
) {
    /** Länderpunkte je Person inkl. erstem/letztem Besuch. */
    val byPerson: Map<PersonId, Map<String, CountryStats>> =
        presences.groupBy { it.person }.mapValues { (_, list) ->
            list.groupBy { it.countryIso2 }.mapValues { (iso, ps) ->
                // Bekannte Daten haben Vorrang; nur ohne Datum erfasste Länder behalten den Platzhalter.
                val known = ps.filter { it.range.start != io.github.mario2712.locus.domain.model.UNKNOWN_DATE }.ifEmpty { ps }
                CountryStats(
                    countryIso2 = iso,
                    firstDate = known.minOf { it.range.start },
                    lastDate = known.maxOf { it.range.endInclusive },
                )
            }
        }

    fun countriesOf(person: PersonId): Set<String> = byPerson[person]?.keys.orEmpty()

    /** Länder, in denen [a] und [b] **zur selben Zeit** waren. */
    fun together(a: PersonId, b: PersonId): Set<String> {
        // Ohne Datum lässt sich „zur selben Zeit“ nur behaupten, wenn es ein gemeinsamer Eintrag ist (gleicher Platzhalter).
        val ofA = presences.filter { it.person == a }.groupBy { it.countryIso2 }
        val ofB = presences.filter { it.person == b }.groupBy { it.countryIso2 }
        return ofA.keys.intersect(ofB.keys).filterTo(sortedSetOf()) { iso ->
            ofA.getValue(iso).any { pa -> ofB.getValue(iso).any { pb -> pa.range.overlaps(pb.range) } }
        }
    }

    /** Länder, in denen beide schon waren – egal ob gemeinsam oder zu unterschiedlichen Zeiten. */
    fun bothVisited(a: PersonId, b: PersonId): Set<String> = countriesOf(a).intersect(countriesOf(b))

    /** Länder, in denen nur [a] war (nicht [b]). */
    fun onlyBy(a: PersonId, b: PersonId): Set<String> = countriesOf(a) - countriesOf(b)
}

/**
 * Berechnet Länderpunkte nach den Locus-Regeln:
 * - Ein Land zählt, sobald ein Aufenthalt oder Besuch dort liegt, dessen Art bzw. Kategorie
 *   für Länderpunkte zählt.
 * - „Transit“, „Anreise/Unterwegs“ und „Rast/Tankstopp“ zählen nicht. Ein Restaurantbesuch oder
 *   eine Sehenswürdigkeit im selben Land gibt dagegen einen Punkt.
 * - Ein Besuch kann die Regel seiner Kategorie per Override überschreiben.
 * - Abhängige Gebiete zählen für ihr Mutterland (Grönland → Dänemark); Orte ohne zählbares
 *   Land (z. B. Antarktis) geben keinen Punkt.
 */
class CountryPointsCalculator {

    fun calculate(
        stays: List<Stay>,
        visits: List<Visit>,
        places: Map<String, Place>,
        stayTypes: Map<String, StayType>,
        visitCategories: Map<String, VisitCategory>,
    ): CountryPoints {
        val fromStays = stays
            .filter { stayTypes[it.typeId]?.countsForCountryPoints == true }
            .flatMap { stay ->
                val iso = countableIso(places[stay.placeId]) ?: return@flatMap emptyList()
                stay.participants.map { CountryPresence(iso, it, stay.dateRange) }
            }

        val fromVisits = visits
            .filter { visit ->
                visit.countsForCountryPointsOverride
                    ?: visit.categoryIds.any { visitCategories[it]?.countsForCountryPoints == true }
            }
            .flatMap { visit ->
                val iso = countableIso(places[visit.placeId]) ?: return@flatMap emptyList()
                visit.participants.map { CountryPresence(iso, it, visit.dateRange) }
            }

        return CountryPoints(fromStays + fromVisits)
    }

    private fun countableIso(place: Place?): String? =
        place?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
}

internal fun <T : Comparable<T>> ClosedRange<T>.overlaps(other: ClosedRange<T>): Boolean =
    start <= other.endInclusive && other.start <= endInclusive
