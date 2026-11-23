package io.github.mario2712.locus.domain.perf

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.stats.YearStats
import io.github.mario2712.locus.domain.timeline.ImplicitHome
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.domain.timeline.TimelineDays
import io.github.mario2712.locus.domain.trips.Trips
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/**
 * Benchmark mit 12 Jahren künstlicher Daten. Die Grenzen sind großzügig (CI-Rechner schwanken);
 * der Test schlägt an, wenn eine Änderung wieder eine Suche „pro Tag über alle Einträge“ einführt.
 */
class StatsPerformanceTest {
    private val data = SyntheticData.generate(years = 12)
    private val types = Defaults.stayTypes.associateBy { it.id }
    private val categories = Defaults.visitCategories.associateBy { it.id }

    private fun timed(label: String, block: () -> Unit): Long {
        block() // Aufwärmen (JIT)
        val ms = measureTimeMillis(block)
        println("Benchmark $label: $ms ms (${data.stays.size} Stationen, ${data.visits.size} Besuche)")
        return ms
    }

    @Test
    fun `Gesamtstatistik ueber 12 Jahre`() {
        val ms = timed("Gesamtstatistik") {
            YearStats.compute(
                data.from, data.today, Defaults.ME, data.stays, data.visits, data.places, types, categories,
                StayPriority.ranker(types), data.homebases, data.today, allTime = true,
            )
        }
        assertTrue(ms < 4_000, "Gesamtstatistik zu langsam: $ms ms")
    }

    @Test
    fun `Jahre im Ueberblick - 13 Jahresstatistiken`() {
        val ms = timed("13 Jahre") {
            (data.today.year downTo data.from.year).forEach { y ->
                YearStats.compute(
                    java.time.LocalDate.of(y, 1, 1), java.time.LocalDate.of(y, 12, 31), Defaults.ME, data.stays, data.visits,
                    data.places, types, categories, StayPriority.ranker(types), data.homebases, data.today,
                )
            }
        }
        assertTrue(ms < 15_000, "Jahresstatistiken zu langsam: $ms ms")
    }

    @Test
    fun `Karte, Timeline und Reisen`() {
        val ms = timed("Karte/Timeline/Reisen") {
            val resolved = Timeline.resolve(data.stays, data.homebases, data.today)
            ImplicitHome.augment(resolved, data.visits, Defaults.ME, data.homebases, data.today)
            TimelineDays.build(resolved, data.visits, Defaults.ME, data.today)
            Trips.derive(data.stays, data.visits, data.places, Defaults.ME, data.homebases, data.today)
        }
        assertTrue(ms < 3_000, "Karte/Timeline zu langsam: $ms ms")
    }
}
