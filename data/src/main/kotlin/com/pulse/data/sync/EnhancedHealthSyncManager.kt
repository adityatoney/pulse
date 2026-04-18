package com.pulse.data.sync

import android.util.Log
import com.pulse.data.datastore.PreferencesRepository
import com.pulse.data.health.HealthConnectDataSource
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.ExerciseHrSampleDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity
import com.pulse.domain.model.MetricType
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.domain.util.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import java.time.Instant as JavaInstant
import java.time.LocalDate as JavaLocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EnhancedSync"
private const val METERS_PER_MILE = 1_609.34
private const val DEFAULT_STEP_GOAL = 10_000.0
private const val DEFAULT_DISTANCE_GOAL_MI = 5.0
private const val DEFAULT_CALORIE_GOAL = 2_500.0
private const val DEFAULT_ZONE_MIN_GOAL = 22.0
private const val CHANGES_TOKEN_KEY = "hc_changes_token"
private const val BACKFILL_CURSOR_KEY = "hc_backfill_cursor"
private const val QUOTA_PAUSE_KEY = "hc_quota_pause_until"
private const val CHUNK_DELAY_MS = 5_000L
private const val QUOTA_PAUSE_DURATION_MS = 5 * 60 * 1000L

@Singleton
class EnhancedHealthSyncManager @Inject constructor(
    private val hc: HealthConnectDataSource,
    private val aggregateDao: DailyAggregateDao,
    private val exerciseDao: ExerciseSessionDao,
    private val hrSampleDao: ExerciseHrSampleDao,
    private val sleepDao: SleepSessionDao,
    private val syncStateDao: SyncStateDao,
    private val goalDao: GoalDao,
    private val prefsRepo: PreferencesRepository,
    private val clock: Clock,
) {

    /**
     * Tier 1: Sync recent data.
     *
     * @param days how many days to look back
     * @param forceFullFetch when true (user-initiated refresh), always do a bulk
     *   fetch instead of relying on the Changes API, which can lag behind.
     */
    suspend fun syncRecent(days: Int = 7, forceFullFetch: Boolean = false): Result<Unit> = runCatching {
        if (!hc.isAvailable()) return@runCatching
        if (isQuotaPaused()) {
            Log.d(TAG, "Quota paused, skipping sync")
            return@runCatching
        }

        // For background periodic syncs, try incremental first
        if (!forceFullFetch) {
            val incrementalResult = syncIncremental()
            if (incrementalResult.isSuccess) {
                Log.d(TAG, "Incremental sync succeeded")
                return@runCatching
            }
        }

        // Bulk fetch for the requested range
        Log.d(TAG, "Type-first bulk fetch for last $days days (forced=$forceFullFetch)")
        val zone = ZoneId.systemDefault()
        val today = JavaLocalDate.now(zone)
        val start = today.minusDays(days.toLong())

        executeWithQuotaGuard {
            fetchBulkAggregates(start, today, zone)
            fetchBulkVitals(start, today, zone)
            fetchBulkSleep(start, today, zone)
            fetchExerciseWithDetails(start, today, zone)
        }

        // Compute activity-only aggregates from DB exercise sessions (covers HC + Fitbit sources)
        computeExerciseAggregates(start, today, zone)

        // Request a new changes token for future incremental syncs
        hc.requestChangesToken()?.let { saveToken(it) }
    }

    /**
     * Tier 2: Backfill remaining history in [chunkDays]-day chunks.
     * Resumes from where it left off via [BACKFILL_CURSOR_KEY].
     */
    suspend fun backfillHistory(totalDays: Int = 365, chunkDays: Int = 30): Result<Unit> = runCatching {
        if (!hc.isAvailable()) return@runCatching
        val zone = ZoneId.systemDefault()
        val today = JavaLocalDate.now(zone)
        val earliest = today.minusDays(totalDays.toLong())

        // Resume from cursor if available
        val cursorEntity = syncStateDao.get(BACKFILL_CURSOR_KEY)
        val cursor = cursorEntity?.value?.let { runCatching { JavaLocalDate.parse(it) }.getOrNull() }
        val startFrom = cursor ?: today.minusDays(7) // Skip recent days already covered by Tier 1

        if (!startFrom.isAfter(earliest)) {
            Log.d(TAG, "Backfill already complete")
            return@runCatching
        }

        Log.d(TAG, "Backfill: $startFrom back to $earliest in $chunkDays-day chunks")

        var chunkEnd = startFrom
        while (chunkEnd.isAfter(earliest)) {
            if (isQuotaPaused()) {
                Log.d(TAG, "Quota paused during backfill, will retry later")
                throw QuotaPausedException()
            }

            val chunkStart = maxOf(earliest, chunkEnd.minusDays(chunkDays.toLong()))
            Log.d(TAG, "Backfill chunk: $chunkStart to $chunkEnd")

            executeWithQuotaGuard {
                fetchBulkAggregates(chunkStart, chunkEnd, zone)
                fetchBulkVitals(chunkStart, chunkEnd, zone)
                fetchBulkSleep(chunkStart, chunkEnd, zone)
                fetchExerciseWithDetails(chunkStart, chunkEnd, zone)
            }
            computeExerciseAggregates(chunkStart, chunkEnd, zone)

            // Update cursor so we can resume after crash
            syncStateDao.upsert(SyncStateEntity(
                key = BACKFILL_CURSOR_KEY,
                value = chunkStart.toString(),
                updatedAtMs = System.currentTimeMillis(),
            ))

            chunkEnd = chunkStart.minusDays(1)
            if (chunkEnd.isAfter(earliest)) {
                delay(CHUNK_DELAY_MS)
            }
        }
        Log.d(TAG, "Backfill complete")
    }

    /**
     * Incremental sync using the Health Connect Changes API.
     * Returns failure if no token exists or token expired.
     */
    suspend fun syncIncremental(): Result<Unit> = runCatching {
        val token = getStoredToken() ?: throw NoTokenException()
        Log.d(TAG, "Attempting incremental sync with changes token")

        when (val result = hc.getChanges(token)) {
            is HealthConnectDataSource.ChangesResult.TokenExpired -> {
                Log.d(TAG, "Changes token expired, clearing")
                clearToken()
                throw TokenExpiredException()
            }
            is HealthConnectDataSource.ChangesResult.Success -> {
                if (result.upsertedRecordTypes.isEmpty() && result.deletionCount == 0) {
                    Log.d(TAG, "No changes detected")
                    saveToken(result.nextToken)
                    return@runCatching
                }
                Log.d(TAG, "Changes detected: ${result.upsertedRecordTypes.size} types updated, ${result.deletionCount} deletions")

                // Re-fetch only affected types for recent period
                val zone = ZoneId.systemDefault()
                val today = JavaLocalDate.now(zone)
                val start = today.minusDays(7)

                executeWithQuotaGuard {
                    // Always refresh aggregates (cheap)
                    fetchBulkAggregates(start, today, zone)
                    fetchBulkVitals(start, today, zone)
                    fetchBulkSleep(start, today, zone)
                    fetchExerciseWithDetails(start, today, zone)
                }
                computeExerciseAggregates(start, today, zone)

                saveToken(result.nextToken)
            }
        }
    }

    // --- Internal fetch methods -----------------------------------------------

    private suspend fun fetchBulkAggregates(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        val goals = buildGoalMap()
        val nowMs = clock.now().toEpochMilliseconds()
        val prefs = prefsRepo.getMetricDisplay()
        val rows = mutableListOf<DailyAggregateEntity>()

        val steps = hc.stepsByDay(start, end, zone)
        steps.forEach { (day, count) ->
            rows += DailyAggregateEntity(
                date = day.toString(), metric = MetricType.Steps.name,
                total = count.toDouble(), goal = goals[MetricType.Steps],
                sampleCount = 1, computedAtMs = nowMs,
            )
        }

        // Distance
        if (!prefs.activityOnlyDistance) {
            val dist = hc.distanceByDay(start, end, zone)
            dist.forEach { (day, meters) ->
                rows += DailyAggregateEntity(
                    date = day.toString(), metric = MetricType.Distance.name,
                    total = meters / METERS_PER_MILE, goal = goals[MetricType.Distance],
                    sampleCount = 1, computedAtMs = nowMs,
                )
            }
        } else {
            // Activity-only: zero out HC total distance; exercise sessions will upsert real values
            Log.d(TAG, "Activity-only distance: zeroing HC totals for ${steps.size} days")
            steps.forEach { (day, _) ->
                rows += DailyAggregateEntity(
                    date = day.toString(), metric = MetricType.Distance.name,
                    total = 0.0, goal = goals[MetricType.Distance],
                    sampleCount = 0, computedAtMs = nowMs,
                )
            }
        }

        // Active calories
        if (!prefs.activityOnlyCalories) {
            val cals = hc.activeCaloriesByDay(start, end, zone)
            for ((day, active) in cals) {
                if (active > 0) {
                    Log.d(TAG, "ActiveCal $day = $active kcal (from HC ActiveCaloriesBurnedRecord)")
                    rows += DailyAggregateEntity(
                        date = day.toString(), metric = MetricType.ActiveCalories.name,
                        total = active, goal = goals[MetricType.ActiveCalories],
                        sampleCount = 1, computedAtMs = nowMs,
                    )
                }
            }
        } else {
            // Activity-only: zero out; exercise sessions will upsert real values
            Log.d(TAG, "Activity-only calories: zeroing HC totals for ${steps.size} days")
            steps.forEach { (day, _) ->
                rows += DailyAggregateEntity(
                    date = day.toString(), metric = MetricType.ActiveCalories.name,
                    total = 0.0, goal = goals[MetricType.ActiveCalories],
                    sampleCount = 0, computedAtMs = nowMs,
                )
            }
        }

        Log.d(TAG, "Aggregates: ${rows.size} rows (steps=${steps.size}d)")
        if (rows.isNotEmpty()) aggregateDao.upsert(rows)
    }

    /**
     * Bulk-fetch all vitals for the range. 7 API calls total instead of 7 × N days.
     */
    private suspend fun fetchBulkVitals(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        val nowMs = clock.now().toEpochMilliseconds()
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()
        val rows = mutableListOf<DailyAggregateEntity>()

        // 7 calls total for all low-density types
        val weights = hc.readWeightRange(startInstant, endInstant, zone)
        val bodyFats = hc.readBodyFatRange(startInstant, endInstant, zone)
        val spo2s = hc.readSpO2Range(startInstant, endInstant, zone)
        val hrvs = hc.readHrvRange(startInstant, endInstant, zone)
        val vo2s = hc.readVo2MaxRange(startInstant, endInstant, zone)
        val skinTemps = hc.readSkinTemperatureRange(startInstant, endInstant, zone)
        val restingHrs = hc.readRestingHeartRateRange(startInstant, endInstant, zone)

        weights.forEach { (day, kg) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.Weight.name, total = kg, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        bodyFats.forEach { (day, pct) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.BodyFat.name, total = pct, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        spo2s.forEach { (day, pct) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.SpO2.name, total = pct, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        hrvs.forEach { (day, ms) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.HRV.name, total = ms, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        vo2s.forEach { (day, v) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.VO2Max.name, total = v, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        skinTemps.forEach { (day, delta) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.SkinTemperature.name, total = delta, goal = null, sampleCount = 1, computedAtMs = nowMs)
        }
        restingHrs.forEach { (day, bpm) ->
            rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.RestingHeartRate.name, total = bpm.toDouble(), goal = null, sampleCount = 1, computedAtMs = nowMs)
        }

        // Compute zone minutes from bulk HR samples + resting HR
        val hrSamples = hc.readHeartRateSamplesRange(startInstant, endInstant)
        if (hrSamples.isNotEmpty()) {
            val goals = buildGoalMap()
            // Group HR samples by day
            val hrByDay = hrSamples.groupBy { (instant, _) -> instant.atZone(zone).toLocalDate() }
            for ((day, samples) in hrByDay) {
                val restingHr = restingHrs[day] ?: 65
                val zmSamples = samples.map { (instant, bpm) ->
                    ZoneMinuteCalculator.HrSample(
                        at = Instant.fromEpochMilliseconds(instant.toEpochMilli()),
                        bpm = bpm,
                    )
                }
                val breakdown = ZoneMinuteCalculator.calculate(zmSamples, restingHr, age = 45)
                rows += DailyAggregateEntity(
                    date = day.toString(), metric = MetricType.ZoneMinutes.name,
                    total = breakdown.total.toDouble(), goal = goals[MetricType.ZoneMinutes],
                    sampleCount = samples.size, computedAtMs = nowMs,
                )
            }
        }

        if (rows.isNotEmpty()) aggregateDao.upsert(rows)
    }

    private suspend fun fetchBulkSleep(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()

        val sleepRecords = hc.readSleepRange(startInstant, endInstant)
        if (sleepRecords.isEmpty()) return

        val sleepEntities = sleepRecords.map { rec ->
            val startMs = rec.startTime.toEpochMilli()
            val endMs = rec.endTime.toEpochMilli()
            val totalMin = (endMs - startMs) / 60_000L
            var deep = 0L; var rem = 0L; var light = 0L; var awake = 0L
            for (stage in rec.stages) {
                val stageMin = (stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()) / 60_000L
                when (stage.stage) {
                    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP -> deep += stageMin
                    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_REM -> rem += stageMin
                    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT -> light += stageMin
                    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE -> awake += stageMin
                }
            }
            SleepSessionEntity(
                id = rec.metadata.id,
                startUtcMs = startMs, endUtcMs = endMs,
                totalMinutes = totalMin,
                deepMinutes = deep, remMinutes = rem,
                lightMinutes = light, awakeMinutes = awake,
                sourceJson = rec.metadata.dataOrigin.packageName,
                dirty = true,
            )
        }
        sleepDao.upsert(sleepEntities)
    }

    private suspend fun fetchExerciseWithDetails(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        Log.d(TAG, "Fetching exercise sessions from $start to $end")
        val exerciseRecords = hc.readExerciseSessions(start, end, zone)
        Log.d(TAG, "HC returned ${exerciseRecords.size} exercise sessions for range $start to $end")
        if (exerciseRecords.isEmpty()) return
        exerciseRecords.forEach { rec ->
            Log.d(TAG, "  Exercise: type=${rec.exerciseType} title=${rec.title} " +
                "start=${rec.startTime} end=${rec.endTime} pkg=${rec.metadata.dataOrigin.packageName}")
        }

        val restingHrCache = mutableMapOf<JavaLocalDate, Int>()
        val goals = buildGoalMap()
        val nowMs = clock.now().toEpochMilliseconds()

        val exerciseEntities = exerciseRecords.map { rec ->
            val typeName = exerciseTypeName(rec.exerciseType)
            val agg = hc.aggregateForTimeRange(rec.startTime, rec.endTime)
            val durationMs = rec.endTime.toEpochMilli() - rec.startTime.toEpochMilli()
            val avgPace = if (agg.meters > 0) {
                ((durationMs / 1000.0) / (agg.meters / METERS_PER_MILE)).toInt()
            } else null

            val sessionHr = hc.readHeartRateSamplesForRange(rec.startTime, rec.endTime)
            val avgHr = if (sessionHr.isNotEmpty()) sessionHr.map { it.second }.average().toInt() else null
            val maxHr = if (sessionHr.isNotEmpty()) sessionHr.maxOf { it.second } else null

            val sessionZoneMin = if (sessionHr.isNotEmpty()) {
                val sessionDay = rec.startTime.atZone(zone).toLocalDate()
                val restingHr = restingHrCache.getOrPut(sessionDay) {
                    hc.restingHeartRate(sessionDay, zone) ?: 65
                }
                val zmSamples = sessionHr.map { (instant, bpm) ->
                    ZoneMinuteCalculator.HrSample(
                        at = Instant.fromEpochMilliseconds(instant.toEpochMilli()),
                        bpm = bpm,
                    )
                }
                ZoneMinuteCalculator.calculate(zmSamples, restingHr, age = 45).total
            } else null

            ExerciseSessionEntity(
                id = rec.metadata.id,
                type = rec.title ?: typeName,
                startUtcMs = rec.startTime.toEpochMilli(),
                endUtcMs = rec.endTime.toEpochMilli(),
                distanceMeters = agg.meters,
                calories = agg.kcal,
                steps = agg.steps.toInt().takeIf { it > 0 },
                avgHr = avgHr, maxHr = maxHr,
                avgPaceSecondsPerMile = avgPace,
                elevationGainMeters = null,
                zoneMinutes = sessionZoneMin,
                sourceJson = rec.metadata.dataOrigin.packageName,
                dirty = true,
            )
        }
        exerciseDao.upsert(exerciseEntities)

        // Sum per-exercise zone minutes as daily aggregate
        val zmRows = mutableListOf<DailyAggregateEntity>()
        val sessionsByDay = exerciseEntities.groupBy {
            Instant.fromEpochMilliseconds(it.startUtcMs)
                .let { inst ->
                    kotlinx.datetime.TimeZone.currentSystemDefault().let { tz ->
                        inst.toLocalDateTime(tz).date.toString()
                    }
                }
        }
        for ((dateStr, sessions) in sessionsByDay) {
            val totalZm = sessions.sumOf { it.zoneMinutes ?: 0 }
            if (totalZm > 0) {
                zmRows += DailyAggregateEntity(
                    date = dateStr, metric = MetricType.ZoneMinutes.name,
                    total = totalZm.toDouble(), goal = goals[MetricType.ZoneMinutes],
                    sampleCount = sessions.size, computedAtMs = nowMs,
                )
            }
        }
        if (zmRows.isNotEmpty()) aggregateDao.upsert(zmRows)
    }

    /**
     * Compute daily distance/calories from DB exercise sessions.
     * Handles both activity-only mode (user preference) and calorie fallback
     * (when HC doesn't provide ActiveCaloriesBurnedRecord, e.g. Fitbit devices).
     * Runs after all data sources (HC + Fitbit) have written sessions.
     */
    /**
     * Compute daily distance/calories from DB exercise sessions.
     * - Distance: only in activity-only mode
     * - Calories: always (exercise session sum is the best source since Fitbit
     *   doesn't write ActiveCaloriesBurnedRecord to HC)
     */
    private suspend fun computeExerciseAggregates(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        val prefs = prefsRepo.getMetricDisplay()
        val goals = buildGoalMap()
        val nowMs = clock.now().toEpochMilliseconds()
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val dbSessions = exerciseDao.getRange(startMs, endMs)
        Log.d(TAG, "Exercise aggregate pass: ${dbSessions.size} DB sessions for $start to $end")
        if (dbSessions.isEmpty()) return

        val rows = mutableListOf<DailyAggregateEntity>()
        val sessionsByDay = dbSessions.groupBy {
            Instant.fromEpochMilliseconds(it.startUtcMs)
                .let { inst ->
                    kotlinx.datetime.TimeZone.currentSystemDefault().let { tz ->
                        inst.toLocalDateTime(tz).date.toString()
                    }
                }
        }
        for ((dateStr, sessions) in sessionsByDay) {
            // Distance: only when activity-only mode is on
            if (prefs.activityOnlyDistance) {
                val totalDist = sessions.sumOf { it.distanceMeters ?: 0.0 }
                Log.d(TAG, "Distance $dateStr = ${totalDist / METERS_PER_MILE} mi (${sessions.size} sessions)")
                rows += DailyAggregateEntity(
                    date = dateStr, metric = MetricType.Distance.name,
                    total = totalDist / METERS_PER_MILE, goal = goals[MetricType.Distance],
                    sampleCount = sessions.size, computedAtMs = nowMs,
                )
            }
            // Calories: always write exercise session sum — HC ActiveCaloriesBurnedRecord
            // is always 0 for Fitbit users, so exercise sessions are the only reliable source
            val totalCal = sessions.sumOf { it.calories ?: 0.0 }
            if (totalCal > 0) {
                Log.d(TAG, "ActiveCal $dateStr = $totalCal kcal (${sessions.size} sessions)")
                rows += DailyAggregateEntity(
                    date = dateStr, metric = MetricType.ActiveCalories.name,
                    total = totalCal, goal = goals[MetricType.ActiveCalories],
                    sampleCount = sessions.size, computedAtMs = nowMs,
                )
            }
        }
        if (rows.isNotEmpty()) aggregateDao.upsert(rows)
    }

    // --- Quota management -----------------------------------------------------

    private suspend fun executeWithQuotaGuard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            val message = e.message ?: ""
            val quotaMatch = Regex("availableQuota[=:]\\s*(\\d+\\.?\\d*)").find(message)
            if (quotaMatch != null) {
                val quota = quotaMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                if (quota < 0.1) {
                    Log.w(TAG, "Quota exhausted (available=$quota), pausing for ${QUOTA_PAUSE_DURATION_MS / 1000}s")
                    syncStateDao.upsert(SyncStateEntity(
                        key = QUOTA_PAUSE_KEY,
                        value = (System.currentTimeMillis() + QUOTA_PAUSE_DURATION_MS).toString(),
                        updatedAtMs = System.currentTimeMillis(),
                    ))
                }
            }
            throw e
        }
    }

    private suspend fun isQuotaPaused(): Boolean {
        val entity = syncStateDao.get(QUOTA_PAUSE_KEY) ?: return false
        val pauseUntil = entity.value.toLongOrNull() ?: return false
        return System.currentTimeMillis() < pauseUntil
    }

    // --- Token management -----------------------------------------------------

    private suspend fun getStoredToken(): String? = syncStateDao.get(CHANGES_TOKEN_KEY)?.value

    private suspend fun saveToken(token: String) {
        syncStateDao.upsert(SyncStateEntity(
            key = CHANGES_TOKEN_KEY,
            value = token,
            updatedAtMs = System.currentTimeMillis(),
        ))
    }

    private suspend fun clearToken() {
        syncStateDao.remove(CHANGES_TOKEN_KEY)
    }

    // --- Helpers ---------------------------------------------------------------

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

    private fun exerciseTypeName(type: Int): String = when (type) {
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        else -> "Workout"
    }

    private fun maxOf(a: JavaLocalDate, b: JavaLocalDate): JavaLocalDate = if (a.isAfter(b)) a else b

    class QuotaPausedException : Exception("Health Connect quota paused")
    class NoTokenException : Exception("No changes token stored")
    class TokenExpiredException : Exception("Changes token expired")
}
