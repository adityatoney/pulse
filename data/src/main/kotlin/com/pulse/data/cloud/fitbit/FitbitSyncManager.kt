package com.pulse.data.cloud.fitbit

import android.util.Log
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity
import com.pulse.domain.model.MetricType
import com.pulse.domain.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FitbitSync"
private const val METERS_PER_MILE = 1_609.34
private const val FITBIT_CURSOR_KEY = "fitbit_sync_cursor"
private const val MIN_RATE_LIMIT = 10

/**
 * Orchestrates syncing data from the Fitbit Web API into the local Room database.
 *
 * Uses Fitbit's efficient time-series endpoints for bulk historical data:
 * - Steps, distance, calories, active minutes: up to 1095 days per request
 * - Activity logs (exercise sessions): paginated, unlimited history
 * - Sleep: up to 100 days per request
 * - Heart rate, weight: up to 30 days per request
 *
 * Typical API call count for a full year backfill: ~35-40 calls (vs 150/hour limit).
 */
@Singleton
class FitbitSyncManager @Inject constructor(
    private val fitbitClient: FitbitRestClient,
    private val fitbitAuth: FitbitAuthManager,
    private val aggregateDao: DailyAggregateDao,
    private val exerciseDao: ExerciseSessionDao,
    private val sleepDao: SleepSessionDao,
    private val syncStateDao: SyncStateDao,
    private val goalDao: GoalDao,
    private val clock: Clock,
) {
    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private fun emitProgress(msg: String) {
        _progress.value = msg
        Log.d(TAG, msg)
    }

    /**
     * Full sync from Fitbit. Syncs from the last cursor date to today.
     * On first run, fetches up to [maxHistoryYears] years of data.
     */
    suspend fun sync(maxHistoryYears: Int = 5): Result<Unit> = runCatching {
        if (!fitbitAuth.tryRestoreTokens()) {
            Log.d(TAG, "Not authenticated with Fitbit, skipping sync")
            return@runCatching
        }

        val today = LocalDate.now()
        val cursor = loadCursor()
        val startDate = cursor ?: today.minusYears(maxHistoryYears.toLong())

        emitProgress("Starting sync: $startDate → $today")

        // Phase 1: Daily aggregates (very efficient — 5 API calls for up to 3 years)
        emitProgress("Phase 1/4: Daily aggregates...")
        syncDailyAggregates(startDate, today)

        // Phase 2: Exercise logs (paginated, unlimited history)
        emitProgress("Phase 2/4: Exercise logs...")
        syncExerciseLogs(startDate)

        // Phase 3: Sleep logs (100-day chunks)
        emitProgress("Phase 3/4: Sleep logs...")
        syncSleepLogs(startDate, today)

        // Phase 4: Vitals — resting HR + weight (30-day chunks)
        emitProgress("Phase 4/4: Vitals (HR, weight)...")
        syncVitals(startDate, today)

        // Update cursor to today
        saveCursor(today)
        emitProgress("Sync complete: $startDate → $today")
    }

    /**
     * Quick sync: only syncs recent data (last 7 days).
     * Used for periodic background sync after initial backfill.
     */
    suspend fun syncRecent(days: Int = 7): Result<Unit> = runCatching {
        if (!fitbitAuth.tryRestoreTokens()) return@runCatching

        val today = LocalDate.now()
        val startDate = today.minusDays(days.toLong())

        Log.d(TAG, "Quick sync from $startDate to $today")
        syncDailyAggregates(startDate, today)
        syncExerciseLogs(startDate)
        syncSleepLogs(startDate, today)
        syncVitals(startDate, today)
        saveCursor(today)
    }

    // ---- Daily aggregates (steps, distance, calories, zone minutes) ----

    private suspend fun syncDailyAggregates(startDate: LocalDate, endDate: LocalDate) {
        if (!checkRateLimit()) return
        val nowMs = clock.now().toEpochMilliseconds()
        val goals = buildGoalMap()
        val start = startDate.toString()
        val end = endDate.toString()

        // Fetch in 180-day chunks (Fitbit API times out on large ranges)
        val chunks = dateChunks(startDate, endDate, 180)
        for ((idx, chunk) in chunks.withIndex()) {
            val (chunkStart, chunkEnd) = chunk
            val s = chunkStart.toString()
            val e = chunkEnd.toString()

            emitProgress("Phase 1/4: Aggregates chunk ${idx + 1}/${chunks.size} ($s → $e)")
            val rows = mutableListOf<DailyAggregateEntity>()

            // Steps
            val steps = fitbitClient.fetchStepsSeries(s, e)
            for (entry in steps) {
                val value = entry.value.toDoubleOrNull() ?: continue
                if (value > 0) {
                    rows += DailyAggregateEntity(
                        date = entry.dateTime, metric = MetricType.Steps.name,
                        total = value, goal = goals[MetricType.Steps],
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }

            // Distance (Fitbit returns miles by default for US accounts)
            val distance = fitbitClient.fetchDistanceSeries(s, e)
            for (entry in distance) {
                val miles = entry.value.toDoubleOrNull() ?: continue
                if (miles > 0) {
                    rows += DailyAggregateEntity(
                        date = entry.dateTime, metric = MetricType.Distance.name,
                        total = miles, goal = goals[MetricType.Distance],
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }

            // Active calories (excludes BMR)
            val calories = fitbitClient.fetchActiveCaloriesSeries(s, e)
            for (entry in calories) {
                val value = entry.value.toDoubleOrNull() ?: continue
                if (value > 0) {
                    rows += DailyAggregateEntity(
                        date = entry.dateTime, metric = MetricType.ActiveCalories.name,
                        total = value, goal = goals[MetricType.ActiveCalories],
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }

            // Zone minutes = fairly active + very active
            val fairly = fitbitClient.fetchFairlyActiveMinutes(s, e)
            val very = fitbitClient.fetchVeryActiveMinutes(s, e)
            val fairlyMap = fairly.associate { it.dateTime to (it.value.toIntOrNull() ?: 0) }
            val veryMap = very.associate { it.dateTime to (it.value.toIntOrNull() ?: 0) }
            val allDates = (fairlyMap.keys + veryMap.keys).toSet()
            for (date in allDates) {
                val totalMin = (fairlyMap[date] ?: 0) + (veryMap[date] ?: 0)
                if (totalMin > 0) {
                    rows += DailyAggregateEntity(
                        date = date, metric = MetricType.ZoneMinutes.name,
                        total = totalMin.toDouble(), goal = goals[MetricType.ZoneMinutes],
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }

            Log.d(TAG, "Upserting ${rows.size} daily aggregate rows for $s → $e")
            if (rows.isNotEmpty()) aggregateDao.upsert(rows)

            if (!checkRateLimit()) return
        }
    }

    // ---- Exercise sessions (activity logs) ----

    private suspend fun syncExerciseLogs(afterDate: LocalDate) {
        if (!checkRateLimit()) return

        val activities = fitbitClient.fetchActivityLogs(
            afterDate = afterDate.toString(),
            limit = 100,
        )

        if (activities.isEmpty()) {
            emitProgress("Phase 2/4: No exercise logs after $afterDate")
            return
        }

        val zone = ZoneId.systemDefault()
        val entities = activities.mapNotNull { activity ->
            activityToEntity(activity, zone)
        }

        emitProgress("Phase 2/4: ${entities.size} exercise sessions")
        if (entities.isNotEmpty()) exerciseDao.upsert(entities)
    }

    private fun activityToEntity(
        activity: FitbitActivity,
        zone: ZoneId,
    ): ExerciseSessionEntity? {
        // Parse start time
        val startDateTime = try {
            LocalDateTime.parse(
                "${activity.startDate}T${activity.startTime}",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            )
        } catch (e: Exception) {
            // Try with simpler format (HH:mm)
            try {
                val parts = activity.startTime.split(":")
                val hour = parts[0].toInt()
                val min = parts.getOrNull(1)?.toInt() ?: 0
                val sec = parts.getOrNull(2)?.toInt() ?: 0
                LocalDate.parse(activity.startDate).atTime(hour, min, sec)
            } catch (e2: Exception) {
                Log.w(TAG, "Cannot parse activity time: ${activity.startDate}T${activity.startTime}")
                return null
            }
        }

        val startMs = startDateTime.atZone(zone).toInstant().toEpochMilli()
        val endMs = startMs + activity.duration

        // Convert distance to meters
        val distanceMeters = when {
            activity.distance == null -> null
            activity.distanceUnit?.equals("Kilometer", ignoreCase = true) == true ->
                activity.distance * 1000.0
            else -> activity.distance * METERS_PER_MILE // Default: miles
        }

        // Calculate max HR from heart rate zones
        val maxHr = activity.heartRateZones
            ?.filter { it.minutes > 0 }
            ?.maxOfOrNull { it.max }

        // Calculate zone minutes from HR zones (Cardio + Peak zones)
        val zoneMinutes = activity.heartRateZones
            ?.filter { it.name == "Fat Burn" || it.name == "Cardio" || it.name == "Peak" }
            ?.sumOf { it.minutes }

        // Calculate pace (seconds per mile)
        val avgPace = if (distanceMeters != null && distanceMeters > 0 && activity.duration > 0) {
            val miles = distanceMeters / METERS_PER_MILE
            ((activity.duration / 1000.0) / miles).toInt()
        } else null

        return ExerciseSessionEntity(
            id = "fitbit-${activity.logId}",
            type = activity.activityName,
            startUtcMs = startMs,
            endUtcMs = endMs,
            distanceMeters = distanceMeters,
            calories = activity.calories.toDouble(),
            steps = activity.steps,
            avgHr = activity.averageHeartRate,
            maxHr = maxHr,
            avgPaceSecondsPerMile = avgPace,
            elevationGainMeters = activity.elevationGain,
            zoneMinutes = zoneMinutes,
            sourceJson = "fitbit",
            dirty = true,
        )
    }

    // ---- Sleep logs ----

    private suspend fun syncSleepLogs(startDate: LocalDate, endDate: LocalDate) {
        if (!checkRateLimit()) return

        // Sleep API supports max 100 days per request
        val chunks = dateChunks(startDate, endDate, 100)
        for ((idx, chunk) in chunks.withIndex()) {
            val (chunkStart, chunkEnd) = chunk
            emitProgress("Phase 3/4: Sleep chunk ${idx + 1}/${chunks.size}")
            val logs = fitbitClient.fetchSleep(chunkStart.toString(), chunkEnd.toString())
            if (logs.isEmpty()) continue

            val entities = logs.filter { it.isMainSleep }.map { log ->
                sleepLogToEntity(log)
            }

            Log.d(TAG, "Upserting ${entities.size} sleep sessions for ${chunkStart} → ${chunkEnd}")
            if (entities.isNotEmpty()) sleepDao.upsert(entities)

            if (!checkRateLimit()) return
        }
    }

    private fun sleepLogToEntity(log: FitbitSleepLog): SleepSessionEntity {
        val startMs = parseIsoDateTime(log.startTime)
        val endMs = parseIsoDateTime(log.endTime)
        val totalMin = log.duration / 60_000L

        val stages = log.levels?.summary
        val deepMin = stages?.deep?.minutes?.toLong()
        val remMin = stages?.rem?.minutes?.toLong()
        val lightMin = stages?.light?.minutes?.toLong()
        val awakeMin = stages?.wake?.minutes?.toLong()
            ?: stages?.awake?.minutes?.toLong()
            ?: log.minutesAwake.toLong()

        return SleepSessionEntity(
            id = "fitbit-sleep-${log.logId}",
            startUtcMs = startMs,
            endUtcMs = endMs,
            totalMinutes = totalMin,
            deepMinutes = deepMin,
            remMinutes = remMin,
            lightMinutes = lightMin,
            awakeMinutes = awakeMin,
            sourceJson = "fitbit",
            dirty = true,
        )
    }

    // ---- Vitals (resting HR, weight, body fat) ----

    private suspend fun syncVitals(startDate: LocalDate, endDate: LocalDate) {
        if (!checkRateLimit()) return
        val nowMs = clock.now().toEpochMilliseconds()

        // Resting heart rate — max 30 days per request
        emitProgress("Phase 4/4: Resting HR...")
        val hrChunks = dateChunks(startDate, endDate, 30)
        val hrRows = mutableListOf<DailyAggregateEntity>()
        for ((idx, chunk) in hrChunks.withIndex()) {
            val (cs, ce) = chunk
            emitProgress("Phase 4/4: HR chunk ${idx + 1}/${hrChunks.size}")
            val hrData = fitbitClient.fetchHeartRate(cs.toString(), ce.toString())
            for (entry in hrData) {
                val rhr = entry.value.restingHeartRate ?: continue
                hrRows += DailyAggregateEntity(
                    date = entry.dateTime, metric = MetricType.RestingHeartRate.name,
                    total = rhr.toDouble(), goal = null,
                    sampleCount = 1, computedAtMs = nowMs,
                )
            }
            if (!checkRateLimit()) break
        }
        if (hrRows.isNotEmpty()) {
            Log.d(TAG, "Upserting ${hrRows.size} resting HR entries")
            aggregateDao.upsert(hrRows)
        }

        if (!checkRateLimit()) return

        // Weight & body fat — max 31 days per request
        emitProgress("Phase 4/4: Weight & body fat...")
        val weightChunks = dateChunks(startDate, endDate, 31)
        val bodyRows = mutableListOf<DailyAggregateEntity>()
        for ((cs, ce) in weightChunks) {
            val weightData = fitbitClient.fetchWeight(cs.toString(), ce.toString())
            for (log in weightData) {
                bodyRows += DailyAggregateEntity(
                    date = log.date, metric = MetricType.Weight.name,
                    total = log.weight, goal = null,
                    sampleCount = 1, computedAtMs = nowMs,
                )
                log.fat?.let { fatPct ->
                    bodyRows += DailyAggregateEntity(
                        date = log.date, metric = MetricType.BodyFat.name,
                        total = fatPct, goal = null,
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }
            if (!checkRateLimit()) break
        }
        if (bodyRows.isNotEmpty()) {
            Log.d(TAG, "Upserting ${bodyRows.size} weight/body fat entries")
            aggregateDao.upsert(bodyRows)
        }
    }

    // ---- Helpers ----

    private fun checkRateLimit(): Boolean {
        val remaining = fitbitClient.getRateLimitRemaining()
        if (remaining < MIN_RATE_LIMIT) {
            Log.w(TAG, "Rate limit low ($remaining remaining), pausing sync")
            return false
        }
        return true
    }

    private suspend fun buildGoalMap(): Map<MetricType, Double?> {
        val goals = goalDao.getAll()
        val goalMap = goals.associate {
            runCatching { MetricType.valueOf(it.metric) }.getOrNull() to it.target
        }.filterKeys { it != null }.mapKeys { it.key!! }
        return mapOf(
            MetricType.Steps to (goalMap[MetricType.Steps] ?: 10_000.0),
            MetricType.Distance to (goalMap[MetricType.Distance] ?: 5.0),
            MetricType.ActiveCalories to (goalMap[MetricType.ActiveCalories] ?: 2_500.0),
            MetricType.ZoneMinutes to (goalMap[MetricType.ZoneMinutes] ?: 22.0),
        )
    }

    private suspend fun loadCursor(): LocalDate? {
        val entity = syncStateDao.get(FITBIT_CURSOR_KEY) ?: return null
        return runCatching { LocalDate.parse(entity.value) }.getOrNull()
    }

    private suspend fun saveCursor(date: LocalDate) {
        syncStateDao.upsert(
            SyncStateEntity(
                key = FITBIT_CURSOR_KEY,
                value = date.toString(),
                updatedAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun parseIsoDateTime(dateTimeStr: String): Long {
        return try {
            val dt = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                // Try with offset
                java.time.OffsetDateTime.parse(dateTimeStr).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                Log.w(TAG, "Cannot parse datetime: $dateTimeStr")
                0L
            }
        }
    }

    /** Split a date range into chunks of at most [maxDays] days. */
    private fun dateChunks(
        start: LocalDate,
        end: LocalDate,
        maxDays: Int,
    ): List<Pair<LocalDate, LocalDate>> {
        val chunks = mutableListOf<Pair<LocalDate, LocalDate>>()
        var chunkStart = start
        while (chunkStart.isBefore(end) || chunkStart.isEqual(end)) {
            val chunkEnd = minOf(chunkStart.plusDays(maxDays.toLong() - 1), end)
            chunks.add(chunkStart to chunkEnd)
            chunkStart = chunkEnd.plusDays(1)
        }
        return chunks
    }
}
