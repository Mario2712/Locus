package io.github.mario2712.locus.domain.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CountryLocatorTest {
    private fun ring(vararg p: Pair<Double, Double>) = p.map { doubleArrayOf(it.first, it.second) }

    // Grobes „Vietnam“ mit Loch, daneben „Laos“
    private val locator = CountryLocator(
        listOf(
            CountryShape("VN", listOf(listOf(ring(105.0 to 19.0, 107.0 to 19.0, 107.0 to 21.0, 105.0 to 21.0), ring(106.5 to 20.5, 106.9 to 20.5, 106.9 to 20.9, 106.5 to 20.9)))),
            CountryShape("LA", listOf(listOf(ring(102.0 to 19.0, 104.9 to 19.0, 104.9 to 21.0, 102.0 to 21.0)))),
        ),
    )

    @Test
    fun `Ninh Binh liegt in Vietnam, Loch und Meer nicht`() {
        assertEquals("VN", locator.locate(20.21, 105.90))
        assertEquals("LA", locator.locate(20.0, 103.0))
        // im Loch: nächster Randpunkt liegt weiter als die Toleranz → nur über Nähe zum Außenring
        assertEquals(null, CountryLocator(emptyList()).locate(20.0, 105.0))
        assertNull(locator.locate(0.0, 0.0))
    }

    @Test
    fun `knapp vor der Kueste zaehlt das naechste Land`() {
        assertEquals("VN", locator.locate(18.9, 105.0))
    }
}
