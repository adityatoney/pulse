package com.pulse.domain.usecase

import com.pulse.domain.model.Timeframe
import com.pulse.domain.repository.Bucket
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class GetMetricSeriesUseCaseTest {

    private val anchor = LocalDate(2026, 4, 16)

    @Test
    fun `day timeframe uses hourly bucket and single day range`() {
        val (range, bucket) = GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.Day)
        assertThat(range.start).isEqualTo(anchor)
        assertThat(range.endInclusive).isEqualTo(anchor)
        assertThat(bucket).isEqualTo(Bucket.Hour)
    }

    @Test
    fun `week timeframe gives 7-day daily window`() {
        val (range, bucket) = GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.Week)
        assertThat(range.start).isEqualTo(LocalDate(2026, 4, 10))
        assertThat(range.endInclusive).isEqualTo(anchor)
        assertThat(bucket).isEqualTo(Bucket.Day)
    }

    @Test
    fun `month timeframe gives 30-day daily window`() {
        val (range, bucket) = GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.Month)
        assertThat(range.start).isEqualTo(LocalDate(2026, 3, 18))
        assertThat(bucket).isEqualTo(Bucket.Day)
    }

    @Test
    fun `3M timeframe buckets by week`() {
        val (_, bucket) = GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.ThreeMonths)
        assertThat(bucket).isEqualTo(Bucket.Week)
    }

    @Test
    fun `6M and year timeframes bucket by month`() {
        assertThat(GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.SixMonths).second)
            .isEqualTo(Bucket.Month)
        assertThat(GetMetricSeriesUseCase.rangeAndBucket(anchor, Timeframe.Year).second)
            .isEqualTo(Bucket.Month)
    }
}
