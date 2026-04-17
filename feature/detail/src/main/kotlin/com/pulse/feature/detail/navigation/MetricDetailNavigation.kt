package com.pulse.feature.detail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pulse.feature.detail.ui.MetricDetailRoute
import kotlinx.serialization.Serializable

@Serializable
data class MetricDetail(val metric: String)

fun NavGraphBuilder.metricDetailScreen(onBack: () -> Unit) {
    composable<MetricDetail> { entry ->
        // The metric arg is read from SavedStateHandle inside the ViewModel.
        entry.toRoute<MetricDetail>()
        MetricDetailRoute(onBack = onBack)
    }
}
