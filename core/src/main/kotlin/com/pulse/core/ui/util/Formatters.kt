package com.pulse.core.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.Locale

/** Thin helper for thousands-separated display ("4,907"). */
fun Int.formatted(locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(this.toLong())

fun Long.formatted(locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(this)

fun Double.formattedMiles(): String = "%.2f".format(this)

/** "5m ago", "just now", "1h ago". */
fun Instant?.relativeTo(now: Instant): String {
    if (this == null) return "never"
    val deltaMs = now.toEpochMilliseconds() - this.toEpochMilliseconds()
    return when {
        deltaMs < 60_000L -> "just now"
        deltaMs < 60 * 60_000L -> "${deltaMs / 60_000L}m ago"
        deltaMs < 24 * 60 * 60_000L -> "${deltaMs / (60 * 60_000L)}h ago"
        else -> "${deltaMs / (24 * 60 * 60_000L)}d ago"
    }
}

fun Instant.timeOfDay(zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val ldt = toLocalDateTime(zone)
    val hour = ldt.hour
    val period = if (hour < 12) "AM" else "PM"
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(display, ldt.minute, period)
}
