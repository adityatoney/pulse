package com.pulse.data.compute

import android.util.Log
import com.pulse.data.local.dao.ComputeQueueDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.RawDailyMetricDao
import com.pulse.data.local.dao.RawSampleDao
import com.pulse.data.local.dao.SummaryDailyMetricDao
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.SummaryDailyMetricEntity
import com.pulse.domain.model.MetricType
import com.pulse.domain.util.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SummaryCompute"
private const val DEFAULT_STEP_GOAL = 10_000.0
private const val DEFAULT_DISTANCE_GOAL_MI = 5.0
private const val DEFAULT_CALORIE_GOAL = 2_500.0
private const val DEFAULT_ZONE_MIN_GOAL = 22.0
private const val METERS_PER_MILE = 1_609.34

/**
 * Source priority: higher index = higher priority (last match wins).
 * HealthConnect > Google Health API > Fitbit API.
 * No legacy tier — Fitbit API overwrites any legacy data.
 */
private val SOURCE_PRIORITY = listOf("Fitbit", "GoogleHealth", "HealthConnect")

@Singleton
class SummaryComputeEngine @Inject constructor(
    private val rawDailyDao: RawDailyMetricDao,
    private val rawSampleDao: RawSampleDao,
    private val summaryDao: SummaryDailyMetricDao,
    private val computeQueueDao: ComputeQueueDao,
    private val exerciseDao: ExerciseSessionDao,
    private val goalDao: GoalDao,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Launch a full queue drain on an application-scoped coroutine
     * so it survives ViewModel destruction.
     */
    fun launchProcessQueueAll() {
        scope.launch { processQueueAll() }
    }

    /**
     * Drain the entire compute queue, processing in batches until empty.
     */
    suspend fun processQueueAll(batchSize: Int = 500) {
        var totalProcessed = 0
        while (true) {
            val entries = computeQueueDao.dequeue(batchSize)
            if (entries.isEmpty()) break
            processEntries(entries)
            totalProcessed += entries.size
        }
        if (totalProcessed > 0) {
            Log.d(TAG, "processQueueAll: processed $totalProcessed entries total")
        }
    }

    /**
     * Drain the compute queue and recompute affected summaries (single batch).
     */
    suspend fun processQueue(batchSize: Int = 500) {
        val entries = computeQueueDao.dequeue(batchSize)
        if (entries.isEmpty()) return
        processEntries(entries)
    }

    private suspend fun processEntries(entries: List<ComputeQueueEntity>) {
        Log.d(TAG, "Processing ${entries.size} compute queue entries")

        val goals = buildGoalMap()
        val nowMs = clock.now().toEpochMilliseconds()

        val byDate = entries.groupBy { it.date }
        for ((date, dateEntries) in byDate) {
            val metrics = dateEntries.map { it.metric }.toSet()
            computeForDate(date, metrics, goals, nowMs)
        }

        for (entry in entries) {
            computeQueueDao.remove(entry.date, entry.metric)
        }
        Log.d(TAG, "Processed ${entries.size} entries across ${byDate.size} dates")
    }

    /**
     * Compute summaries for specific metrics on a given date.
     */
    private suspend fun computeForDate(
        date: String,
        metrics: Set<String>,
        goals: Map<MetricType, Double>,
        nowMs: Long,
    ) {
        val summaries = mutableListOf<SummaryDailyMetricEntity>()

        for (metricName in metrics) {
            val metric = runCatching { MetricType.valueOf(metricName) }.getOrNull() ?: continue

            when (metric) {
                MetricType.Distance -> {
                    val rawResolved = resolveRawMetric(date, MetricType.Distance.name)
                    val exerciseDist = computeExerciseDistanceForDate(date)
                    summaries += SummaryDailyMetricEntity(
                        date = date, metric = MetricType.Distance.name,
                        total = rawResolved?.first ?: 0.0,
                        activityTotal = exerciseDist,
                        goal = goals[MetricType.Distance],
                        sampleCount = 1, computedAtMs = nowMs,
                        sourceUsed = rawResolved?.second ?: "none",
                    )
                }
                MetricType.ActiveCalories -> {
                    val rawResolved = resolveRawMetric(date, MetricType.ActiveCalories.name)
                    val exerciseCals = computeExerciseCaloriesForDate(date)
                    val total = rawResolved?.first ?: 0.0
                    if (total > 0 || exerciseCals > 0) {
                        summaries += SummaryDailyMetricEntity(
                            date = date, metric = MetricType.ActiveCalories.name,
                            total = total,
                            activityTotal = if (exerciseCals > 0) exerciseCals else null,
                            goal = goals[MetricType.ActiveCalories],
                            sampleCount = 1, computedAtMs = nowMs,
                            sourceUsed = rawResolved?.second ?: "none",
                        )
                    }
                }
                MetricType.ZoneMinutes -> {
                    // Try raw daily metric first (from Fitbit or HC computed value)
                    val resolved = resolveRawMetric(date, MetricType.ZoneMinutes.name)
                    if (resolved != null) {
                        summaries += SummaryDailyMetricEntity(
                            date = date, metric = MetricType.ZoneMinutes.name,
                            total = resolved.first, goal = goals[MetricType.ZoneMinutes],
                            sampleCount = 1, computedAtMs = nowMs,
                            sourceUsed = resolved.second,
                        )
                    }
                }
                else -> {
                    // Standard metrics: resolve from raw with source priority
                    val resolved = resolveRawMetric(date, metricName)
                    if (resolved != null) {
                        summaries += SummaryDailyMetricEntity(
                            date = date, metric = metricName,
                            total = resolved.first, goal = goals[metric],
                            sampleCount = 1, computedAtMs = nowMs,
                            sourceUsed = resolved.second,
                        )
                    }
                }
            }
        }

        if (summaries.isNotEmpty()) {
            summaryDao.upsert(summaries)
        }
    }

    /**
     * Resolve the best value for a raw daily metric using source priority.
     * Returns (value, source) or null if no data.
     */
    private suspend fun resolveRawMetric(date: String, metric: String): Pair<Double, String>? {
        val rawRows = rawDailyDao.getForDateAndMetric(date, metric)
        if (rawRows.isEmpty()) return null

        // Take the highest-priority source that has a positive value
        val sorted = rawRows.sortedByDescending { SOURCE_PRIORITY.indexOf(it.source) }
        for (row in sorted) {
            if (row.value > 0) return row.value to row.source
        }
        // Fallback: take max value
        val best = rawRows.maxByOrNull { it.value }
        return best?.let { it.value to it.source }
    }

    /**
     * Sum exercise session distance for a date (in miles).
     */
    private suspend fun computeExerciseDistanceForDate(date: String): Double {
        val (startMs, endMs) = dayBoundsMs(date)
        val sessions = exerciseDao.getRange(startMs, endMs)
        return sessions.sumOf { (it.distanceMeters ?: 0.0) / METERS_PER_MILE }
    }

    /**
     * Sum exercise session calories for a date.
     */
    private suspend fun computeExerciseCaloriesForDate(date: String): Double {
        val (startMs, endMs) = dayBoundsMs(date)
        val sessions = exerciseDao.getRange(startMs, endMs)
        return sessions.sumOf { it.calories ?: 0.0 }
    }

    /**
     * Enqueue all metrics for the last [days] days for recomputation.
     */
    suspend fun recomputeAll(days: Int = 30) {
        val nowMs = clock.now().toEpochMilliseconds()
        val today = clock.today()
        val entries = mutableListOf<ComputeQueueEntity>()

        val allMetrics = listOf(
            MetricType.Steps, MetricType.Distance, MetricType.ActiveCalories,
            MetricType.ZoneMinutes, MetricType.Weight, MetricType.BodyFat,
            MetricType.SpO2, MetricType.HRV, MetricType.VO2Max,
            MetricType.SkinTemperature, MetricType.RestingHeartRate, MetricType.Floors,
        )

        var d = today
        repeat(days) {
            for (metric in allMetrics) {
                entries += ComputeQueueEntity(
                    date = d.toString(), metric = metric.name, enqueuedAtMs = nowMs,
                )
            }
            d = d.minus(DatePeriod(days = 1))
        }

        if (entries.isNotEmpty()) {
            computeQueueDao.enqueue(entries)
            Log.d(TAG, "Enqueued ${entries.size} entries for recomputation ($days days)")
            // Process on app-scoped coroutine so it survives ViewModel cancellation
            launchProcessQueueAll()
        }
    }

    /**
     * Invalidate a specific metric for recomputation (e.g., after profile change).
     */
    suspend fun invalidateMetric(metric: MetricType, days: Int = 365) {
        val nowMs = clock.now().toEpochMilliseconds()
        val today = clock.today()
        val entries = mutableListOf<ComputeQueueEntity>()

        var d = today
        repeat(days) {
            entries += ComputeQueueEntity(
                date = d.toString(), metric = metric.name, enqueuedAtMs = nowMs,
            )
            d = d.minus(DatePeriod(days = 1))
        }

        if (entries.isNotEmpty()) {
            computeQueueDao.enqueue(entries)
            launchProcessQueueAll()
        }
    }

    private suspend fun buildGoalMap(): Map<MetricType, Double> {
        val map = mutableMapOf(
            MetricType.Steps to DEFAULT_STEP_GOAL,
            MetricType.Distance to DEFAULT_DISTANCE_GOAL_MI,
            MetricType.ActiveCalories to DEFAULT_CALORIE_GOAL,
            MetricType.ZoneMinutes to DEFAULT_ZONE_MIN_GOAL,
        )
        for (metric in map.keys.toList()) {
            goalDao.get(metric.name)?.let { map[metric] = it.target }
        }
        return map
    }

    private fun dayBoundsMs(dateStr: String): Pair<Long, Long> {
        val date = kotlinx.datetime.LocalDate.parse(dateStr)
        val zone = TimeZone.currentSystemDefault()
        val startMs = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0)
            .toInstant(zone).toEpochMilliseconds()
        val endMs = startMs + 24 * 60 * 60 * 1000L
        return startMs to endMs
    }
}
