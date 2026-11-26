package io.github.mario2712.locus.domain.timeline

import io.github.mario2712.locus.domain.perf.SyntheticData
import io.github.mario2712.locus.domain.reference.Defaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimelineIndexTest {
    @Test
    fun `Index liefert dieselben Basen wie die direkte Suche`() {
        val data = SyntheticData.generate(years = 2)
        val resolved = Timeline.resolve(data.stays, data.homebases, data.today)
        val own = resolved.filter { Defaults.ME in it.participants }
        val index = TimelineIndex(resolved, Defaults.ME)
        var d = data.from
        while (!d.isAfter(data.today)) {
            assertEquals(Timeline.baseAtStart(d, Defaults.ME, own)?.id, index.baseAtStart(d)?.id, "Start $d")
            assertEquals(Timeline.baseAtEnd(d, Defaults.ME, own, data.homebases, data.today)?.id, index.baseAtEnd(d, data.homebases, data.today)?.id, "Ende $d")
            d = d.plusDays(1)
        }
    }
}
