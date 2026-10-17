package com.physcard.physics

import com.physcard.physics.domain.time.AppClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AppClockTest {
    @Test
    fun localDayBoundaryHandlesTimeZoneOffset() {
        val clock = AppClock(
            instantProvider = { Instant.parse("2026-10-17T15:30:00Z") },
            zoneProvider = { ZoneId.of("Asia/Shanghai") },
        )
        assertEquals("2026-10-17", clock.dayKey())
        assertEquals("2026-10-17", clock.localDate(Instant.parse("2026-10-17T15:59:59Z")).toString())
        assertEquals("2026-10-18", clock.localDate(Instant.parse("2026-10-17T16:00:00Z")).toString())
    }

    @Test
    fun elapsedDaysUsesCalendarDatesNotRawHours() {
        val clock = AppClock(
            instantProvider = { Instant.parse("2026-10-18T00:30:00Z") },
            zoneProvider = { ZoneOffset.UTC },
        )
        val previous = Instant.parse("2026-10-17T23:30:00Z").toEpochMilli()
        assertEquals(1, clock.elapsedDays(previous))
    }

    @Test
    fun startAndEndOfDayAreConsistentAroundDstStyleBoundary() {
        val clock = AppClock(zoneProvider = { ZoneOffset.UTC })
        val date = java.time.LocalDate.of(2026, 10, 17)
        assertEquals(date.atStartOfDay(ZoneOffset.UTC).toInstant(), clock.startOfDay(date))
        assertTrue(clock.endOfDayExclusive(date).isAfter(clock.startOfDay(date)))
        assertEquals(86_400L, clock.endOfDayExclusive(date).epochSecond - clock.startOfDay(date).epochSecond)
    }
}
