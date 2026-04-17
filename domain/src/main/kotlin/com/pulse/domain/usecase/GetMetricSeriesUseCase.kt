package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricSeries
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

class GetMetricSeriesUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metric: MetricType, anchor: LocalDate, timeframe: Timeframe): Flow<MetricSeries> {
        val (range, bucket) = rangeAndBucket(anchor, timeframe)
        return health.observeSeries(metric, range, bucket)
    }

    companion object {
        fun rangeAndBucket(anchor: LocalDate, tf: Timeframe): Pair<DateRange, Bucket> = when (tf) {
            Timeframe.Day -> DateRange(anchor, anchor) to Bucket.Hour
            Timeframe.Week -> {
                // Align to Monday-Sunday calendar week
                val dow = anchor.dayOfWeek.ordinal // Monday=0
                val monday = anchor.minus(DatePeriod(days = dow))
                val sunday = monday.plus(DatePeriod(days = 6))
                DateRange(monday, sunday) to Bucket.Day
            }
            Timeframe.Month -> {
                val first = LocalDate(anchor.year, anchor.monthNumber, 1)
                val nextMonth = first.plus(DatePeriod(months = 1))
                val last = nextMonth.minus(DatePeriod(days = 1))
                DateRange(first, last) to Bucket.Week
            }
            Timeframe.ThreeMonths -> DateRange(anchor.minus(DatePeriod(months = 3)), anchor) to Bucket.Month
            Timeframe.SixMonths -> DateRange(anchor.minus(DatePeriod(months = 6)), anchor) to Bucket.Month
            Timeframe.Year -> DateRange(anchor.minus(DatePeriod(years = 1)), anchor) to Bucket.Month
        }
    }
}
