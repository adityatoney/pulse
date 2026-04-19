package com.pulse.feature.insights.state

import com.pulse.domain.model.Insight

data class InsightsState(
    val dailyInsights: List<Insight> = emptyList(),
    val weeklyInsights: List<Insight> = emptyList(),
    val longitudinalInsights: List<Insight> = emptyList(),
    val weeklyBars: List<DayBar> = emptyList(),
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val todayPosition: MetricPosition? = null,
    val loading: Boolean = true,
)

data class DayBar(
    val label: String,
    val value: Double,
    val goal: Double,
    val isToday: Boolean,
)

data class HeatmapDay(
    val date: String,
    val intensity: Float,
)

data class MetricPosition(
    val current: Double,
    val min: Double,
    val max: Double,
    val avg: Double,
    val percentile: Float,
)

sealed interface InsightsIntent {
    data object Load : InsightsIntent
    data object Back : InsightsIntent
}

sealed interface InsightsEffect {
    data object NavigateBack : InsightsEffect
}
