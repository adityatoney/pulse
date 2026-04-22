package com.pulse.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** A single health reading ingested from Health Connect or a reconciled cloud source. */
data class HealthMetric(
    val id: String,
    val type: MetricType,
    val value: Double,
    val unit: MeasurementUnit,
    val start: Instant,
    val end: Instant,
    val source: DataSource,
    val deviceId: String? = null,
)

/** Day-level rollup of a single metric. PK = (date, metric). */
data class DailyAggregate(
    val date: LocalDate,
    val metric: MetricType,
    val total: Double,
    val goal: Double? = null,
    val sampleCount: Int,
    val computedAt: Instant,
) {
    val progress: Float get() = goal?.takeIf { it > 0.0 }?.let { (total / it).toFloat() } ?: 0f
}

/** A series of binned values for a metric over a date range. */
data class MetricSeries(
    val metric: MetricType,
    val range: DateRange,
    val points: List<SeriesPoint>,
    val aggregation: Aggregation,
)

data class SeriesPoint(
    val bucketStart: Instant,
    val value: Double,
    val goal: Double? = null,
)

data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

data class Goal(
    val metric: MetricType,
    val target: Double,
    val effectiveFrom: LocalDate,
    val cadence: Cadence,
)

data class DeltaPercent(val value: Float, val direction: TrendDirection) {
    companion object {
        fun from(current: Double, previous: Double?): DeltaPercent? {
            if (previous == null || previous == 0.0) return null
            val pct = ((current - previous) / previous * 100.0).toFloat()
            val dir = when {
                pct > 1f -> TrendDirection.Up
                pct < -1f -> TrendDirection.Down
                else -> TrendDirection.Flat
            }
            return DeltaPercent(pct, dir)
        }
    }
}

data class MetricTrend(
    val metric: MetricType,
    val recentAvg: Double,
    val priorAvg: Double,
    val delta: DeltaPercent?,
    val sparklinePoints: List<Float>,
)

data class MoveStreak(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastClosedDate: LocalDate?,
)

data class WeeklyChallenge(
    val id: String,
    val title: String,
    val description: String,
    val metric: MetricType?,
    val targetValue: Double,
    val currentValue: Double,
    val progress: Float,
    val isComplete: Boolean,
)
