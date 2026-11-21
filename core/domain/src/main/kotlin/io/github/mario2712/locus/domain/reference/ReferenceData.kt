package io.github.mario2712.locus.domain.reference

import io.github.mario2712.locus.domain.model.Continent
import io.github.mario2712.locus.domain.model.Country
import io.github.mario2712.locus.domain.model.Region

/**
 * Statische Stammdaten: zählbare Länder, Regionen und die Zuordnung abhängiger Gebiete.
 * Die Daten liegen im Code (nicht in der Datenbank), weil sie sich nur mit App-Updates ändern.
 */
object ReferenceData {

    val countries: List<Country> = COUNTRY_DATA

    private val byIso2: Map<String, Country> = countries.associateBy { it.iso2 }

    val regions: List<Region> = REGION_DATA

    /**
     * Abhängige Gebiete → Mutterland. Ein Besuch auf Grönland zählt als Länderpunkt Dänemark,
     * Hongkong als China, Réunion als Frankreich usw.
     */
    val territoryToCountry: Map<String, String> = mapOf(
        // Dänemark
        "GL" to "DK", "FO" to "DK",
        // USA
        "PR" to "US", "GU" to "US", "VI" to "US", "AS" to "US", "MP" to "US", "UM" to "US",
        // China
        "HK" to "CN", "MO" to "CN",
        // Frankreich
        "RE" to "FR", "GP" to "FR", "MQ" to "FR", "GF" to "FR", "YT" to "FR", "PM" to "FR",
        "BL" to "FR", "MF" to "FR", "PF" to "FR", "NC" to "FR", "WF" to "FR", "TF" to "FR",
        // Niederlande
        "AW" to "NL", "CW" to "NL", "SX" to "NL", "BQ" to "NL",
        // Vereinigtes Königreich
        "AI" to "GB", "BM" to "GB", "IO" to "GB", "VG" to "GB", "KY" to "GB", "FK" to "GB",
        "GI" to "GB", "MS" to "GB", "PN" to "GB", "SH" to "GB", "GS" to "GB", "TC" to "GB",
        "GG" to "GB", "JE" to "GB", "IM" to "GB",
        // Finnland, Norwegen
        "AX" to "FI", "SJ" to "NO", "BV" to "NO",
        // Australien
        "CX" to "AU", "CC" to "AU", "NF" to "AU", "HM" to "AU",
        // Neuseeland
        "CK" to "NZ", "NU" to "NZ", "TK" to "NZ",
    )

    fun country(iso2: String): Country? = byIso2[iso2.uppercase()]

    /**
     * Liefert das Land, für das ein Ort mit ISO-Code [rawIso2] einen Länderpunkt gibt:
     * das Land selbst, das Mutterland eines Gebiets, oder `null` (z. B. Antarktis).
     */
    fun countableCountry(rawIso2: String): Country? {
        val iso = rawIso2.uppercase()
        return byIso2[iso] ?: territoryToCountry[iso]?.let(byIso2::get)
    }

    fun countriesOf(continent: Continent): List<Country> = countries.filter { it.continent == continent }

    fun regionsOf(countryIso2: String): List<Region> = regions.filter { it.countryIso2 == countryIso2.uppercase() }

    fun region(code: String): Region? = regions.firstOrNull { it.code == code }
}
