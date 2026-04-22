package com.pulse.feature.dashboard.state

import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.DeviceStatus
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.model.HrSample
import com.pulse.domain.model.Insight
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.MoveStreak
import com.pulse.domain.model.SyncStatus
import com.pulse.domain.model.Timeframe
import com.pulse.domain.model.TodayMetrics
import com.pulse.domain.model.UserChrome
import com.pulse.domain.model.RecoveryBlock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Single immutable render bag for DashboardScreen. */
data class DashboardState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val timeframe: Timeframe = Timeframe.Day,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DashboardError? = null,
    val metrics: TodayMetrics? = null,
    val recovery: RecoveryBlock? = null,
    val wow: DeltaPercent? = null,
    val mom: DeltaPercent? = null,
    val sync: SyncStatus? = null,
    val device: DeviceStatus? = null,
    val user: UserChrome? = null,
    val permissionsGranted: Boolean = false,
    val recentExercises: List<ExerciseSession> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val restingHr: Double? = null,
    val weight: Double? = null,
    val spo2: Double? = null,
    val intradayHrSamples: List<HrSample> = emptyList(),
    val currentHrBpm: Int? = null,
    val lastHrSampleAt: Instant? = null,
    val activityOnlyDistance: Boolean = false,
    val activityOnlyCalories: Boolean = false,
    val moveStreak: MoveStreak? = null,
)

sealed interface DashboardError {
    data object PermissionDenied : DashboardError
    data object NoHealthConnect : DashboardError
    data class Network(val message: String) : DashboardError
    data class Unknown(val message: String) : DashboardError
}

sealed interface DashboardIntent {
    data object Load : DashboardIntent
    data object PullToRefresh : DashboardIntent
    data class ChangeDate(val date: LocalDate) : DashboardIntent
    data class ChangeTimeframe(val tf: Timeframe) : DashboardIntent
    data class SelectMetric(val metric: MetricType) : DashboardIntent
    data object OpenExerciseLog : DashboardIntent
    data class OpenExerciseDetail(val sessionId: String) : DashboardIntent
    data object RetrySync : DashboardIntent
    data object ForceSyncNow : DashboardIntent
    data object RequestPermissions : DashboardIntent
    data class PermissionsResult(val granted: Boolean) : DashboardIntent
    data object OpenInsights : DashboardIntent
    data object OpenChat : DashboardIntent
    data object OpenProfile : DashboardIntent
    data object OpenHeatmap : DashboardIntent
    data object OpenDebugMenu : DashboardIntent
    data object ToggleDistanceMode : DashboardIntent
    data object ToggleCaloriesMode : DashboardIntent
    data object OpenSleepDetail : DashboardIntent
    data object OpenHrDetail : DashboardIntent
}

sealed interface DashboardEffect {
    data class NavigateToMetricDetail(val metric: MetricType) : DashboardEffect
    data object NavigateToExerciseLog : DashboardEffect
    data class NavigateToExerciseDetail(val sessionId: String) : DashboardEffect
    data object NavigateToInsights : DashboardEffect
    data object NavigateToChat : DashboardEffect
    data object NavigateToProfile : DashboardEffect
    data object NavigateToHeatmap : DashboardEffect
    data object NavigateToDebugMenu : DashboardEffect
    data class ShowSnackbar(val message: String) : DashboardEffect
    data object RequestHealthConnectPermissions : DashboardEffect
    data object LaunchPlayStoreForHealthConnect : DashboardEffect
    data object NavigateToSleepDetail : DashboardEffect
    data object NavigateToHrDetail : DashboardEffect
}
