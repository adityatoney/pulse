package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Compares **month-to-date** (day 1 → anchor) against the **same day count of the previous calendar month**.
 * Uses local timezone for date resolution. Returns null when the prior window totals 0.
 */
class CalculateMoMUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metric: MetricType, anchor: LocalDate): Flow<DeltaPercent?> {
        val (current, previous) = windows(anchor)
        val full = DateRange(previous.start, current.endInclusive)
        val tz = TimeZone.currentSystemDefault()
        println("MoM anchor=$anchor current=${current.start}..${current.endInclusive} prev=${previous.start}..${previous.endInclusive}")
        return health.observeSeries(metric, full, Bucket.Day).map { series ->
            var curSum = 0.0
            var prevSum = 0.0
            val curDays = mutableMapOf<LocalDate, Double>()
            val prevDays = mutableMapOf<LocalDate, Double>()
            series.points.forEach { point ->
                val date = point.bucketStart.toLocalDateTime(tz).date
                if (date >= current.start && date <= current.endInclusive) {
                    curSum += point.value
                    curDays[date] = (curDays[date] ?: 0.0) + point.value
                } else if (date >= previous.start && date <= previous.endInclusive) {
                    prevSum += point.value
                    prevDays[date] = (prevDays[date] ?: 0.0) + point.value
                }
            }
            println("MoM totalPoints=${series.points.size} curSum=$curSum prevSum=$prevSum")
            curDays.toSortedMap().forEach { (d, v) -> println("MoM   CUR $d = $v") }
            prevDays.toSortedMap().forEach { (d, v) -> println("MoM   PRV $d = $v") }
            val result = DeltaPercent.from(curSum, prevSum)
            println("MoM result=${result?.value}% dir=${result?.direction}")
            result
        }
    }

    companion object {
        /** Returns (thisMonthToDate, lastMonthThroughSameDayOffset). */
        fun windows(anchor: LocalDate): Pair<DateRange, DateRange> {
            val firstOfThisMonth = LocalDate(anchor.year, anchor.month, 1)
            val daysIntoMonth = anchor.dayOfMonth - 1
            val firstOfLastMonth = firstOfThisMonth.minus(DatePeriod(months = 1))
            val lastOfLastMonth = firstOfThisMonth.minus(DatePeriod(days = 1))
            val candidateEnd = firstOfLastMonth.plus(DatePeriod(days = daysIntoMonth))
            val previousEnd = if (candidateEnd > lastOfLastMonth) lastOfLastMonth else candidateEnd
            return DateRange(firstOfThisMonth, anchor) to DateRange(firstOfLastMonth, previousEnd)
        }
    }
}
