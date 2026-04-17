package com.pulse.domain.usecase

import kotlinx.datetime.Instant
import kotlin.math.max

/**
 * Derives active zone minutes from heart-rate samples using Fitbit's HRR model.
 *
 * Thresholds use Heart Rate Reserve (HRR = maxHR − restingHR):
 *  - Zone 1 (below 40% HRR): Below zones → not counted
 *  - Zone 2 (40–60% HRR): Fat Burn       → 1 zone minute per minute
 *  - Zone 3 (60–76% HRR): Cardio         → 1 zone minute per minute
 *  - Zone 4 (76–86% HRR): Vigorous       → 2 zone minutes per minute
 *  - Zone 5 (86%+ HRR): Peak             → 2 zone minutes per minute
 *
 * Default max HR uses the "220 − age" rule; caller may override.
 */
object ZoneMinuteCalculator {
    data class HrSample(val at: Instant, val bpm: Int)

    data class ZoneBreakdown(
        val zone1: Int,
        val zone2: Int,
        val zone3: Int,
        val zone4: Int,
        val zone5: Int,
    ) {
        /** Active zone minutes: zone 1 excluded, zones 2-3 at 1x, zones 4-5 at 2x. */
        val total: Int get() = zone2 + zone3 + 2 * zone4 + 2 * zone5
    }

    data class ZoneThresholds(
        val z2: Int,
        val z3: Int,
        val z4: Int,
        val z5: Int,
        val maxHr: Int,
        val restingHr: Int,
    )

    fun thresholds(restingHr: Int, age: Int, maxHrOverride: Int? = null): ZoneThresholds {
        val maxHr = maxHrOverride ?: max(120, 220 - age)
        val hrr = max(1, maxHr - restingHr)
        return ZoneThresholds(
            z2 = restingHr + (0.40 * hrr).toInt(),
            z3 = restingHr + (0.60 * hrr).toInt(),
            z4 = restingHr + (0.76 * hrr).toInt(),
            z5 = restingHr + (0.86 * hrr).toInt(),
            maxHr = maxHr,
            restingHr = restingHr,
        )
    }

    fun calculate(
        samples: List<HrSample>,
        restingHr: Int,
        age: Int,
        maxHrOverride: Int? = null,
    ): ZoneBreakdown {
        if (samples.size < 2) return ZoneBreakdown(0, 0, 0, 0, 0)
        val t = thresholds(restingHr, age, maxHrOverride)

        val sorted = samples.sortedBy { it.at.toEpochMilliseconds() }
        var z1Ms = 0L
        var z2Ms = 0L
        var z3Ms = 0L
        var z4Ms = 0L
        var z5Ms = 0L

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            val deltaMs = cur.at.toEpochMilliseconds() - prev.at.toEpochMilliseconds()
            if (deltaMs <= 0L) continue
            if (deltaMs > 5 * 60_000L) continue
            val intervalHr = (prev.bpm + cur.bpm) / 2.0
            when {
                intervalHr >= t.z5 -> z5Ms += deltaMs
                intervalHr >= t.z4 -> z4Ms += deltaMs
                intervalHr >= t.z3 -> z3Ms += deltaMs
                intervalHr >= t.z2 -> z2Ms += deltaMs
                else -> z1Ms += deltaMs
            }
        }
        return ZoneBreakdown(
            zone1 = (z1Ms / 60_000L).toInt(),
            zone2 = (z2Ms / 60_000L).toInt(),
            zone3 = (z3Ms / 60_000L).toInt(),
            zone4 = (z4Ms / 60_000L).toInt(),
            zone5 = (z5Ms / 60_000L).toInt(),
        )
    }
}
