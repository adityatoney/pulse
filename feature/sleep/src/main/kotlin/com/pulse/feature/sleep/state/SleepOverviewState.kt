package com.pulse.feature.sleep.state

import kotlinx.datetime.LocalDate

enum class SleepViewMode { Duration, Schedule }

data class SleepNightUi(
    val date: LocalDate,
    val dayLabel: String,
    val durationMinutes: Long,
    val bedtimeLabel: String,
    val wakeTimeLabel: String,
    val bedtimeHour: Float,
    val wakeHour: Float,
)

data class SleepOverviewState(
    val viewMode: SleepViewMode = SleepViewMode.Duration,
    val periodAnchor: LocalDate = LocalDate(2024, 1, 1),
    val periodLabel: String = "",
    val nights: List<SleepNightUi> = emptyList(),
    val avgDurationMinutes: Long = 0,
    val bedtimeRangeLabel: String = "",
    val isLoading: Boolean = true,
)

sealed interface SleepOverviewIntent {
    data object Back : SleepOverviewIntent
    data class ToggleMode(val mode: SleepViewMode) : SleepOverviewIntent
    data class MovePeriod(val forward: Boolean) : SleepOverviewIntent
    data class SelectNight(val date: LocalDate) : SleepOverviewIntent
}

sealed interface SleepOverviewEffect {
    data class NavigateToNight(val dateStr: String) : SleepOverviewEffect
    data object NavigateBack : SleepOverviewEffect
}
