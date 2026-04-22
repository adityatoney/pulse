package com.pulse.feature.detail.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import com.pulse.domain.usecase.CalculateMoMUseCase
import com.pulse.domain.usecase.CalculateWoWUseCase
import com.pulse.domain.usecase.GetInsightsUseCase
import com.pulse.domain.usecase.GetMetricSeriesUseCase
import com.pulse.domain.util.Clock
import com.pulse.feature.detail.state.MetricDetailEffect
import com.pulse.feature.detail.state.MetricDetailIntent
import com.pulse.feature.detail.state.MetricDetailState
import com.pulse.feature.detail.state.PeriodComparison
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import javax.inject.Inject

@HiltViewModel
class MetricDetailViewModel @Inject constructor(
    private val getSeries: GetMetricSeriesUseCase,
    private val calcWoW: CalculateWoWUseCase,
    private val calcMoM: CalculateMoMUseCase,
    private val getInsights: GetInsightsUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val metric: MetricType = savedStateHandle.get<String>("metric")
        ?.let(MetricType::valueOf) ?: MetricType.Steps

    private val _state = MutableStateFlow(
        MetricDetailState(metric = metric, periodAnchor = clock.today())
    )
    val state: StateFlow<MetricDetailState> = _state.asStateFlow()

    private val _effects = Channel<MetricDetailEffect>(Channel.BUFFERED)
    val effects: Flow<MetricDetailEffect> = _effects.receiveAsFlow()

    private var streamJob: Job? = null

    init {
        rewire()
    }

    fun onIntent(intent: MetricDetailIntent) {
        when (intent) {
            MetricDetailIntent.Load -> rewire()
            is MetricDetailIntent.ChangeTimeframe -> {
                _state.update { it.copy(timeframe = intent.tf) }
                rewire()
            }
            is MetricDetailIntent.MovePeriod -> {
                val step = when (_state.value.timeframe) {
                    Timeframe.Day -> DatePeriod(days = 1)
                    Timeframe.Week -> DatePeriod(days = 7)
                    Timeframe.Month -> DatePeriod(months = 1)
                    Timeframe.ThreeMonths -> DatePeriod(months = 3)
                    Timeframe.SixMonths -> DatePeriod(months = 6)
                    Timeframe.Year -> DatePeriod(years = 1)
                }
                val today = clock.today()
                val raw = if (intent.forward) _state.value.periodAnchor.plus(step)
                else _state.value.periodAnchor.minus(step)
                val newAnchor = if (raw > today) today else raw
                _state.update { it.copy(periodAnchor = newAnchor) }
                rewire()
            }
            is MetricDetailIntent.DrillIntoMonth -> {
                _state.update {
                    it.copy(
                        timeframe = Timeframe.Month,
                        periodAnchor = intent.monthAnchor,
                    )
                }
                rewire()
            }
            MetricDetailIntent.Back -> _effects.trySend(MetricDetailEffect.NavigateBack)
        }
    }

    private fun rewire() {
        streamJob?.cancel()
        val s = _state.value
        val comparisonAnchors = comparisonAnchors(s.periodAnchor, s.timeframe)
        // For comparisons, use the appropriate timeframe for each anchor:
        // - Month view: anchors are weekly Mondays, so fetch Week series per anchor
        // - ThreeMonths view: anchors are monthly, so fetch Month series per anchor
        // - Others: use the same timeframe
        val comparisonTf = when (s.timeframe) {
            Timeframe.Week -> Timeframe.Day
            Timeframe.Month -> Timeframe.Week
            Timeframe.ThreeMonths -> Timeframe.Month
            Timeframe.SixMonths -> Timeframe.Month
            else -> s.timeframe
        }
        val comparisonFlows = comparisonAnchors.map { anchor ->
            getSeries(s.metric, anchor, comparisonTf)
        }
        // Align WoW/MoM anchor to the end of the displayed period so the
        // comparison window matches the dates the user actually sees.
        val today = clock.today()
        val deltaAnchor = when (s.timeframe) {
            Timeframe.Week -> {
                // Snap to Sunday of the displayed calendar week (Mon-Sun)
                val dow = s.periodAnchor.dayOfWeek.ordinal // Monday=0
                val sunday = s.periodAnchor.plus(DatePeriod(days = 6 - dow))
                if (sunday > today) today else sunday
            }
            Timeframe.Month -> {
                // Snap to last day of the displayed month
                val first = LocalDate(s.periodAnchor.year, s.periodAnchor.monthNumber, 1)
                val lastDay = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                if (lastDay > today) today else lastDay
            }
            else -> s.periodAnchor
        }
        @Suppress("UNCHECKED_CAST")
        val allFlows: List<Flow<Any?>> = listOf(
            getSeries(s.metric, s.periodAnchor, s.timeframe) as Flow<Any?>,
            calcWoW(s.metric, deltaAnchor) as Flow<Any?>,
            calcMoM(s.metric, deltaAnchor) as Flow<Any?>,
        ) + comparisonFlows.map { it as Flow<Any?> }

        streamJob = combine(allFlows) { results ->
            val series = results[0] as com.pulse.domain.model.MetricSeries
            val wow = results[1] as? com.pulse.domain.model.DeltaPercent
            val mom = results[2] as? com.pulse.domain.model.DeltaPercent
            val compSeries = (3 until results.size).map { results[it] as com.pulse.domain.model.MetricSeries }

            val points = series.points
            val total = points.sumOf { it.value }
            val avg = if (points.isNotEmpty()) total / points.size else 0.0
            val goalHits = points.count { p -> p.goal?.let { g -> p.value >= g } == true }
            val goal = points.firstOrNull()?.goal
            val comparisons = buildComparisons(comparisonAnchors, compSeries, s.metric, s.timeframe, series)
            _state.update {
                it.copy(
                    series = series,
                    average = avg,
                    total = total,
                    goalHitDays = goalHits,
                    goal = goal,
                    wow = wow,
                    mom = mom,
                    comparisons = comparisons,
                    loading = false,
                    error = null,
                )
            }
        }.launchIn(viewModelScope)

        // Wire insights for context-appropriate view
        val insightContext = when (s.timeframe) {
            Timeframe.Day -> "DetailDay"
            Timeframe.Week -> "DetailWeek"
            Timeframe.Month -> "DetailMonth"
            else -> "Detail3M6MY"
        }
        val insightLimit = when (s.timeframe) {
            Timeframe.Day -> 1
            Timeframe.Week, Timeframe.Month -> 2
            else -> 1
        }
        getInsights.forMetric(s.periodAnchor.toString(), insightContext, s.metric.name, insightLimit)
            .onEach { insights ->
                _state.update { it.copy(insights = insights) }
            }.launchIn(viewModelScope)
    }

    /** Returns the anchor dates for comparison rows. */
    private fun comparisonAnchors(anchor: LocalDate, tf: Timeframe): List<LocalDate> = when (tf) {
        Timeframe.Week -> {
            // 7 daily anchors: Mon-Sun of the week containing anchor
            val dow = anchor.dayOfWeek.ordinal // Monday=0
            val monday = anchor.minus(DatePeriod(days = dow))
            (0..6).map { monday.plus(DatePeriod(days = it)) }
        }
        Timeframe.Month -> {
            // Weeks within the month: each Monday starting from the first Monday on or before the 1st
            val first = LocalDate(anchor.year, anchor.monthNumber, 1)
            val nextMonth = first.plus(DatePeriod(months = 1))
            val last = nextMonth.minus(DatePeriod(days = 1))
            val mondays = mutableListOf<LocalDate>()
            // Find the Monday of the week containing the 1st
            val firstDow = first.dayOfWeek.ordinal // Monday=0
            var monday = first.minus(DatePeriod(days = firstDow))
            while (monday <= last) {
                mondays += monday
                monday = monday.plus(DatePeriod(days = 7))
            }
            mondays
        }
        Timeframe.ThreeMonths -> {
            // 3 monthly anchors, newest first
            (0..2).map { anchor.minus(DatePeriod(months = it)) }
        }
        Timeframe.SixMonths -> {
            // 6 monthly anchors, newest first
            (0..5).map { anchor.minus(DatePeriod(months = it)) }
        }
        else -> listOf(anchor)
    }

    private fun buildComparisons(
        anchors: List<LocalDate>,
        seriesList: List<com.pulse.domain.model.MetricSeries>,
        metric: MetricType,
        tf: Timeframe,
        chartSeries: com.pulse.domain.model.MetricSeries,
    ): List<PeriodComparison> {
        val today = clock.today()
        return when (tf) {
            Timeframe.Week -> {
                // Use chart series points directly (from daily aggregates) — avoids
                // the mismatch where Day-timeframe comparison flows use exercise-only data.
                val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
                val chartPoints = chartSeries.points
                anchors.mapIndexed { i, dayDate ->
                    val dayName = dayDate.dayOfWeek.name.take(3).lowercase()
                        .replaceFirstChar { it.uppercase() }
                    val mon = dayDate.month.name.take(3).lowercase()
                        .replaceFirstChar { it.uppercase() }
                    val label = if (dayDate == today) "Today"
                    else "$dayName, $mon ${dayDate.dayOfMonth}"
                    val value = chartPoints.getOrNull(i)?.value ?: 0.0
                    PeriodComparison(label = label, value = formatValue(value, metric))
                }
            }
            Timeframe.Month -> {
                // Each anchor is a Monday; show "Week of Mon D" with that week's total
                anchors.zip(seriesList).map { (monday, series) ->
                    val mon = monday.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    val label = "Week of $mon ${monday.dayOfMonth}"
                    val pts = series.points
                    val total = pts.sumOf { it.value }
                    PeriodComparison(label = label, value = formatValue(total, metric))
                }
            }
            Timeframe.ThreeMonths -> {
                // 3 monthly rows (newest first), show month name with total
                anchors.zip(seriesList).mapIndexed { idx, (anchor, series) ->
                    val monthName = anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }
                    val label = if (idx == 0 && anchor.monthNumber == today.monthNumber && anchor.year == today.year)
                        "This month" else monthName
                    val pts = series.points
                    val total = pts.sumOf { it.value }
                    PeriodComparison(label = label, value = formatValue(total, metric))
                }
            }
            Timeframe.SixMonths -> {
                // 6 monthly rows (newest first), show month name with total
                anchors.zip(seriesList).mapIndexed { idx, (anchor, series) ->
                    val monthName = anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }
                    val label = if (idx == 0 && anchor.monthNumber == today.monthNumber && anchor.year == today.year)
                        "This month" else monthName
                    val pts = series.points
                    val total = pts.sumOf { it.value }
                    PeriodComparison(label = label, value = formatValue(total, metric))
                }
            }
            else -> {
                val pts = seriesList.firstOrNull()?.points.orEmpty()
                val total = pts.sumOf { it.value }
                val displayValue = if (tf == Timeframe.Day) total
                    else if (pts.isNotEmpty()) total / pts.size else 0.0
                val label = when (tf) {
                    Timeframe.Day -> "Today"
                    Timeframe.Year -> "This year"
                    else -> ""
                }
                listOf(PeriodComparison(label = label, value = formatValue(displayValue, metric)))
            }
        }
    }

    private fun LocalDate.atStartMs(): Long {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        return kotlinx.datetime.LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(zone).toEpochMilliseconds()
    }

    private fun formatValue(v: Double, metric: MetricType): String = when (metric) {
        MetricType.Steps, MetricType.Calories, MetricType.ActiveCalories, MetricType.ZoneMinutes ->
            "%,d".format(v.toInt())
        MetricType.Distance -> "%.2f".format(v)
        else -> "%.1f".format(v)
    }
}
