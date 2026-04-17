package com.pulse.feature.detail.state

import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.MetricSeries
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import kotlinx.datetime.LocalDate

data class PeriodComparison(
    val label: String,
    val value: String,
    val subtitle: String? = null,
)

data class MetricDetailState(
    val metric: MetricType,
    val timeframe: Timeframe = Timeframe.Week,
    val periodAnchor: LocalDate,
    val series: MetricSeries? = null,
    val average: Double = 0.0,
    val total: Double = 0.0,
    val goalHitDays: Int = 0,
    val goal: Double? = null,
    val wow: DeltaPercent? = null,
    val mom: DeltaPercent? = null,
    val comparisons: List<PeriodComparison> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

sealed interface MetricDetailIntent {
    data object Load : MetricDetailIntent
    data class ChangeTimeframe(val tf: Timeframe) : MetricDetailIntent
    data class MovePeriod(val forward: Boolean) : MetricDetailIntent
    data class DrillIntoMonth(val monthAnchor: LocalDate) : MetricDetailIntent
    data object Back : MetricDetailIntent
}

sealed interface MetricDetailEffect {
    data object NavigateBack : MetricDetailEffect
    data class ShowSnackbar(val message: String) : MetricDetailEffect
}
