package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.timeline.Homebases
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlaceStatsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val me = Defaults.ME
    private val jo = Defaults.JOHANNA

    @Test
    fun `Kennzahlen - Abstand, Wochentage, Jahre, Personen`() {
        val visits = listOf(
            Visit("v1", "lidl", "shop", d("2025-12-27"), participants = setOf(me, jo)), // Sa
            Visit("v2", "lidl", "shop", d("2026-01-03"), participants = setOf(me)), // Sa
            Visit("v3", "lidl", "shop", d("2026-01-08"), participants = setOf(me, jo)), // Do
        )
        val r = PlaceStats.record("lidl", me, emptyList(), visits)
        assertEquals(6.0, r.averageIntervalDays) // 12 Tage / 2 Abstände
        assertEquals(2, r.byWeekday[DayOfWeek.SATURDAY])
        assertEquals(1, r.byWeekday[DayOfWeek.THURSDAY])
        assertEquals(listOf(2026 to 2, 2025 to 1), r.byYear)
        assertEquals(mapOf(jo to 2), r.daysWith)
        assertEquals(2, r.daysSince(d("2026-01-01")))
    }

    @Test
    fun `Aufenthalte - Hotels einer Stadt zusammengefasst, Zuhause ausgenommen`() {
        val resolved = listOf(
            Stay("a", "hotelA", "holiday", d("2026-09-12"), d("2026-09-14"), participants = setOf(me), overnight = true),
            Stay("b", "hotelB", "holiday", d("2026-09-14"), d("2026-09-16"), participants = setOf(me), overnight = true),
            Stay("c", "hotelA", "holiday", d("2026-10-01"), d("2026-10-02"), participants = setOf(me), overnight = true),
            Stay("home", "city", "home", d("2026-01-01"), d("2026-09-12"), participants = setOf(me), overnight = true),
        )
        val r = PlaceStats.record("city", me, resolved, emptyList(), setOf("hotelA", "hotelB"), Homebases.single("city"))
        assertEquals(listOf(StayPeriod(d("2026-10-01"), d("2026-10-02")), StayPeriod(d("2026-09-12"), d("2026-09-16"))), r.stayPeriods)
        assertEquals(5, r.nights)
    }
}
