package com.pulse.domain.usecase

import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.MetricType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test

class CalculateWoWUseCaseTest {

    @Test
    fun `thursday anchor compares mon-thu windows`() {
        val anchor = LocalDate(2026, 4, 16) // Thursday
        val (current, previous) = CalculateWoWUseCase.windows(anchor)
        assertThat(current.start).isEqualTo(LocalDate(2026, 4, 13))
        assertThat(current.endInclusive).isEqualTo(LocalDate(2026, 4, 16))
        assertThat(previous.start).isEqualTo(LocalDate(2026, 4, 6))
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2026, 4, 9))
    }

    @Test
    fun `monday anchor compares single-day windows`() {
        val anchor = LocalDate(2026, 4, 13) // Monday
        val (current, previous) = CalculateWoWUseCase.windows(anchor)
        assertThat(current.start).isEqualTo(LocalDate(2026, 4, 13))
        assertThat(current.endInclusive).isEqualTo(LocalDate(2026, 4, 13))
        assertThat(previous.start).isEqualTo(LocalDate(2026, 4, 6))
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2026, 4, 6))
    }

    @Test
    fun `sum split correctly partitions by window`() {
        val anchor = LocalDate(2026, 4, 16)
        val (current, previous) = CalculateWoWUseCase.windows(anchor)
        val aggs = (6..16).map { day ->
            DailyAggregate(
                date = LocalDate(2026, 4, day),
                metric = MetricType.Steps,
                total = (day * 1000).toDouble(),
                goal = 10000.0,
                sampleCount = 1,
                computedAt = Instant.fromEpochMilliseconds(0),
            )
        }
        val (cur, prev) = CalculateWoWUseCase.sumSplit(aggs, current, previous)
        assertThat(cur).isEqualTo((13 + 14 + 15 + 16) * 1000.0)
        assertThat(prev).isEqualTo((6 + 7 + 8 + 9) * 1000.0)
    }

    @Test
    fun `zero prior returns null delta`() {
        val delta = com.pulse.domain.model.DeltaPercent.from(current = 1000.0, previous = 0.0)
        assertThat(delta).isNull()
    }

    @Test
    fun `positive delta returns Up direction`() {
        val delta = com.pulse.domain.model.DeltaPercent.from(current = 120.0, previous = 100.0)!!
        assertThat(delta.value).isEqualTo(20f)
        assertThat(delta.direction).isEqualTo(com.pulse.domain.model.TrendDirection.Up)
    }

    @Test
    fun `negative delta returns Down direction`() {
        val delta = com.pulse.domain.model.DeltaPercent.from(current = 80.0, previous = 100.0)!!
        assertThat(delta.value).isWithin(0.001f).of(-20f)
        assertThat(delta.direction).isEqualTo(com.pulse.domain.model.TrendDirection.Down)
    }
}
