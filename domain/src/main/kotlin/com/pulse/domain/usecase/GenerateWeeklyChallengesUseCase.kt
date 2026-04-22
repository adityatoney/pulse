package com.pulse.domain.usecase

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.WeeklyChallenge
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.GoalsRepository
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

class GenerateWeeklyChallengesUseCase @Inject constructor(
    private val health: HealthRepository,
    private val goalsRepo: GoalsRepository,
) {
    operator fun invoke(anchor: LocalDate): Flow<List<WeeklyChallenge>> {
        val twoWeeksAgo = anchor.minus(DatePeriod(days = 14))
        val range = DateRange(twoWeeksAgo, anchor)
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

            // Find Monday of the current week
            val startOfWeek = mondayOf(anchor)
            val startOfLastWeek = startOfWeek.minus(DatePeriod(days = 7))

            fun dateOf(point: com.pulse.domain.model.SeriesPoint) =
                point.bucketStart.toLocalDateTime(tz).date

            // Split into last week and current week
            val endOfLastWeek = startOfWeek.minus(DatePeriod(days = 1))
            fun inLastWeek(d: LocalDate) = d >= startOfLastWeek && d <= endOfLastWeek
            fun inThisWeek(d: LocalDate) = d >= startOfWeek && d <= anchor

            val stepsLastWeek = stepsSeries.points.filter { inLastWeek(dateOf(it)) }
            val stepsThisWeek = stepsSeries.points.filter { inThisWeek(dateOf(it)) }
            val calLastWeek = calSeries.points.filter { inLastWeek(dateOf(it)) }
            val calThisWeek = calSeries.points.filter { inThisWeek(dateOf(it)) }
            val distLastWeek = distSeries.points.filter { inLastWeek(dateOf(it)) }
            val distThisWeek = distSeries.points.filter { inThisWeek(dateOf(it)) }

            if (stepsLastWeek.isEmpty()) return@combine emptyList()

            val weekId = startOfWeek.toString()
            val challenges = mutableListOf<WeeklyChallenge>()

            // Challenge 1: Beat Your Steps Average
            val lastWeekStepsAvg = stepsLastWeek.map { it.value }.average()
            val stepsTarget = (lastWeekStepsAvg * 1.1).roundToNearest(500.0)
            val currentStepsAvg = if (stepsThisWeek.isNotEmpty()) stepsThisWeek.map { it.value }.average() else 0.0
            val stepsProgress = (currentStepsAvg / stepsTarget).toFloat().coerceIn(0f, 1f)
            challenges.add(
                WeeklyChallenge(
                    id = "$weekId-steps-avg",
                    title = "Average ${stepsTarget.toInt().formatted()} steps",
                    description = "You averaged ${lastWeekStepsAvg.toInt().formatted()} last week",
                    metric = MetricType.Steps,
                    targetValue = stepsTarget,
                    currentValue = currentStepsAvg,
                    progress = stepsProgress,
                    isComplete = stepsProgress >= 1f,
                )
            )

            // Challenge 2: Close All Rings X days
            val lastWeekAllClosed = (0 until 7).count { dayOffset ->
                val d = startOfLastWeek.plus(DatePeriod(days = dayOffset))
                val s = stepsLastWeek.find { dateOf(it) == d }?.value ?: 0.0
                val c = calLastWeek.find { dateOf(it) == d }?.value ?: 0.0
                val di = distLastWeek.find { dateOf(it) == d }?.value ?: 0.0
                s >= stepsGoal && c >= calGoal && di >= distGoal
            }
            val ringTarget = (lastWeekAllClosed + 1).coerceAtMost(7)
            val thisWeekAllClosed = (0..6).count { dayOffset ->
                val d = startOfWeek.plus(DatePeriod(days = dayOffset))
                if (d > anchor) return@count false
                val s = stepsThisWeek.find { dateOf(it) == d }?.value ?: 0.0
                val c = calThisWeek.find { dateOf(it) == d }?.value ?: 0.0
                val di = distThisWeek.find { dateOf(it) == d }?.value ?: 0.0
                s >= stepsGoal && c >= calGoal && di >= distGoal
            }
            val ringProgress = (thisWeekAllClosed.toFloat() / ringTarget).coerceIn(0f, 1f)
            challenges.add(
                WeeklyChallenge(
                    id = "$weekId-rings-closed",
                    title = "Close all rings $ringTarget days",
                    description = "You closed all rings $lastWeekAllClosed days last week",
                    metric = null,
                    targetValue = ringTarget.toDouble(),
                    currentValue = thisWeekAllClosed.toDouble(),
                    progress = ringProgress,
                    isComplete = ringProgress >= 1f,
                )
            )

            // Challenge 3: Weakest metric push
            val calAvgLast = calLastWeek.map { it.value }.average()
            val distAvgLast = distLastWeek.map { it.value }.average()
            val calRatio = calAvgLast / calGoal
            val distRatio = distAvgLast / distGoal
            val stepsRatio = lastWeekStepsAvg / stepsGoal

            val weakest = listOf(
                Triple(MetricType.Steps, stepsRatio, stepsGoal),
                Triple(MetricType.ActiveCalories, calRatio, calGoal),
                Triple(MetricType.Distance, distRatio, distGoal),
            ).minByOrNull { it.second }!!

            val (weakMetric, _, weakGoal) = weakest
            val daysGoalMet = when (weakMetric) {
                MetricType.Steps -> stepsLastWeek.count { it.value >= weakGoal }
                MetricType.ActiveCalories -> calLastWeek.count { it.value >= weakGoal }
                MetricType.Distance -> distLastWeek.count { it.value >= weakGoal }
                else -> 0
            }
            val pushTarget = (daysGoalMet + 1).coerceAtMost(7)
            val pushCurrent = when (weakMetric) {
                MetricType.Steps -> stepsThisWeek.count { it.value >= weakGoal }
                MetricType.ActiveCalories -> calThisWeek.count { it.value >= weakGoal }
                MetricType.Distance -> distThisWeek.count { it.value >= weakGoal }
                else -> 0
            }
            val pushProgress = (pushCurrent.toFloat() / pushTarget).coerceIn(0f, 1f)
            val metricLabel = when (weakMetric) {
                MetricType.Steps -> "step"
                MetricType.ActiveCalories -> "calorie"
                MetricType.Distance -> "distance"
                else -> weakMetric.name.lowercase()
            }
            challenges.add(
                WeeklyChallenge(
                    id = "$weekId-push-${weakMetric.name.lowercase()}",
                    title = "Hit your $metricLabel goal $pushTarget days",
                    description = "You hit it $daysGoalMet days last week",
                    metric = weakMetric,
                    targetValue = pushTarget.toDouble(),
                    currentValue = pushCurrent.toDouble(),
                    progress = pushProgress,
                    isComplete = pushProgress >= 1f,
                )
            )

            challenges
        }
    }

    companion object {
        private const val DEFAULT_STEPS_GOAL = 10_000.0
        private const val DEFAULT_CALORIES_GOAL = 500.0
        private const val DEFAULT_DISTANCE_GOAL = 5.0

        private fun mondayOf(date: LocalDate): LocalDate {
            val daysFromMonday = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> 0
                DayOfWeek.TUESDAY -> 1
                DayOfWeek.WEDNESDAY -> 2
                DayOfWeek.THURSDAY -> 3
                DayOfWeek.FRIDAY -> 4
                DayOfWeek.SATURDAY -> 5
                DayOfWeek.SUNDAY -> 6
                else -> 0
            }
            return date.minus(DatePeriod(days = daysFromMonday))
        }

        private fun Double.roundToNearest(step: Double): Double =
            (this / step).roundToInt() * step

        private fun Int.formatted(): String =
            "%,d".format(this)
    }
}
