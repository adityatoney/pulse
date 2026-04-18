package com.pulse.data.repository

import android.content.Context
import com.pulse.data.compute.SummaryComputeEngine
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.local.PulseDatabase
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.RawDailyMetricEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity
import com.pulse.domain.model.DataSource
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.HealthMetric
import com.pulse.domain.model.MeasurementUnit
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.DataStats
import com.pulse.domain.repository.DebugBuildInfo
import com.pulse.domain.repository.DebugRepository
import com.pulse.domain.util.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random

@Singleton
class DebugRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PulseDatabase,
    private val syncRepository: SyncRepositoryImpl,
    private val computeEngine: SummaryComputeEngine,
    private val featureFlags: FeatureFlagRepository,
    private val clock: Clock,
) : DebugRepository {

    override suspend fun seedFakeData(days: Int, seed: Long): Int {
        val rng = Random(seed)
        val tz = TimeZone.currentSystemDefault()
        val today = clock.now().toLocalDateTime(tz).date
        val rawRows = mutableListOf<RawDailyMetricEntity>()
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()
        val nowMs = clock.now().toEpochMilliseconds()

        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
            val dateStr = date.toString()
            val dow = date.dayOfWeek.ordinal // Mon=0
            val baseSteps = when (dow) {
                5, 6 -> rng.nextInt(2_000, 6_000)   // weekend shorter
                else -> rng.nextInt(4_500, 12_500)
            }
            val steps = max(100, baseSteps + rng.nextInt(-800, 800))
            val distanceMi = steps / 2000.0 + rng.nextDouble() * 0.5
            val kcal = 1800 + rng.nextInt(-300, 700)
            val zMin = when {
                steps > 10_000 -> rng.nextInt(30, 60)
                steps > 7_000 -> rng.nextInt(15, 35)
                else -> rng.nextInt(0, 15)
            }

            fun addRaw(metric: MetricType, value: Double, unit: String) {
                rawRows += RawDailyMetricEntity(
                    date = dateStr, metric = metric.name, source = "Seeded",
                    value = value, unit = unit,
                    externalId = "seed-${metric.name.lowercase()}-$dateStr",
                    ingestedAtMs = nowMs,
                )
                dirtyEntries += ComputeQueueEntity(date = dateStr, metric = metric.name, enqueuedAtMs = nowMs)
            }

            addRaw(MetricType.Steps, steps.toDouble(), "count")
            addRaw(MetricType.Distance, distanceMi, "miles")
            addRaw(MetricType.ActiveCalories, kcal.toDouble(), "kcal")
            addRaw(MetricType.ZoneMinutes, zMin.toDouble(), "minutes")
        }

        // Seed exercise sessions
        val exerciseTypes = listOf("Running", "Walking", "Cycling", "Treadmill run", "Outdoor walk")
        val exerciseRows = mutableListOf<ExerciseSessionEntity>()
        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
            val sessionCount = rng.nextInt(0, 3) // 0-2 sessions
            for (j in 0 until sessionCount) {
                val type = exerciseTypes[rng.nextInt(exerciseTypes.size)]
                val durationMin = rng.nextInt(15, 61)
                val startHour = rng.nextInt(6, 10) // 6-9 AM
                val startMinute = rng.nextInt(0, 60)
                val startMs = date.atStartMs() + startHour * 3_600_000L + startMinute * 60_000L + j * 3_600_000L
                val endMs = startMs + durationMin * 60_000L
                val distance = rng.nextDouble(1000.0, 8001.0)
                val calories = rng.nextDouble(100.0, 501.0)
                val avgHr = rng.nextInt(110, 166)
                val maxHr = avgHr + rng.nextInt(10, 31)
                exerciseRows += ExerciseSessionEntity(
                    id = "seed-$date-$j",
                    type = type,
                    startUtcMs = startMs,
                    endUtcMs = endMs,
                    distanceMeters = distance,
                    calories = calories,
                    avgHr = avgHr,
                    maxHr = maxHr,
                    sourceJson = null,
                    dirty = true,
                )
            }
        }
        if (exerciseRows.isNotEmpty()) {
            db.exerciseSessionDao().upsert(exerciseRows)
        }

        // Seed body metrics (weight in lbs)
        val baseWeight = 159.0 + rng.nextDouble(-11.0, 11.0)
        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
            val dateStr = date.toString()

            fun addBody(metric: MetricType, value: Double, unit: String) {
                rawRows += RawDailyMetricEntity(
                    date = dateStr, metric = metric.name, source = "Seeded",
                    value = value, unit = unit,
                    externalId = "seed-${metric.name.lowercase()}-$dateStr",
                    ingestedAtMs = nowMs,
                )
                dirtyEntries += ComputeQueueEntity(date = dateStr, metric = metric.name, enqueuedAtMs = nowMs)
            }

            addBody(MetricType.RestingHeartRate, rng.nextInt(58, 72).toDouble(), "bpm")
            addBody(MetricType.Weight, baseWeight + rng.nextDouble(-1.0, 1.0), "lbs")
            addBody(MetricType.BodyFat, 18.0 + rng.nextDouble(-2.0, 2.0), "percent")
            addBody(MetricType.SpO2, rng.nextInt(95, 100).toDouble(), "percent")
            addBody(MetricType.HRV, rng.nextInt(25, 65).toDouble(), "ms")
            addBody(MetricType.VO2Max, 38.0 + rng.nextDouble(-3.0, 3.0), "ml/kg/min")
        }

        // Write raw data and trigger compute
        if (rawRows.isNotEmpty()) {
            db.rawDailyMetricDao().insertAll(rawRows)
            db.computeQueueDao().enqueue(dirtyEntries)
            computeEngine.processQueue(dirtyEntries.size)
        }

        // Seed sleep sessions
        val sleepRows = mutableListOf<SleepSessionEntity>()
        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
            val bedtimeHour = rng.nextInt(22, 24)
            val bedtimeMs = date.minus(DatePeriod(days = 1)).atStartMs() + bedtimeHour * 3_600_000L + rng.nextInt(0, 60) * 60_000L
            val totalMin = rng.nextInt(300, 510).toLong() // 5-8.5h
            val wakeMs = bedtimeMs + totalMin * 60_000L
            val deep = rng.nextInt(40, 90).toLong()
            val rem = rng.nextInt(50, 110).toLong()
            val awake = rng.nextInt(10, 40).toLong()
            val light = totalMin - deep - rem - awake
            sleepRows += SleepSessionEntity(
                id = "seed-sleep-$date",
                startUtcMs = bedtimeMs,
                endUtcMs = wakeMs,
                totalMinutes = totalMin,
                deepMinutes = deep,
                remMinutes = rem,
                lightMinutes = light.coerceAtLeast(0),
                awakeMinutes = awake,
                sourceJson = null,
                dirty = true,
            )
        }
        db.sleepSessionDao().upsert(sleepRows)

        return rawRows.size + exerciseRows.size + sleepRows.size
    }

    override suspend fun seedRealisticWeek(): Int {
        val tz = TimeZone.currentSystemDefault()
        val today = clock.now().toLocalDateTime(tz).date
        val nowMs = clock.now().toEpochMilliseconds()
        val stepsByOffset = listOf(3_500, 2_800, 5_200, 4_100, 1_900, 0, 0)
        val rawRows = mutableListOf<RawDailyMetricEntity>()
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()
        stepsByOffset.forEachIndexed { idx, steps ->
            val d = today.minus(DatePeriod(days = idx))
            val dateStr = d.toString()
            rawRows += RawDailyMetricEntity(
                date = dateStr, metric = MetricType.Steps.name, source = "Seeded",
                value = steps.toDouble(), unit = "count",
                externalId = "seed-steps-$dateStr", ingestedAtMs = nowMs,
            )
            rawRows += RawDailyMetricEntity(
                date = dateStr, metric = MetricType.Distance.name, source = "Seeded",
                value = steps / 2000.0, unit = "miles",
                externalId = "seed-distance-$dateStr", ingestedAtMs = nowMs,
            )
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.Steps.name, enqueuedAtMs = nowMs)
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = MetricType.Distance.name, enqueuedAtMs = nowMs)
        }
        db.rawDailyMetricDao().insertAll(rawRows)
        db.computeQueueDao().enqueue(dirtyEntries)
        computeEngine.processQueue(dirtyEntries.size)

        // Seed realistic exercise sessions
        val exerciseRows = mutableListOf<ExerciseSessionEntity>()
        val wed = today.minus(DatePeriod(days = 2))
        val wedStartMs = wed.atStartMs() + 7 * 3_600_000L
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$wed-0",
            type = "Treadmill run",
            startUtcMs = wedStartMs,
            endUtcMs = wedStartMs + 35 * 60_000L,
            distanceMeters = 5200.0,
            calories = 320.0,
            avgHr = 145,
            maxHr = 168,
            sourceJson = null,
            dirty = true,
        )
        val thu = today.minus(DatePeriod(days = 3))
        val thuStartMs = thu.atStartMs() + 8 * 3_600_000L
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$thu-0",
            type = "Outdoor walk",
            startUtcMs = thuStartMs,
            endUtcMs = thuStartMs + 45 * 60_000L,
            distanceMeters = 3800.0,
            calories = 180.0,
            avgHr = 115,
            maxHr = 132,
            sourceJson = null,
            dirty = true,
        )
        val todayStartMs = today.atStartMs() + 6 * 3_600_000L + 30 * 60_000L
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$today-0",
            type = "Running",
            startUtcMs = todayStartMs,
            endUtcMs = todayStartMs + 28 * 60_000L,
            distanceMeters = 4500.0,
            calories = 290.0,
            avgHr = 152,
            maxHr = 175,
            sourceJson = null,
            dirty = true,
        )
        db.exerciseSessionDao().upsert(exerciseRows)

        return rawRows.size + exerciseRows.size
    }

    override suspend fun clearLocalCache(hard: Boolean) {
        db.rawDailyMetricDao().clear()
        db.rawSampleDao().clear()
        db.summaryDailyMetricDao().clear()
        db.computeQueueDao().clear()
        db.exerciseSessionDao().clear()
        db.sleepSessionDao().clear()
        if (hard) {
            db.syncStateDao().clear()
        }
    }

    override suspend fun exportAsCsv(range: DateRange): String {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "export_${clock.now().toEpochMilliseconds()}.csv")
        FileWriter(file).use { w ->
            w.appendLine("date,metric,total,goal,sampleCount,computedAtMs")
        }
        return file.absolutePath
    }

    override suspend fun dumpRecords(date: LocalDate): List<HealthMetric> {
        val dateStr = date.toString()
        val allRaw = mutableListOf<HealthMetric>()
        for (type in MetricType.entries) {
            val rows = db.rawDailyMetricDao().getForDateAndMetric(dateStr, type.name)
            for (row in rows) {
                allRaw += HealthMetric(
                    id = row.externalId ?: "${row.date}-${row.metric}-${row.source}",
                    type = type,
                    value = row.value,
                    unit = MeasurementUnit.Count,
                    start = kotlinx.datetime.Instant.fromEpochMilliseconds(date.atStartMs()),
                    end = kotlinx.datetime.Instant.fromEpochMilliseconds(date.atStartMs() + 24 * 60 * 60 * 1000L),
                    source = when (row.source) {
                        "Fitbit" -> DataSource.Fitbit
                        "GoogleHealth" -> DataSource.GoogleHealth
                        else -> DataSource.HealthConnect
                    },
                )
            }
        }
        return allRaw
    }

    override suspend fun resetChangeToken() {
        db.syncStateDao().remove("hc_changes_token")
        db.syncStateDao().upsert(
            SyncStateEntity(
                key = "hc_changes_token",
                value = "",
                updatedAtMs = clock.now().toEpochMilliseconds(),
            )
        )
    }

    override suspend fun forceSyncNow() {
        syncRepository.forceSyncNow()
    }

    override suspend fun simulateNetworkFailure(durationSeconds: Int) {
        featureFlags.setFaultInjection(durationSeconds * 1000L)
    }

    override suspend fun dataStats(): DataStats {
        val summaryDao = db.summaryDailyMetricDao()
        val metricCounts = summaryDao.getAll()
            .groupBy { it.metric }
            .mapValues { (_, rows) -> rows.size }

        val backfillCursor = db.syncStateDao().get("hc_backfill_cursor")?.value
        val today = java.time.LocalDate.now()
        val earliest = today.minusDays(365)
        val cursorDate = backfillCursor?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val backfillComplete = cursorDate != null && !cursorDate.isAfter(earliest)

        return DataStats(
            minStepDate = summaryDao.minStepDate(),
            maxStepDate = summaryDao.maxStepDate(),
            totalStepDays = summaryDao.stepDayCount(),
            totalExerciseSessions = db.exerciseSessionDao().totalCount(),
            totalSleepSessions = db.sleepSessionDao().getAll().size,
            metricCounts = metricCounts,
            backfillCursor = backfillCursor,
            backfillComplete = backfillComplete,
        )
    }

    override suspend fun pendingQueueSize(): Int = db.summaryDailyMetricDao().dirtyCount()

    override suspend fun fitbitSyncCursor(): String? =
        db.syncStateDao().get("fitbit_sync_cursor")?.value

    override suspend fun exportDriveBackup(): String {
        val payload = com.pulse.data.cloud.backup.BackupPayload(
            version = 2,
            dbVersion = com.pulse.data.local.PulseDatabase.VERSION,
            appVersion = "export",
            createdAtMs = clock.now().toEpochMilliseconds(),
            rawDailyMetrics = db.rawDailyMetricDao().getAll(),
            rawSamples = db.rawSampleDao().getAll(),
            summaryDailyMetrics = db.summaryDailyMetricDao().getAll(),
            exerciseSessions = db.exerciseSessionDao().getAll(),
            exerciseHrSamples = db.exerciseHrSampleDao().getAll(),
            exerciseLaps = db.exerciseLapDao().getAll(),
            exerciseRoutePoints = db.exerciseRoutePointDao().getAll(),
            sleepSessions = db.sleepSessionDao().getAll(),
            syncState = db.syncStateDao().getAll(),
            goals = db.goalDao().getAll(),
        )
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        val jsonStr = json.encodeToString(com.pulse.data.cloud.backup.BackupPayload.serializer(), payload)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "pulse_backup_${clock.now().toEpochMilliseconds()}.json")
        FileWriter(file).use { it.write(jsonStr) }
        return file.absolutePath
    }

    override suspend fun debugBuildInfo(): DebugBuildInfo = DebugBuildInfo(
        appVersion = "0.1.0-debug",
        gitSha = "dev",
        deviceId = android.os.Build.MODEL,
        healthConnectSdkVersion = "1.1.0",
    )

    private fun LocalDate.atStartMs(): Long {
        val zone = TimeZone.currentSystemDefault()
        return kotlinx.datetime.LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(zone).toEpochMilliseconds()
    }
}
