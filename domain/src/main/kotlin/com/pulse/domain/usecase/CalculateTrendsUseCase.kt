package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricTrend
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

class CalculateTrendsUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metrics: List<MetricType>, anchor: LocalDate): Flow<List<MetricTrend>> {
        if (metrics.isEmpty()) return flowOf(emptyList())
        val sixtyDaysAgo = anchor.minus(DatePeriod(days = 60))
        val thirtyDaysAgo = anchor.minus(DatePeriod(days = 30))
        val tz = TimeZone.currentSystemDefault()

        val flows = metrics.map { metric ->
            health.observeSeries(metric, DateRange(sixtyDaysAgo, anchor), Bucket.Day)
                .map { series ->
                    val byDate = series.points.associateBy {
                        it.bucketStart.toLocalDateTime(tz).date
                    }
                    val recentValues = mutableListOf<Double>()
                    val priorValues = mutableListOf<Double>()
                    var d = sixtyDaysAgo
                    while (d <= anchor) {
                        val v = byDate[d]?.value ?: 0.0
                        if (d > thirtyDaysAgo) recentValues.add(v) else priorValues.add(v)
                        d = d.plus(DatePeriod(days = 1))
                    }
                    val recentAvg = if (recentValues.isNotEmpty()) recentValues.average() else 0.0
                    val priorAvg = if (priorValues.isNotEmpty()) priorValues.average() else 0.0
                    val delta = DeltaPercent.from(recentAvg, priorAvg)

                    val sparkline = if (recentValues.isNotEmpty()) {
                        val min = recentValues.min()
                        val max = recentValues.max()
                        val range = (max - min).coerceAtLeast(1.0)
                        recentValues.map { ((it - min) / range).toFloat() }
                    } else {
                        emptyList()
                    }

                    MetricTrend(
                        metric = metric,
                        recentAvg = recentAvg,
                        priorAvg = priorAvg,
                        delta = delta,
                        sparklinePoints = sparkline,
                    )
                }
        }

        return combine(flows) { it.toList() }
    }
}
