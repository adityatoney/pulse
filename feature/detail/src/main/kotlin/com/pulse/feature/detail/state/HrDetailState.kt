package com.pulse.feature.detail.state

import com.pulse.domain.model.DailyHrRange
import com.pulse.domain.model.HrSample
import kotlinx.datetime.LocalDate

enum class HrTimeframe { Day, Week, Month }

data class WeeklyHrSummary(
    val label: String,
    val minBpm: Int,
    val maxBpm: Int,
    val avgBpm: Int,
)

data class HrDetailState(
    val timeframe: HrTimeframe = HrTimeframe.Day,
    val periodAnchor: LocalDate = LocalDate(2024, 1, 1),
    val periodLabel: String = "",
    // Day view
    val intradaySamples: List<HrSample> = emptyList(),
    val dayMin: Int? = null,
    val dayMax: Int? = null,
    val dayAvg: Int? = null,
    val restingHr: Int? = null,
    // Week/Month view
    val dailyRanges: List<DailyHrRange> = emptyList(),
    val weeklySummaries: List<WeeklyHrSummary> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface HrDetailIntent {
    data class ChangeTimeframe(val tf: HrTimeframe) : HrDetailIntent
    data class MovePeriod(val forward: Boolean) : HrDetailIntent
    data object Back : HrDetailIntent
}

sealed interface HrDetailEffect {
    data object NavigateBack : HrDetailEffect
}
