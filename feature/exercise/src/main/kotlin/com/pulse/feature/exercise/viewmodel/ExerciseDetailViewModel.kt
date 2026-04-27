package com.pulse.feature.exercise.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.RoutePoint
import com.pulse.domain.repository.HealthRepository
import com.pulse.feature.exercise.state.EditField
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
            ExerciseDetailIntent.OpenEdit -> openEdit()
            ExerciseDetailIntent.DismissEdit -> _state.update { it.copy(showEditDialog = false) }
            is ExerciseDetailIntent.UpdateEditField -> updateEditField(intent.field, intent.value)
            ExerciseDetailIntent.SaveEdit -> saveEdit()
        }
    }

    private fun openEdit() {
        val detail = _state.value.detail ?: return
        val distMi = detail.session.distanceMeters?.let { it / 1_609.34 }
        _state.update {
            it.copy(
                showEditDialog = true,
                editCalories = detail.session.calories?.let { c -> "%.0f".format(c) } ?: "",
                editDistance = distMi?.let { d -> "%.2f".format(d) } ?: "",
                editSteps = detail.steps?.toString() ?: "",
            )
        }
    }

    private fun updateEditField(field: EditField, value: String) {
        _state.update {
            when (field) {
                EditField.Calories -> it.copy(editCalories = value)
                EditField.Distance -> it.copy(editDistance = value)
                EditField.Steps -> it.copy(editSteps = value)
            }
        }
    }

    private fun saveEdit() {
        val s = _state.value
        val calories = s.editCalories.toDoubleOrNull() ?: return
        val distMi = s.editDistance.toDoubleOrNull()
        val distMeters = distMi?.let { it * 1_609.34 }
        val steps = s.editSteps.toIntOrNull()

        viewModelScope.launch {
            health.updateExerciseMetrics(sessionId, calories, distMeters, steps)
            _state.update { it.copy(showEditDialog = false) }
            load() // reload to show updated values
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
