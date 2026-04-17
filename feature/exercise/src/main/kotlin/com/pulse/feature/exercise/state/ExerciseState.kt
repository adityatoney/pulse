package com.pulse.feature.exercise.state

import com.pulse.domain.model.ExerciseSession
import kotlinx.datetime.LocalDate

enum class ExerciseTimeframe { Week, Month }

enum class ExerciseFilter { ExerciseDays, Duration, Distance, Time, ZoneMin }

data class DayMarker(val label: String, val hasExercise: Boolean)

data class ExerciseState(
    val timeframe: ExerciseTimeframe = ExerciseTimeframe.Week,
    val periodAnchor: LocalDate,
    val today: LocalDate,
    val exerciseDaysHit: Int = 0,
    val exerciseDayGoal: Int = 5,
    val dayMarkers: List<DayMarker> = emptyList(),
    val activeFilter: ExerciseFilter = ExerciseFilter.ExerciseDays,
    val sessionsByDay: Map<LocalDate, List<ExerciseSession>> = emptyMap(),
    val totalSessions: Int = 0,
    val totalDurationMin: Int = 0,
    val totalDistanceMiles: Double = 0.0,
    val totalCalories: Int = 0,
    val totalZoneMin: Int = 0,
    val isLoading: Boolean = true,
)

sealed interface ExerciseIntent {
    data object Load : ExerciseIntent
    data class ChangeTimeframe(val tf: ExerciseTimeframe) : ExerciseIntent
    data class MovePeriod(val forward: Boolean) : ExerciseIntent
    data class ChangeFilter(val filter: ExerciseFilter) : ExerciseIntent
    data object Back : ExerciseIntent
}

sealed interface ExerciseEffect {
    data object NavigateBack : ExerciseEffect
}
