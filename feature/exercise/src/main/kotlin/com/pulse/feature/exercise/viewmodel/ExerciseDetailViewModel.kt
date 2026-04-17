package com.pulse.feature.exercise.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.RoutePoint
import com.pulse.domain.repository.HealthRepository
import com.pulse.feature.exercise.state.ExerciseDetailEffect
import com.pulse.feature.exercise.state.ExerciseDetailIntent
import com.pulse.feature.exercise.state.ExerciseDetailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val health: HealthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId") ?: ""

    private val _state = MutableStateFlow(ExerciseDetailState(sessionId = sessionId))
    val state: StateFlow<ExerciseDetailState> = _state.asStateFlow()

    private val _effects = Channel<ExerciseDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ExerciseDetailEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onIntent(intent: ExerciseDetailIntent) {
        when (intent) {
            ExerciseDetailIntent.Load -> load()
            ExerciseDetailIntent.Back -> _effects.trySend(ExerciseDetailEffect.NavigateBack)
            ExerciseDetailIntent.RequestRouteConsent ->
                _effects.trySend(ExerciseDetailEffect.LaunchRouteConsent(sessionId))
            is ExerciseDetailIntent.RouteConsentResult -> onRouteConsentResult(intent)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = health.getExerciseDetail(sessionId)
                _state.update { it.copy(detail = detail, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun onRouteConsentResult(intent: ExerciseDetailIntent.RouteConsentResult) {
        viewModelScope.launch {
            val points = intent.route.map {
                RoutePoint(
                    timestampMs = it.timestampMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                )
            }
            if (points.isNotEmpty()) {
                health.saveRoutePoints(sessionId, points)
                // Reload to get the updated detail with route
                load()
            }
        }
    }
}
