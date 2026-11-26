package io.github.mario2712.locus.domain.day

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayClassifierTest {

    private val me = setOf(PersonId("me"))
    private val classifier = DayClassifier(StayPriority.ranker(Defaults.stayTypes.associateBy { it.id }))
    private val day = LocalDate.of(2026, 11, 17)

    @Test
    fun `Homeoffice-Tag`() {
        val summary = classifier.classify(day, listOf(Stay("1", "hallein", StayTypeIds.HOMEOFFICE, day, participants = me)))!!
        assertEquals(StayTypeIds.HOMEOFFICE, summary.mainTypeId)
        assertTrue(summary.isExclusively(StayTypeIds.HOMEOFFICE))
    }

    @Test
    fun `Buero dann Homeoffice - Hauptart Buero, beide Arten zaehlen`() {
        val stays = listOf(
            Stay("1", "salzburg", StayTypeIds.OFFICE, day, sortIndex = 0, participants = me),
            Stay("2", "hallein", StayTypeIds.HOMEOFFICE, day, sortIndex = 1, participants = me),
        )
        val summary = classifier.classify(day, stays)!!
        assertEquals(setOf(StayTypeIds.OFFICE, StayTypeIds.HOMEOFFICE), summary.typeIds)
        assertEquals(StayTypeIds.OFFICE, summary.mainTypeId)
        assertFalse(summary.isExclusively(StayTypeIds.HOMEOFFICE))
    }

    @Test
    fun `Buero dann Urlaub - Hauptart Urlaub, unabhaengig von der Reihenfolge`() {
        val stays = listOf(
            Stay("1", "salzburg", StayTypeIds.OFFICE, day, sortIndex = 0, participants = me),
            Stay("2", "ljubljana", StayTypeIds.VACATION, day, day.plusDays(2), sortIndex = 1, participants = me),
        )
        assertEquals(StayTypeIds.VACATION, classifier.classify(day, stays)!!.mainTypeId)
        assertTrue(classifier.classify(day.plusDays(1), stays)!!.isExclusively(StayTypeIds.VACATION))
    }

    @Test
    fun `ohne Rangfolge gewinnt die erste Station`() {
        val stays = listOf(
            Stay("1", "a", "x", day, sortIndex = 0, participants = me),
            Stay("2", "b", "y", day, sortIndex = 1, participants = me),
        )
        assertEquals("x", DayClassifier().classify(day, stays)!!.mainTypeId)
    }

    @Test
    fun `unscharfe Aufenthalte und leere Tage werden ignoriert`() {
        val fuzzy = Stay(
            "1", "rom", StayTypeIds.VACATION, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
            precision = DatePrecision.MONTH, participants = me,
        )
        assertNull(classifier.classify(day, listOf(fuzzy)))
        assertNull(classifier.classify(day, emptyList()))
    }

    @Test
    fun `Statistik zaehlt geteilte Tage in beiden Arten, Hauptart nur einmal`() {
        val days = listOf(
            DaySummary(day, setOf("homeoffice"), "homeoffice"),
            DaySummary(day.plusDays(1), setOf("buero", "homeoffice"), "buero"),
            DaySummary(day.plusDays(2), setOf("buero"), "buero"),
        )
        assertEquals(mapOf("homeoffice" to 2, "buero" to 2), DayStatistics.daysWithType(days))
        assertEquals(mapOf("homeoffice" to 1, "buero" to 2), DayStatistics.daysByMainType(days))
        assertEquals(1, DayStatistics.exclusiveDays(days, "homeoffice"))
    }
}
