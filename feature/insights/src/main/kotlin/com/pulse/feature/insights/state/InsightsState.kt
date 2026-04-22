package com.pulse.feature.insights.state

import com.pulse.domain.model.Insight
import com.pulse.domain.model.MetricTrend
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.WeeklyChallenge

data class InsightsState(
    val dailyInsights: List<Insight> = emptyList(),
    val weeklyInsights: List<Insight> = emptyList(),
    val longitudinalInsights: List<Insight> = emptyList(),
    val weeklyBars: List<DayBar> = emptyList(),
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val heatmapMetric: MetricType = MetricType.Steps,
    val todayPosition: MetricPosition? = null,
    val trends: List<MetricTrend> = emptyList(),
    val weeklyChallenges: List<WeeklyChallenge> = emptyList(),
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
    val rawValue: Double = 0.0,
    val metricLabel: String = "steps",
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
    data class ChangeHeatmapMetric(val metric: MetricType) : InsightsIntent
    data object OpenHeatmapDetail : InsightsIntent
}

sealed interface InsightsEffect {
    data object NavigateBack : InsightsEffect
    data class NavigateToHeatmapDetail(val metric: String) : InsightsEffect
}
