package com.pulse.feature.insights.state

import com.pulse.domain.model.MetricType

data class HeatmapDetailState(
    val metric: MetricType = MetricType.Steps,
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val loading: Boolean = true,
)

sealed interface HeatmapDetailIntent {
    data class ChangeMetric(val metric: MetricType) : HeatmapDetailIntent
    data object Back : HeatmapDetailIntent
}

sealed interface HeatmapDetailEffect {
    data object NavigateBack : HeatmapDetailEffect
}
