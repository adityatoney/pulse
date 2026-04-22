package com.pulse.feature.insights.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.insights.state.HeatmapDay
import com.pulse.feature.insights.state.HeatmapDetailEffect
import com.pulse.feature.insights.state.HeatmapDetailIntent
import com.pulse.feature.insights.state.HeatmapDetailState
import com.pulse.feature.insights.state.HeatmapViewMode
import com.pulse.feature.insights.state.RingDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class HeatmapDetailViewModel @Inject constructor(
    private val healthRepo: HealthRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialMetric: MetricType = savedStateHandle.get<String>("metric")
        ?.let { name -> MetricType.entries.find { it.name == name } }
        ?: MetricType.Steps

    private val _state = MutableStateFlow(HeatmapDetailState(metric = initialMetric))
    val state: StateFlow<HeatmapDetailState> = _state.asStateFlow()

    private val _effects = Channel<HeatmapDetailEffect>(Channel.BUFFERED)
    val effects: Flow<HeatmapDetailEffect> = _effects.receiveAsFlow()

    private var dataJob: Job? = null
    private var ringsJob: Job? = null

    init {
        loadData(initialMetric)
    }

    fun onIntent(intent: HeatmapDetailIntent) {
        when (intent) {
            is HeatmapDetailIntent.ChangeMetric -> {
                _state.update { it.copy(metric = intent.metric) }
                loadData(intent.metric)
            }
            is HeatmapDetailIntent.ChangeViewMode -> {
                _state.update { it.copy(viewMode = intent.mode) }
                if (intent.mode == HeatmapViewMode.Rings && _state.value.ringDays.isEmpty()) {
                    loadRingsData()
                }
            }
            HeatmapDetailIntent.Back -> _effects.trySend(HeatmapDetailEffect.NavigateBack)
            HeatmapDetailIntent.PrevMonth -> {
                val months = _state.value.availableMonths
                val current = _state.value.selectedMonth ?: return
                val idx = months.indexOf(current)
                if (idx > 0) _state.update { it.copy(selectedMonth = months[idx - 1]) }
            }
            HeatmapDetailIntent.NextMonth -> {
                val months = _state.value.availableMonths
                val current = _state.value.selectedMonth ?: return
                val idx = months.indexOf(current)
                if (idx < months.lastIndex) _state.update { it.copy(selectedMonth = months[idx + 1]) }
            }
        }
    }

    private fun loadData(metric: MetricType) {
        dataJob?.cancel()
        val todayDate = clock.today()
        val tz = TimeZone.currentSystemDefault()
        val oneYearAgo = todayDate.minus(DatePeriod(days = 365))
        val label = InsightsViewModel.metricLabel(metric)
        val currentMonth = todayDate.toString().substring(0, 7)

        dataJob = healthRepo.observeSeries(
            metric,
            DateRange(oneYearAgo, todayDate),
            Bucket.Day,
        ).onEach { series ->
            val maxVal = series.points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
            val days = series.points.map { point ->
                HeatmapDay(
                    date = point.bucketStart.toLocalDateTime(tz).date.toString(),
                    intensity = (point.value / maxVal).toFloat().coerceIn(0f, 1f),
                    rawValue = point.value,
                    metricLabel = label,
                )
            }
            val months = days.map { it.date.substring(0, 7) }.distinct().sorted()
            _state.update {
                it.copy(
                    heatmapDays = days,
                    loading = false,
                    availableMonths = if (it.viewMode == HeatmapViewMode.Heatmap) months
                        else it.availableMonths,
                    selectedMonth = it.selectedMonth ?: currentMonth,
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun loadRingsData() {
        ringsJob?.cancel()
        val todayDate = clock.today()
        val tz = TimeZone.currentSystemDefault()
        val oneYearAgo = todayDate.minus(DatePeriod(days = 365))
        val range = DateRange(oneYearAgo, todayDate)
        val currentMonth = todayDate.toString().substring(0, 7)

        ringsJob = combine(
            healthRepo.observeSeries(MetricType.Steps, range, Bucket.Day),
            healthRepo.observeSeries(MetricType.ActiveCalories, range, Bucket.Day),
            healthRepo.observeSeries(MetricType.Distance, range, Bucket.Day),
        ) { stepsSeries, calSeries, distSeries ->
            Triple(stepsSeries, calSeries, distSeries)
        }.onEach { (stepsSeries, calSeries, distSeries) ->
            val stepsMap = stepsSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date.toString()
            }
            val calMap = calSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date.toString()
            }
            val distMap = distSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date.toString()
            }

            val allDates = (stepsMap.keys + calMap.keys + distMap.keys).sorted()
            val ringDays = allDates.map { date ->
                val sp = stepsMap[date]
                val cp = calMap[date]
                val dp = distMap[date]
                RingDay(
                    date = date,
                    stepsValue = sp?.value ?: 0.0,
                    stepsGoal = sp?.goal ?: DEFAULT_STEPS_GOAL,
                    stepsProgress = progress(sp?.value, sp?.goal, DEFAULT_STEPS_GOAL),
                    caloriesValue = cp?.value ?: 0.0,
                    caloriesGoal = cp?.goal ?: DEFAULT_CALORIES_GOAL,
                    caloriesProgress = progress(cp?.value, cp?.goal, DEFAULT_CALORIES_GOAL),
                    distanceValue = dp?.value ?: 0.0,
                    distanceGoal = dp?.goal ?: DEFAULT_DISTANCE_GOAL,
                    distanceProgress = progress(dp?.value, dp?.goal, DEFAULT_DISTANCE_GOAL),
                )
            }

            val months = ringDays.map { it.date.substring(0, 7) }.distinct().sorted()
            _state.update {
                it.copy(
                    ringDays = ringDays,
                    availableMonths = if (it.viewMode == HeatmapViewMode.Rings) months
                        else it.availableMonths,
                    selectedMonth = it.selectedMonth ?: currentMonth,
                )
            }
        }.launchIn(viewModelScope)
    }

    companion object {
        private const val DEFAULT_STEPS_GOAL = 10_000.0
        private const val DEFAULT_CALORIES_GOAL = 500.0
        private const val DEFAULT_DISTANCE_GOAL = 5.0

        private fun progress(value: Double?, goal: Double?, fallback: Double): Float {
            val v = value ?: 0.0
            val g = (goal ?: fallback).coerceAtLeast(1.0)
            return (v / g).toFloat()
        }
    }
}
