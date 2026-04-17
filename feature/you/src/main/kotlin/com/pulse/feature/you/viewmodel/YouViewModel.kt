package com.pulse.feature.you.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.Cadence
import com.pulse.domain.model.Goal
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.GoalsRepository
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.domain.util.Clock
import com.pulse.feature.you.state.GoalSetting
import com.pulse.feature.you.state.YouEffect
import com.pulse.feature.you.state.YouIntent
import com.pulse.feature.you.state.YouState
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
class YouViewModel @Inject constructor(
    private val goalsRepo: GoalsRepository,
    private val healthRepo: HealthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(YouState())
    val state: StateFlow<YouState> = _state.asStateFlow()

    private val _effects = Channel<YouEffect>(Channel.BUFFERED)
    val effects: Flow<YouEffect> = _effects.receiveAsFlow()

    init {
        loadGoals()
        loadWeeklyStats()
    }

    fun onIntent(intent: YouIntent) {
        when (intent) {
            is YouIntent.UpdateAge -> {
                val maxHr = 220 - intent.age
                _state.update {
                    it.copy(
                        age = intent.age,
                        maxHr = maxHr,
                        thresholds = ZoneMinuteCalculator.thresholds(it.restingHr, intent.age),
                    )
                }
            }
            is YouIntent.UpdateRestingHr -> {
                _state.update {
                    it.copy(
                        restingHr = intent.hr,
                        thresholds = ZoneMinuteCalculator.thresholds(intent.hr, it.age),
                    )
                }
            }
            is YouIntent.UpdateMaxHr -> {
                _state.update {
                    it.copy(
                        maxHr = intent.hr,
                        thresholds = ZoneMinuteCalculator.thresholds(it.restingHr, it.age, maxHrOverride = intent.hr),
                    )
                }
            }
            is YouIntent.EditGoal -> {
                val goal = _state.value.goals.find { it.metric == intent.metric }
                _state.update { it.copy(editingGoal = goal) }
            }
            YouIntent.DismissGoalEditor -> {
                _state.update { it.copy(editingGoal = null) }
            }
            is YouIntent.SaveDailyGoal -> {
                viewModelScope.launch {
                    val today = clock.today()
                    goalsRepo.setGoal(
                        Goal(
                            metric = intent.metric,
                            target = intent.target.toDouble(),
                            effectiveFrom = today,
                            cadence = Cadence.Daily,
                        )
                    )
                    _state.update { state ->
                        state.copy(
                            goals = state.goals.map { g ->
                                if (g.metric == intent.metric) g.copy(
                                    dailyTarget = intent.target,
                                    weeklyTarget = intent.target * 7,
                                ) else g
                            },
                            editingGoal = null,
                        )
                    }
                }
            }
            YouIntent.Back -> _effects.trySend(YouEffect.NavigateBack)
        }
    }

    private fun loadGoals() {
        viewModelScope.launch {
            goalsRepo.observeGoals().collect { goalMap ->
                _state.update { state ->
                    state.copy(
                        goals = state.goals.map { g ->
                            val saved = goalMap[g.metric]
                            if (saved != null) g.copy(
                                dailyTarget = saved.target.toInt(),
                                weeklyTarget = (saved.target * 7).toInt(),
                            ) else g
                        }
                    )
                }
            }
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch {
            val today = clock.today()
            healthRepo.observeTodaySummary(today).collect { summary ->
                _state.update {
                    it.copy(
                        todaySteps = summary.today.steps.current,
                        weekZoneMin = summary.today.zoneMinutes.current,
                        weekCalories = summary.today.calories.current,
                    )
                }
            }
        }
    }
}
