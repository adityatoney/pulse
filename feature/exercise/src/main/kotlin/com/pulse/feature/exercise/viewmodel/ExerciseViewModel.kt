package com.pulse.feature.exercise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.exercise.state.DayMarker
import com.pulse.feature.exercise.state.ExerciseEffect
import com.pulse.feature.exercise.state.ExerciseIntent
import com.pulse.feature.exercise.state.ExerciseState
import com.pulse.feature.exercise.state.ExerciseTimeframe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val health: HealthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ExerciseState(
            periodAnchor = clock.today(),
            today = clock.today(),
        )
    )
    val state: StateFlow<ExerciseState> = _state.asStateFlow()

    private val _effects = Channel<ExerciseEffect>(Channel.BUFFERED)
    val effects: Flow<ExerciseEffect> = _effects.receiveAsFlow()

    private var streamJob: Job? = null

    init {
        rewire()
    }

    fun onIntent(intent: ExerciseIntent) {
        when (intent) {
            ExerciseIntent.Load -> rewire()
            is ExerciseIntent.ChangeTimeframe -> {
                _state.update { it.copy(timeframe = intent.tf) }
                rewire()
            }
            is ExerciseIntent.MovePeriod -> {
                val current = _state.value
                val step = when (current.timeframe) {
                    ExerciseTimeframe.Week -> DatePeriod(days = 7)
                    ExerciseTimeframe.Month -> DatePeriod(months = 1)
                }
                val newAnchor = if (intent.forward) {
                    val candidate = current.periodAnchor.plus(step)
                    if (candidate > clock.today()) clock.today() else candidate
                } else {
                    current.periodAnchor.minus(step)
                }
                _state.update { it.copy(periodAnchor = newAnchor) }
                rewire()
            }
            is ExerciseIntent.ChangeFilter -> {
                _state.update { it.copy(activeFilter = intent.filter) }
            }
            ExerciseIntent.Back -> _effects.trySend(ExerciseEffect.NavigateBack)
        }
    }

    private fun rewire() {
        streamJob?.cancel()
        val s = _state.value
        val range = when (s.timeframe) {
            ExerciseTimeframe.Week -> weekRange(s.periodAnchor)
            ExerciseTimeframe.Month -> monthRange(s.periodAnchor)
        }
        streamJob = health.observeExerciseSessions(range)
            .onEach { sessions -> processSessions(sessions, range) }
            .launchIn(viewModelScope)
    }

    private fun processSessions(sessions: List<ExerciseSession>, range: DateRange) {
        val zone = TimeZone.currentSystemDefault()
        val sessionsByDay = sessions.groupBy { session ->
            session.start.toLocalDateTime(zone).date
        }
        val weekDates = weekRange(_state.value.periodAnchor)
        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        val monday = weekDates.start
        val dayMarkers = dayLabels.mapIndexed { index, label ->
            val date = monday.plus(DatePeriod(days = index))
            DayMarker(label = label, hasExercise = sessionsByDay.containsKey(date))
        }
        val exerciseDaysHit = sessionsByDay.keys.size
        val totalDuration = sessions.sumOf { it.durationMinutes }.toInt()
        val totalDistance = sessions.sumOf { it.distanceMeters ?: 0.0 } / 1609.34
        val totalCals = sessions.sumOf { it.calories ?: 0.0 }.toInt()
        val totalZm = sessions.sumOf { it.zoneMinutes ?: 0 }

        _state.update {
            it.copy(
                sessionsByDay = sessionsByDay,
                dayMarkers = dayMarkers,
                exerciseDaysHit = exerciseDaysHit,
                totalSessions = sessions.size,
                totalDurationMin = totalDuration,
                totalDistanceMiles = totalDistance,
                totalCalories = totalCals,
                totalZoneMin = totalZm,
                isLoading = false,
            )
        }
    }

    private fun weekRange(anchor: LocalDate): DateRange {
        val dow = anchor.dayOfWeek.ordinal // Monday=0
        val monday = anchor.minus(DatePeriod(days = dow))
        val sunday = monday.plus(DatePeriod(days = 6))
        return DateRange(monday, sunday)
    }

    private fun monthRange(anchor: LocalDate): DateRange {
        val first = LocalDate(anchor.year, anchor.monthNumber, 1)
        val last = when (anchor.month) {
            Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY,
            Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> LocalDate(anchor.year, anchor.monthNumber, 31)
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> LocalDate(anchor.year, anchor.monthNumber, 30)
            Month.FEBRUARY -> {
                val isLeap = anchor.year % 4 == 0 && (anchor.year % 100 != 0 || anchor.year % 400 == 0)
                LocalDate(anchor.year, 2, if (isLeap) 29 else 28)
            }
        }
        return DateRange(first, last)
    }
}
