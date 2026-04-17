package com.pulse.domain.usecase

import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * Compares **month-to-date** (day 1 → anchor) against the **same day count of the previous calendar month**.
 * Returns null when the prior window totals 0.
 */
class CalculateMoMUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metric: MetricType, anchor: LocalDate): Flow<DeltaPercent?> {
        val (current, previous) = windows(anchor)
        val full = DateRange(previous.start, current.endInclusive)
        return health.observeSeries(metric, full, Bucket.Day).map { series ->
            val aggs = series.points.map {
                val d = it.bucketStart.toString().substring(0, 10)
                DailyAggregate(
                    date = LocalDate.parse(d),
                    metric = metric,
                    total = it.value,
                    goal = it.goal,
                    sampleCount = 1,
                    computedAt = it.bucketStart,
                )
            }
            val cur = aggs.filter { it.date >= current.start && it.date <= current.endInclusive }
                .sumOf { it.total }
            val prev = aggs.filter { it.date >= previous.start && it.date <= previous.endInclusive }
                .sumOf { it.total }
            DeltaPercent.from(cur, prev)
        }
    }

    companion object {
        /** Returns (thisMonthToDate, lastMonthThroughSameDayOffset). */
        fun windows(anchor: LocalDate): Pair<DateRange, DateRange> {
            val firstOfThisMonth = LocalDate(anchor.year, anchor.month, 1)
            val daysIntoMonth = anchor.dayOfMonth - 1 // 0-based offset
            val firstOfLastMonth = firstOfThisMonth.minus(DatePeriod(months = 1))
            // Cap offset if last month has fewer days (e.g., anchor = Mar 31 → compare Feb 1..28).
            val lastOfLastMonth = firstOfThisMonth.minus(DatePeriod(days = 1))
            val candidateEnd = firstOfLastMonth.plus(DatePeriod(days = daysIntoMonth))
            val previousEnd = if (candidateEnd > lastOfLastMonth) lastOfLastMonth else candidateEnd
            return DateRange(firstOfThisMonth, anchor) to DateRange(firstOfLastMonth, previousEnd)
        }
    }
}
