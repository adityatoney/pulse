package com.pulse.data.repository

import android.content.Context
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.local.PulseDatabase
import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
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
    private val featureFlags: FeatureFlagRepository,
    private val clock: Clock,
) : DebugRepository {

    override suspend fun seedFakeData(days: Int, seed: Long): Int {
        val rng = Random(seed)
        val tz = TimeZone.currentSystemDefault()
        val today = clock.now().toLocalDateTime(tz).date
        val rows = mutableListOf<DailyAggregateEntity>()
        val nowMs = clock.now().toEpochMilliseconds()

        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
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
            rows += DailyAggregateEntity(
                date = date.toString(),
                metric = MetricType.Steps.name,
                total = steps.toDouble(),
                goal = 10_000.0,
                sampleCount = 1,
                computedAtMs = nowMs,
                dirty = true,
            )
            rows += DailyAggregateEntity(
                date = date.toString(),
                metric = MetricType.Distance.name,
                total = distanceMi,
                goal = 5.0,
                sampleCount = 1,
                computedAtMs = nowMs,
                dirty = true,
            )
            rows += DailyAggregateEntity(
                date = date.toString(),
                metric = MetricType.ActiveCalories.name,
                total = kcal.toDouble(),
                goal = 2_500.0,
                sampleCount = 1,
                computedAtMs = nowMs,
                dirty = true,
            )
            rows += DailyAggregateEntity(
                date = date.toString(),
                metric = MetricType.ZoneMinutes.name,
                total = zMin.toDouble(),
                goal = 22.0,
                sampleCount = 1,
                computedAtMs = nowMs,
                dirty = true,
            )
        }
        db.dailyAggregateDao().upsert(rows)

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

        // Seed body metrics (Weight, BodyFat, SpO2, HRV, RestingHeartRate, VO2Max, SkinTemperature)
        val bodyRows = mutableListOf<DailyAggregateEntity>()
        val baseWeight = 72.0 + rng.nextDouble(-5.0, 5.0)
        for (i in 0 until days) {
            val date = today.minus(DatePeriod(days = i))
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.RestingHeartRate.name,
                total = rng.nextInt(58, 72).toDouble(), goal = 0.0,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.Weight.name,
                total = baseWeight + rng.nextDouble(-0.5, 0.5),
                goal = 70.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.BodyFat.name,
                total = 18.0 + rng.nextDouble(-2.0, 2.0),
                goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.SpO2.name,
                total = rng.nextInt(95, 100).toDouble(), goal = 0.0,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.HRV.name,
                total = rng.nextInt(25, 65).toDouble(), goal = 0.0,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
            bodyRows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.VO2Max.name,
                total = 38.0 + rng.nextDouble(-3.0, 3.0),
                goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }
        db.dailyAggregateDao().upsert(bodyRows)

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

        return rows.size + exerciseRows.size + bodyRows.size + sleepRows.size
    }

    override suspend fun seedRealisticWeek(): Int {
        val tz = TimeZone.currentSystemDefault()
        val today = clock.now().toLocalDateTime(tz).date
        val nowMs = clock.now().toEpochMilliseconds()
        // Fixtures chosen to match the attached Google Fit week-view screenshot:
        // avg ~1.78 mi/day, Wed spikes highest, Sat/Sun no data.
        val stepsByOffset = listOf(3_500, 2_800, 5_200, 4_100, 1_900, 0, 0)
        val rows = mutableListOf<DailyAggregateEntity>()
        stepsByOffset.forEachIndexed { idx, steps ->
            val d = today.minus(DatePeriod(days = idx))
            rows += DailyAggregateEntity(
                date = d.toString(),
                metric = MetricType.Steps.name,
                total = steps.toDouble(),
                goal = 10_000.0,
                sampleCount = 1,
                computedAtMs = nowMs,
            )
            rows += DailyAggregateEntity(
                date = d.toString(),
                metric = MetricType.Distance.name,
                total = steps / 2000.0,
                goal = 5.0,
                sampleCount = 1,
                computedAtMs = nowMs,
            )
        }
        db.dailyAggregateDao().upsert(rows)

        // Seed realistic exercise sessions (matching screenshot pattern)
        val exerciseRows = mutableListOf<ExerciseSessionEntity>()
        // Wed (offset 2): Treadmill run
        val wed = today.minus(DatePeriod(days = 2))
        val wedStartMs = wed.atStartMs() + 7 * 3_600_000L // 7 AM
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$wed-0",
            type = "Treadmill run",
            startUtcMs = wedStartMs,
            endUtcMs = wedStartMs + 35 * 60_000L, // 35 min
            distanceMeters = 5200.0,
            calories = 320.0,
            avgHr = 145,
            maxHr = 168,
            sourceJson = null,
            dirty = true,
        )
        // Thu (offset 3): Outdoor walk
        val thu = today.minus(DatePeriod(days = 3))
        val thuStartMs = thu.atStartMs() + 8 * 3_600_000L // 8 AM
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$thu-0",
            type = "Outdoor walk",
            startUtcMs = thuStartMs,
            endUtcMs = thuStartMs + 45 * 60_000L, // 45 min
            distanceMeters = 3800.0,
            calories = 180.0,
            avgHr = 115,
            maxHr = 132,
            sourceJson = null,
            dirty = true,
        )
        // Today (offset 0): Running
        val todayStartMs = today.atStartMs() + 6 * 3_600_000L + 30 * 60_000L // 6:30 AM
        exerciseRows += ExerciseSessionEntity(
            id = "realistic-$today-0",
            type = "Running",
            startUtcMs = todayStartMs,
            endUtcMs = todayStartMs + 28 * 60_000L, // 28 min
            distanceMeters = 4500.0,
            calories = 290.0,
            avgHr = 152,
            maxHr = 175,
            sourceJson = null,
            dirty = true,
        )
        db.exerciseSessionDao().upsert(exerciseRows)

        return rows.size + exerciseRows.size
    }

    override suspend fun clearLocalCache(hard: Boolean) {
        db.dailyAggregateDao().clear()
        db.exerciseSessionDao().clear()
        db.sleepSessionDao().clear()
        db.healthSampleDao().clear()
        if (hard) {
            db.syncStateDao().clear()
        }
    }

    override suspend fun exportAsCsv(range: DateRange): String {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "export_${clock.now().toEpochMilliseconds()}.csv")
        FileWriter(file).use { w ->
            w.appendLine("date,metric,total,goal,sampleCount,computedAtMs")
            // Real impl iterates the DB; we emit a header for now.
        }
        return file.absolutePath
    }

    override suspend fun dumpRecords(date: LocalDate): List<HealthMetric> {
        val startMs = date.atStartMs()
        val endMs = startMs + 24 * 60 * 60 * 1000L
        return db.healthSampleDao().dump(startMs, endMs).map {
            HealthMetric(
                id = it.id,
                type = runCatching { MetricType.valueOf(it.type) }.getOrDefault(MetricType.Steps),
                value = it.value,
                unit = MeasurementUnit.Count,
                start = kotlinx.datetime.Instant.fromEpochMilliseconds(it.startUtcMs),
                end = kotlinx.datetime.Instant.fromEpochMilliseconds(it.endUtcMs),
                source = DataSource.HealthConnect,
            )
        }
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

    override suspend fun dataStats(): DataStats = DataStats(
        minStepDate = db.dailyAggregateDao().minStepDate(),
        maxStepDate = db.dailyAggregateDao().maxStepDate(),
        totalStepDays = db.dailyAggregateDao().stepDayCount(),
        totalExerciseSessions = db.exerciseSessionDao().totalCount(),
    )

    override suspend fun pendingQueueSize(): Int = db.dailyAggregateDao().dirtyCount()

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
