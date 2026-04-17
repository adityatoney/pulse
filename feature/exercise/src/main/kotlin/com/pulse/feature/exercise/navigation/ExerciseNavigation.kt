package com.pulse.feature.exercise.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pulse.feature.exercise.ui.ExerciseDetailRoute
import com.pulse.feature.exercise.ui.ExerciseRoute
import kotlinx.serialization.Serializable

@Serializable
data object ExerciseLog

@Serializable
data class ExerciseDetail(val sessionId: String)

fun NavGraphBuilder.exerciseLogScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
) {
    composable<ExerciseLog> {
        ExerciseRoute(onBack = onBack, onNavigateToDetail = onNavigateToDetail)
    }
}

fun NavGraphBuilder.exerciseDetailScreen(onBack: () -> Unit) {
    composable<ExerciseDetail> {
        ExerciseDetailRoute(onBack = onBack)
    }
}
