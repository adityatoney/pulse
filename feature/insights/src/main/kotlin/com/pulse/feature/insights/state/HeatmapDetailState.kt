package com.pulse.feature.insights.state

import com.pulse.domain.model.MetricType

data class HeatmapDetailState(
    val metric: MetricType = MetricType.Steps,
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val loading: Boolean = true,
    val selectedMonth: String? = null,
    val availableMonths: List<String> = emptyList(),
)

sealed interface HeatmapDetailIntent {
    data class ChangeMetric(val metric: MetricType) : HeatmapDetailIntent
    data object Back : HeatmapDetailIntent
    data object PrevMonth : HeatmapDetailIntent
    data object NextMonth : HeatmapDetailIntent
}

sealed interface HeatmapDetailEffect {
    data object NavigateBack : HeatmapDetailEffect
}
