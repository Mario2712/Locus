package io.github.mario2712.locus.domain.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleMapsLinkTest {
    @Test
    fun `geteilter Text mit Kurzlink`() {
        val shared = GoogleMapsLink.parseShared("Café Sacher Wien\nhttps://maps.app.goo.gl/AbCdEf123")!!
        assertEquals("Café Sacher Wien", shared.name)
        assertEquals("https://maps.app.goo.gl/AbCdEf123", shared.url)
        assertTrue(GoogleMapsLink.isShortLink(shared.url!!))
        assertNull(shared.latitude)
    }

    @Test
    fun `aufgeloeste Adresse - Ortsmarke vor Kartenausschnitt`() {
        val p = GoogleMapsLink.fromUrl(
            "https://www.google.com/maps/place/Caf%C3%A9+Sacher+Wien/@48.2038,16.3670,17z/data=!3m1!4b1!4m6!3m5!1s0x1:0x2!8m2!3d48.20385!4d16.36915",
        )
        assertEquals("Café Sacher Wien", p.name)
        assertEquals(48.20385, p.latitude)
        assertEquals(16.36915, p.longitude)
    }

    @Test
    fun `Koordinaten aus q-Parameter und kein Maps-Link`() {
        val p = GoogleMapsLink.fromUrl("https://maps.google.com/?q=20.2156,105.9061")
        assertEquals(20.2156, p.latitude)
        assertNull(GoogleMapsLink.parseShared("Schau mal https://example.com/x"))
        val q = GoogleMapsLink.fromUrl("https://maps.google.com/maps?q=Caf%C3%A9+Sacher,+Philharmoniker+Str.+4,+Wien&ftid=0x1:0x2")
        assertNull(q.latitude)
        assertEquals("Café Sacher, Philharmoniker Str. 4, Wien", q.query)
    }
}
