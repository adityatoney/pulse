package com.pulse.data.compute

import android.util.Log
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.InsightDao
import com.pulse.data.local.dao.RawHourlyMetricDao
import com.pulse.data.local.dao.SummaryDailyMetricDao
import com.pulse.data.local.entity.InsightEntity
import com.pulse.domain.util.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "HealthIntelligence"

private const val DEFAULT_STEP_GOAL = 10_000.0
private const val DEFAULT_DISTANCE_GOAL = 5.0
private const val DEFAULT_CALORIE_GOAL = 500.0
private const val DEFAULT_ZONE_MIN_GOAL = 30.0

@Singleton
class HealthIntelligenceService @Inject constructor(
    private val summaryDao: SummaryDailyMetricDao,
    private val rawHourlyDao: RawHourlyMetricDao,
    private val insightDao: InsightDao,
    private val goalDao: GoalDao,
    private val clock: Clock,
) {
    suspend fun computeAll(datesAffected: List<String>) {
        val today = datesAffected.maxOrNull() ?: return
        val nowMs = clock.now().toEpochMilliseconds()
        val insights = mutableListOf<InsightEntity>()

        // Signal-based (Insights tab)
        insights += computeCircadianDelta(today, nowMs)
        insights += computeSupportLevel(today, nowMs)
        insights += computeBasalTrends(today, nowMs)

        // Contextual (Dashboard + MetricDetail)
        insights += computeStreaks(today, nowMs)
        insights += computePersonalRecords(today, nowMs)
        insights += computeGoalConsistency(today, nowMs)
        insights += computeAnomalies(today, nowMs)
        insights += computePaceTrajectory(today, nowMs)
        insights += computeWoW(today, nowMs)
        insights += computeMoM(today, nowMs)

        if (insights.isNotEmpty()) {
            insightDao.upsert(insights)
            Log.d(TAG, "Computed ${insights.size} insights for $today")
        }

        // Prune old insights
        val cutoff = LocalDate.parse(today).minus(DatePeriod(days = 180)).toString()
        insightDao.pruneOlderThan(cutoff)
    }

    // ---- Signal-based insights (Insights tab) ----

    private suspend fun computeCircadianDelta(today: String, nowMs: Long): List<InsightEntity> {
        val tz = TimeZone.currentSystemDefault()
        val currentHour = clock.now().toLocalDateTime(tz).hour
        if (currentHour < 6) return emptyList() // Too early for meaningful signal

        val todayDate = LocalDate.parse(today)
        val dow = todayDate.dayOfWeek

        // Get same day-of-week for last 4 weeks
        val sameDowDates = (1..4).map { weeksBack ->
            todayDate.minus(DatePeriod(days = weeksBack * 7)).toString()
        }

        val todayCumulative = rawHourlyDao.cumulativeUpToHour(today, "Steps", currentHour) ?: return emptyList()
        if (todayCumulative <= 0) return emptyList()

        val historicalRows = rawHourlyDao.getForDatesUpToHour(sameDowDates, "Steps", currentHour)
        if (historicalRows.isEmpty()) return emptyList()

        val historicalCumulatives = historicalRows.groupBy { it.date }
            .mapValues { (_, rows) -> rows.sumOf { it.value } }
            .values.toList()

        if (historicalCumulatives.isEmpty()) return emptyList()
        val historicalAvg = historicalCumulatives.average()
        if (historicalAvg <= 0) return emptyList()

        val deltaPct = ((todayCumulative - historicalAvg) / historicalAvg * 100).toFloat()
        val dayName = dow.name.lowercase().replaceFirstChar { it.uppercase() }
        val timeLabel = if (currentHour < 12) "$currentHour AM" else if (currentHour == 12) "12 PM" else "${currentHour - 12} PM"

        val sentiment = when {
            deltaPct > 10 -> "Positive"
            deltaPct < -10 -> "Negative"
            else -> "Neutral"
        }
        val direction = if (deltaPct >= 0) "+" else ""
        val headline = "${direction}${"%.0f".format(deltaPct)}% vs your typical $dayName $timeLabel pace"
        val body = if (deltaPct >= 0) {
            "You're ahead of your usual rhythm. ${todayCumulative.toInt()} steps so far vs ${historicalAvg.toInt()} average."
        } else {
            "You're behind your usual rhythm. ${todayCumulative.toInt()} steps so far vs ${historicalAvg.toInt()} average."
        }

        return listOf(InsightEntity(
            id = "$today:CircadianDelta:Steps",
            date = today, type = "CircadianDelta", metric = "Steps",
            category = "Daily", context = "InsightsTab",
            headline = headline, body = body,
            sentiment = sentiment, score = 0.8f,
            signalValue = deltaPct.toDouble(), metadata = null,
            computedAtMs = nowMs,
        ))
    }

    private suspend fun computeSupportLevel(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance")) {
            val thisWeekStart = todayDate.startOfIsoWeek()
            val lastWeekStart = thisWeekStart.minus(DatePeriod(days = 7))
            val lastWeekEnd = thisWeekStart.minus(DatePeriod(days = 1))

            val thisWeekMin = summaryDao.minInRange(metric, thisWeekStart.toString(), today)
            val lastWeekMin = summaryDao.minInRange(metric, lastWeekStart.toString(), lastWeekEnd.toString())

            if (thisWeekMin == null || lastWeekMin == null || lastWeekMin <= 0) continue

            val deltaPct = ((thisWeekMin - lastWeekMin) / lastWeekMin * 100).toFloat()
            if (abs(deltaPct) < 5) continue // Not significant enough

            val sentiment = if (deltaPct > 0) "Positive" else "Negative"
            val direction = if (deltaPct >= 0) "risen" else "dropped"
            val metricLabel = metricDisplayName(metric).replaceFirstChar { it.uppercase() }
            val headline = "$metricLabel floor $direction ${"%.0f".format(abs(deltaPct))}% WoW"
            val body = if (deltaPct > 0) {
                "Your minimum daily ${metric.lowercase()} is higher than last week. You are raising your baseline support level."
            } else {
                "Your minimum daily ${metric.lowercase()} dipped below last week's floor."
            }

            insights += InsightEntity(
                id = "$today:SupportLevel:$metric",
                date = today, type = "SupportLevel", metric = metric,
                category = "Weekly", context = "InsightsTab",
                headline = headline, body = body,
                sentiment = sentiment, score = 0.7f,
                signalValue = deltaPct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computeBasalTrends(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val insights = mutableListOf<InsightEntity>()

        val metricsWithDirection = listOf(
            "Steps" to true,            // higher is better
            "ActiveCalories" to true,
            "Distance" to true,
            "RestingHeartRate" to false, // lower is better
            "HRV" to true,              // higher is better
        )

        for ((metric, higherIsBetter) in metricsWithDirection) {
            val thirtyDaysAgo = todayDate.minus(DatePeriod(days = 30)).toString()
            val ninetyDaysAgo = todayDate.minus(DatePeriod(days = 90)).toString()
            val thirtyOneDaysAgo = todayDate.minus(DatePeriod(days = 31)).toString()

            val recentAvg = summaryDao.avgInRange(metric, thirtyDaysAgo, today)
            val baselineAvg = summaryDao.avgInRange(metric, ninetyDaysAgo, thirtyOneDaysAgo)
            val recentCount = summaryDao.countInRange(metric, thirtyDaysAgo, today)

            if (recentAvg == null || baselineAvg == null || baselineAvg <= 0 || recentCount < 7) continue

            val deltaPct = ((recentAvg - baselineAvg) / baselineAvg * 100).toFloat()
            if (abs(deltaPct) < 3) continue // Not significant

            val improving = if (higherIsBetter) deltaPct > 0 else deltaPct < 0
            val sentiment = if (improving) "Positive" else "Negative"

            val metricLabel = when (metric) {
                "RestingHeartRate" -> "resting heart rate"
                "HRV" -> "heart rate variability"
                "ActiveCalories" -> "active calories"
                else -> metric.lowercase()
            }
            val valueLabel = when (metric) {
                "RestingHeartRate" -> "${recentAvg.toInt()}bpm"
                "HRV" -> "${"%.0f".format(recentAvg)}ms"
                "Steps" -> "${recentAvg.toInt()}/day"
                else -> "${"%.1f".format(recentAvg)}"
            }

            val headline = "Structural update: ${metricLabel.replaceFirstChar { it.uppercase() }} at $valueLabel"
            val changeDir = if (deltaPct > 0) "up" else "down"
            val body = "30-day average is $changeDir ${"%.0f".format(abs(deltaPct))}% from your 90-day baseline of ${"%.0f".format(baselineAvg)}."

            insights += InsightEntity(
                id = "$today:BasalTrend:$metric",
                date = today, type = "BasalTrend", metric = metric,
                category = "Longitudinal", context = "InsightsTab",
                headline = headline, body = body,
                sentiment = sentiment, score = 0.6f,
                signalValue = deltaPct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    // ---- Contextual insights (Dashboard + MetricDetail) ----

    private suspend fun computeStreaks(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val ninetyDaysAgo = todayDate.minus(DatePeriod(days = 90)).toString()
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
            val goal = goalDao.get(metric)?.target ?: defaultGoal(metric)
            val days = summaryDao.getRange(metric, ninetyDaysAgo, today)
                .sortedByDescending { it.date }

            var streak = 0
            for (day in days) {
                if (day.total >= goal) streak++ else break
            }
            if (streak < 3) continue

            val metricLabel = metricDisplayName(metric)
            val score = minOf(1.0f, streak / 14f)

            insights += InsightEntity(
                id = "$today:Streak:$metric",
                date = today, type = "Streak", metric = metric,
                category = "Daily", context = "Dashboard,DetailWeek",
                headline = "$streak-day streak!",
                body = "You've hit your $metricLabel goal every day for $streak days straight.",
                sentiment = "Celebratory", score = score,
                signalValue = streak.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computePersonalRecords(today: String, nowMs: Long): List<InsightEntity> {
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
            val todayValue = summaryDao.get(today, metric)?.total ?: continue
            if (todayValue <= 0) continue
            val allTimeBest = summaryDao.bestEver(metric)
            if (todayValue >= (allTimeBest?.total ?: 0.0)) {
                val metricLabel = metricDisplayName(metric)
                insights += InsightEntity(
                    id = "$today:PersonalRecord:$metric",
                    date = today, type = "PersonalRecord", metric = metric,
                    category = "Daily", context = "Dashboard,DetailDay,DetailWeek,DetailMonth,Detail3M6MY",
                    headline = "New best! ${formatValue(todayValue, metric)}",
                    body = "Your highest $metricLabel day ever.",
                    sentiment = "Celebratory", score = 1.0f,
                    signalValue = todayValue, metadata = null,
                    computedAtMs = nowMs,
                )
            }
        }
        return insights
    }

    private suspend fun computeGoalConsistency(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val weekStart = todayDate.startOfIsoWeek()
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
            val goal = goalDao.get(metric)?.target ?: defaultGoal(metric)
            val weekDays = summaryDao.getRange(metric, weekStart.toString(), today)
            if (weekDays.isEmpty()) continue

            val hits = weekDays.count { it.total >= goal }
            val total = weekDays.size
            val pct = hits.toFloat() / total

            val metricLabel = metricDisplayName(metric)
            val sentiment = when {
                pct >= 0.8 -> "Positive"
                pct >= 0.5 -> "Neutral"
                else -> "Negative"
            }

            insights += InsightEntity(
                id = "$today:GoalConsistency:$metric",
                date = today, type = "GoalConsistency", metric = metric,
                category = "Weekly", context = "DetailWeek,DetailMonth,Detail3M6MY",
                headline = "Goal hit $hits/$total days (${"%.0f".format(pct * 100)}%)",
                body = "$metricLabel goal of ${formatValue(goal, metric)} met $hits out of $total days this week.",
                sentiment = sentiment, score = pct * 0.7f,
                signalValue = pct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computeAnomalies(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val yesterday = todayDate.minus(DatePeriod(days = 1)).toString()
        val thirtyDaysAgo = todayDate.minus(DatePeriod(days = 30)).toString()
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance")) {
            val todayValue = summaryDao.get(today, metric)?.total ?: continue
            if (todayValue <= 0) continue

            val last30 = summaryDao.getRange(metric, thirtyDaysAgo, yesterday)
            if (last30.size < 7) continue

            val values = last30.map { it.total }
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            val stddev = sqrt(variance)
            if (stddev <= 0) continue

            val zScore = (todayValue - mean) / stddev
            if (abs(zScore) < 1.5) continue

            val metricLabel = metricDisplayName(metric)
            val sentiment = if (zScore > 0) "Positive" else "Negative"
            val headline = if (zScore > 0) "Standout day!" else "Unusually low"
            val body = if (zScore > 0) {
                "${formatValue(todayValue, metric)} $metricLabel — well above your 30-day average of ${formatValue(mean, metric)}."
            } else {
                "${formatValue(todayValue, metric)} $metricLabel — below your 30-day average of ${formatValue(mean, metric)}."
            }

            insights += InsightEntity(
                id = "$today:Anomaly:$metric",
                date = today, type = "Anomaly", metric = metric,
                category = "Daily", context = "Dashboard,DetailDay",
                headline = headline, body = body,
                sentiment = sentiment, score = minOf(1.0f, abs(zScore).toFloat() / 3f),
                signalValue = zScore, metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computePaceTrajectory(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val weekStart = todayDate.startOfIsoWeek()
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance")) {
            val goal = goalDao.get(metric)?.target ?: defaultGoal(metric)
            val weeklyGoal = goal * 7
            val weekDays = summaryDao.getRange(metric, weekStart.toString(), today)
            if (weekDays.isEmpty()) continue

            val totalSoFar = weekDays.sumOf { it.total }
            val daysElapsed = weekDays.size
            val daysRemaining = 7 - daysElapsed
            if (daysRemaining <= 0) continue

            val projectedTotal = totalSoFar + (totalSoFar / daysElapsed * daysRemaining)
            val onTrackPct = (projectedTotal / weeklyGoal * 100).toFloat()

            val metricLabel = metricDisplayName(metric)
            val sentiment = when {
                onTrackPct >= 100 -> "Positive"
                onTrackPct >= 80 -> "Neutral"
                else -> "Negative"
            }
            val headline = when {
                onTrackPct >= 100 -> "On pace for weekly $metricLabel goal"
                onTrackPct >= 80 -> "Close to weekly $metricLabel target"
                else -> "Behind on weekly $metricLabel goal"
            }
            val body = "Projected ${formatValue(projectedTotal, metric)} of ${formatValue(weeklyGoal, metric)} weekly target (${"%.0f".format(onTrackPct)}%)."

            insights += InsightEntity(
                id = "$today:PaceTrajectory:$metric",
                date = today, type = "PaceTrajectory", metric = metric,
                category = "Weekly", context = "DetailWeek,DetailMonth",
                headline = headline, body = body,
                sentiment = sentiment, score = 0.5f,
                signalValue = onTrackPct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computeWoW(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
            val thisWeekStart = todayDate.startOfIsoWeek()
            val daysIntoWeek = (todayDate.toEpochDays() - thisWeekStart.toEpochDays()).toInt()
            val lastWeekStart = thisWeekStart.minus(DatePeriod(days = 7))
            val lastWeekEnd = lastWeekStart.plus(DatePeriod(days = daysIntoWeek))

            val thisWeek = summaryDao.getRange(metric, thisWeekStart.toString(), today)
            val lastWeek = summaryDao.getRange(metric, lastWeekStart.toString(), lastWeekEnd.toString())

            val currentTotal = thisWeek.sumOf { it.total }
            val previousTotal = lastWeek.sumOf { it.total }
            if (previousTotal <= 0) continue

            val deltaPct = ((currentTotal - previousTotal) / previousTotal * 100).toFloat()
            val metricLabel = metricDisplayName(metric)
            val direction = if (deltaPct >= 0) "+" else ""

            insights += InsightEntity(
                id = "$today:WoW:$metric",
                date = today, type = "WoW", metric = metric,
                category = "Weekly", context = "DetailWeek",
                headline = "${direction}${"%.0f".format(deltaPct)}% $metricLabel vs last week",
                body = "${formatValue(currentTotal, metric)} this week vs ${formatValue(previousTotal, metric)} last week (same days).",
                sentiment = if (deltaPct > 1) "Positive" else if (deltaPct < -1) "Negative" else "Neutral",
                score = 0.4f,
                signalValue = deltaPct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    private suspend fun computeMoM(today: String, nowMs: Long): List<InsightEntity> {
        val todayDate = LocalDate.parse(today)
        val insights = mutableListOf<InsightEntity>()

        for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
            val thisMonthStart = LocalDate(todayDate.year, todayDate.monthNumber, 1)
            val dayOfMonth = todayDate.dayOfMonth
            val lastMonthStart = thisMonthStart.minus(DatePeriod(months = 1))
            val lastMonthEnd = lastMonthStart.plus(DatePeriod(days = dayOfMonth - 1))

            val thisMonth = summaryDao.getRange(metric, thisMonthStart.toString(), today)
            val lastMonth = summaryDao.getRange(metric, lastMonthStart.toString(), lastMonthEnd.toString())

            val currentTotal = thisMonth.sumOf { it.total }
            val previousTotal = lastMonth.sumOf { it.total }
            if (previousTotal <= 0) continue

            val deltaPct = ((currentTotal - previousTotal) / previousTotal * 100).toFloat()
            val metricLabel = metricDisplayName(metric)
            val direction = if (deltaPct >= 0) "+" else ""

            insights += InsightEntity(
                id = "$today:MoM:$metric",
                date = today, type = "MoM", metric = metric,
                category = "Monthly", context = "DetailMonth",
                headline = "${direction}${"%.0f".format(deltaPct)}% $metricLabel vs last month",
                body = "${formatValue(currentTotal, metric)} this month vs ${formatValue(previousTotal, metric)} last month (same days).",
                sentiment = if (deltaPct > 1) "Positive" else if (deltaPct < -1) "Negative" else "Neutral",
                score = 0.4f,
                signalValue = deltaPct.toDouble(), metadata = null,
                computedAtMs = nowMs,
            )
        }
        return insights
    }

    // ---- Helpers ----

    private fun defaultGoal(metric: String): Double = when (metric) {
        "Steps" -> DEFAULT_STEP_GOAL
        "Distance" -> DEFAULT_DISTANCE_GOAL
        "ActiveCalories" -> DEFAULT_CALORIE_GOAL
        "ZoneMinutes" -> DEFAULT_ZONE_MIN_GOAL
        else -> DEFAULT_STEP_GOAL
    }

    private fun metricDisplayName(metric: String): String = when (metric) {
        "Steps" -> "steps"
        "Distance" -> "distance"
        "ActiveCalories" -> "active calories"
        "ZoneMinutes" -> "zone minutes"
        "RestingHeartRate" -> "resting heart rate"
        "HRV" -> "HRV"
        else -> metric.lowercase()
    }

    private fun formatValue(value: Double, metric: String): String = when (metric) {
        "Steps" -> "%,.0f".format(value)
        "Distance" -> "%.1f mi".format(value)
        "ActiveCalories" -> "%,.0f kcal".format(value)
        "ZoneMinutes" -> "%.0f min".format(value)
        "RestingHeartRate" -> "${value.toInt()} bpm"
        "HRV" -> "%.0f ms".format(value)
        else -> "%.0f".format(value)
    }

    private fun LocalDate.startOfIsoWeek(): LocalDate {
        val daysFromMonday = when (dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        return minus(DatePeriod(days = daysFromMonday))
    }
}
