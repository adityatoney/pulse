package com.pulse.feature.dashboard.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.domain.model.MetricType
import com.pulse.feature.dashboard.ui.DashboardRoute
import kotlinx.serialization.Serializable

@Serializable
data object Dashboard

fun NavGraphBuilder.dashboardScreen(
    onNavigateToMetric: (MetricType) -> Unit,
    onNavigateToExerciseLog: () -> Unit,
    onNavigateToExerciseDetail: (String) -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToHeatmap: () -> Unit = {},
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDebug: () -> Unit,
) {
    composable<Dashboard> {
        DashboardRoute(
            onNavigateToMetric = onNavigateToMetric,
            onNavigateToExerciseLog = onNavigateToExerciseLog,
            onNavigateToExerciseDetail = onNavigateToExerciseDetail,
            onNavigateToInsights = onNavigateToInsights,
            onNavigateToHeatmap = onNavigateToHeatmap,
            onNavigateToChat = onNavigateToChat,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToDebug = onNavigateToDebug,
        )
    }
}
