package com.pulse.feature.exercise.state

import com.pulse.domain.model.ExerciseDetail

data class ExerciseDetailState(
    val sessionId: String = "",
    val detail: ExerciseDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEditDialog: Boolean = false,
    val editCalories: String = "",
    val editDistance: String = "",
    val editSteps: String = "",
)

sealed interface ExerciseDetailIntent {
    data object Load : ExerciseDetailIntent
    data object Back : ExerciseDetailIntent
    data object RequestRouteConsent : ExerciseDetailIntent
    data class RouteConsentResult(val route: List<RoutePointData>) : ExerciseDetailIntent
    data object OpenEdit : ExerciseDetailIntent
    data object DismissEdit : ExerciseDetailIntent
    data class UpdateEditField(val field: EditField, val value: String) : ExerciseDetailIntent
    data object SaveEdit : ExerciseDetailIntent
}

enum class EditField { Calories, Distance, Steps }

/** Minimal route point for the consent result — avoids HC dependency in the feature module. */
data class RoutePointData(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
)

sealed interface ExerciseDetailEffect {
    data object NavigateBack : ExerciseDetailEffect
    data class LaunchRouteConsent(val sessionId: String) : ExerciseDetailEffect
}
