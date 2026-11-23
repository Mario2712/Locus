package io.github.mario2712.locus.domain.perf

import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.timeline.Homebases
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** Künstliche, aber realistische Daten (Arbeitsalltag, Einkäufe, Reisen) für Tests und Benchmarks. */
object SyntheticData {
    class Data(
        val stays: List<Stay>,
        val visits: List<Visit>,
        val places: Map<String, Place>,
        val homebases: Homebases,
        val from: LocalDate,
        val today: LocalDate,
    )

    fun generate(years: Int, today: LocalDate = LocalDate.parse("2026-11-23"), seed: Int = 42): Data {
        val random = Random(seed)
        val me = Defaults.ME
        val jo = Defaults.JOHANNA
        val cities = (0 until 60).map { i ->
            Place("city$i", "Stadt $i", PlaceKind.CITY, listOf("AT", "DE", "IT", "FR", "ES", "VN", "US", "JP")[i % 8],
                latitude = 30.0 + i % 30, longitude = -10.0 + i * 2.0)
        }
        val pois = (0 until 300).map { i -> Place("poi$i", "Ort $i", PlaceKind.POI, cities[i % 60].countryIso2, parentPlaceId = cities[i % 60].id) }
        val base = listOf(
            Place("hallein", "Hallein", PlaceKind.CITY, "AT", latitude = 47.68, longitude = 13.10),
            Place("home", "Zuhause", PlaceKind.ADDRESS, "AT", parentPlaceId = "hallein", isHomebase = true),
            Place("salzburg", "Salzburg", PlaceKind.CITY, "AT", latitude = 47.80, longitude = 13.04),
            Place("office", "Büro", PlaceKind.POI, "AT", parentPlaceId = "salzburg"),
        )
        val places = (base + cities + pois).associateBy { it.id }
        val stays = mutableListOf<Stay>()
        val visits = mutableListOf<Visit>()
        val from = today.minusYears(years.toLong())
        var n = 0
        var d = from
        while (!d.isAfter(today)) {
            // Alle ~60 Tage eine Reise von 4–14 Tagen
            if (random.nextInt(60) == 0) {
                val length = 4 + random.nextInt(11)
                val dest = cities[random.nextInt(cities.size)]
                stays += Stay("s${n++}", "office", StayTypeIds.TRANSIT, d, participants = setOf(me, jo), transport = setOf(TransportMode.CAR), sortIndex = 0)
                stays += Stay("s${n++}", dest.id, StayTypeIds.VACATION, d, participants = setOf(me, jo), overnight = true, sortIndex = 1,
                    transport = setOf(TransportMode.PLANE))
                repeat(length / 2) { k ->
                    visits += Visit("v${n++}", pois[random.nextInt(pois.size)].id, VisitCategoryIds.SIGHT, d.plusDays(k.toLong() + 1), participants = setOf(me, jo))
                }
                d = d.plusDays(length.toLong())
                if (d.isAfter(today)) break
                stays += Stay("s${n++}", "home", StayTypeIds.FREE, d, participants = setOf(me, jo), transport = setOf(TransportMode.PLANE))
                d = d.plusDays(1)
                continue
            }
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            when {
                weekend -> stays += Stay("s${n++}", "home", StayTypeIds.FREE, d, participants = setOf(me))
                d.dayOfWeek == DayOfWeek.WEDNESDAY -> stays += Stay("s${n++}", "office", StayTypeIds.OFFICE, d, participants = setOf(me),
                    transport = setOf(TransportMode.WALK, TransportMode.TRAIN))
                else -> stays += Stay("s${n++}", "home", StayTypeIds.HOMEOFFICE, d, participants = setOf(me))
            }
            if (random.nextInt(3) == 0) {
                visits += Visit("v${n++}", pois[random.nextInt(pois.size)].id, VisitCategoryIds.SHOPPING, d, participants = setOf(me))
            }
            d = d.plusDays(1)
        }
        return Data(stays, visits, places, Homebases.single("home"), from, today)
    }
}
