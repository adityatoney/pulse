package com.pulse.feature.detail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.detail.ui.HrDetailRoute
import kotlinx.serialization.Serializable

@Serializable
data object HrDetail

fun NavGraphBuilder.hrDetailScreen(onBack: () -> Unit) {
    composable<HrDetail> {
        HrDetailRoute(onBack = onBack)
    }
}
