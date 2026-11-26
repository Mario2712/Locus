package io.github.mario2712.locus.domain.share

import java.net.URLDecoder

/** Aus Google Maps geteilter Ort: Name (falls mitgeschickt), Link und – sobald bekannt – Koordinaten. */
data class SharedMapsPlace(
    val name: String?,
    val url: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Suchtext aus dem Link (z. B. „Café Sacher, Philharmoniker Str. 4, Wien“), falls keine Koordinaten drin sind. */
    val query: String? = null,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

/** Liest geteilte Google-Maps-Texte und -Links (ohne Netz; Kurzlinks löst die App separat auf). */
object GoogleMapsLink {

    private val urlRegex = Regex("""https?://\S+""")

    /** Kurzlinks, die erst über eine Weiterleitung zur eigentlichen Adresse führen. */
    fun isShortLink(url: String): Boolean =
        url.contains("maps.app.goo.gl") || url.contains("goo.gl/maps") || url.contains("g.co/kgs")

    fun isMapsLink(url: String): Boolean =
        isShortLink(url) || Regex("""(google\.[a-z.]+/maps|maps\.google\.)""").containsMatchIn(url)

    /** Text aus „Teilen“ (z. B. „Café Sacher\nhttps://maps.app.goo.gl/…“). `null`, wenn kein Maps-Link darin ist. */
    fun parseShared(text: String): SharedMapsPlace? {
        val url = urlRegex.find(text)?.value?.trimEnd('.', ',', ')') ?: return null
        if (!isMapsLink(url)) return null
        val name = text.replace(url, "").lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val parsed = fromUrl(url)
        return parsed.copy(name = name ?: parsed.name, url = url)
    }

    /** Koordinaten und Name aus einer (aufgelösten) Maps-Adresse. Bevorzugt die Ortsmarke (!3d…!4d…) vor dem Kartenausschnitt (@…). */
    fun fromUrl(url: String): SharedMapsPlace {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        val pin = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""").find(decoded)
        val coordQuery = Regex("""[?&](?:q|query|ll|destination)=(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""").find(decoded)
        val view = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""").find(decoded)
        val coords = (pin ?: coordQuery ?: view)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?.takeIf { (lat, lon) -> lat in -90.0..90.0 && lon in -180.0..180.0 }
        val name = Regex("""/maps/place/([^/@?]+)""").find(decoded)?.groupValues?.get(1)
            ?.replace('+', ' ')?.trim()?.takeIf { it.isNotEmpty() && !it.first().isDigit() }
        val query = Regex("""[?&](?:q|query)=([^&]+)""").find(decoded)?.groupValues?.get(1)
            ?.replace('+', ' ')?.trim()?.takeIf { coords == null && it.isNotEmpty() }
        return SharedMapsPlace(name, url, coords?.first, coords?.second, query)
    }
}
