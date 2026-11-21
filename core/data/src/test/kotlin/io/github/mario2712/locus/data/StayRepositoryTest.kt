package io.github.mario2712.locus.data

import io.github.mario2712.locus.data.repository.RoomStayRepository
import io.github.mario2712.locus.domain.model.DatePrecision
import io.github.mario2712.locus.domain.model.EntrySource
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.reference.Defaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class StayRepositoryTest : DbTestBase() {

    private val repo by lazy { RoomStayRepository(db.stayDao(), meta) }
    private fun d(s: String) = LocalDate.parse(s)

    private val office = Stay(
        id = "s1", placeId = "salzburg", typeId = Defaults.StayTypeIds.OFFICE,
        startDate = d("2026-11-17"), startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0),
        participants = setOf(Defaults.ME), note = "Teammeeting", source = EntrySource.PATTERN,
    )

    @Test
    fun `speichert und liest Aufenthalt vollstaendig`() = runTest {
        repo.upsert(office)
        assertEquals(office, repo.get("s1"))
    }

    @Test
    fun `Zeitraumabfrage liefert beruehrende Aufenthalte`() = runTest {
        val ljubljana = Stay(
            "s2", "ljubljana", Defaults.StayTypeIds.VACATION, d("2026-11-20"), d("2026-11-22"),
            startTime = LocalTime.of(13, 0), participants = setOf(Defaults.ME, Defaults.JOHANNA),
        )
        val rom2014 = Stay(
            "s3", "rom", Defaults.StayTypeIds.VACATION, d("2014-07-01"), d("2014-07-31"),
            precision = DatePrecision.MONTH, participants = setOf(Defaults.ME),
        )
        listOf(office, ljubljana, rom2014).forEach { repo.upsert(it) }

        val week = repo.observeBetween(d("2026-11-16"), d("2026-11-21")).first()
        assertEquals(listOf("s1", "s2"), week.map { it.id })
        assertEquals(setOf(Defaults.ME, Defaults.JOHANNA), week.last().participants)

        val sunday = repo.observeBetween(d("2026-11-22"), d("2026-11-22")).first()
        assertEquals(listOf("s2"), sunday.map { it.id })

        assertEquals(listOf("s3"), repo.observeBetween(d("2014-07-15"), d("2014-07-15")).first().map { it.id })
    }

    @Test
    fun `Update ersetzt Teilnehmer und behaelt createdAt`() = runTest {
        repo.upsert(office)
        now = 5_000L
        repo.upsert(office.copy(participants = setOf(Defaults.JOHANNA)))

        assertEquals(setOf(Defaults.JOHANNA), repo.get("s1")!!.participants)
        val row = db.stayDao().get("s1")!!.stay.meta
        assertEquals(1_000L, row.createdAt)
        assertEquals(5_000L, row.updatedAt)
        assertEquals("test-device", row.originDevice)
    }

    @Test
    fun `Loeschen ist ein Soft-Delete`() = runTest {
        repo.upsert(office)
        now = 9_000L
        repo.delete("s1")

        assertNull(repo.get("s1"))
        assertEquals(emptyList<Stay>(), repo.observeAll().first())
        val count = db.query("SELECT deleted, updated_at FROM stay WHERE id = 's1'", null).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
            assertEquals(9_000L, c.getLong(1))
            c.count
        }
        assertEquals(1, count)
    }

    @Test
    fun `Aufenthalte einer Reise`() = runTest {
        repo.upsert(office)
        repo.upsert(office.copy(id = "s2", tripId = "trip-1"))
        assertEquals(listOf("s2"), repo.observeByTrip("trip-1").first().map { it.id })
    }
}
