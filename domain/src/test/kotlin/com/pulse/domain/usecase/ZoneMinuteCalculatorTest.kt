package com.pulse.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test

class ZoneMinuteCalculatorTest {

    private fun samplesEveryMinute(startEpochMs: Long, bpms: List<Int>): List<ZoneMinuteCalculator.HrSample> =
        bpms.mapIndexed { i, bpm ->
            ZoneMinuteCalculator.HrSample(
                at = Instant.fromEpochMilliseconds(startEpochMs + i * 60_000L),
                bpm = bpm,
            )
        }

    // age=45 maxHr=175 restingHr=72 HRR=103
    // z2=72+41=113, z3=72+61=133, z4=72+78=150, z5=72+88=160

    @Test
    fun `empty or single sample returns zero`() {
        assertThat(ZoneMinuteCalculator.calculate(emptyList(), restingHr = 72, age = 45).total).isEqualTo(0)
        val one = listOf(ZoneMinuteCalculator.HrSample(Instant.fromEpochMilliseconds(0), 150))
        assertThat(ZoneMinuteCalculator.calculate(one, restingHr = 72, age = 45).total).isEqualTo(0)
    }

    @Test
    fun `below zone 2 threshold does not count`() {
        // z2=113, all samples at 100 are below
        val samples = samplesEveryMinute(0, List(30) { 100 })
        val result = ZoneMinuteCalculator.calculate(samples, restingHr = 72, age = 45)
        assertThat(result.total).isEqualTo(0)
    }

    @Test
    fun `zone 2 credits 1x`() {
        // z2=113 z3=133 → 120 bpm is zone 2
        val samples = samplesEveryMinute(0, List(11) { 120 })
        val result = ZoneMinuteCalculator.calculate(samples, restingHr = 72, age = 45)
        assertThat(result.zone2).isEqualTo(10)
        assertThat(result.total).isEqualTo(10)
    }

    @Test
    fun `zone 4 credits 2x`() {
        // z4=150 z5=160 → 155 bpm is zone 4
        val samples = samplesEveryMinute(0, List(11) { 155 })
        val result = ZoneMinuteCalculator.calculate(samples, restingHr = 72, age = 45)
        assertThat(result.zone4).isEqualTo(10)
        assertThat(result.total).isEqualTo(20)
    }

    @Test
    fun `zone 5 credits 2x`() {
        // z5=160 → 170 bpm is zone 5
        val samples = samplesEveryMinute(0, List(11) { 170 })
        val result = ZoneMinuteCalculator.calculate(samples, restingHr = 72, age = 45)
        assertThat(result.zone5).isEqualTo(10)
        assertThat(result.total).isEqualTo(20)
    }

    @Test
    fun `thresholds with HRR`() {
        val t = ZoneMinuteCalculator.thresholds(restingHr = 72, age = 45)
        assertThat(t.maxHr).isEqualTo(175)
        assertThat(t.z2).isEqualTo(113)  // 72 + 0.40*103
        assertThat(t.z3).isEqualTo(133)  // 72 + 0.60*103
        assertThat(t.z4).isEqualTo(150)  // 72 + 0.76*103
        assertThat(t.z5).isEqualTo(160)  // 72 + 0.86*103
    }

    @Test
    fun `gap over five minutes breaks contiguity`() {
        val first = listOf(
            ZoneMinuteCalculator.HrSample(Instant.fromEpochMilliseconds(0), 155),
            ZoneMinuteCalculator.HrSample(Instant.fromEpochMilliseconds(10 * 60_000L), 155),
        )
        val result = ZoneMinuteCalculator.calculate(first, restingHr = 72, age = 45)
        assertThat(result.total).isEqualTo(0)
    }
}
