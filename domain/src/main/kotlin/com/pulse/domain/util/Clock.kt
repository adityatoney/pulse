package com.pulse.domain.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Thin abstraction over system time so tests can supply a FixedClock.
 * Prefer this over `Clock.System` or `Instant.now()` in use cases.
 */
interface Clock {
    fun now(): Instant
    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        now().toLocalDateTime(zone).date
}

object SystemClock : Clock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
}

class FixedClock(private var instant: Instant) : Clock {
    override fun now(): Instant = instant
    fun advanceTo(new: Instant) { instant = new }
}
