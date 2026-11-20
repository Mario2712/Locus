package io.github.mario2712.locus.domain.day

import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Stay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class DayClassifierTest {

    private val me = setOf(PersonId("me"))
    private val classifier = DayClassifier()
    private val day = LocalDate.of(2026, 11, 17)

    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `ganztaegiger Homeoffice-Tag`() {
        val summary = classifier.classify(day, listOf(Stay("1", "hallein", "homeoffice", day, participants = me)))!!
        assertEquals("homeoffice", summary.mainTypeId)
        assertTrue(summary.isExclusively("homeoffice"))
        assertEquals(mapOf("homeoffice" to 1440), summary.minutesByType)
    }

    @Test
    fun `vormittags Buero, ab 12 Uhr Homeoffice - Ende des Bueros wird abgeleitet`() {
        val stays = listOf(
            Stay("1", "salzburg", "buero", day, startTime = t("08:00"), sortIndex = 0, participants = me),
            Stay("2", "hallein", "homeoffice", day, startTime = t("12:00"), endTime = t("17:00"), sortIndex = 1, participants = me),
        )
        val summary = classifier.classify(day, stays)!!
        assertEquals(setOf("buero", "homeoffice"), summary.typeIds)
        assertEquals(mapOf("buero" to 240, "homeoffice" to 300), summary.minutesByType)
        assertEquals("homeoffice", summary.mainTypeId)
        assertFalse(summary.isExclusively("homeoffice"))
    }

    @Test
    fun `ohne Uhrzeiten ist die erste Art die Hauptart`() {
        val stays = listOf(
            Stay("1", "salzburg", "buero", day, sortIndex = 0, participants = me),
            Stay("2", "hallein", "homeoffice", day, sortIndex = 1, participants = me),
        )
        val summary = classifier.classify(day, stays)!!
        assertEquals("buero", summary.mainTypeId)
        assertNull(summary.minutesByType)
    }

    @Test
    fun `vormittags Buero, ab 13 Uhr mehrtaegiger Urlaub`() {
        val friday = LocalDate.of(2026, 11, 20)
        val stays = listOf(
            Stay("1", "salzburg", "buero", friday, sortIndex = 0, participants = me),
            Stay(
                "2", "ljubljana", "urlaub", friday, friday.plusDays(2),
                startTime = t("13:00"), sortIndex = 1, participants = me,
            ),
        )
        val fri = classifier.classify(friday, stays)!!
        assertEquals(mapOf("buero" to 780, "urlaub" to 660), fri.minutesByType)
        assertEquals("buero", fri.mainTypeId)

        val sat = classifier.classify(friday.plusDays(1), stays)!!
        assertTrue(sat.isExclusively("urlaub"))
    }

    @Test
    fun `unscharfe Aufenthalte und leere Tage werden ignoriert`() {
        val fuzzy = Stay(
            "1", "rom", "urlaub", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
            precision = DatePrecision.MONTH, participants = me,
        )
        assertNull(classifier.classify(day, listOf(fuzzy)))
        assertNull(classifier.classify(day, emptyList()))
    }

    @Test
    fun `Statistik zaehlt geteilte Tage in beiden Arten, Hauptart nur einmal`() {
        val days = listOf(
            DaySummary(day, setOf("homeoffice"), "homeoffice", null),
            DaySummary(day.plusDays(1), setOf("buero", "homeoffice"), "homeoffice", null),
            DaySummary(day.plusDays(2), setOf("buero"), "buero", null),
        )
        assertEquals(mapOf("homeoffice" to 2, "buero" to 2), DayStatistics.daysWithType(days))
        assertEquals(mapOf("homeoffice" to 2, "buero" to 1), DayStatistics.daysByMainType(days))
        assertEquals(1, DayStatistics.exclusiveDays(days, "homeoffice"))
    }
}
