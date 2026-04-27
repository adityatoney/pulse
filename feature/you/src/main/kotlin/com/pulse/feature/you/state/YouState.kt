package com.pulse.feature.you.state

import com.pulse.domain.model.MetricType
import com.pulse.domain.usecase.ZoneMinuteCalculator

data class GoalSetting(
    val metric: MetricType,
    val label: String,
    val unit: String,
    val dailyTarget: Int,
    val weeklyTarget: Int,
    val step: Int,
    val min: Int,
    val max: Int,
)

data class YouState(
    val displayName: String = "Aditya",
    val age: Int = 45,
    val restingHr: Int = 72,
    val maxHr: Int = 175,
    val thresholds: ZoneMinuteCalculator.ZoneThresholds = ZoneMinuteCalculator.thresholds(72, 45),
    val memberSince: String = "2024",

    // Goals
    val goals: List<GoalSetting> = listOf(
        GoalSetting(MetricType.Steps, "Steps", "steps", 10_000, 70_000, 500, 1000, 50_000),
        GoalSetting(MetricType.Distance, "Distance", "miles", 5, 35, 1, 1, 30),
        GoalSetting(MetricType.ActiveCalories, "Calories burned", "cal", 500, 3_500, 50, 100, 5_000),
        GoalSetting(MetricType.ZoneMinutes, "Active zone min", "min", 30, 150, 5, 5, 300),
    ),

    // This week's actuals
    val weekSteps: Int = 0,
    val weekDistance: Double = 0.0,
    val weekCalories: Int = 0,
    val weekZoneMin: Int = 0,
    val weekExercises: Int = 0,
    val todaySteps: Int = 0,

    // Editing
    val editingGoal: GoalSetting? = null,

    // Fitbit
    val fitbitConnected: Boolean = false,
    val fitbitSyncCursor: String? = null,
    val fitbitSyncing: Boolean = false,

    // Google Health
    val googleHealthSignedIn: Boolean = false,

    // Sync
    val syncing: Boolean = false,
    val syncMessage: String? = null,

    // Appearance
    val forceDarkMode: Boolean = false,
    val useDynamicColor: Boolean = false,

    // Display preferences
    val activityOnlySteps: Boolean = false,
    val activityOnlyDistance: Boolean = false,
    val activityOnlyCalories: Boolean = false,

    // Backup (always shown)
    val driveSignedIn: Boolean = false,
    val lastBackupTime: String? = null,
    val lastBackupSize: String? = null,
    val backupInProgress: Boolean = false,
    val restoreInProgress: Boolean = false,
    val backupMessage: String? = null,
)

sealed interface YouIntent {
    data class UpdateAge(val age: Int) : YouIntent
    data class UpdateRestingHr(val hr: Int) : YouIntent
    data class UpdateMaxHr(val hr: Int) : YouIntent
    data class EditGoal(val metric: MetricType) : YouIntent
    data object DismissGoalEditor : YouIntent
    data class SaveDailyGoal(val metric: MetricType, val target: Int) : YouIntent
    data object Back : YouIntent
    data object FitbitSignIn : YouIntent
    data object FitbitSignOut : YouIntent
    data object GoogleHealthSignIn : YouIntent
    data object GoogleHealthSignOut : YouIntent
    data object SyncNow : YouIntent
    data object DriveSignIn : YouIntent
    data object DriveSignOut : YouIntent
    data object BackupNow : YouIntent
    data object RestoreNow : YouIntent
    data object DismissBackupMessage : YouIntent
    data class SetActivityOnlySteps(val enabled: Boolean) : YouIntent
    data class SetActivityOnlyDistance(val enabled: Boolean) : YouIntent
    data class SetActivityOnlyCalories(val enabled: Boolean) : YouIntent
    data class SetDarkMode(val enabled: Boolean) : YouIntent
    data class SetDynamicColor(val enabled: Boolean) : YouIntent
}

sealed interface YouEffect {
    data object NavigateBack : YouEffect
    data object LaunchDriveSignIn : YouEffect
    data object LaunchFitbitSignIn : YouEffect
    data object LaunchGoogleHealthAuth : YouEffect
}
