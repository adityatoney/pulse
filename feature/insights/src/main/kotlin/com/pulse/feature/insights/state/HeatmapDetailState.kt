package com.pulse.feature.insights.state

import com.pulse.domain.model.MetricType

enum class HeatmapViewMode { Heatmap, Rings }

data class RingDay(
    val date: String,
    val stepsValue: Double,
    val stepsGoal: Double,
    val stepsProgress: Float,
    val caloriesValue: Double,
    val caloriesGoal: Double,
    val caloriesProgress: Float,
    val distanceValue: Double,
    val distanceGoal: Double,
    val distanceProgress: Float,
) {
    val allRingsClosed: Boolean get() =
        stepsProgress >= 1f && caloriesProgress >= 1f && distanceProgress >= 1f
}

data class HeatmapDetailState(
    val metric: MetricType = MetricType.Steps,
    val viewMode: HeatmapViewMode = HeatmapViewMode.Heatmap,
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val ringDays: List<RingDay> = emptyList(),
    val loading: Boolean = true,
    val selectedMonth: String? = null,
    val availableMonths: List<String> = emptyList(),
)

sealed interface HeatmapDetailIntent {
    data class ChangeMetric(val metric: MetricType) : HeatmapDetailIntent
    data class ChangeViewMode(val mode: HeatmapViewMode) : HeatmapDetailIntent
    data object Back : HeatmapDetailIntent
    data object PrevMonth : HeatmapDetailIntent
    data object NextMonth : HeatmapDetailIntent
}

sealed interface HeatmapDetailEffect {
    data object NavigateBack : HeatmapDetailEffect
}
