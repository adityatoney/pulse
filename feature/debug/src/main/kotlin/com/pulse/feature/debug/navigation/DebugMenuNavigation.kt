package com.pulse.feature.debug.navigation

import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.pulse.feature.debug.ui.DebugMenuRoute
import kotlinx.serialization.Serializable

@Serializable
data object DebugMenu

fun NavGraphBuilder.debugMenuScreen(onDismiss: () -> Unit) {
    composable<DebugMenu>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "pulse://debug" },
        ),
    ) {
        DebugMenuRoute(onDismiss = onDismiss)
    }
}
