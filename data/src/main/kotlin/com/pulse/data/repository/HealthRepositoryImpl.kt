package com.pulse.data.repository

import com.pulse.data.health.HealthConnectDataSource
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.HealthSampleDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.mapper.toDomain
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.domain.model.Aggregation
import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.DataSource
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.model.HealthMetric
import com.pulse.domain.model.MeasurementUnit
import com.pulse.domain.model.MetricSeries
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.MetricValue
import com.pulse.domain.model.RecoveryBlock
import com.pulse.domain.model.SeriesPoint
import com.pulse.domain.model.SleepSummary
import com.pulse.domain.model.TodayMetrics
import com.pulse.domain.model.TodaySummary
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import android.util.Log
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDate as JavaLocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val DEFAULT_STEP_GOAL = 10_000.0
private const val DEFAULT_DISTANCE_GOAL_MI = 5.0
private const val DEFAULT_CALORIE_GOAL = 2_500.0
private const val DEFAULT_ZONE_MIN_GOAL = 22.0
private const val METERS_PER_MILE = 1_609.34

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val hc: HealthConnectDataSource,
    private val cloudApi: com.pulse.data.cloud.GoogleHealthRemoteDataSource,
    private val aggregateDao: DailyAggregateDao,
    private val exerciseDao: ExerciseSessionDao,
    private val hrSampleDao: com.pulse.data.local.dao.ExerciseHrSampleDao,
    private val lapDao: com.pulse.data.local.dao.ExerciseLapDao,
    private val routePointDao: com.pulse.data.local.dao.ExerciseRoutePointDao,
    private val sleepDao: SleepSessionDao,
    private val sampleDao: HealthSampleDao,
    private val goalDao: GoalDao,
    private val clock: Clock,
) : HealthRepository {

    override fun observeTodaySummary(date: LocalDate): Flow<TodaySummary> {
        val key = date.toString()
        val startMs = date.atStartOfDayMillis()
        val endMs = startMs + 24 * 60 * 60 * 1000L
        val goalsFlow = goalDao.observeAll().map { rows ->
            rows.associate { it.metric to it.target }
        }
        val dataFlow = combine(
            aggregateDao.observe(key, MetricType.Steps.name),
            exerciseDao.sumDistanceMeters(startMs, endMs),
            exerciseDao.sumCalories(startMs, endMs),
            aggregateDao.observe(key, MetricType.ZoneMinutes.name),
            observeSleep(date),
        ) { steps, distMeters, cals, zmin, sleep ->
            DataTuple(steps, distMeters, cals, zmin, sleep)
        }
        return combine(goalsFlow, dataFlow) { goals, data ->
            val stepGoal = goals[MetricType.Steps.name] ?: DEFAULT_STEP_GOAL
            val distGoal = goals[MetricType.Distance.name] ?: DEFAULT_DISTANCE_GOAL_MI
            val calGoal = goals[MetricType.ActiveCalories.name] ?: DEFAULT_CALORIE_GOAL
            val zmGoal = goals[MetricType.ZoneMinutes.name] ?: DEFAULT_ZONE_MIN_GOAL
            Log.d("PulseGoals", "date=$key goals=$goals → steps=$stepGoal dist=$distGoal cal=$calGoal zm=$zmGoal")
            TodaySummary(
                today = TodayMetrics(
                    steps = buildInt(data.steps, stepGoal, "steps"),
                    distanceMiles = buildMilesFromMeters(data.distMeters, distGoal),
                    calories = buildIntFromValue(data.cals, calGoal, "cal"),
                    zoneMinutes = buildInt(data.zmin, zmGoal, "Zone Min"),
                ),
                recovery = RecoveryBlock(sleep = data.sleep),
            )
        }
    }

    private data class DataTuple(
        val steps: DailyAggregateEntity?,
        val distMeters: Double,
        val cals: Double,
        val zmin: DailyAggregateEntity?,
        val sleep: SleepSummary?,
    )

    override fun observeDailyAggregate(date: LocalDate, metric: MetricType): Flow<DailyAggregate> =
        aggregateDao.observe(date.toString(), metric.name).map { entity ->
            entity?.toDomain() ?: DailyAggregate(
                date = date,
                metric = metric,
                total = 0.0,
                goal = defaultGoal(metric),
                sampleCount = 0,
                computedAt = clock.now(),
            )
        }

    override fun observeSeries(
        metric: MetricType,
        range: DateRange,
        bucket: Bucket,
    ): Flow<MetricSeries> {
        return when (bucket) {
            Bucket.Hour -> observeHourlySeries(metric, range)
            Bucket.Day -> observeDailySeriesFromAggregates(metric, range)
            Bucket.Week -> observeWeeklySeriesFromAggregates(metric, range)
            Bucket.Month -> observeMonthlySeriesFromAggregates(metric, range)
        }
    }

    private fun observeDailySeriesFromAggregates(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        val zone = TimeZone.currentSystemDefault()
        return aggregateDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString())
            .map { rows ->
                val rowMap = rows.associateBy { it.date }
                // Build a point per day across the full range
                var d = range.start
                val points = mutableListOf<SeriesPoint>()
                while (d <= range.endInclusive) {
                    val row = rowMap[d.toString()]
                    points += SeriesPoint(
                        bucketStart = Instant.fromEpochMilliseconds(d.atStartOfDayMillis()),
                        value = row?.total ?: 0.0,
                        goal = row?.goal ?: defaultGoal(metric),
                    )
                    d = d.plus(kotlinx.datetime.DatePeriod(days = 1))
                }
                MetricSeries(
                    metric = metric,
                    range = range,
                    aggregation = Aggregation.Sum,
                    points = points,
                )
            }
    }

    private fun observeWeeklySeriesFromAggregates(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        return aggregateDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString())
            .map { rows ->
                val rowMap = rows.associateBy { it.date }
                // Build daily data, then group by ISO week (Monday-Sunday)
                val dailyEntries = mutableListOf<Pair<LocalDate, com.pulse.data.local.entity.DailyAggregateEntity?>>()
                var d = range.start
                while (d <= range.endInclusive) {
                    dailyEntries += d to rowMap[d.toString()]
                    d = d.plus(kotlinx.datetime.DatePeriod(days = 1))
                }
                // Group by the Monday of each date's ISO week
                val grouped = dailyEntries.groupBy { (date, _) ->
                    val dow = date.dayOfWeek.ordinal // Monday=0
                    date.minus(kotlinx.datetime.DatePeriod(days = dow))
                }
                val points = grouped.toSortedMap().map { (monday, entries) ->
                    val sum = entries.sumOf { (_, row) -> row?.total ?: 0.0 }
                    val goal = entries.firstNotNullOfOrNull { (_, row) -> row?.goal } ?: defaultGoal(metric)
                    SeriesPoint(
                        bucketStart = Instant.fromEpochMilliseconds(monday.atStartOfDayMillis()),
                        value = sum,
                        goal = goal,
                    )
                }
                MetricSeries(
                    metric = metric,
                    range = range,
                    aggregation = Aggregation.Sum,
                    points = points,
                )
            }
    }

    private fun observeMonthlySeriesFromAggregates(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        return aggregateDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString())
            .map { rows ->
                val rowMap = rows.associateBy { it.date }
                // Build daily data, then group by calendar month
                val dailyEntries = mutableListOf<Pair<LocalDate, com.pulse.data.local.entity.DailyAggregateEntity?>>()
                var d = range.start
                while (d <= range.endInclusive) {
                    dailyEntries += d to rowMap[d.toString()]
                    d = d.plus(kotlinx.datetime.DatePeriod(days = 1))
                }
                // Group by year-month, bucket start = first day of that month
                val grouped = dailyEntries.groupBy { (date, _) ->
                    LocalDate(date.year, date.monthNumber, 1)
                }
                val points = grouped.toSortedMap().map { (firstOfMonth, entries) ->
                    val sum = entries.sumOf { (_, row) -> row?.total ?: 0.0 }
                    val goal = entries.firstNotNullOfOrNull { (_, row) -> row?.goal } ?: defaultGoal(metric)
                    SeriesPoint(
                        bucketStart = Instant.fromEpochMilliseconds(firstOfMonth.atStartOfDayMillis()),
                        value = sum,
                        goal = goal,
                    )
                }
                MetricSeries(
                    metric = metric,
                    range = range,
                    aggregation = Aggregation.Sum,
                    points = points,
                )
            }
    }

    /**
     * For Day view: builds 24 hourly buckets (0-23) using exercise session data.
     * Each bucket shows activity that occurred during that hour.
     */
    private fun observeHourlySeries(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        val fromMs = range.start.atStartOfDayMillis()
        val toMs = fromMs + 24 * 60 * 60 * 1000L
        val zone = TimeZone.currentSystemDefault()
        return exerciseDao.observeRange(fromMs, toMs).map { sessions ->
            val hourly = DoubleArray(24)
            for (s in sessions) {
                val hour = Instant.fromEpochMilliseconds(s.startUtcMs)
                    .toLocalDateTime(zone).hour
                hourly[hour] += when (metric) {
                    MetricType.Distance -> (s.distanceMeters ?: 0.0) / METERS_PER_MILE
                    MetricType.Calories, MetricType.ActiveCalories -> s.calories ?: 0.0
                    MetricType.Steps -> s.distanceMeters?.let { it / 0.762 } ?: 0.0 // ~steps from distance
                    else -> s.calories ?: 0.0
                }
            }
            val points = hourly.mapIndexed { h, value ->
                SeriesPoint(
                    bucketStart = Instant.fromEpochMilliseconds(fromMs + h * 3_600_000L),
                    value = value,
                    goal = null,
                )
            }
            MetricSeries(
                metric = metric,
                range = range,
                aggregation = Aggregation.Sum,
                points = points,
            )
        }
    }

    override fun observeExerciseSessions(range: DateRange): Flow<List<ExerciseSession>> {
        val from = range.start.atStartOfDayMillis()
        val to = range.endInclusive.atStartOfDayMillis() + 24 * 60 * 60 * 1000L
        return exerciseDao.observeRange(from, to).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getExerciseDetail(sessionId: String): com.pulse.domain.model.ExerciseDetail? {
        val entity = exerciseDao.findById(sessionId) ?: return null
        val session = entity.toDomain()

        // Load HR samples from local cache; if empty, try fetching from HC
        var hrEntities = hrSampleDao.forSession(sessionId)
        if (hrEntities.isEmpty() && hc.isAvailable()) {
            val start = java.time.Instant.ofEpochMilli(entity.startUtcMs)
            val end = java.time.Instant.ofEpochMilli(entity.endUtcMs)
            val samples = hc.readHeartRateSamplesForRange(start, end)
            if (samples.isNotEmpty()) {
                val sampleEntities = samples.map { (instant, bpm) ->
                    com.pulse.data.local.entity.ExerciseHrSampleEntity(
                        sessionId = sessionId,
                        timestampMs = instant.toEpochMilli(),
                        bpm = bpm,
                    )
                }
                hrSampleDao.deleteForSession(sessionId)
                hrSampleDao.insertAll(sampleEntities)
                hrEntities = sampleEntities
            }
        }

        val lapEntities = lapDao.forSession(sessionId)

        // Load route points from local cache; if empty, try fetching from HC
        var routeEntities = routePointDao.forSession(sessionId)
        var routeConsentRequired = false
        android.util.Log.d("Health", "Route: cached=${routeEntities.size} for session=$sessionId")
        if (routeEntities.isEmpty() && hc.isAvailable()) {
            val rtStart = java.time.Instant.ofEpochMilli(entity.startUtcMs)
            val rtEnd = java.time.Instant.ofEpochMilli(entity.endUtcMs)
            android.util.Log.d("Health", "Route: fetching from HC, range=$rtStart..$rtEnd")
            when (val routeResult = hc.readExerciseRoute(rtStart, rtEnd, sessionId)) {
                is com.pulse.data.health.HealthConnectDataSource.RouteResult.Success -> {
                    android.util.Log.d("Health", "Route: HC returned ${routeResult.locations.size} points")
                    val routePointEntities = routeResult.locations.map { loc ->
                        com.pulse.data.local.entity.ExerciseRoutePointEntity(
                            sessionId = sessionId,
                            timestampMs = loc.timestampMs,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude,
                        )
                    }
                    if (routePointEntities.isNotEmpty()) {
                        routePointDao.deleteForSession(sessionId)
                        routePointDao.insertAll(routePointEntities)
                        routeEntities = routePointEntities
                    }
                }
                is com.pulse.data.health.HealthConnectDataSource.RouteResult.ConsentRequired -> {
                    android.util.Log.w("Health", "Route: CONSENT REQUIRED for session $sessionId")
                    routeConsentRequired = true
                }
                is com.pulse.data.health.HealthConnectDataSource.RouteResult.NoData -> {
                    android.util.Log.d("Health", "Route: NO DATA from HC for session $sessionId")
                }
            }
        }

        // Compute zone minutes on demand if missing but HR samples are available
        var zoneMinutes = entity.zoneMinutes
        if (zoneMinutes == null && hrEntities.isNotEmpty()) {
            val zone = ZoneId.systemDefault()
            val sessionDay = java.time.Instant.ofEpochMilli(entity.startUtcMs)
                .atZone(zone).toLocalDate()
            val restingHr = hc.restingHeartRate(sessionDay, zone) ?: 65
            val zmSamples = hrEntities.map { sample ->
                ZoneMinuteCalculator.HrSample(
                    at = Instant.fromEpochMilliseconds(sample.timestampMs),
                    bpm = sample.bpm,
                )
            }
            val breakdown = ZoneMinuteCalculator.calculate(zmSamples, restingHr, age = 45)
            zoneMinutes = breakdown.total
            // Persist so future loads don't recompute
            if (zoneMinutes > 0) {
                exerciseDao.updateZoneMinutes(sessionId, zoneMinutes)
            }
        }

        return com.pulse.domain.model.ExerciseDetail(
            session = session,
            steps = entity.steps,
            zoneMinutes = zoneMinutes,
            avgPaceSecondsPerMile = entity.avgPaceSecondsPerMile,
            elevationGainMeters = entity.elevationGainMeters,
            hrSamples = hrEntities.map { com.pulse.domain.model.HrSample(it.timestampMs, it.bpm) },
            laps = lapEntities.map {
                com.pulse.domain.model.ExerciseLap(
                    lapNumber = it.lapNumber,
                    distanceMeters = it.distanceMeters,
                    durationMs = it.durationMs,
                    paceSecondsPerMile = it.paceSecondsPerMile,
                )
            },
            route = routeEntities.map {
                com.pulse.domain.model.RoutePoint(
                    timestampMs = it.timestampMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                )
            },
            routeConsentRequired = routeConsentRequired,
        )
    }

    override suspend fun saveRoutePoints(sessionId: String, points: List<com.pulse.domain.model.RoutePoint>) {
        val entities = points.map {
            com.pulse.data.local.entity.ExerciseRoutePointEntity(
                sessionId = sessionId,
                timestampMs = it.timestampMs,
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
            )
        }
        routePointDao.deleteForSession(sessionId)
        routePointDao.insertAll(entities)
    }

    override fun observeSleep(date: LocalDate): Flow<SleepSummary?> {
        // Sleep for "date" means the session ending on that morning.
        // Use noon-to-noon window: noon of previous day to noon of target day.
        val noonPrevMs = date.atStartOfDayMillis() - 12 * 60 * 60 * 1000L
        val noonMs = date.atStartOfDayMillis() + 12 * 60 * 60 * 1000L
        return sleepDao.observeLatestForDate(noonPrevMs, noonMs).map { entity ->
            entity?.let {
                SleepSummary(
                    start = Instant.fromEpochMilliseconds(it.startUtcMs),
                    end = Instant.fromEpochMilliseconds(it.endUtcMs),
                    totalMinutes = it.totalMinutes,
                    deepMinutes = it.deepMinutes,
                    remMinutes = it.remMinutes,
                    lightMinutes = it.lightMinutes,
                    awakeMinutes = it.awakeMinutes,
                )
            }
        }
    }

    override suspend fun refreshFromHealthConnect(range: DateRange): Result<Unit> = runCatching {
        if (!hc.isAvailable()) return@runCatching
        val zone = ZoneId.systemDefault()
        val javaStart = JavaLocalDate.of(range.start.year, range.start.monthNumber, range.start.dayOfMonth)
        val javaEnd = JavaLocalDate.of(range.endInclusive.year, range.endInclusive.monthNumber, range.endInclusive.dayOfMonth)

        val steps = hc.stepsByDay(javaStart, javaEnd, zone)
        val goals = buildGoalMap()
        val nowMs = clock.now().toEpochMilliseconds()

        val rows = mutableListOf<DailyAggregateEntity>()
        steps.forEach { (day, count) ->
            rows += DailyAggregateEntity(
                date = day.toString(),
                metric = MetricType.Steps.name,
                total = count.toDouble(),
                goal = goals[MetricType.Steps],
                sampleCount = 1,
                computedAtMs = nowMs,
            )
        }

        // Fetch daily distance and calories (try both active and total — Fitbit syncs total)
        val distByDay = hc.distanceByDay(javaStart, javaEnd, zone)
        val activeCalByDay = hc.activeCaloriesByDay(javaStart, javaEnd, zone)
        val totalCalByDay = hc.totalCaloriesByDay(javaStart, javaEnd, zone)

        // Store daily distance aggregates
        distByDay.forEach { (day, meters) ->
            rows += DailyAggregateEntity(
                date = day.toString(),
                metric = MetricType.Distance.name,
                total = meters / METERS_PER_MILE,
                goal = goals[MetricType.Distance],
                sampleCount = 1,
                computedAtMs = nowMs,
            )
        }

        // Store daily calorie aggregates (prefer active calories; fall back to total)
        val allCalDays = (activeCalByDay.keys + totalCalByDay.keys).toSet()
        for (day in allCalDays) {
            val active = activeCalByDay[day] ?: 0.0
            val total = totalCalByDay[day] ?: 0.0
            val cal = if (active > 0) active else total
            if (cal > 0) {
                rows += DailyAggregateEntity(
                    date = day.toString(),
                    metric = MetricType.ActiveCalories.name,
                    total = cal,
                    goal = goals[MetricType.ActiveCalories],
                    sampleCount = 1,
                    computedAtMs = nowMs,
                )
            }
        }

        // Zone minutes + vitals: per-day reads are expensive (HC quota).
        // Only do per-day reads for the last 14 days to avoid quota exhaustion.
        val recentCutoff = JavaLocalDate.now().minusDays(14)
        var day = maxOf(javaStart, recentCutoff)
        Log.d("HealthSync", "Per-day loop: ${day} to ${javaEnd} (skipping before $recentCutoff)")
        while (!day.isAfter(javaEnd)) {
            val hrSamples = hc.readHeartRateSamples(day, zone)
            if (hrSamples.isNotEmpty()) {
                val restingHr = hc.restingHeartRate(day, zone) ?: 65
                val zmSamples = hrSamples.map { (instant, bpm) ->
                    ZoneMinuteCalculator.HrSample(
                        at = Instant.fromEpochMilliseconds(instant.toEpochMilli()),
                        bpm = bpm,
                    )
                }
                val breakdown = ZoneMinuteCalculator.calculate(zmSamples, restingHr, age = 45)
                rows += DailyAggregateEntity(
                    date = day.toString(),
                    metric = MetricType.ZoneMinutes.name,
                    total = breakdown.total.toDouble(),
                    goal = goals[MetricType.ZoneMinutes],
                    sampleCount = hrSamples.size,
                    computedAtMs = nowMs,
                )
            }

            // Read body & vitals metrics for expanded data coverage
            hc.readWeight(day, zone)?.let { kg ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.Weight.name, total = kg, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }
            hc.readBodyFat(day, zone)?.let { pct ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.BodyFat.name, total = pct, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }
            hc.readSpO2(day, zone)?.let { pct ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.SpO2.name, total = pct, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }
            hc.readHrv(day, zone)?.let { ms ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.HRV.name, total = ms, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }
            hc.readVo2Max(day, zone)?.let { v ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.VO2Max.name, total = v, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }
            hc.readSkinTemperature(day, zone)?.let { delta ->
                rows += DailyAggregateEntity(date = day.toString(), metric = MetricType.SkinTemperature.name, total = delta, goal = null, sampleCount = 1, computedAtMs = nowMs)
            }

            day = day.plusDays(1)
        }

        // Sleep sessions (noon-to-noon window captures overnight sleep)
        val sleepEntities = mutableListOf<SleepSessionEntity>()
        var sleepDay = javaStart
        while (!sleepDay.isAfter(javaEnd)) {
            val sleepRecords = hc.readSleep(sleepDay, zone)
            for (rec in sleepRecords) {
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
                sleepEntities += SleepSessionEntity(
                    id = rec.metadata.id,
                    startUtcMs = startMs,
                    endUtcMs = endMs,
                    totalMinutes = totalMin,
                    deepMinutes = deep,
                    remMinutes = rem,
                    lightMinutes = light,
                    awakeMinutes = awake,
                    sourceJson = rec.metadata.dataOrigin.packageName,
                    dirty = true,
                )
            }
            sleepDay = sleepDay.plusDays(1)
        }
        if (sleepEntities.isNotEmpty()) sleepDao.upsert(sleepEntities)

        // Exercise sessions with per-session distance/calories/steps/zone-minutes
        val exerciseRecords = hc.readExerciseSessions(javaStart, javaEnd, zone)
        if (exerciseRecords.isNotEmpty()) {
            val restingHrCache = mutableMapOf<JavaLocalDate, Int>()
            val exerciseEntities = exerciseRecords.map { rec ->
                val typeName = exerciseTypeName(rec.exerciseType)
                val agg = hc.aggregateForTimeRange(rec.startTime, rec.endTime)
                val durationMs = rec.endTime.toEpochMilli() - rec.startTime.toEpochMilli()
                val avgPace = if (agg.meters > 0) {
                    ((durationMs / 1000.0) / (agg.meters / METERS_PER_MILE)).toInt()
                } else null

                // Per-session HR for zone minutes + avg/max HR
                val sessionHr = hc.readHeartRateSamplesForRange(rec.startTime, rec.endTime)
                android.util.Log.d("Health", "Exercise ${rec.title ?: typeName}: HR samples=${sessionHr.size}, " +
                    "start=${rec.startTime}, end=${rec.endTime}, dur=${durationMs/60_000}min")
                if (sessionHr.isNotEmpty()) {
                    val bpms = sessionHr.map { it.second }
                    android.util.Log.d("Health", "  HR range: ${bpms.min()}-${bpms.max()} avg=${bpms.average().toInt()}")
                }
                val avgHr = if (sessionHr.isNotEmpty()) sessionHr.map { it.second }.average().toInt() else null
                val maxHr = if (sessionHr.isNotEmpty()) sessionHr.maxOf { it.second } else null

                // Compute per-session zone minutes from HR data
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
                    val breakdown = ZoneMinuteCalculator.calculate(zmSamples, restingHr, age = 45)
                    android.util.Log.d("Health", "  Zone breakdown: z1=${breakdown.zone1} z2=${breakdown.zone2} z3=${breakdown.zone3} z4=${breakdown.zone4} z5=${breakdown.zone5} total=${breakdown.total}, restingHr=$restingHr, maxHr=175")
                    breakdown.total
                } else null

                ExerciseSessionEntity(
                    id = rec.metadata.id,
                    type = rec.title ?: typeName,
                    startUtcMs = rec.startTime.toEpochMilli(),
                    endUtcMs = rec.endTime.toEpochMilli(),
                    distanceMeters = agg.meters,
                    calories = agg.kcal,
                    steps = agg.steps.toInt().takeIf { it > 0 },
                    avgHr = avgHr,
                    maxHr = maxHr,
                    avgPaceSecondsPerMile = avgPace,
                    elevationGainMeters = null,
                    zoneMinutes = sessionZoneMin,
                    sourceJson = rec.metadata.dataOrigin.packageName,
                    dirty = true,
                )
            }
            exerciseDao.upsert(exerciseEntities)

            // Fallback: if daily zone minutes weren't computed from all-day HR,
            // sum per-exercise zone minutes and store as daily aggregate
            val zmByDay = exerciseEntities
                .filter { it.zoneMinutes != null }
                .groupBy { Instant.fromEpochMilliseconds(it.startUtcMs)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }
            for ((dateStr, sessions) in zmByDay) {
                val existing = rows.find { it.date == dateStr && it.metric == MetricType.ZoneMinutes.name }
                if (existing == null) {
                    val totalZm = sessions.sumOf { it.zoneMinutes ?: 0 }
                    if (totalZm > 0) {
                        rows += DailyAggregateEntity(
                            date = dateStr,
                            metric = MetricType.ZoneMinutes.name,
                            total = totalZm.toDouble(),
                            goal = goals[MetricType.ZoneMinutes],
                            sampleCount = sessions.size,
                            computedAtMs = nowMs,
                        )
                    }
                }
            }
        }
        // Upsert any additional rows accumulated from exercise zone min fallback
        if (rows.isNotEmpty()) aggregateDao.upsert(rows)
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

    override suspend fun refreshFromCloudApi(range: DateRange): Result<Unit> = runCatching {
        if (!cloudApi.isAvailable) return@runCatching

        val nowMs = clock.now().toEpochMilliseconds()
        val goals = buildGoalMap()
        val rows = mutableListOf<DailyAggregateEntity>()

        // Steps — overwrites HC data with reconciled values
        cloudApi.reconcileSteps(range).forEach { (date, steps) ->
            rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.Steps.name,
                total = steps.toDouble(), goal = goals[MetricType.Steps] ?: DEFAULT_STEP_GOAL,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        // Distance — REST returns millimeters, convert to miles
        cloudApi.reconcileDistance(range).forEach { (date, mm) ->
            val miles = mm / 1000.0 / METERS_PER_MILE
            rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.Distance.name,
                total = miles, goal = goals[MetricType.Distance] ?: DEFAULT_DISTANCE_GOAL_MI,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        // Calories
        cloudApi.reconcileCalories(range).forEach { (date, cals) ->
            rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.ActiveCalories.name,
                total = cals, goal = goals[MetricType.ActiveCalories] ?: DEFAULT_CALORIE_GOAL,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        // Zone Minutes
        cloudApi.reconcileZoneMinutes(range).forEach { (date, mins) ->
            rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.ZoneMinutes.name,
                total = mins.toDouble(), goal = goals[MetricType.ZoneMinutes] ?: DEFAULT_ZONE_MIN_GOAL,
                sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        // Body metrics
        cloudApi.reconcileWeight(range).forEach { (date, kg) ->
            if (kg > 0) rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.Weight.name,
                total = kg, goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        cloudApi.reconcileHrv(range).forEach { (date, ms) ->
            if (ms > 0) rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.HRV.name,
                total = ms, goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        cloudApi.reconcileSpO2(range).forEach { (date, pct) ->
            if (pct > 0) rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.SpO2.name,
                total = pct, goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        cloudApi.reconcileRestingHr(range).forEach { (date, bpm) ->
            if (bpm > 0) rows += DailyAggregateEntity(
                date = date.toString(), metric = MetricType.RestingHeartRate.name,
                total = bpm, goal = 0.0, sampleCount = 1, computedAtMs = nowMs, dirty = true,
            )
        }

        if (rows.isNotEmpty()) {
            aggregateDao.upsert(rows)
        }
    }

    override suspend fun dumpRawRecords(date: LocalDate): List<HealthMetric> {
        // For now just return whatever's in the health_samples table for the day.
        val startMs = date.atStartOfDayMillis()
        val endMs = startMs + 24 * 60 * 60 * 1000L
        return sampleDao.dump(startMs, endMs).map {
            HealthMetric(
                id = it.id,
                type = runCatching { MetricType.valueOf(it.type) }.getOrDefault(MetricType.Steps),
                value = it.value,
                unit = unitOf(it.unit),
                start = Instant.fromEpochMilliseconds(it.startUtcMs),
                end = Instant.fromEpochMilliseconds(it.endUtcMs),
                source = runCatching { DataSource.valueOf(it.source) }.getOrDefault(DataSource.HealthConnect),
            )
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

    private fun defaultGoal(metric: MetricType): Double? = when (metric) {
        MetricType.Steps -> DEFAULT_STEP_GOAL
        MetricType.Distance -> DEFAULT_DISTANCE_GOAL_MI
        MetricType.ActiveCalories, MetricType.Calories -> DEFAULT_CALORIE_GOAL
        MetricType.ZoneMinutes -> DEFAULT_ZONE_MIN_GOAL
        else -> null
    }

    private fun buildInt(
        entity: com.pulse.data.local.entity.DailyAggregateEntity?,
        defaultGoal: Double,
        unitLabel: String,
    ): MetricValue<Int> {
        val total = entity?.total ?: 0.0
        val progress = if (defaultGoal > 0) (total / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = total.roundToInt(),
            goal = defaultGoal.roundToInt(),
            progress = progress,
            unitLabel = unitLabel,
        )
    }

    private fun buildMiles(
        entity: com.pulse.data.local.entity.DailyAggregateEntity?,
        defaultGoal: Double,
    ): MetricValue<Double> {
        val total = entity?.total ?: 0.0
        val progress = if (defaultGoal > 0) (total / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = total,
            goal = defaultGoal,
            progress = progress,
            unitLabel = "mi",
        )
    }

    private fun buildMilesFromMeters(
        meters: Double,
        defaultGoal: Double,
    ): MetricValue<Double> {
        val miles = meters / METERS_PER_MILE
        val progress = if (defaultGoal > 0) (miles / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = miles,
            goal = defaultGoal,
            progress = progress,
            unitLabel = "mi",
        )
    }

    private fun buildIntFromValue(
        value: Double,
        defaultGoal: Double,
        unitLabel: String,
    ): MetricValue<Int> {
        val progress = if (defaultGoal > 0) (value / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = value.roundToInt(),
            goal = defaultGoal.roundToInt(),
            progress = progress,
            unitLabel = unitLabel,
        )
    }

    private fun unitOf(token: String): MeasurementUnit = when (token.lowercase()) {
        "count" -> MeasurementUnit.Count
        "meters", "m" -> MeasurementUnit.Meters
        "miles", "mi" -> MeasurementUnit.Miles
        "kilocalories", "kcal" -> MeasurementUnit.Kilocalories
        "bpm" -> MeasurementUnit.Bpm
        "minutes", "min" -> MeasurementUnit.Minutes
        "floors" -> MeasurementUnit.Floors
        else -> MeasurementUnit.Count
    }

    private fun LocalDate.atStartOfDayMillis(): Long {
        val zone = TimeZone.currentSystemDefault()
        return kotlinx.datetime.LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(zone).toEpochMilliseconds()
    }
}
