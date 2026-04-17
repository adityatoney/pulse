package com.pulse.feature.exercise.state

import com.pulse.domain.model.ExerciseDetail

data class ExerciseDetailState(
    val sessionId: String = "",
    val detail: ExerciseDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface ExerciseDetailIntent {
    data object Load : ExerciseDetailIntent
    data object Back : ExerciseDetailIntent
    data object RequestRouteConsent : ExerciseDetailIntent
    data class RouteConsentResult(val route: List<RoutePointData>) : ExerciseDetailIntent
}

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
