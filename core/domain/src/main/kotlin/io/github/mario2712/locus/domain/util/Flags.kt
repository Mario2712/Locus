package io.github.mario2712.locus.domain.util

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/**
 * Wandelt einen ISO-3166-1-alpha-2-Code in ein Flaggen-Emoji um, z. B. "AT" → 🇦🇹.
 * Auf Pixel-Geräten (Noto Color Emoji) werden diese Flaggen nativ dargestellt.
 */
fun flagEmoji(iso2: String): String {
    require(iso2.length == 2 && iso2.all { it.uppercaseChar() in 'A'..'Z' }) {
        "Ungültiger ISO-3166-1-alpha-2-Code: $iso2"
    }
    return iso2.uppercase()
        .map { Character.toChars(REGIONAL_INDICATOR_A + (it - 'A')) }
        .joinToString("") { String(it) }
}
