package com.physcard.physics.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * All instants are persisted as UTC epoch milliseconds. Local dates are only used for
 * calendar-facing statistics and are computed in the device's current time zone at the time of
 * the event. This keeps scheduling correct across time-zone changes while still giving the
 * learner a sensible "today".
 */
class AppClock(
    private val instantProvider: () -> Instant = { Instant.now() },
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun now(): Instant = instantProvider()
    fun nowMillis(): Long = now().toEpochMilli()
    fun zone(): ZoneId = zoneProvider()
    fun today(): LocalDate = LocalDate.now(zone())
    fun localDate(instant: Instant): LocalDate = instant.atZone(zone()).toLocalDate()
    fun dayKey(instant: Instant = now()): String = localDate(instant).format(DAY_FORMAT)
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(zone()).toInstant()
    fun endOfDayExclusive(date: LocalDate): Instant = date.plusDays(1).atStartOfDay(zone()).toInstant()
    fun startOfTodayMillis(): Long = startOfDay(today()).toEpochMilli()
    fun elapsedDays(fromMillis: Long?, toMillis: Long = nowMillis()): Int {
        if (fromMillis == null) return 0
        val from = localDate(Instant.ofEpochMilli(fromMillis))
        val to = localDate(Instant.ofEpochMilli(toMillis))
        return ChronoUnit.DAYS.between(from, to).coerceAtLeast(0).toInt()
    }

    companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
