package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Compares the **last 7 days** (anchor-6 → anchor) against the **prior 7 days** (anchor-13 → anchor-7).
 *
 * Uses a rolling window so the comparison is always full 7-day vs 7-day,
 * avoiding the noise of partial week-to-date comparisons early in the week.
 * Returns null when the prior window totals 0 (undefined %) — callers render "—".
 */
class CalculateWoWUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metric: MetricType, anchor: LocalDate): Flow<DeltaPercent?> {
        val (currentRange, previousRange) = windows(anchor)
        val tz = TimeZone.currentSystemDefault()
        println("WoW anchor=$anchor current=${currentRange.start}..${currentRange.endInclusive} prev=${previousRange.start}..${previousRange.endInclusive}")
        return health.observeSeries(metric, DateRange(previousRange.start, currentRange.endInclusive), Bucket.Day)
            .map { series ->
                var curSum = 0.0
                var prevSum = 0.0
                val curDays = mutableMapOf<LocalDate, Double>()
                val prevDays = mutableMapOf<LocalDate, Double>()
                series.points.forEach { point ->
                    val date = point.bucketStart.toLocalDateTime(tz).date
                    if (date >= currentRange.start && date <= currentRange.endInclusive) {
                        curSum += point.value
                        curDays[date] = (curDays[date] ?: 0.0) + point.value
                    } else if (date >= previousRange.start && date <= previousRange.endInclusive) {
                        prevSum += point.value
                        prevDays[date] = (prevDays[date] ?: 0.0) + point.value
                    }
                }
                println("WoW totalPoints=${series.points.size} curSum=$curSum prevSum=$prevSum")
                curDays.toSortedMap().forEach { (d, v) -> println("WoW   CUR $d = $v") }
                prevDays.toSortedMap().forEach { (d, v) -> println("WoW   PRV $d = $v") }
                val result = DeltaPercent.from(curSum, prevSum)
                println("WoW result=${result?.value}% dir=${result?.direction}")
                result
            }
    }

    companion object {
        /** Returns (last7days, prior7days) as rolling windows. */
        fun windows(anchor: LocalDate): Pair<DateRange, DateRange> {
            val currentStart = anchor.minus(DatePeriod(days = 6))
            val previousEnd = anchor.minus(DatePeriod(days = 7))
            val previousStart = anchor.minus(DatePeriod(days = 13))
            return DateRange(currentStart, anchor) to DateRange(previousStart, previousEnd)
        }
    }
}
