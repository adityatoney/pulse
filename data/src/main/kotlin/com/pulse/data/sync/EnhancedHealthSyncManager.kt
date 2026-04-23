package com.pulse.data.sync

import android.util.Log
import com.pulse.data.compute.HealthIntelligenceService
import com.pulse.data.compute.SummaryComputeEngine
import com.pulse.data.health.HealthConnectDataSource
import com.pulse.data.local.dao.ComputeQueueDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.ExerciseHrSampleDao
import com.pulse.data.local.dao.RawDailyMetricDao
import com.pulse.data.local.dao.RawHourlyMetricDao
import com.pulse.data.local.dao.RawSampleDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.RawDailyMetricEntity
import com.pulse.data.local.entity.RawHourlyMetricEntity
import com.pulse.data.local.entity.RawSampleEntity
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
private const val CHANGES_TOKEN_KEY = "hc_changes_token"
private const val BACKFILL_CURSOR_KEY = "hc_backfill_cursor"
private const val QUOTA_PAUSE_KEY = "hc_quota_pause_until"
private const val CHUNK_DELAY_MS = 5_000L
private const val QUOTA_PAUSE_DURATION_MS = 5 * 60 * 1000L
private const val SOURCE_PRIORITY_V2_KEY = "source_priority_v2_recomputed"

@Singleton
class EnhancedHealthSyncManager @Inject constructor(
    private val hc: HealthConnectDataSource,
    private val rawDailyDao: RawDailyMetricDao,
    private val rawHourlyDao: RawHourlyMetricDao,
    private val rawSampleDao: RawSampleDao,
    private val computeQueueDao: ComputeQueueDao,
    private val computeEngine: SummaryComputeEngine,
    private val intelligenceService: HealthIntelligenceService,
    private val exerciseDao: ExerciseSessionDao,
    private val hrSampleDao: ExerciseHrSampleDao,
    private val sleepDao: SleepSessionDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock,
) {

    /**
     * Tier 1: Sync recent data.
     */
    suspend fun syncRecent(days: Int = 7, forceFullFetch: Boolean = false): Result<Unit> = runCatching {
        if (!hc.isAvailable()) return@runCatching
        if (isQuotaPaused()) {
            Log.d(TAG, "Quota paused, skipping sync")
            return@runCatching
        }

        if (!forceFullFetch) {
            val incrementalResult = syncIncremental()
            if (incrementalResult.isSuccess) {
                Log.d(TAG, "Incremental sync succeeded")
                return@runCatching
            }
        }

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

        // Trigger summary computation from raw data
        computeEngine.processQueue()

        // One-time recompute after source priority restructuring (HC > Google > Fitbit)
        if (syncStateDao.get(SOURCE_PRIORITY_V2_KEY) == null) {
            Log.d(TAG, "Running one-time recompute for source priority v2")
            computeEngine.recomputeAll(days = 365)
            syncStateDao.upsert(SyncStateEntity(
                key = SOURCE_PRIORITY_V2_KEY,
                value = "done",
                updatedAtMs = System.currentTimeMillis(),
            ))
        }

        // Compute insights for affected dates
        val affectedDates = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { it.toString() }
            .toList()
        intelligenceService.computeAll(affectedDates)

        hc.requestChangesToken()?.let { saveToken(it) }
    }

    /**
     * Tier 2: Backfill remaining history in [chunkDays]-day chunks.
     */
    suspend fun backfillHistory(totalDays: Int = 365, chunkDays: Int = 30): Result<Unit> = runCatching {
        if (!hc.isAvailable()) return@runCatching
        val zone = ZoneId.systemDefault()
        val today = JavaLocalDate.now(zone)
        val earliest = today.minusDays(totalDays.toLong())

        val cursorEntity = syncStateDao.get(BACKFILL_CURSOR_KEY)
        val cursor = cursorEntity?.value?.let { runCatching { JavaLocalDate.parse(it) }.getOrNull() }
        val startFrom = cursor ?: today.minusDays(7)

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
            computeEngine.processQueue()

            val chunkDates = generateSequence(chunkStart) { it.plusDays(1) }
                .takeWhile { !it.isAfter(chunkEnd) }
                .map { it.toString() }
                .toList()
            intelligenceService.computeAll(chunkDates)

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
                if (result.upsertedRecordTypes.isEmpty() && result.deletedIds.isEmpty()) {
                    Log.d(TAG, "No changes detected")
                    saveToken(result.nextToken)
                    return@runCatching
                }
                Log.d(TAG, "Changes detected: ${result.upsertedRecordTypes.size} types updated, ${result.deletedIds.size} deletions")

                // Process deletions: remove records from local DB
                if (result.deletedIds.isNotEmpty()) {
                    // HC doesn't tell us which table the deleted ID belongs to,
                    // so try all session tables. IDs are globally unique.
                    for (chunk in result.deletedIds.chunked(500)) {
                        hrSampleDao.deleteForSessions(chunk)
                        exerciseDao.deleteByIds(chunk)
                        sleepDao.deleteByIds(chunk)
                    }
                    Log.d(TAG, "Processed ${result.deletedIds.size} deletions from local DB")
                }

                val zone = ZoneId.systemDefault()
                val today = JavaLocalDate.now(zone)
                val start = today.minusDays(7)

                executeWithQuotaGuard {
                    fetchBulkAggregates(start, today, zone)
                    fetchBulkVitals(start, today, zone)
                    fetchBulkSleep(start, today, zone)
                    fetchExerciseWithDetails(start, today, zone)
                }
                computeEngine.processQueueAll()

                val incrementalDates = generateSequence(start) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(today) }
                    .map { it.toString() }
                    .toList()
                intelligenceService.computeAll(incrementalDates)

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
        val nowMs = clock.now().toEpochMilliseconds()
        val rawRows = mutableListOf<RawDailyMetricEntity>()
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()

        val steps = hc.stepsByDay(start, end, zone)
        steps.forEach { (day, count) ->
            val dateStr = day.toString()
            rawRows += RawDailyMetricEntity(
                date = dateStr, metric = MetricType.Steps.name, source = "HealthConnect",
                value = count.toDouble(), unit = "count",
                externalId = "hc-steps-$dateStr", ingestedAtMs = nowMs,
            )
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.Steps.name, enqueuedAtMs = nowMs)
        }

        // Ingest hourly step data for Circadian Delta
        val hourlyEntities = mutableListOf<RawHourlyMetricEntity>()
        for (day in generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }) {
            val hourly = hc.stepsByHour(day, zone)
            hourly.forEach { (hour, value) ->
                hourlyEntities += RawHourlyMetricEntity(
                    date = day.toString(), hour = hour, metric = "Steps",
                    value = value.toDouble(), source = "HealthConnect", ingestedAtMs = nowMs,
                )
            }
        }
        if (hourlyEntities.isNotEmpty()) rawHourlyDao.insertAll(hourlyEntities)

        val dist = hc.distanceByDay(start, end, zone)
        dist.forEach { (day, meters) ->
            val dateStr = day.toString()
            val miles = meters / METERS_PER_MILE
            rawRows += RawDailyMetricEntity(
                date = dateStr, metric = MetricType.Distance.name, source = "HealthConnect",
                value = miles, unit = "miles",
                externalId = "hc-distance-$dateStr", ingestedAtMs = nowMs,
            )
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.Distance.name, enqueuedAtMs = nowMs)
        }

        val cals = hc.activeCaloriesByDay(start, end, zone)
        for ((day, active) in cals) {
            if (active > 0) {
                val dateStr = day.toString()
                rawRows += RawDailyMetricEntity(
                    date = dateStr, metric = MetricType.ActiveCalories.name, source = "HealthConnect",
                    value = active, unit = "kcal",
                    externalId = "hc-activecal-$dateStr", ingestedAtMs = nowMs,
                )
                dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.ActiveCalories.name, enqueuedAtMs = nowMs)
            }
        }

        Log.d(TAG, "Aggregates: ${rawRows.size} raw rows (steps=${steps.size}d)")
        if (rawRows.isNotEmpty()) rawDailyDao.insertAll(rawRows)
        if (dirtyEntries.isNotEmpty()) computeQueueDao.enqueue(dirtyEntries)
    }

    private suspend fun fetchBulkVitals(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ) {
        val nowMs = clock.now().toEpochMilliseconds()
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()
        val rawRows = mutableListOf<RawDailyMetricEntity>()
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()

        val weights = hc.readWeightRange(startInstant, endInstant, zone)
        val bodyFats = hc.readBodyFatRange(startInstant, endInstant, zone)
        val spo2s = hc.readSpO2Range(startInstant, endInstant, zone)
        val hrvs = hc.readHrvRange(startInstant, endInstant, zone)
        val vo2s = hc.readVo2MaxRange(startInstant, endInstant, zone)
        val skinTemps = hc.readSkinTemperatureRange(startInstant, endInstant, zone)
        val restingHrs = hc.readRestingHeartRateRange(startInstant, endInstant, zone)

        fun addVital(day: JavaLocalDate, metric: MetricType, value: Double, unit: String) {
            val dateStr = day.toString()
            rawRows += RawDailyMetricEntity(date = dateStr, metric = metric.name, source = "HealthConnect", value = value, unit = unit, externalId = "hc-${metric.name.lowercase()}-$dateStr", ingestedAtMs = nowMs)
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = metric.name, enqueuedAtMs = nowMs)
        }

        weights.forEach { (day, lbs) -> addVital(day, MetricType.Weight, lbs, "lbs") }
        bodyFats.forEach { (day, pct) -> addVital(day, MetricType.BodyFat, pct, "percent") }
        spo2s.forEach { (day, pct) -> addVital(day, MetricType.SpO2, pct, "percent") }
        hrvs.forEach { (day, ms) -> addVital(day, MetricType.HRV, ms, "ms") }
        vo2s.forEach { (day, v) -> addVital(day, MetricType.VO2Max, v, "ml/kg/min") }
        skinTemps.forEach { (day, delta) -> addVital(day, MetricType.SkinTemperature, delta, "celsius") }
        restingHrs.forEach { (day, bpm) -> addVital(day, MetricType.RestingHeartRate, bpm.toDouble(), "bpm") }

        // Compute zone minutes from bulk HR samples + resting HR
        val hrSamples = hc.readHeartRateSamplesRange(startInstant, endInstant)
        if (hrSamples.isNotEmpty()) {
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
                val dateStr = day.toString()
                rawRows += RawDailyMetricEntity(
                    date = dateStr, metric = MetricType.ZoneMinutes.name, source = "HealthConnect",
                    value = breakdown.total.toDouble(), unit = "minutes",
                    externalId = "hc-zoneminutes-$dateStr", ingestedAtMs = nowMs,
                )
                dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.ZoneMinutes.name, enqueuedAtMs = nowMs)
            }

            // Store raw HR samples
            val rawHrSamples = hrSamples.map { (instant, bpm) ->
                RawSampleEntity(
                    type = "HeartRate", value = bpm.toDouble(), unit = "bpm",
                    startUtcMs = instant.toEpochMilli(), endUtcMs = instant.toEpochMilli(),
                    source = "HealthConnect",
                    externalId = "hc-hr-${instant.toEpochMilli()}",
                    ingestedAtMs = nowMs,
                )
            }
            rawSampleDao.insertAll(rawHrSamples)
        }

        if (rawRows.isNotEmpty()) rawDailyDao.insertAll(rawRows)
        if (dirtyEntries.isNotEmpty()) computeQueueDao.enqueue(dirtyEntries)
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

        val restingHrCache = mutableMapOf<JavaLocalDate, Int>()
        val nowMs = clock.now().toEpochMilliseconds()

        val exerciseEntities = exerciseRecords.mapNotNull { rec ->
            try {
                val typeName = exerciseTypeName(rec.exerciseType)
                val agg = hc.aggregateForTimeRange(rec.startTime, rec.endTime)
                val durationMs = rec.endTime.toEpochMilli() - rec.startTime.toEpochMilli()
                val durationHours = durationMs / 3_600_000.0
                val avgPace = if (agg.meters > 0) {
                    ((durationMs / 1000.0) / (agg.meters / METERS_PER_MILE)).toInt()
                } else null

                val sessionHr = try {
                    hc.readHeartRateSamplesForRange(rec.startTime, rec.endTime)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read HR for exercise ${rec.metadata.id}: ${e.message}")
                    emptyList()
                }
                val avgHr = if (sessionHr.isNotEmpty()) sessionHr.map { it.second }.average().toInt() else null
                val maxHr = if (sessionHr.isNotEmpty()) sessionHr.maxOf { it.second } else null

                val hrBasedZoneMin = if (sessionHr.isNotEmpty()) {
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

                // Fall back to duration for active exercises with no/zero HR-based zone minutes
                // (common for manual entries where passive HR readings don't reach zone thresholds)
                val sessionZoneMin = if ((hrBasedZoneMin == null || hrBasedZoneMin == 0) && isActiveExerciseType(rec.exerciseType)) {
                    val durMin = (durationMs / 60_000).toInt()
                    Log.d(TAG, "Using duration ${durMin}m as zone minutes for ${rec.title ?: typeName} (HR-based was $hrBasedZoneMin)")
                    durMin
                } else hrBasedZoneMin

                // If HC aggregate calories are too low (< 1 cal/min), estimate via MET.
                // Google Fit calculates "Energy expended" internally for manual entries
                // but doesn't write it to HC as a calorie record.
                val minExpectedCalPerMin = 1.0
                val sessionMinutes = durationMs / 60_000.0
                val hcCaloriesTooLow = agg.kcal < sessionMinutes * minExpectedCalPerMin
                val calories = if (hcCaloriesTooLow && durationHours > 0) {
                    val estimated = estimateCalories(rec.exerciseType, durationHours, agg.meters)
                    Log.d(TAG, "HC cal too low (${agg.kcal}), estimating: $estimated for ${rec.title ?: typeName}")
                    estimated
                } else {
                    agg.kcal
                }

                ExerciseSessionEntity(
                    id = rec.metadata.id,
                    type = rec.title ?: typeName,
                    startUtcMs = rec.startTime.toEpochMilli(),
                    endUtcMs = rec.endTime.toEpochMilli(),
                    distanceMeters = agg.meters,
                    calories = calories,
                    steps = agg.steps.toInt().takeIf { it > 0 },
                    avgHr = avgHr, maxHr = maxHr,
                    avgPaceSecondsPerMile = avgPace,
                    elevationGainMeters = null,
                    zoneMinutes = sessionZoneMin,
                    sourceJson = rec.metadata.dataOrigin.packageName,
                    dirty = true,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process exercise ${rec.metadata.id}: ${e.message}")
                null
            }
        }
        exerciseDao.upsert(exerciseEntities)
        exerciseDao.deduplicateOverlapping()

        // Enqueue Distance and ActiveCalories for compute engine
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()
        val sessionsByDay = exerciseEntities.groupBy {
            Instant.fromEpochMilliseconds(it.startUtcMs)
                .let { inst ->
                    kotlinx.datetime.TimeZone.currentSystemDefault().let { tz ->
                        inst.toLocalDateTime(tz).date.toString()
                    }
                }
        }

        for ((dateStr, daySessions) in sessionsByDay) {
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.Distance.name, enqueuedAtMs = nowMs)
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.ActiveCalories.name, enqueuedAtMs = nowMs)

            // For exercises that got duration-based zone minutes (manual entries or
            // sessions where passive HR didn't reach zone thresholds), add them to
            // the daily zone minutes aggregate so the dashboard reflects them.
            // Detect these by checking if zone minutes equals the session duration.
            val manualZoneMin = daySessions
                .filter { session ->
                    val zm = session.zoneMinutes ?: 0
                    val durMin = ((session.endUtcMs - session.startUtcMs) / 60_000).toInt()
                    zm > 0 && zm == durMin // duration-based fallback was used
                }
                .sumOf { it.zoneMinutes ?: 0 }
            if (manualZoneMin > 0) {
                // Read existing HR-based zone minutes for this day
                val existing = rawDailyDao.getForDateAndMetric(dateStr, MetricType.ZoneMinutes.name)
                val hrBasedZm = existing.firstOrNull()?.value ?: 0.0
                val combined = hrBasedZm + manualZoneMin
                rawDailyDao.insertAll(listOf(
                    RawDailyMetricEntity(
                        date = dateStr, metric = MetricType.ZoneMinutes.name,
                        source = "HealthConnect", value = combined, unit = "minutes",
                        externalId = "hc-zoneminutes-$dateStr", ingestedAtMs = nowMs,
                    )
                ))
                dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.ZoneMinutes.name, enqueuedAtMs = nowMs)
                Log.d(TAG, "Zone min for $dateStr: HR-based=$hrBasedZm + manual=$manualZoneMin = $combined")
            }
        }
        if (dirtyEntries.isNotEmpty()) computeQueueDao.enqueue(dirtyEntries)
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

    // --- Calorie Estimation (MET-based) ----------------------------------------

    /**
     * Estimate calories when HC doesn't provide reliable calorie data
     * (common for manually entered exercises in Google Fit).
     * Uses MET values × default body weight × duration.
     * Default weight 80 kg matches Google Fit's mid-range assumption.
     */
    private fun estimateCalories(
        exerciseType: Int,
        durationHours: Double,
        distanceMeters: Double,
    ): Double {
        val ESR = androidx.health.connect.client.records.ExerciseSessionRecord
        val defaultWeightKg = 80.0

        // MET values from Compendium of Physical Activities
        val met = when (exerciseType) {
            ESR.EXERCISE_TYPE_WALKING -> {
                // Adjust MET by walking speed if distance is available
                if (distanceMeters > 0 && durationHours > 0) {
                    val mph = (distanceMeters / METERS_PER_MILE) / durationHours
                    when {
                        mph < 2.5 -> 2.8
                        mph < 3.0 -> 3.0
                        mph < 3.5 -> 3.3
                        mph < 4.0 -> 3.6
                        mph < 4.5 -> 4.3
                        else -> 5.0
                    }
                } else 3.3
            }
            ESR.EXERCISE_TYPE_RUNNING -> {
                if (distanceMeters > 0 && durationHours > 0) {
                    val mph = (distanceMeters / METERS_PER_MILE) / durationHours
                    when {
                        mph < 5.0 -> 8.3
                        mph < 6.0 -> 9.8
                        mph < 7.0 -> 11.0
                        mph < 8.0 -> 11.8
                        else -> 12.8
                    }
                } else 9.8
            }
            ESR.EXERCISE_TYPE_BIKING -> {
                if (distanceMeters > 0 && durationHours > 0) {
                    val mph = (distanceMeters / METERS_PER_MILE) / durationHours
                    when {
                        mph < 10.0 -> 4.0
                        mph < 12.0 -> 6.8
                        mph < 14.0 -> 8.0
                        mph < 16.0 -> 10.0
                        else -> 12.0
                    }
                } else 7.5
            }
            ESR.EXERCISE_TYPE_HIKING -> 6.0
            ESR.EXERCISE_TYPE_SWIMMING_POOL,
            ESR.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> 7.0
            ESR.EXERCISE_TYPE_YOGA -> 3.0
            ESR.EXERCISE_TYPE_WEIGHTLIFTING,
            ESR.EXERCISE_TYPE_STRENGTH_TRAINING -> 5.0
            ESR.EXERCISE_TYPE_ELLIPTICAL -> 5.0
            ESR.EXERCISE_TYPE_STAIR_CLIMBING -> 9.0
            ESR.EXERCISE_TYPE_ROWING -> 7.0
            else -> 5.0 // moderate activity default
        }

        return met * defaultWeightKg * durationHours
    }

    // --- Helpers ---------------------------------------------------------------

    private fun isActiveExerciseType(type: Int): Boolean {
        val ESR = androidx.health.connect.client.records.ExerciseSessionRecord
        return type in setOf(
            ESR.EXERCISE_TYPE_WALKING,
            ESR.EXERCISE_TYPE_RUNNING,
            ESR.EXERCISE_TYPE_BIKING,
            ESR.EXERCISE_TYPE_HIKING,
            ESR.EXERCISE_TYPE_SWIMMING_POOL,
            ESR.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ESR.EXERCISE_TYPE_ELLIPTICAL,
            ESR.EXERCISE_TYPE_STAIR_CLIMBING,
            ESR.EXERCISE_TYPE_ROWING,
            ESR.EXERCISE_TYPE_OTHER_WORKOUT,
        )
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
