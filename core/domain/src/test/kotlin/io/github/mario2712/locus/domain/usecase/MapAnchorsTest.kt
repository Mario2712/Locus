package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MapAnchorsTest {
    private val me = Defaults.ME
    private val d = LocalDate.parse("2026-09-20")

    @Test
    fun `zuletzt erfasst zuerst, dann Unterkunft und Homebase`() {
        val stays = listOf(Stay("s", "hue-hotel", "stay-vacation", d, participants = setOf(me)).also { it.createdAtMillis = 100 })
        val visits = listOf(
            Visit("v1", "citadel", "visit-sight", d, participants = setOf(me)).also { it.createdAtMillis = 300 },
            Visit("v2", "restaurant", "visit-restaurant", d, participants = setOf(me)).also { it.createdAtMillis = 200 },
            Visit("old", "elsewhere", "visit-sight", d.minusDays(1), participants = setOf(me)).also { it.createdAtMillis = 999 },
        )
        assertEquals(listOf("citadel", "restaurant", "hue-hotel", "base", "home"), MapAnchors.forDay(d, me, stays, visits, "base", "home"))
        assertEquals(listOf("base", "home"), MapAnchors.forDay(d.plusDays(1), me, stays, visits, "base", "home"))
    }
}
