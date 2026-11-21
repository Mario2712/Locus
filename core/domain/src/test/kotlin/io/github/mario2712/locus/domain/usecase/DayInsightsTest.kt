package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults.JOHANNA
import io.github.mario2712.locus.domain.reference.Defaults.ME
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayInsightsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val stays = listOf(
        Stay("1", "hallein", "home", d("2026-11-16"), participants = setOf(ME)),
        Stay("2", "salzburg", "office", d("2026-11-17"), participants = setOf(ME)),
        Stay("3", "hallein", "home", d("2026-11-18"), participants = setOf(ME)),
        Stay("4", "ljubljana", "vacation", d("2026-11-20"), d("2026-11-22"), participants = setOf(ME, JOHANNA)),
        Stay("5", "linz", "office", d("2026-11-19"), participants = setOf(JOHANNA)),
    )

    @Test
    fun `fehlende Tage`() {
        assertEquals(listOf(d("2026-11-19")), DayInsights.missingDays(d("2026-11-16"), d("2026-11-22"), ME, stays))
    }

    @Test
    fun `letzte Kombinationen ohne Duplikate, neueste zuerst`() {
        val combos = DayInsights.recentCombos(ME, stays)
        assertEquals(
            listOf(StayCombo("vacation", "ljubljana"), StayCombo("home", "hallein"), StayCombo("office", "salzburg")),
            combos,
        )
    }

    @Test
    fun `Tageszusammenfassungen je Person`() {
        val summaries = DayInsights.summaries(d("2026-11-16"), d("2026-11-22"), JOHANNA, stays)
        assertEquals(setOf(d("2026-11-19"), d("2026-11-20"), d("2026-11-21"), d("2026-11-22")), summaries.keys)
        assertEquals("office", summaries.getValue(d("2026-11-19")).mainTypeId)
    }
}
