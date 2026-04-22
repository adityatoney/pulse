package com.pulse.feature.sleep.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.SleepSummary
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.sleep.state.SleepNightUi
import com.pulse.feature.sleep.state.SleepOverviewEffect
import com.pulse.feature.sleep.state.SleepOverviewIntent
import com.pulse.feature.sleep.state.SleepOverviewState
import com.pulse.feature.sleep.state.SleepViewMode
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class SleepOverviewViewModel @Inject constructor(
    private val healthRepo: HealthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SleepOverviewState(periodAnchor = weekStart(clock.today())))
    val state: StateFlow<SleepOverviewState> = _state.asStateFlow()

    private val _effects = Channel<SleepOverviewEffect>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<SleepOverviewEffect> = _effects.receiveAsFlow()

    private val intents = MutableSharedFlow<SleepOverviewIntent>(extraBufferCapacity = 16)
    private var dataJob: Job? = null

    init {
        intents.onEach(::reduce).launchIn(viewModelScope)
        wireData()
    }

    fun onIntent(intent: SleepOverviewIntent) { intents.tryEmit(intent) }

    private fun wireData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            val anchor = _state.value.periodAnchor
            val weekEnd = anchor.plus(DatePeriod(days = 6))
            val range = DateRange(anchor, weekEnd)

            _state.update {
                it.copy(
                    periodLabel = formatPeriodLabel(anchor, weekEnd),
                    isLoading = true,
                )
            }

            healthRepo.observeSleepRange(range).onEach { summaries ->
                val nights = summaries.map { it.toUi() }
                val avgMins = if (nights.isNotEmpty()) nights.map { it.durationMinutes }.average().toLong() else 0L

                // Fill all 7 days of the week
                val allNights = (0..6).map { offset ->
                    val date = anchor.plus(DatePeriod(days = offset))
                    val dayLabel = date.dayOfWeek.name.take(1)
                    nights.find { it.date == date } ?: SleepNightUi(
                        date = date,
                        dayLabel = dayLabel,
                        durationMinutes = 0,
                        bedtimeLabel = "",
                        wakeTimeLabel = "",
                        bedtimeHour = 0f,
                        wakeHour = 0f,
                    )
                }

                // Bedtime range for Schedule mode
                val bedtimeRange = if (nights.isNotEmpty()) {
                    val bedtimes = nights.filter { it.bedtimeLabel.isNotEmpty() }.map { it.bedtimeLabel }
                    if (bedtimes.size >= 2) "Bedtimes between ${bedtimes.min()} and ${bedtimes.max()}"
                    else if (bedtimes.size == 1) "Bedtime at ${bedtimes.first()}"
                    else ""
                } else ""

                _state.update {
                    it.copy(
                        nights = allNights,
                        avgDurationMinutes = avgMins,
                        bedtimeRangeLabel = bedtimeRange,
                        isLoading = false,
                    )
                }
            }.launchIn(this)
        }
    }

    private suspend fun reduce(intent: SleepOverviewIntent) {
        when (intent) {
            SleepOverviewIntent.Back -> _effects.trySend(SleepOverviewEffect.NavigateBack)
            is SleepOverviewIntent.ToggleMode -> _state.update { it.copy(viewMode = intent.mode) }
            is SleepOverviewIntent.MovePeriod -> {
                val delta = if (intent.forward) DatePeriod(days = 7) else DatePeriod(days = -7)
                val newAnchor = _state.value.periodAnchor.plus(delta)
                if (newAnchor <= clock.today()) {
                    _state.update { it.copy(periodAnchor = newAnchor) }
                    wireData()
                }
            }
            is SleepOverviewIntent.SelectNight -> {
                _effects.trySend(SleepOverviewEffect.NavigateToNight(intent.date.toString()))
            }
        }
    }

    private fun SleepSummary.toUi(): SleepNightUi {
        val tz = TimeZone.currentSystemDefault()
        val startLocal = start.toLocalDateTime(tz)
        val endLocal = end.toLocalDateTime(tz)

        // Assign to the date the person woke up on
        val date = endLocal.date

        val bedtimeHour = startLocal.hour + startLocal.minute / 60f
        val wakeHour = endLocal.hour + endLocal.minute / 60f

        return SleepNightUi(
            date = date,
            dayLabel = date.dayOfWeek.name.take(1),
            durationMinutes = totalMinutes,
            bedtimeLabel = formatTime(startLocal.hour, startLocal.minute),
            wakeTimeLabel = formatTime(endLocal.hour, endLocal.minute),
            bedtimeHour = bedtimeHour,
            wakeHour = wakeHour,
        )
    }

    companion object {
        private fun weekStart(date: LocalDate): LocalDate {
            val dow = date.dayOfWeek.ordinal // Monday=0
            return date.minus(DatePeriod(days = dow))
        }

        private fun formatPeriodLabel(start: LocalDate, end: LocalDate): String {
            val sMonth = start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val eMonth = end.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            return if (sMonth == eMonth) {
                "$sMonth ${start.dayOfMonth}–${end.dayOfMonth}"
            } else {
                "$sMonth ${start.dayOfMonth} – $eMonth ${end.dayOfMonth}"
            }
        }

        private fun formatTime(hour: Int, minute: Int): String {
            val amPm = if (hour < 12) "AM" else "PM"
            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            return "$h12:${"%02d".format(minute)} $amPm"
        }
    }
}
