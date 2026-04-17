package com.pulse.domain.usecase

import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import javax.inject.Inject

/**
 * Compares the **current in-progress ISO week-to-date** against the **same day-count window of the prior week**.
 *
 * Example: if "anchor" is Thursday, we sum Mon–Thu of this week vs Mon–Thu of last week.
 * This avoids the partial-week bias of comparing a full 7-day block against today's incomplete week.
 * Returns null when the prior window totals 0 (undefined %) — callers render "—".
 */
class CalculateWoWUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metric: MetricType, anchor: LocalDate): Flow<DeltaPercent?> {
        val (currentRange, previousRange) = windows(anchor)
        return health.observeSeries(metric, DateRange(previousRange.start, currentRange.endInclusive), Bucket.Day)
            .map { series ->
                val (cur, prev) = sumSplit(series.points.map {
                    val d = it.bucketStart.toString().substring(0, 10)
                    DailyAggregate(
                        date = LocalDate.parse(d),
                        metric = metric,
                        total = it.value,
                        goal = it.goal,
                        sampleCount = 1,
                        computedAt = it.bucketStart,
                    )
                }, currentRange, previousRange)
                DeltaPercent.from(cur, prev)
            }
    }

    companion object {
        /** Returns (thisWeekToDate, lastWeekSameLength). */
        fun windows(anchor: LocalDate): Pair<DateRange, DateRange> {
            val daysIntoWeek = anchor.dayOfWeek.ordinalFromMonday()
            val startOfWeek = anchor.minus(DatePeriod(days = daysIntoWeek))
            val lastWeekStart = startOfWeek.minus(DatePeriod(days = 7))
            val lastWeekEnd = lastWeekStart.plus(DatePeriod(days = daysIntoWeek))
            val currentRange = DateRange(startOfWeek, anchor)
            val previousRange = DateRange(lastWeekStart, lastWeekEnd)
            return currentRange to previousRange
        }

        private fun DayOfWeek.ordinalFromMonday(): Int = when (this) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }

        fun sumSplit(
            aggs: List<DailyAggregate>,
            currentRange: DateRange,
            previousRange: DateRange,
        ): Pair<Double, Double> {
            val cur = aggs
                .filter { it.date >= currentRange.start && it.date <= currentRange.endInclusive }
                .sumOf { it.total }
            val prev = aggs
                .filter { it.date >= previousRange.start && it.date <= previousRange.endInclusive }
                .sumOf { it.total }
            return cur to prev
        }
    }
}
