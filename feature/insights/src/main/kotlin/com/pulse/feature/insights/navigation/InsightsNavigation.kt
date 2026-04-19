package com.pulse.feature.insights.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.insights.ui.HeatmapDetailRoute
import com.pulse.feature.insights.ui.InsightsRoute
import kotlinx.serialization.Serializable

@Serializable
data object Insights

@Serializable
data class HeatmapDetail(val metric: String = "Steps")

fun NavGraphBuilder.insightsScreen(
    onBack: () -> Unit,
    onNavigateToHeatmap: (String) -> Unit = {},
) {
    composable<Insights> {
        InsightsRoute(onBack = onBack, onNavigateToHeatmap = onNavigateToHeatmap)
    }
}

fun NavGraphBuilder.heatmapDetailScreen(onBack: () -> Unit) {
    composable<HeatmapDetail> {
        HeatmapDetailRoute(onBack = onBack)
    }
}
