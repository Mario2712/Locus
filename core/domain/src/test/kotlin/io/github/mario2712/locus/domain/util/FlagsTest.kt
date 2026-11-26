package io.github.mario2712.locus.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlagsTest {
    @Test
    fun `ISO-Code wird zu Flaggen-Emoji`() {
        assertEquals("🇦🇹", flagEmoji("AT"))
        assertEquals("🇸🇮", flagEmoji("si"))
    }

    @Test
    fun `ungueltiger Code wird abgelehnt`() {
        assertThrows<IllegalArgumentException> { flagEmoji("AUT") }
        assertThrows<IllegalArgumentException> { flagEmoji("1A") }
    }
}
