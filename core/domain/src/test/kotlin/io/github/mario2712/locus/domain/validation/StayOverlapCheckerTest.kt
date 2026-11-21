package io.github.mario2712.locus.domain.validation

import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class StayOverlapCheckerTest {

    private val me = PersonId("me")
    private val johanna = PersonId("johanna")
    private val checker = StayOverlapChecker()
    private fun d(s: String) = LocalDate.parse(s)
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `Buero und Homeoffice am selben Tag ohne Uhrzeiten sind kein Konflikt`() {
        val office = Stay("1", "sbg", "office", d("2026-11-17"), participants = setOf(me))
        val home = Stay("2", "hallein", "home", d("2026-11-17"), sortIndex = 1, participants = setOf(me))
        assertTrue(checker.conflicts(home, listOf(office)).isEmpty())
    }

    @Test
    fun `aneinander anschliessende Zeiten sind kein Konflikt`() {
        val office = Stay("1", "sbg", "office", d("2026-11-17"), startTime = t("08:00"), endTime = t("12:00"), participants = setOf(me))
        val home = Stay("2", "hallein", "home", d("2026-11-17"), startTime = t("12:00"), endTime = t("17:00"), participants = setOf(me))
        assertTrue(checker.conflicts(home, listOf(office)).isEmpty())
    }

    @Test
    fun `sich ueberschneidende Uhrzeiten sind ein Konflikt`() {
        val office = Stay("1", "sbg", "office", d("2026-11-17"), startTime = t("08:00"), endTime = t("13:00"), participants = setOf(me))
        val home = Stay("2", "hallein", "home", d("2026-11-17"), startTime = t("12:00"), endTime = t("17:00"), participants = setOf(me))
        val conflicts = checker.conflicts(home, listOf(office))
        assertEquals(1, conflicts.size)
        assertEquals(d("2026-11-17"), conflicts.single().date)
    }

    @Test
    fun `zwei ueberlappende mehrtaegige Urlaube sind ein Konflikt`() {
        val rom = Stay("1", "rom", "vacation", d("2025-05-01"), d("2025-05-10"), participants = setOf(me))
        val paris = Stay("2", "paris", "vacation", d("2025-05-05"), d("2025-05-12"), participants = setOf(me))
        assertEquals(d("2025-05-06"), checker.conflicts(paris, listOf(rom)).single().date)
    }

    @Test
    fun `verschiedene Personen und unscharfe Daten erzeugen keinen Konflikt`() {
        val mine = Stay("1", "rom", "vacation", d("2025-05-01"), d("2025-05-10"), participants = setOf(me))
        val hers = Stay("2", "paris", "vacation", d("2025-05-01"), d("2025-05-10"), participants = setOf(johanna))
        assertTrue(checker.conflicts(hers, listOf(mine)).isEmpty())
        val fuzzy = mine.copy(id = "3", precision = io.github.mario2712.locus.domain.model.DatePrecision.MONTH)
        assertTrue(checker.conflicts(fuzzy, listOf(mine.copy(id = "4", placeId = "x"))).isEmpty())
    }
}
