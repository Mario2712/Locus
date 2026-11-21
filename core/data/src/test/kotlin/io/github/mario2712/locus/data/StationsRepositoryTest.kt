package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.repository.RoomDayTemplateRepository
import io.github.mario2712.locus.data.repository.RoomPatternRuleRepository
import io.github.mario2712.locus.data.repository.RoomPlaceRepository
import io.github.mario2712.locus.data.repository.RoomStayRepository
import io.github.mario2712.locus.data.repository.RoomVisitRepository
import io.github.mario2712.locus.data.seed.DatabaseSeeder
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.PatternRule
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StationsRepositoryTest : DbTestBase() {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `Station mit Uebernachtung und Verkehrsmitteln`() = runTest {
        val repo = RoomStayRepository(db.stayDao(), meta)
        val stay = Stay(
            "s1", "wien", StayTypeIds.BUSINESS_TRIP, d("2026-11-24"), participants = setOf(Defaults.ME),
            overnight = true, transport = setOf(TransportMode.WALK, TransportMode.TRAIN, TransportMode.TRAM),
        )
        repo.upsert(stay)
        assertEquals(stay, repo.get("s1"))
    }

    @Test
    fun `Besuch mit mehreren Kategorien`() = runTest {
        val repo = RoomVisitRepository(db.visitDao(), meta)
        val visit = Visit(
            "v1", "oberalm", VisitCategoryIds.SHOPPING, d("2026-11-21"), participants = setOf(Defaults.ME),
            extraCategoryIds = setOf(VisitCategoryIds.RESTAURANT, VisitCategoryIds.MUSEUM),
        )
        repo.upsert(visit)
        assertEquals(visit, repo.get("v1"))
    }

    @Test
    fun `Tagesvorlage und Muster mit Vorlage`() = runTest {
        val templates = RoomDayTemplateRepository(db.dayTemplateDao(), meta)
        val hybrid = DayTemplate(
            "t1", "Hybrid",
            listOf(
                TemplateStep(StayTypeIds.OFFICE, "salzburg", setOf(TransportMode.TRAIN, TransportMode.WALK)),
                TemplateStep(StayTypeIds.HOMEOFFICE, "hallein"),
            ),
        )
        templates.upsert(hybrid)
        assertEquals(listOf(hybrid), templates.observeAll().first())

        val rules = RoomPatternRuleRepository(db.patternRuleDao(), meta)
        val rule = PatternRule("r1", Defaults.ME, DayOfWeek.THURSDAY, StayTypeIds.OFFICE, "salzburg", templateId = "t1")
        rules.upsert(rule)
        assertEquals(listOf(rule), rules.observeAll().first())
    }

    @Test
    fun `es gibt hoechstens eine Homebase, Seeder uebernimmt Zuhause`() = runTest {
        val places = RoomPlaceRepository(db.placeDao(), meta)
        places.upsert(Place("p1", "Hallein", PlaceKind.CITY, "AT", alias = "Zuhause"))
        places.upsert(Place("p2", "Salzburg", PlaceKind.CITY, "AT"))

        DatabaseSeeder(db, meta).seed()
        assertEquals(listOf("p1"), places.observeAll().first().filter { it.isHomebase }.map { it.id })

        places.setHomebase("p2")
        assertEquals(listOf("p2"), places.observeAll().first().filter { it.isHomebase }.map { it.id })
    }

    @Test
    fun `Umzuege - fruehere Homebase bleibt als Zeitraum, aktuelle ist die spaeteste`() = runTest {
        val places = RoomPlaceRepository(db.placeDao(), meta)
        places.upsert(Place("w1", "Eltern", PlaceKind.ADDRESS, "AT"))
        places.upsert(Place("w2", "Wohnung 2", PlaceKind.ADDRESS, "AT"))
        places.upsert(Place("w3", "Wohnung 3", PlaceKind.ADDRESS, "AT"))
        places.setHomebase("w1")
        places.addHomebase("w3", d("2022-08-01"))
        places.addHomebase("w2", d("2018-02-05"))
        val all = places.observeAll().first().associateBy { it.id }
        assertEquals(io.github.mario2712.locus.domain.model.UNKNOWN_DATE, all.getValue("w1").homebaseSince)
        assertEquals(listOf("w3"), all.values.filter { it.isHomebase }.map { it.id })
        val homes = io.github.mario2712.locus.domain.timeline.Homebases.of(all.values)
        assertEquals("w2", homes.at(d("2020-01-01")))

        places.removeHomebase("w3")
        assertEquals(listOf("w2"), places.observeAll().first().filter { it.isHomebase }.map { it.id })
    }
}
