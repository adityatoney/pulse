package com.pulse.feature.you.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.you.ui.YouRoute
import kotlinx.serialization.Serializable

@Serializable
data object YouProfile

fun NavGraphBuilder.youScreen(onBack: () -> Unit) {
    composable<YouProfile> {
        YouRoute(onBack = onBack)
    }
}
