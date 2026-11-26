package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DaySuggesterTest {
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val rules = listOf(
        PatternRule("mo", Defaults.ME, DayOfWeek.MONDAY, StayTypeIds.HOMEOFFICE, "hallein"),
        PatternRule("di", Defaults.ME, DayOfWeek.TUESDAY, StayTypeIds.OFFICE, "salzburg"),
        PatternRule("sa", Defaults.ME, DayOfWeek.SATURDAY, StayTypeIds.FREE, "hallein"),
        PatternRule("jo", Defaults.JOHANNA, DayOfWeek.MONDAY, StayTypeIds.OFFICE, "linz"),
    )
    private val suggester = DaySuggester()
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Montag Homeoffice Hallein, Dienstag Buero Salzburg`() {
        assertEquals("hallein", suggester.suggest(d("2026-11-16"), Defaults.ME, rules, types)!!.first.placeId)
        assertEquals(StayTypeIds.OFFICE, suggester.suggest(d("2026-11-17"), Defaults.ME, rules, types)!!.first.stayTypeId)
    }

    @Test
    fun `ohne Regel kein Vorschlag, Regeln anderer Personen zaehlen nicht`() {
        assertNull(suggester.suggest(d("2026-11-18"), Defaults.ME, rules, types))
        assertEquals("linz", suggester.suggest(d("2026-11-16"), Defaults.JOHANNA, rules, types)!!.first.placeId)
    }

    @Test
    fun `am Feiertag kein Arbeits-Vorschlag, Freizeit-Regel gilt weiter`() {
        // Ostermontag 2026
        assertNull(suggester.suggest(d("2026-04-06"), Defaults.ME, rules, types))
        // Samstag, 26.10.2024 Nationalfeiertag – Freizeit-Regel bleibt
        assertEquals(StayTypeIds.FREE, suggester.suggest(d("2024-10-26"), Defaults.ME, rules, types)!!.first.stayTypeId)
    }

    @Test
    fun `neuere Regel ersetzt aeltere`() {
        val newer = PatternRule(
            "di-neu", Defaults.ME, DayOfWeek.TUESDAY, StayTypeIds.HOMEOFFICE, "hallein",
            validFrom = d("2026-11-01"),
        )
        assertEquals("di-neu", suggester.suggest(d("2026-11-17"), Defaults.ME, rules + newer, types)!!.ruleId)
        assertEquals("di", suggester.suggest(d("2026-10-27"), Defaults.ME, rules + newer, types)!!.ruleId)
    }

    @Test
    fun `Muster mit Tagesvorlage liefert alle Schritte`() {
        val hybrid = io.github.mario2712.locus.domain.model.DayTemplate(
            "t-hybrid", "Hybrid",
            listOf(
                io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.OFFICE, "salzburg"),
                io.github.mario2712.locus.domain.model.TemplateStep(StayTypeIds.HOMEOFFICE, "hallein"),
            ),
        )
        val thursday = PatternRule("do", Defaults.ME, DayOfWeek.THURSDAY, StayTypeIds.OFFICE, "salzburg", templateId = "t-hybrid")
        val suggestion = suggester.suggest(d("2026-11-19"), Defaults.ME, listOf(thursday), types, mapOf(hybrid.id to hybrid))!!
        assertEquals(listOf("salzburg", "hallein"), suggestion.steps.map { it.placeId })
        assertEquals("Hybrid", suggestion.templateName)
        // An Feiertagen (Fronleichnam 2026 ist ein Donnerstag) entfällt die Vorlage mit Arbeit.
        assertNull(suggester.suggest(d("2026-06-04"), Defaults.ME, listOf(thursday), types, mapOf(hybrid.id to hybrid)))
    }
}
