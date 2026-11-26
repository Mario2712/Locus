package io.github.mario2712.locus.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderSchedulerTest {
    @Test
    fun `vor der Erinnerungszeit wird heute erinnert`() {
        val delay = ReminderScheduler.delayUntil(LocalDateTime.of(2026, 11, 21, 18, 0), LocalTime.of(20, 30))
        assertEquals(Duration.ofMinutes(150), delay)
    }

    @Test
    fun `nach der Erinnerungszeit wird morgen erinnert`() {
        val delay = ReminderScheduler.delayUntil(LocalDateTime.of(2026, 11, 21, 21, 0), LocalTime.of(20, 30))
        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }
}
