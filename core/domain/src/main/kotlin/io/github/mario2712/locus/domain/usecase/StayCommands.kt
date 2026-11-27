package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.repository.StayRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Ein Abschnitt einer Reise: Wegpunkt (Umstieg, Abflughafen) oder Ziel. */
data class JourneyLeg(
    val placeId: String,
    val transport: Set<TransportMode> = emptySet(),
    /** Ankunft relativ zum Reisebeginn, z. B. 1 für „Ankunft am Folgetag“. */
    val dayOffset: Int = 0,
)

/** Schreibende Abläufe rund um Stationen: Einchecken, Weiter, Reisen, Nachtragen. */
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
     * Hängt eine Station an die Kette von [date] an („Weiter nach …“).
     * [liveTime] wird bei Live-Erfassung automatisch gesetzt, beim Nachtragen bleibt sie leer.
     */
    suspend fun addStation(
        date: LocalDate,
        participants: Set<PersonId>,
        stayTypeId: String,
        placeId: String,
        transport: Set<TransportMode> = emptySet(),
        overnight: Boolean = false,
        liveTime: LocalTime? = null,
        source: EntrySource = EntrySource.MANUAL,
    ): Stay {
        val stay = Stay(
            id = newId(), placeId = placeId, typeId = stayTypeId, startDate = date,
            startTime = liveTime, sortIndex = nextSortIndex(date, participants.first()),
            participants = participants, source = source, overnight = overnight, transport = transport,
        )
        stays.upsert(stay)
        return stay
    }

    /**
     * Wendet eine Tagesvorlage an (z. B. „Büro → Homeoffice“), sofern der Tag noch leer ist.
     * @return angelegte Stationen, leer wenn der Tag schon belegt war.
     */
    suspend fun applyTemplate(
        date: LocalDate,
        participants: Set<PersonId>,
        steps: List<TemplateStep>,
        source: EntrySource = EntrySource.PATTERN,
    ): List<Stay> {
        if (dayStays(date, participants.first()).isNotEmpty()) return emptyList()
        val created = steps.mapIndexed { index, step ->
            Stay(
                id = newId(), placeId = step.placeId, typeId = step.stayTypeId, startDate = date,
                sortIndex = index, participants = participants, source = source,
                overnight = step.overnight, transport = step.transport,
            )
        }
        created.forEach { stays.upsert(it) }
        return created
    }

    /**
     * Vorlage einfügen: An einem leeren Tag alle Schritte; sonst hinten angehängt, wobei der erste Schritt
     * entfällt, wenn er der letzten Station des Tages entspricht (gleicher Ort und gleiche Art).
     * Teilnehmer je Schritt über [participantsFor] (z. B. „Frei / Zuhause“ gemeinsam).
     */
    suspend fun insertTemplate(
        date: LocalDate,
        person: PersonId,
        steps: List<TemplateStep>,
        source: EntrySource = EntrySource.PATTERN,
        participantsFor: (TemplateStep) -> Set<PersonId>,
    ): List<Stay> {
        val existing = dayStays(date, person).sortedBy { it.sortIndex }
        val last = existing.lastOrNull()
        val toAdd = if (last != null && steps.firstOrNull()?.let { it.placeId == last.placeId && it.stayTypeId == last.typeId } == true) {
            steps.drop(1)
        } else steps
        val start = (last?.sortIndex ?: -1) + 1
        val created = toAdd.mapIndexed { index, step ->
            Stay(
                id = newId(), placeId = step.placeId, typeId = step.stayTypeId, startDate = date,
                sortIndex = start + index, participants = participantsFor(step) + person, source = source,
                overnight = step.overnight, transport = step.transport,
            )
        }
        created.forEach { stays.upsert(it) }
        return created
    }

    /**
     * Reise starten oder fortsetzen: Wegpunkte werden als „Transit“ (ohne Länderpunkt) angelegt,
     * das letzte Teilstück ist das Ziel mit Übernachtung (neue Basis).
     * Beispiel: München (Abflug, 🚗) → Y (Umstieg, ✈️, +1 Tag) → X (✈️, +1 Tag, Urlaub).
     */
    suspend fun journey(
        startDate: LocalDate,
        participants: Set<PersonId>,
        legs: List<JourneyLeg>,
        destinationTypeId: String,
        liveTime: LocalTime? = null,
    ): List<Stay> {
        require(legs.isNotEmpty()) { "Eine Reise braucht ein Ziel" }
        return legs.mapIndexed { index, leg ->
            val isDestination = index == legs.lastIndex
            val date = startDate.plusDays(leg.dayOffset.toLong())
            addStation(
                date = date,
                participants = participants,
                stayTypeId = if (isDestination) destinationTypeId else Defaults.StayTypeIds.TRANSIT,
                placeId = leg.placeId,
                transport = leg.transport,
                overnight = isDestination,
                liveTime = liveTime.takeIf { index == 0 && leg.dayOffset == 0 },
            )
        }
    }

    /**
     * Vergangene Reise nachtragen, auch mit unscharfem Datum (z. B. „Italien, Juli 2014“).
     * Je Ziel eine Station über den ganzen Zeitraum; bei genauen Daten mit Übernachtung und festem Ende.
     */
    suspend fun addPastTrip(
        placeIds: List<String>,
        start: LocalDate,
        end: LocalDate,
        precision: DatePrecision,
        participants: Set<PersonId>,
        stayTypeId: String,
    ): List<Stay> {
        require(placeIds.isNotEmpty()) { "Mindestens ein Ziel" }
        require(!end.isBefore(start)) { "Ende darf nicht vor Beginn liegen" }
        val created = placeIds.mapIndexed { index, placeId ->
            Stay(
                id = newId(), placeId = placeId, typeId = stayTypeId, startDate = start, endDate = end,
                precision = precision, sortIndex = index, participants = participants,
                overnight = precision == DatePrecision.DAY && end > start, source = EntrySource.MANUAL,
            )
        }
        created.forEach { stays.upsert(it) }
        return created
    }

    /**
     * „Schon dort gewesen – Datum unbekannt“: Eintrag für Länderpunkte ohne Datum (auf einen Länder-Ort).
     * Erscheint nicht in Timeline, Reisen oder Tagesstatistiken.
     */
    suspend fun markVisitedUndated(countryPlaceId: String, participants: Set<PersonId>): Stay {
        val stay = Stay(
            id = newId(), placeId = countryPlaceId, typeId = Defaults.StayTypeIds.VACATION,
            startDate = io.github.mario2712.locus.domain.model.UNKNOWN_DATE, precision = DatePrecision.UNKNOWN,
            participants = participants, source = EntrySource.MANUAL,
        )
        stays.upsert(stay)
        return stay
    }

    /** Heimreise: Station an der Homebase; damit ist die Homebase wieder die Basis. */
    suspend fun returnHome(
        date: LocalDate,
        participants: Set<PersonId>,
        homebaseId: String,
        transport: Set<TransportMode> = emptySet(),
        liveTime: LocalTime? = null,
    ): Stay = addStation(
        date, participants, Defaults.StayTypeIds.FREE, homebaseId, transport, overnight = false, liveTime = liveTime,
    )

    /**
     * Heimreise mit Zwischenstopps (z. B. Umstieg in München, Landung erst am Folgetag): Stopps als Transit,
     * am Ende die Homebase. [homeDayOffset] und die Offsets der Stopps zählen ab [date].
     */
    suspend fun returnJourney(
        date: LocalDate,
        participants: Set<PersonId>,
        stops: List<JourneyLeg>,
        homebaseId: String,
        homeTransport: Set<TransportMode>,
        homeDayOffset: Int,
        liveTime: LocalTime? = null,
    ): List<Stay> {
        val transit = stops.mapIndexed { index, leg ->
            addStation(
                date = date.plusDays(leg.dayOffset.toLong()),
                participants = participants,
                stayTypeId = Defaults.StayTypeIds.TRANSIT,
                placeId = leg.placeId,
                transport = leg.transport,
                overnight = false,
                liveTime = liveTime.takeIf { index == 0 && leg.dayOffset == 0 },
            )
        }
        val home = addStation(
            date.plusDays(homeDayOffset.toLong()), participants, Defaults.StayTypeIds.FREE, homebaseId, homeTransport,
            overnight = false, liveTime = liveTime.takeIf { stops.isEmpty() && homeDayOffset == 0 },
        )
        return transit + home
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

    /** Platz für eine eingefügte Station: Stationen ab [index] am [date] rücken eins nach hinten. */
    suspend fun makeRoomAt(date: LocalDate, person: PersonId, index: Int) {
        dayStays(date, person).filter { it.startDate == date && it.sortIndex >= index }
            .sortedByDescending { it.sortIndex }
            .forEach { stays.upsert(it.copy(sortIndex = it.sortIndex + 1)) }
    }

    /** Neue Reihenfolge der Stationen eines Tages (z. B. nach Ziehen). */
    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            val stay = stays.get(id) ?: return@forEachIndexed
            if (stay.sortIndex != index) stays.upsert(stay.copy(sortIndex = index))
        }
    }

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
