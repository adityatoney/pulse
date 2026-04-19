package com.pulse.feature.insights.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.insights.ui.InsightsRoute
import kotlinx.serialization.Serializable

@Serializable
data object Insights

fun NavGraphBuilder.insightsScreen(onBack: () -> Unit) {
    composable<Insights> {
        InsightsRoute(onBack = onBack)
    }
}
