package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults.JOHANNA
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class StayCommandsTest {
    private val repo = FakeStayRepository()
    private var counter = 0
    private val commands = StayCommands(repo).apply { newId = { "id${++counter}" } }
    private fun d(s: String) = LocalDate.parse(s)
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `Check-in legt ganztaegigen Aufenthalt an, aber nur einmal pro Tag`() = runTest {
        assertNotNull(commands.checkIn(d("2026-11-16"), ME, StayTypeIds.HOMEOFFICE, "hallein"))
        assertNull(commands.checkIn(d("2026-11-16"), ME, StayTypeIds.OFFICE, "salzburg"))
        // Johanna hat ihren eigenen Tag
        assertNotNull(commands.checkIn(d("2026-11-16"), JOHANNA, StayTypeIds.OFFICE, "linz"))
        assertEquals(2, repo.all.size)
    }

    @Test
    fun `Wechsel ab 12 Uhr - Buero endet, Homeoffice beginnt`() = runTest {
        commands.checkIn(d("2026-11-17"), ME, StayTypeIds.OFFICE, "salzburg")
        commands.switchAt(d("2026-11-17"), t("12:00"), ME, StayTypeIds.HOMEOFFICE, "hallein")

        val (office, home) = repo.all
        assertEquals(t("12:00"), office.endTime)
        assertEquals(StayTypeIds.HOMEOFFICE, home.typeId)
        assertEquals(t("12:00"), home.startTime)
        assertEquals(1, home.sortIndex)
    }

    @Test
    fun `Wechsel ab 13 Uhr in einen Kurzurlaub bis Sonntag`() = runTest {
        commands.checkIn(d("2026-11-20"), ME, StayTypeIds.OFFICE, "salzburg")
        val trip = commands.switchAt(
            d("2026-11-20"), t("13:00"), ME, StayTypeIds.VACATION, "ljubljana", endDate = d("2026-11-22"),
        )
        assertEquals(d("2026-11-22"), trip.endDate)
        assertEquals(t("13:00"), repo.all.first { it.typeId == StayTypeIds.OFFICE }.endTime)
    }

    @Test
    fun `Wechsel beendet einen laufenden mehrtaegigen Aufenthalt am Wechseltag`() = runTest {
        repo.upsert(Stay("v", "rom", StayTypeIds.VACATION, d("2026-08-01"), d("2026-08-10"), participants = setOf(ME)))
        commands.switchAt(d("2026-08-05"), t("15:00"), ME, StayTypeIds.ON_THE_WAY, "autobahn")
        val vacation = repo.all.first { it.id == "v" }
        assertEquals(d("2026-08-05"), vacation.endDate)
        assertEquals(t("15:00"), vacation.endTime)
    }

    @Test
    fun `Wechsel ohne bestehenden Aufenthalt legt nur den neuen an`() = runTest {
        commands.switchAt(d("2026-11-18"), t("09:00"), ME, StayTypeIds.FREE, "hallein")
        assertEquals(1, repo.all.size)
        assertEquals(0, repo.all.single().sortIndex)
    }

    @Test
    fun `Mo bis Fr Buero nachtragen - Wochenende, Feiertage und belegte Tage bleiben frei`() = runTest {
        commands.checkIn(d("2026-12-09"), ME, StayTypeIds.HOMEOFFICE, "hallein")
        // Mo 07.12. bis So 13.12.2026; 08.12. ist Mariä Empfängnis
        val created = commands.fillRange(
            d("2026-12-07"), d("2026-12-13"), ME, StayTypeIds.OFFICE, "salzburg", skipWeekendsAndHolidays = true,
        )
        assertEquals(3, created) // 07., 10., 11.
        assertEquals(
            listOf(d("2026-12-07"), d("2026-12-10"), d("2026-12-11")),
            repo.all.filter { it.typeId == StayTypeIds.OFFICE }.map { it.startDate },
        )
    }

    @Test
    fun `Vorlage anwenden - Hybrid-Tag in einem Schritt, nur wenn der Tag leer ist`() = runTest {
        val steps = listOf(
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.OFFICE, "salzburg", setOf(io.github.mario2712.locus.domain.model.TransportMode.TRAIN)),
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.HOMEOFFICE, "hallein"),
        )
        val created = commands.applyTemplate(d("2026-11-19"), setOf(ME), steps)
        assertEquals(listOf(0, 1), created.map { it.sortIndex })
        assertEquals(setOf(io.github.mario2712.locus.domain.model.TransportMode.TRAIN), created.first().transport)
        assertEquals(emptyList<Stay>(), commands.applyTemplate(d("2026-11-19"), setOf(ME), steps))
    }

    @Test
    fun `Station anhaengen setzt Reihenfolge und Live-Uhrzeit`() = runTest {
        commands.addStation(d("2026-11-17"), setOf(ME), StayTypeIds.OFFICE, "salzburg")
        val next = commands.addStation(d("2026-11-17"), setOf(ME), StayTypeIds.HOMEOFFICE, "hallein", liveTime = t("12:03"))
        assertEquals(1, next.sortIndex)
        assertEquals(t("12:03"), next.startTime)
    }

    @Test
    fun `Vorlage am begonnenen Tag wird angehaengt, doppelter Startort entfaellt, Zuhause gemeinsam`() = runTest {
        val day = LocalDate.parse("2026-11-27")
        commands.addStation(day, setOf(ME), StayTypeIds.FREE, "home")
        val steps = listOf(
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.FREE, "home"),
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.TRANSIT, "bahnhof"),
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.OFFICE, "buero"),
            io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.FREE, "home"),
        )
        val defaults = io.github.mario2712.locus.domain.model.EntryDefaults()
        commands.insertTemplate(day, ME, steps) { step -> defaults.participantsFor(step.stayTypeId) }

        val chain = repo.all
        assertEquals(listOf("home", "bahnhof", "buero", "home"), chain.map { it.placeId })
        assertEquals(listOf(0, 1, 2, 3), chain.map { it.sortIndex })
        assertEquals(setOf(ME), chain[2].participants)
        assertEquals(setOf(ME, JOHANNA), chain[3].participants) // „Frei / Zuhause“ standardmäßig gemeinsam
    }
}
