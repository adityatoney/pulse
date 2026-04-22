package com.pulse.feature.detail.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DailyHrRange
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.detail.state.HrDetailEffect
import com.pulse.feature.detail.state.HrDetailIntent
import com.pulse.feature.detail.state.HrDetailState
import com.pulse.feature.detail.state.HrTimeframe
import com.pulse.feature.detail.state.WeeklyHrSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

@HiltViewModel
class HrDetailViewModel @Inject constructor(
    private val healthRepo: HealthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(HrDetailState(periodAnchor = clock.today()))
    val state: StateFlow<HrDetailState> = _state.asStateFlow()

    private val _effects = Channel<HrDetailEffect>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<HrDetailEffect> = _effects.receiveAsFlow()

    private val intents = MutableSharedFlow<HrDetailIntent>(extraBufferCapacity = 16)
    private var dataJob: Job? = null

    init {
        intents.onEach(::reduce).launchIn(viewModelScope)
        wireData()
    }

    fun onIntent(intent: HrDetailIntent) { intents.tryEmit(intent) }

    private fun wireData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            val anchor = _state.value.periodAnchor
            val tf = _state.value.timeframe

            _state.update { it.copy(isLoading = true, periodLabel = formatPeriodLabel(anchor, tf)) }

            when (tf) {
                HrTimeframe.Day -> {
                    healthRepo.observeIntradayHr(anchor).onEach { samples ->
                        val min = samples.minOfOrNull { it.bpm }
                        val max = samples.maxOfOrNull { it.bpm }
                        val avg = if (samples.isNotEmpty()) samples.map { it.bpm }.average().toInt() else null
                        _state.update {
                            it.copy(
                                intradaySamples = samples,
                                dayMin = min,
                                dayMax = max,
                                dayAvg = avg,
                                isLoading = false,
                            )
                        }
                    }.launchIn(this)

                    healthRepo.observeDailyAggregate(anchor, MetricType.RestingHeartRate).onEach { agg ->
                        _state.update { it.copy(restingHr = agg.total.takeIf { v -> v > 0 }?.toInt()) }
                    }.launchIn(this)
                }
                HrTimeframe.Week -> {
                    val dow = anchor.dayOfWeek.ordinal
                    val weekStart = anchor.minus(DatePeriod(days = dow))
                    val weekEnd = weekStart.plus(DatePeriod(days = 6))
                    val range = DateRange(weekStart, weekEnd)

                    healthRepo.observeHrDailyRanges(range).onEach { ranges ->
                        val summaries = buildWeeklySummaries(ranges, weekStart, weekEnd)
                        _state.update {
                            it.copy(dailyRanges = ranges, weeklySummaries = summaries, isLoading = false)
                        }
                    }.launchIn(this)
                }
                HrTimeframe.Month -> {
                    val first = LocalDate(anchor.year, anchor.monthNumber, 1)
                    val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                    val range = DateRange(first, last)

                    healthRepo.observeHrDailyRanges(range).onEach { ranges ->
                        val summaries = buildWeeklySummaries(ranges, first, last)
                        _state.update {
                            it.copy(dailyRanges = ranges, weeklySummaries = summaries, isLoading = false)
                        }
                    }.launchIn(this)
                }
            }
        }
    }

    private suspend fun reduce(intent: HrDetailIntent) {
        when (intent) {
            is HrDetailIntent.ChangeTimeframe -> {
                _state.update { it.copy(timeframe = intent.tf) }
                wireData()
            }
            is HrDetailIntent.MovePeriod -> {
                val s = _state.value
                val delta = when (s.timeframe) {
                    HrTimeframe.Day -> if (intent.forward) DatePeriod(days = 1) else DatePeriod(days = -1)
                    HrTimeframe.Week -> if (intent.forward) DatePeriod(days = 7) else DatePeriod(days = -7)
                    HrTimeframe.Month -> if (intent.forward) DatePeriod(months = 1) else DatePeriod(months = -1)
                }
                val newAnchor = s.periodAnchor.plus(delta)
                if (newAnchor <= clock.today()) {
                    _state.update { it.copy(periodAnchor = newAnchor) }
                    wireData()
                }
            }
            HrDetailIntent.Back -> _effects.trySend(HrDetailEffect.NavigateBack)
        }
    }

    private fun buildWeeklySummaries(ranges: List<DailyHrRange>, start: LocalDate, end: LocalDate): List<WeeklyHrSummary> {
        if (ranges.isEmpty()) return emptyList()

        // Group into 7-day chunks
        val sorted = ranges.sortedBy { it.date }
        val summaries = mutableListOf<WeeklyHrSummary>()
        var chunkStart = start

        while (chunkStart <= end) {
            val chunkEnd = chunkStart.plus(DatePeriod(days = 6)).let { if (it > end) end else it }
            val chunk = sorted.filter { it.date in chunkStart..chunkEnd }
            if (chunk.isNotEmpty()) {
                val sMonth = chunkStart.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                val eMonth = chunkEnd.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                val label = if (sMonth == eMonth) "$sMonth ${chunkStart.dayOfMonth}–${chunkEnd.dayOfMonth}"
                else "$sMonth ${chunkStart.dayOfMonth} – $eMonth ${chunkEnd.dayOfMonth}"

                summaries.add(
                    WeeklyHrSummary(
                        label = label,
                        minBpm = chunk.minOf { it.minBpm },
                        maxBpm = chunk.maxOf { it.maxBpm },
                        avgBpm = chunk.map { it.avgBpm }.average().toInt(),
                    )
                )
            }
            chunkStart = chunkStart.plus(DatePeriod(days = 7))
        }
        return summaries
    }

    companion object {
        private fun formatPeriodLabel(anchor: LocalDate, tf: HrTimeframe): String {
            return when (tf) {
                HrTimeframe.Day -> {
                    val month = anchor.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    "$month ${anchor.dayOfMonth}, ${anchor.year}"
                }
                HrTimeframe.Week -> {
                    val dow = anchor.dayOfWeek.ordinal
                    val start = anchor.minus(DatePeriod(days = dow))
                    val end = start.plus(DatePeriod(days = 6))
                    val sMonth = start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    val eMonth = end.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    if (sMonth == eMonth) "$sMonth ${start.dayOfMonth}–${end.dayOfMonth}"
                    else "$sMonth ${start.dayOfMonth} – $eMonth ${end.dayOfMonth}"
                }
                HrTimeframe.Month -> {
                    val month = anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }
                    "$month ${anchor.year}"
                }
            }
        }
    }
}
