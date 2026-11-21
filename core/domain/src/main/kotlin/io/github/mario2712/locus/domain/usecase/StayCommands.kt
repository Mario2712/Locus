package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.repository.StayRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Schreibende Abläufe rund um Aufenthalte: Einchecken, Wechseln, Nachtragen. */
class StayCommands(
    private val stays: StayRepository,
) {
    var newId: () -> String = { UUID.randomUUID().toString() }

    private suspend fun dayStays(date: LocalDate, person: PersonId): List<Stay> =
        stays.observeBetween(date, date).first()
            .filter { person in it.participants && it.precision == DatePrecision.DAY }

    /**
     * Ganztägiger Aufenthalt für [date], sofern die Person an dem Tag noch nichts eingetragen hat.
     * @return der neue Aufenthalt oder `null`, wenn der Tag schon belegt ist.
     */
    suspend fun checkIn(
        date: LocalDate,
        person: PersonId,
        stayTypeId: String,
        placeId: String,
        source: EntrySource = EntrySource.MANUAL,
    ): Stay? {
        if (dayStays(date, person).isNotEmpty()) return null
        val stay = Stay(
            id = newId(), placeId = placeId, typeId = stayTypeId, startDate = date,
            participants = setOf(person), source = source,
        )
        stays.upsert(stay)
        return stay
    }

    /**
     * „Wechsel ab [time]“: beendet den laufenden Aufenthalt zu [time] und beginnt einen neuen.
     * Ein laufender mehrtägiger Aufenthalt endet am [date]. Der neue Aufenthalt kann bis
     * [endDate] dauern (z. B. „ab 13:00 Kurzurlaub bis Sonntag“).
     */
    suspend fun switchAt(
        date: LocalDate,
        time: LocalTime,
        person: PersonId,
        stayTypeId: String,
        placeId: String,
        endDate: LocalDate = date,
        tripId: String? = null,
    ): Stay {
        require(!endDate.isBefore(date)) { "Ende darf nicht vor dem Wechsel liegen" }
        val today = dayStays(date, person)
            .sortedWith(compareBy({ if (it.startDate < date) 0 else 1 }, { it.sortIndex }))

        val current = today.lastOrNull { stay ->
            val startedBefore = stay.startDate < date || stay.startTime == null || stay.startTime < time
            val endsLater = stay.endDate > date || stay.endTime == null || stay.endTime > time
            startedBefore && endsLater
        }
        if (current != null) {
            stays.upsert(current.copy(endDate = date, endTime = time))
        }

        val next = Stay(
            id = newId(), placeId = placeId, typeId = stayTypeId,
            startDate = date, endDate = endDate, startTime = time,
            sortIndex = (today.maxOfOrNull { it.sortIndex } ?: -1) + 1,
            participants = setOf(person), tripId = tripId,
        )
        stays.upsert(next)
        return next
    }

    /** Nächster freier sortIndex für einen weiteren Aufenthalt an [date]. */
    suspend fun nextSortIndex(date: LocalDate, person: PersonId): Int =
        (dayStays(date, person).maxOfOrNull { it.sortIndex } ?: -1) + 1

    /**
     * Mehrere Tage auf einmal setzen (z. B. „Mo–Fr Büro“). Bereits belegte Tage bleiben unverändert.
     * @return Anzahl neu angelegter Tage.
     */
    suspend fun fillRange(
        from: LocalDate,
        to: LocalDate,
        person: PersonId,
        stayTypeId: String,
        placeId: String,
        skipWeekendsAndHolidays: Boolean,
        isHoliday: (LocalDate) -> Boolean = AustrianHolidays::isHoliday,
    ): Int {
        require(!to.isBefore(from)) { "Ende darf nicht vor Beginn liegen" }
        val existing = stays.observeBetween(from, to).first()
            .filter { person in it.participants && it.precision == DatePrecision.DAY }
        var created = 0
        var day = from
        while (!day.isAfter(to)) {
            val nonWorkday = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(day)
            val occupied = existing.any { it.covers(day) }
            if (!occupied && !(skipWeekendsAndHolidays && nonWorkday)) {
                stays.upsert(
                    Stay(
                        id = newId(), placeId = placeId, typeId = stayTypeId, startDate = day,
                        participants = setOf(person),
                    ),
                )
                created++
            }
            day = day.plusDays(1)
        }
        return created
    }
}
