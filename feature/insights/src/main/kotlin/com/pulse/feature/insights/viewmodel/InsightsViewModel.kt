package com.pulse.feature.insights.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.usecase.GetInsightsUseCase
import com.pulse.domain.usecase.GetMetricSeriesUseCase
import com.pulse.domain.util.Clock
import com.pulse.feature.insights.state.DayBar
import com.pulse.feature.insights.state.HeatmapDay
import com.pulse.feature.insights.state.InsightsEffect
import com.pulse.feature.insights.state.InsightsIntent
import com.pulse.feature.insights.state.InsightsState
import com.pulse.feature.insights.state.MetricPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val getInsights: GetInsightsUseCase,
    private val getMetricSeries: GetMetricSeriesUseCase,
    private val healthRepo: HealthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    private val _effects = Channel<InsightsEffect>(Channel.BUFFERED)
    val effects: Flow<InsightsEffect> = _effects.receiveAsFlow()

    init {
        wireStreams()
    }

    fun onIntent(intent: InsightsIntent) {
        when (intent) {
            InsightsIntent.Load -> wireStreams()
            InsightsIntent.Back -> _effects.trySend(InsightsEffect.NavigateBack)
        }
    }

    private fun wireStreams() {
        val todayDate = clock.today()
        val today = todayDate.toString()
        val tz = TimeZone.currentSystemDefault()

        // ── Insight cards ──

        getInsights.byCategory("Daily", today, today).onEach { insights ->
            _state.update { it.copy(dailyInsights = insights, loading = false) }
        }.launchIn(viewModelScope)

        getInsights.byCategory("Weekly", today, today).onEach { insights ->
            val deduped = insights
                .groupBy { it.type }
                .flatMap { (_, group) -> group.sortedByDescending { it.score }.take(1) }
                .sortedByDescending { it.score }
            _state.update { it.copy(weeklyInsights = deduped) }
        }.launchIn(viewModelScope)

        getInsights.byCategory("Longitudinal", today, today).onEach { insights ->
            _state.update { it.copy(longitudinalInsights = insights) }
        }.launchIn(viewModelScope)

        // ── Visual: weekly bars ──

        getMetricSeries(MetricType.Steps, todayDate, Timeframe.Week).onEach { series ->
            val bars = series.points.map { point ->
                val date = point.bucketStart.toLocalDateTime(tz).date
                DayBar(
                    label = dayLabel(date.dayOfWeek),
                    value = point.value,
                    goal = point.goal ?: DEFAULT_STEP_GOAL,
                    isToday = date == todayDate,
                )
            }
            _state.update { it.copy(weeklyBars = bars) }
        }.launchIn(viewModelScope)

        // ── Visual: 90-day heatmap ──

        val ninetyDaysAgo = todayDate.minus(DatePeriod(days = 90))
        healthRepo.observeSeries(
            MetricType.Steps,
            DateRange(ninetyDaysAgo, todayDate),
            Bucket.Day,
        ).onEach { series ->
            val maxVal = series.points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
            val days = series.points.map { point ->
                HeatmapDay(
                    date = point.bucketStart.toLocalDateTime(tz).date.toString(),
                    intensity = (point.value / maxVal).toFloat().coerceIn(0f, 1f),
                )
            }
            _state.update { it.copy(heatmapDays = days) }
        }.launchIn(viewModelScope)

        // ── Visual: 30-day position strip ──

        val thirtyDaysAgo = todayDate.minus(DatePeriod(days = 30))
        healthRepo.observeSeries(
            MetricType.Steps,
            DateRange(thirtyDaysAgo, todayDate),
            Bucket.Day,
        ).onEach { series ->
            val values = series.points.map { it.value }.filter { it > 0 }
            if (values.size < 3) return@onEach
            val todayVal = series.points.lastOrNull()?.value ?: return@onEach
            val minVal = values.min()
            val maxVal = values.max()
            val avgVal = values.average()
            val range = maxVal - minVal
            val percentile = if (range > 0) {
                ((todayVal - minVal) / range).toFloat().coerceIn(0f, 1f)
            } else {
                0.5f
            }
            _state.update {
                it.copy(todayPosition = MetricPosition(todayVal, minVal, maxVal, avgVal, percentile))
            }
        }.launchIn(viewModelScope)
    }

    companion object {
        private const val DEFAULT_STEP_GOAL = 10_000.0

        private fun dayLabel(dow: DayOfWeek): String = when (dow) {
            DayOfWeek.MONDAY -> "M"
            DayOfWeek.TUESDAY -> "T"
            DayOfWeek.WEDNESDAY -> "W"
            DayOfWeek.THURSDAY -> "T"
            DayOfWeek.FRIDAY -> "F"
            DayOfWeek.SATURDAY -> "S"
            DayOfWeek.SUNDAY -> "S"
            else -> ""
        }
    }
}
