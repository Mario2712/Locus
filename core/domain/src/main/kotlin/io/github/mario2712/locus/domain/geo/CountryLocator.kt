package io.github.mario2712.locus.domain.geo

import io.github.mario2712.locus.domain.places.PlaceCandidates

/** Umriss eines Landes: Polygone aus Ringen (erster = außen, weitere = Löcher), Punkte als (Länge, Breite). */
data class CountryShape(val iso2: String, val polygons: List<List<List<DoubleArray>>>)

/**
 * Bestimmt offline das Land zu Koordinaten (Punkt-in-Polygon über die App-eigenen Umrisse).
 * Die Umrisse sind vereinfacht: Liegt ein Punkt knapp daneben (Küste), zählt das nächste Land
 * innerhalb von [toleranceMeters].
 */
class CountryLocator(private val shapes: List<CountryShape>, private val toleranceMeters: Double = 30_000.0) {

    fun locate(latitude: Double, longitude: Double): String? {
        shapes.firstOrNull { shape -> shape.polygons.any { contains(it, longitude, latitude) } }?.let { return it.iso2 }
        var best: String? = null
        var bestDistance = toleranceMeters
        shapes.forEach { shape ->
            shape.polygons.forEach { polygon ->
                polygon.first().forEach { p ->
                    val d = PlaceCandidates.distanceMeters(latitude, longitude, p[1], p[0])
                    if (d < bestDistance) { bestDistance = d; best = shape.iso2 }
                }
            }
        }
        return best
    }

    private fun contains(polygon: List<List<DoubleArray>>, x: Double, y: Double): Boolean =
        inRing(polygon.first(), x, y) && polygon.drop(1).none { inRing(it, x, y) }

    /** Strahlverfahren (Ray Casting). */
    private fun inRing(ring: List<DoubleArray>, x: Double, y: Double): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
