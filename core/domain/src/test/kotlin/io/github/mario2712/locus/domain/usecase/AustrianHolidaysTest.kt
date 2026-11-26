package io.github.mario2712.locus.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AustrianHolidaysTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Ostersonntag`() {
        assertEquals(d("2024-03-31"), AustrianHolidays.easterSunday(2024))
        assertEquals(d("2025-04-20"), AustrianHolidays.easterSunday(2025))
        assertEquals(d("2026-04-05"), AustrianHolidays.easterSunday(2026))
        assertEquals(d("2027-03-28"), AustrianHolidays.easterSunday(2027))
    }

    @Test
    fun `13 Feiertage 2026 mit beweglichen Terminen`() {
        val h = AustrianHolidays.of(2026)
        assertEquals(13, h.size)
        assertEquals("Ostermontag", h[d("2026-04-06")])
        assertEquals("Christi Himmelfahrt", h[d("2026-05-14")])
        assertEquals("Pfingstmontag", h[d("2026-05-25")])
        assertEquals("Fronleichnam", h[d("2026-06-04")])
        assertEquals("Nationalfeiertag", h[d("2026-10-26")])
    }

    @Test
    fun `Karfreitag und Heiligabend sind keine Feiertage`() {
        assertFalse(AustrianHolidays.isHoliday(d("2026-04-03")))
        assertFalse(AustrianHolidays.isHoliday(d("2026-12-24")))
        assertTrue(AustrianHolidays.isHoliday(d("2026-12-08")))
    }
}
