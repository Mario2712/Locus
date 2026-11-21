package io.github.mario2712.locus.domain.stats

import io.github.mario2712.locus.domain.countrypoints.CountryStats
import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.ME
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BirthdayStatsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val birth = d("1996-05-10")
    private val today = d("2026-11-21")

    @Test
    fun `Alter und naechster Geburtstag`() {
        assertEquals(30, BirthdayStats.age(birth, today))
        assertEquals(d("2027-05-10"), BirthdayStats.nextBirthday(birth, today))
        assertEquals(d("2026-05-10"), BirthdayStats.nextBirthday(birth, d("2026-05-10")))
    }

    @Test
    fun `29 Februar wird in Nicht-Schaltjahren am 28 gefeiert`() {
        assertEquals(d("2027-02-28"), BirthdayStats.birthdayAt(d("2004-02-29"), 23))
    }

    @Test
    fun `Laender bis zu runden Geburtstagen und naechster Meilenstein`() {
        val countries = listOf(
            CountryStats("AT", d("1996-05-10"), today),
            CountryStats("IT", d("2010-08-01"), today),
            CountryStats("HR", d("2016-07-01"), today),   // nach dem 20.
            CountryStats("SI", d("2026-05-09"), today),   // einen Tag vor dem 30.
            CountryStats("UZ", d("2026-06-01"), today),   // nach dem 30.
        )
        val milestones = BirthdayStats.milestones(birth, countries, today)
        assertEquals(listOf(18, 20, 25, 30, 40), milestones.map { it.age })
        assertEquals(listOf(2, 2, 3, 4), milestones.filter { it.reached }.map { it.countries })
        val next = milestones.last()
        assertFalse(next.reached)
        assertEquals(5, next.countries) // Stand heute
    }

    @Test
    fun `Geburtstagsorte - Station, laufende Reise, Besuch, sonst ausgelassen`() {
        val places = listOf(
            Place("hallein", "Hallein", PlaceKind.CITY, "AT", isHomebase = true),
            Place("rom", "Rom", PlaceKind.CITY, "IT"),
            Place("ljubljana", "Ljubljana", PlaceKind.CITY, "SI"),
            Place("nuuk", "Nuuk", PlaceKind.CITY, "GL"),
        ).associateBy { it.id }
        val stays = listOf(
            Stay("1", "salzburg", StayTypeIds.OFFICE, d("2024-05-10"), participants = setOf(ME)),
            Stay("2", "hallein", StayTypeIds.HOMEOFFICE, d("2024-05-10"), sortIndex = 1, participants = setOf(ME)),
            Stay("3", "rom", StayTypeIds.VACATION, d("2025-05-08"), participants = setOf(ME), overnight = true),
            Stay("4", "hallein", StayTypeIds.FREE, d("2025-05-12"), participants = setOf(ME)),
            Stay("5", "nuuk", StayTypeIds.VACATION, d("2022-05-10"), participants = setOf(ME)),
        )
        val visits = listOf(
            Visit("v", "ljubljana", Defaults.VisitCategoryIds.RESTAURANT, d("2026-05-10"), participants = setOf(ME)),
        )
        val resolved = Timeline.resolve(stays, "hallein", today)
        val entries = BirthdayStats.birthdayPlaces(
            birth, ME, resolved, visits, places, StayPriority.ranker(Defaults.stayTypes.associateBy { it.id }), today,
        )
        assertEquals(listOf(30, 29, 28, 26), entries.map { it.age })
        assertEquals(listOf("ljubljana", "rom", "salzburg", "nuuk"), entries.map { it.placeId })
        assertEquals(listOf("SI", "IT", null, "DK"), entries.map { it.countryIso2 })
        assertTrue(entries.none { it.age == 27 })
    }
}
