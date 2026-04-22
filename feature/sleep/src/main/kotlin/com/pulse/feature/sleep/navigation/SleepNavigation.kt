package com.pulse.feature.sleep.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.sleep.ui.SleepOverviewRoute
import com.pulse.feature.sleep.ui.SleepNightRoute
import kotlinx.serialization.Serializable

@Serializable
data object SleepOverview

@Serializable
data class SleepNightDetail(val dateStr: String)

fun NavGraphBuilder.sleepOverviewScreen(
    onBack: () -> Unit,
    onNavigateToNight: (String) -> Unit,
) {
    composable<SleepOverview> {
        SleepOverviewRoute(onBack = onBack, onNavigateToNight = onNavigateToNight)
    }
}

fun NavGraphBuilder.sleepNightScreen(onBack: () -> Unit) {
    composable<SleepNightDetail> {
        SleepNightRoute(onBack = onBack)
    }
}
