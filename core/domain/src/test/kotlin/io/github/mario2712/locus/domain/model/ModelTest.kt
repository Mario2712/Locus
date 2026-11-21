package io.github.mario2712.locus.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDate

class ModelTest {

    @Test
    fun `Muster gilt nur am Wochentag und im Gueltigkeitszeitraum`() {
        val rule = PatternRule(
            "r", PersonId("me"), DayOfWeek.TUESDAY, "office", "sbg",
            validFrom = LocalDate.parse("2026-01-01"), validTo = LocalDate.parse("2026-12-31"),
        )
        assertTrue(rule.appliesTo(LocalDate.parse("2026-11-17")))   // Dienstag
        assertFalse(rule.appliesTo(LocalDate.parse("2026-11-18")))  // Mittwoch
        assertFalse(rule.appliesTo(LocalDate.parse("2027-01-05")))  // Dienstag, aber außerhalb
    }

    @Test
    fun `Wunsch braucht genau einen Ort oder ein Land`() {
        Wish("w1", placeId = "p", createdAtEpochMillis = 0)
        Wish("w2", countryIso2 = "JP", createdAtEpochMillis = 0)
        assertThrows<IllegalArgumentException> { Wish("w3", createdAtEpochMillis = 0) }
        assertThrows<IllegalArgumentException> { Wish("w4", placeId = "p", countryIso2 = "JP", createdAtEpochMillis = 0) }
    }

    @Test
    fun `Aufenthalt braucht Personen und gueltigen Zeitraum`() {
        val d = LocalDate.parse("2026-11-20")
        assertThrows<IllegalArgumentException> { Stay("s", "p", "t", d, participants = emptySet()) }
        assertThrows<IllegalArgumentException> { Stay("s", "p", "t", d, d.minusDays(1), participants = setOf(PersonId("me"))) }
    }

    @Test
    fun `Alias ersetzt den Anzeigenamen`() {
        val place = Place("p", "Hallein", PlaceKind.CITY, "AT", alias = "Zuhause")
        assertEquals("Zuhause", place.displayName)
    }
}
