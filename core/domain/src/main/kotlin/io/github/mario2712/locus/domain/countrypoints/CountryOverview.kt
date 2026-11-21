package io.github.mario2712.locus.domain.countrypoints

import io.github.mario2712.locus.domain.model.Continent
import io.github.mario2712.locus.domain.model.Country
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Wish
import io.github.mario2712.locus.domain.reference.ReferenceData
import java.time.LocalDate

/** Wer ein Land schon erlebt hat. */
enum class CountryStatus {
    /** Beide zur selben Zeit. */
    TOGETHER,
    /** Beide, aber zu unterschiedlichen Zeiten. */
    BOTH_SEPARATELY,
    ONLY_A,
    ONLY_B,
    NONE,
}

data class CountryRow(
    val country: Country,
    val status: CountryStatus,
    val firstVisitA: LocalDate?,
    val firstVisitB: LocalDate?,
    val lastVisit: LocalDate?,
    /** Tage mit Station im Land (Person A). */
    val daysA: Int,
    val wished: Boolean,
) {
    val visitedByA: Boolean get() = status == CountryStatus.TOGETHER || status == CountryStatus.BOTH_SEPARATELY || status == CountryStatus.ONLY_A
    val visitedByB: Boolean get() = status == CountryStatus.TOGETHER || status == CountryStatus.BOTH_SEPARATELY || status == CountryStatus.ONLY_B
}

data class ContinentProgress(val continent: Continent, val visitedA: Int, val visitedB: Int, val together: Int, val total: Int)

data class CountryOverviewData(
    val rows: List<CountryRow>,
    val continents: List<ContinentProgress>,
) {
    val countA: Int get() = rows.count { it.visitedByA }
    val countB: Int get() = rows.count { it.visitedByB }
    val together: Int get() = rows.count { it.status == CountryStatus.TOGETHER }
    val onlyA: Int get() = rows.count { it.status == CountryStatus.ONLY_A }
    val onlyB: Int get() = rows.count { it.status == CountryStatus.ONLY_B }
    val total: Int get() = rows.size
}

/** Länderübersicht für zwei Personen (z. B. Ich und Johanna) inkl. Wunschländern. */
object CountryOverview {

    fun build(
        points: CountryPoints,
        a: PersonId,
        b: PersonId?,
        wishes: List<Wish>,
        countryDaysA: Map<String, Int> = emptyMap(),
    ): CountryOverviewData {
        val together = if (b != null) points.together(a, b) else emptySet()
        val statsA = points.byPerson[a].orEmpty()
        val statsB = b?.let { points.byPerson[it] }.orEmpty()
        val wished = wishes.filter { !it.isFulfilled && (it.personId == null || it.personId == a) }
            .mapNotNull { it.countryIso2?.uppercase() }.toSet()

        val rows = ReferenceData.countries.map { country ->
            val inA = country.iso2 in statsA
            val inB = country.iso2 in statsB
            val status = when {
                country.iso2 in together -> CountryStatus.TOGETHER
                inA && inB -> CountryStatus.BOTH_SEPARATELY
                inA -> CountryStatus.ONLY_A
                inB -> CountryStatus.ONLY_B
                else -> CountryStatus.NONE
            }
            CountryRow(
                country = country,
                status = status,
                firstVisitA = statsA[country.iso2]?.firstDate,
                firstVisitB = statsB[country.iso2]?.firstDate,
                lastVisit = listOfNotNull(statsA[country.iso2]?.lastDate, statsB[country.iso2]?.lastDate).maxOrNull(),
                daysA = countryDaysA[country.iso2] ?: 0,
                wished = country.iso2 in wished,
            )
        }
        val continents = Continent.entries.map { continent ->
            val inContinent = rows.filter { it.country.continent == continent }
            ContinentProgress(
                continent = continent,
                visitedA = inContinent.count { it.visitedByA },
                visitedB = inContinent.count { it.visitedByB },
                together = inContinent.count { it.status == CountryStatus.TOGETHER },
                total = inContinent.size,
            )
        }
        return CountryOverviewData(rows, continents)
    }

    /** Tage mit (tagesgenauer) Station je zählbarem Land für eine Person. Erwartet aufgelöste Stationen. */
    fun countryDays(resolved: List<Stay>, places: Map<String, Place>, person: PersonId): Map<String, Int> =
        resolved.asSequence()
            .filter { person in it.participants && it.precision == DatePrecision.DAY }
            .flatMap { stay ->
                val iso = places[stay.placeId]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 }
                    ?: return@flatMap emptySequence()
                generateSequence(stay.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(stay.endDate) }.map { iso to it }
            }
            .distinct()
            .groupingBy { it.first }
            .eachCount()
}
