package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.MoveStreak
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.GoalsRepository
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

class ComputeStreakUseCase @Inject constructor(
    private val health: HealthRepository,
    private val goalsRepo: GoalsRepository,
) {
    operator fun invoke(anchor: LocalDate): Flow<MoveStreak> {
        val yearAgo = anchor.minus(DatePeriod(days = 365))
        val range = DateRange(yearAgo, anchor)
        val tz = TimeZone.currentSystemDefault()

        return combine(
            health.observeSeries(MetricType.Steps, range, Bucket.Day),
            health.observeSeries(MetricType.ActiveCalories, range, Bucket.Day),
            health.observeSeries(MetricType.Distance, range, Bucket.Day),
            goalsRepo.observeGoals(),
        ) { stepsSeries, calSeries, distSeries, goals ->
            val stepsGoal = goals[MetricType.Steps]?.target ?: DEFAULT_STEPS_GOAL
            val calGoal = goals[MetricType.ActiveCalories]?.target ?: DEFAULT_CALORIES_GOAL
            val distGoal = goals[MetricType.Distance]?.target ?: DEFAULT_DISTANCE_GOAL

            val stepsMap = stepsSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date
            }
            val calMap = calSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date
            }
            val distMap = distSeries.points.associateBy {
                it.bucketStart.toLocalDateTime(tz).date
            }

            val allDates = (stepsMap.keys + calMap.keys + distMap.keys)
                .filter { it in yearAgo..anchor }
                .sorted()

            val closedDates = allDates.filter { date ->
                val s = stepsMap[date]?.value ?: 0.0
                val c = calMap[date]?.value ?: 0.0
                val d = distMap[date]?.value ?: 0.0
                s >= stepsGoal && c >= calGoal && d >= distGoal
            }.toSet()

            // Current streak: walk backwards from anchor
            var currentStreak = 0
            var d = anchor
            while (d >= yearAgo && d in closedDates) {
                currentStreak++
                d = d.minus(DatePeriod(days = 1))
            }

            // Longest streak
            var longestStreak = 0
            var runLength = 0
            for (date in allDates) {
                if (date in closedDates) {
                    runLength++
                    if (runLength > longestStreak) longestStreak = runLength
                } else {
                    runLength = 0
                }
            }

            val lastClosed = closedDates.maxOrNull()

            MoveStreak(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastClosedDate = lastClosed,
            )
        }
    }

    companion object {
        private const val DEFAULT_STEPS_GOAL = 10_000.0
        private const val DEFAULT_CALORIES_GOAL = 500.0
        private const val DEFAULT_DISTANCE_GOAL = 5.0
    }
}
