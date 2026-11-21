package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import java.time.LocalDate

/**
 * Die Homebase ist an Tagen mit Einträgen implizit dabei: Wer dort aufwacht oder schläft, war an dem
 * Tag auch zuhause (Bürotag: Zuhause ▸ Büro ▸ Zuhause). Für Orts-, Städte-, Länder- und Kartenzählungen
 * werden dafür synthetische Aufenthalte ergänzt. Für die Tagesart (Klassifizierung) nicht verwenden.
 */
object ImplicitHome {

    const val TYPE_ID = "implicit-home"

    fun isImplicit(stay: Stay): Boolean = stay.typeId == TYPE_ID

    /**
     * @param resolved aufgelöste Stationen ([Timeline.resolve])
     * @return [resolved] plus je Tag mit Einträgen einen Homebase-Aufenthalt, wenn die Person
     *   morgens oder abends an der Homebase war und dort nicht ohnehin eine Station hat.
     */
    fun augment(
        resolved: List<Stay>,
        visits: List<Visit>,
        person: PersonId,
        homebaseId: String?,
        today: LocalDate,
    ): List<Stay> = augment(resolved, visits, person, Homebases.single(homebaseId), today)

    fun augment(
        resolved: List<Stay>,
        visits: List<Visit>,
        person: PersonId,
        homebases: Homebases,
        today: LocalDate,
    ): List<Stay> {
        if (homebases.periods.isEmpty()) return resolved
        val own = resolved.filter { person in it.participants && it.precision == DatePrecision.DAY }
        val days = (
            own.flatMap { stay -> generateSequence(stay.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(stay.endDate) }.toList() } +
                visits.filter { person in it.participants && it.precision == DatePrecision.DAY }.map { it.date }
            ).toSortedSet()
        val extra = days.mapNotNull { day ->
            val home = homebases.at(day) ?: return@mapNotNull null
            val atHome = own.none { it.placeId == home && it.covers(day) } &&
                (Timeline.baseAtStart(day, person, own) == null || Timeline.baseAtEnd(day, person, own, homebases, today) == null)
            if (!atHome) return@mapNotNull null
            Stay(id = "implicit-${person.value}-$day", placeId = home, typeId = TYPE_ID, startDate = day, participants = setOf(person))
        }
        return resolved + extra
    }
}
