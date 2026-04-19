package com.pulse.data.cloud.fitbit

import android.util.Log
import com.pulse.data.compute.SummaryComputeEngine
import com.pulse.data.local.dao.ComputeQueueDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.RawDailyMetricDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.RawDailyMetricEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity
import com.pulse.domain.model.MetricType
import com.pulse.domain.util.Clock
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
private const val KG_TO_LBS = 2.20462
private const val FITBIT_CURSOR_KEY = "fitbit_sync_cursor"
private const val MIN_RATE_LIMIT = 10

@Singleton
class FitbitSyncManager @Inject constructor(
    private val fitbitClient: FitbitRestClient,
    private val fitbitAuth: FitbitAuthManager,
    private val rawDailyDao: RawDailyMetricDao,
    private val computeQueueDao: ComputeQueueDao,
    private val computeEngine: SummaryComputeEngine,
    private val exerciseDao: ExerciseSessionDao,
    private val sleepDao: SleepSessionDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock,
) {
    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private fun emitProgress(msg: String) {
        _progress.value = msg
        Log.d(TAG, msg)
    }

    suspend fun sync(maxHistoryYears: Int = 5): Result<Unit> = runCatching {
        if (!fitbitAuth.tryRestoreTokens()) {
            Log.d(TAG, "Not authenticated with Fitbit, skipping sync")
            return@runCatching
        }

        val today = LocalDate.now()
        val cursor = loadCursor()
        val startDate = cursor ?: today.minusYears(maxHistoryYears.toLong())

        emitProgress("Starting sync: $startDate → $today")

        emitProgress("Phase 1/4: Daily aggregates...")
        syncDailyAggregates(startDate, today)

        emitProgress("Phase 2/4: Exercise logs...")
        syncExerciseLogs(startDate)

        emitProgress("Phase 3/4: Sleep logs...")
        syncSleepLogs(startDate, today)

        emitProgress("Phase 4/4: Vitals (HR, weight)...")
        syncVitals(startDate, today)

        // Trigger summary computation from raw data — drain fully
        computeEngine.processQueueAll()

        saveCursor(today)
        emitProgress("Sync complete: $startDate → $today")
    }

    suspend fun syncRecent(days: Int = 7): Result<Unit> = runCatching {
        if (!fitbitAuth.tryRestoreTokens()) return@runCatching

        val today = LocalDate.now()
        val startDate = today.minusDays(days.toLong())

        Log.d(TAG, "Quick sync from $startDate to $today")
        syncDailyAggregates(startDate, today)
        syncExerciseLogs(startDate)
        syncSleepLogs(startDate, today)
        syncVitals(startDate, today)
        computeEngine.processQueue()
        saveCursor(today)
    }

    // ---- Daily aggregates (steps, distance, calories, zone minutes) ----

    private suspend fun syncDailyAggregates(startDate: LocalDate, endDate: LocalDate) {
        if (!checkRateLimit()) return
        val nowMs = clock.now().toEpochMilliseconds()

        val chunks = dateChunks(startDate, endDate, 180)
        for ((idx, chunk) in chunks.withIndex()) {
            val (chunkStart, chunkEnd) = chunk
            val s = chunkStart.toString()
            val e = chunkEnd.toString()

            emitProgress("Phase 1/4: Aggregates chunk ${idx + 1}/${chunks.size} ($s → $e)")
            val rawRows = mutableListOf<RawDailyMetricEntity>()
            val dirtyEntries = mutableListOf<ComputeQueueEntity>()

            // Steps
            val steps = fitbitClient.fetchStepsSeries(s, e)
            for (entry in steps) {
                val value = entry.value.toDoubleOrNull() ?: continue
                if (value > 0) {
                    rawRows += RawDailyMetricEntity(
                        date = entry.dateTime, metric = MetricType.Steps.name, source = "Fitbit",
                        value = value, unit = "count",
                        externalId = "fitbit-steps-${entry.dateTime}", ingestedAtMs = nowMs,
                    )
                    dirtyEntries += ComputeQueueEntity(date = entry.dateTime, metric = MetricType.Steps.name, enqueuedAtMs = nowMs)
                }
            }

            // Distance
            val distance = fitbitClient.fetchDistanceSeries(s, e)
            for (entry in distance) {
                val miles = entry.value.toDoubleOrNull() ?: continue
                if (miles > 0) {
                    rawRows += RawDailyMetricEntity(
                        date = entry.dateTime, metric = MetricType.Distance.name, source = "Fitbit",
                        value = miles, unit = "miles",
                        externalId = "fitbit-distance-${entry.dateTime}", ingestedAtMs = nowMs,
                    )
                    dirtyEntries += ComputeQueueEntity(date = entry.dateTime, metric = MetricType.Distance.name, enqueuedAtMs = nowMs)
                }
            }

            // Active calories
            val calories = fitbitClient.fetchActiveCaloriesSeries(s, e)
            for (entry in calories) {
                val value = entry.value.toDoubleOrNull() ?: continue
                if (value > 0) {
                    rawRows += RawDailyMetricEntity(
                        date = entry.dateTime, metric = MetricType.ActiveCalories.name, source = "Fitbit",
                        value = value, unit = "kcal",
                        externalId = "fitbit-activecal-${entry.dateTime}", ingestedAtMs = nowMs,
                    )
                    dirtyEntries += ComputeQueueEntity(date = entry.dateTime, metric = MetricType.ActiveCalories.name, enqueuedAtMs = nowMs)
                }
            }

            // Zone minutes
            val fairly = fitbitClient.fetchFairlyActiveMinutes(s, e)
            val very = fitbitClient.fetchVeryActiveMinutes(s, e)
            val fairlyMap = fairly.associate { it.dateTime to (it.value.toIntOrNull() ?: 0) }
            val veryMap = very.associate { it.dateTime to (it.value.toIntOrNull() ?: 0) }
            val allDates = (fairlyMap.keys + veryMap.keys).toSet()
            for (date in allDates) {
                val totalMin = (fairlyMap[date] ?: 0) + (veryMap[date] ?: 0)
                if (totalMin > 0) {
                    rawRows += RawDailyMetricEntity(
                        date = date, metric = MetricType.ZoneMinutes.name, source = "Fitbit",
                        value = totalMin.toDouble(), unit = "minutes",
                        externalId = "fitbit-zoneminutes-$date", ingestedAtMs = nowMs,
                    )
                    dirtyEntries += ComputeQueueEntity(date = date, metric = MetricType.ZoneMinutes.name, enqueuedAtMs = nowMs)
                }
            }

            Log.d(TAG, "Upserting ${rawRows.size} raw metric rows for $s → $e")
            if (rawRows.isNotEmpty()) rawDailyDao.insertAll(rawRows)
            if (dirtyEntries.isNotEmpty()) computeQueueDao.enqueue(dirtyEntries)

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
        val startDateTime = try {
            LocalDateTime.parse(
                "${activity.startDate}T${activity.startTime}",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            )
        } catch (e: Exception) {
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

        val distanceMeters = when {
            activity.distance == null -> null
            activity.distanceUnit?.equals("Kilometer", ignoreCase = true) == true ->
                activity.distance * 1000.0
            else -> activity.distance * METERS_PER_MILE
        }

        val maxHr = activity.heartRateZones
            ?.filter { it.minutes > 0 }
            ?.maxOfOrNull { it.max }

        val zoneMinutes = activity.heartRateZones
            ?.filter { it.name == "Fat Burn" || it.name == "Cardio" || it.name == "Peak" }
            ?.sumOf { it.minutes }

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

        // Resting heart rate
        emitProgress("Phase 4/4: Resting HR...")
        val hrChunks = dateChunks(startDate, endDate, 30)
        val hrRaw = mutableListOf<RawDailyMetricEntity>()
        val hrDirty = mutableListOf<ComputeQueueEntity>()
        for ((idx, chunk) in hrChunks.withIndex()) {
            val (cs, ce) = chunk
            emitProgress("Phase 4/4: HR chunk ${idx + 1}/${hrChunks.size}")
            val hrData = fitbitClient.fetchHeartRate(cs.toString(), ce.toString())
            for (entry in hrData) {
                val rhr = entry.value.restingHeartRate ?: continue
                hrRaw += RawDailyMetricEntity(
                    date = entry.dateTime, metric = MetricType.RestingHeartRate.name, source = "Fitbit",
                    value = rhr.toDouble(), unit = "bpm",
                    externalId = "fitbit-rhr-${entry.dateTime}", ingestedAtMs = nowMs,
                )
                hrDirty += ComputeQueueEntity(date = entry.dateTime, metric = MetricType.RestingHeartRate.name, enqueuedAtMs = nowMs)
            }
            if (!checkRateLimit()) break
        }
        if (hrRaw.isNotEmpty()) rawDailyDao.insertAll(hrRaw)
        if (hrDirty.isNotEmpty()) computeQueueDao.enqueue(hrDirty)

        if (!checkRateLimit()) return

        // Weight & body fat
        emitProgress("Phase 4/4: Weight & body fat...")
        val weightChunks = dateChunks(startDate, endDate, 31)
        val bodyRaw = mutableListOf<RawDailyMetricEntity>()
        val bodyDirty = mutableListOf<ComputeQueueEntity>()
        for ((cs, ce) in weightChunks) {
            val weightData = fitbitClient.fetchWeight(cs.toString(), ce.toString())
            for (log in weightData) {
                bodyRaw += RawDailyMetricEntity(
                    date = log.date, metric = MetricType.Weight.name, source = "Fitbit",
                    value = log.weight * KG_TO_LBS, unit = "lbs",
                    externalId = "fitbit-weight-${log.date}", ingestedAtMs = nowMs,
                )
                bodyDirty += ComputeQueueEntity(date = log.date, metric = MetricType.Weight.name, enqueuedAtMs = nowMs)
                log.fat?.let { fatPct ->
                    bodyRaw += RawDailyMetricEntity(
                        date = log.date, metric = MetricType.BodyFat.name, source = "Fitbit",
                        value = fatPct, unit = "percent",
                        externalId = "fitbit-bodyfat-${log.date}", ingestedAtMs = nowMs,
                    )
                    bodyDirty += ComputeQueueEntity(date = log.date, metric = MetricType.BodyFat.name, enqueuedAtMs = nowMs)
                }
            }
            if (!checkRateLimit()) break
        }
        if (bodyRaw.isNotEmpty()) rawDailyDao.insertAll(bodyRaw)
        if (bodyDirty.isNotEmpty()) computeQueueDao.enqueue(bodyDirty)
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
                java.time.OffsetDateTime.parse(dateTimeStr).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                Log.w(TAG, "Cannot parse datetime: $dateTimeStr")
                0L
            }
        }
    }

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
