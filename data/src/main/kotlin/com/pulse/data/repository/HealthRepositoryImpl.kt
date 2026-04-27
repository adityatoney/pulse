package com.pulse.data.repository

import android.util.Log
import com.pulse.data.compute.SummaryComputeEngine
import com.pulse.data.datastore.MetricDisplayPrefs
import com.pulse.data.datastore.PreferencesRepository
import com.pulse.data.health.HealthConnectDataSource
import com.pulse.data.sync.EnhancedHealthSyncManager
import com.pulse.data.local.dao.ComputeQueueDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.RawDailyMetricDao
import com.pulse.data.local.dao.SummaryDailyMetricDao
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.RawDailyMetricEntity
import com.pulse.data.local.entity.SummaryDailyMetricEntity
import com.pulse.data.mapper.toDomain
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
import com.pulse.domain.model.DailyHrRange
import com.pulse.domain.model.HrSample
import com.pulse.domain.model.RecoveryBlock
import com.pulse.domain.model.SeriesPoint
import com.pulse.domain.model.SleepSummary
import com.pulse.domain.model.TodayMetrics
import com.pulse.domain.model.TodaySummary
import com.pulse.domain.repository.Bucket
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
    private val syncManager: EnhancedHealthSyncManager,
    private val computeEngine: SummaryComputeEngine,
    private val summaryDao: SummaryDailyMetricDao,
    private val rawDailyDao: RawDailyMetricDao,
    private val computeQueueDao: ComputeQueueDao,
    private val exerciseDao: ExerciseSessionDao,
    private val hrSampleDao: com.pulse.data.local.dao.ExerciseHrSampleDao,
    private val lapDao: com.pulse.data.local.dao.ExerciseLapDao,
    private val routePointDao: com.pulse.data.local.dao.ExerciseRoutePointDao,
    private val sleepDao: com.pulse.data.local.dao.SleepSessionDao,
    private val rawSampleDao: com.pulse.data.local.dao.RawSampleDao,
    private val goalDao: GoalDao,
    private val prefsRepo: PreferencesRepository,
    private val clock: Clock,
) : HealthRepository {

    /** Pick the effective total based on current pref for distance/calories metrics. */
    private fun SummaryDailyMetricEntity.effectiveTotal(
        metric: MetricType,
        prefs: MetricDisplayPrefs,
    ): Double = when {
        metric == MetricType.Distance && prefs.activityOnlyDistance -> activityTotal ?: 0.0
        metric == MetricType.ActiveCalories && prefs.activityOnlyCalories -> activityTotal ?: 0.0
        else -> total
    }

    override fun observeTodaySummary(date: LocalDate): Flow<TodaySummary> {
        val key = date.toString()
        val goalsFlow = goalDao.observeAll().map { rows ->
            rows.associate { it.metric to it.target }
        }
        val dataFlow = combine(
            summaryDao.observe(key, MetricType.Steps.name),
            summaryDao.observe(key, MetricType.Distance.name),
            summaryDao.observe(key, MetricType.ActiveCalories.name),
            summaryDao.observe(key, MetricType.ZoneMinutes.name),
            observeSleep(date),
        ) { steps, dist, cals, zmin, sleep ->
            DataTuple(steps = steps, dist = dist, cals = cals, zmin = zmin, sleep = sleep)
        }
        return combine(goalsFlow, dataFlow, prefsRepo.observeMetricDisplay()) { goals, data, prefs ->
            val stepGoal = goals[MetricType.Steps.name] ?: DEFAULT_STEP_GOAL
            val distGoal = goals[MetricType.Distance.name] ?: DEFAULT_DISTANCE_GOAL_MI
            val calGoal = goals[MetricType.ActiveCalories.name] ?: DEFAULT_CALORIE_GOAL
            val zmGoal = goals[MetricType.ZoneMinutes.name] ?: DEFAULT_ZONE_MIN_GOAL

            val distTotal = data.dist?.effectiveTotal(MetricType.Distance, prefs) ?: 0.0
            val calTotal = data.cals?.effectiveTotal(MetricType.ActiveCalories, prefs) ?: 0.0

            TodaySummary(
                today = TodayMetrics(
                    steps = buildInt(data.steps?.total ?: 0.0, stepGoal, "steps"),
                    distanceMiles = buildMiles(distTotal, distGoal),
                    calories = buildInt(calTotal, calGoal, "cal"),
                    zoneMinutes = buildInt(data.zmin?.total ?: 0.0, zmGoal, "Zone Min"),
                ),
                recovery = RecoveryBlock(sleep = data.sleep),
            )
        }
    }

    private data class DataTuple(
        val steps: SummaryDailyMetricEntity?,
        val dist: SummaryDailyMetricEntity?,
        val cals: SummaryDailyMetricEntity?,
        val zmin: SummaryDailyMetricEntity?,
        val sleep: SleepSummary?,
    )

    override fun observeDailyAggregate(date: LocalDate, metric: MetricType): Flow<DailyAggregate> =
        combine(
            summaryDao.observe(date.toString(), metric.name),
            prefsRepo.observeMetricDisplay(),
        ) { entity, prefs ->
            if (entity != null) {
                val effective = entity.effectiveTotal(metric, prefs)
                DailyAggregate(
                    date = date,
                    metric = metric,
                    total = effective,
                    goal = entity.goal,
                    sampleCount = entity.sampleCount,
                    computedAt = Instant.fromEpochMilliseconds(entity.computedAtMs),
                )
            } else {
                DailyAggregate(
                    date = date,
                    metric = metric,
                    total = 0.0,
                    goal = defaultGoal(metric),
                    sampleCount = 0,
                    computedAt = clock.now(),
                )
            }
        }

    override fun observeSeries(
        metric: MetricType,
        range: DateRange,
        bucket: Bucket,
    ): Flow<MetricSeries> {
        return when (bucket) {
            Bucket.Hour -> observeHourlySeries(metric, range)
            Bucket.Day -> observeDailySeriesFromSummary(metric, range)
            Bucket.Week -> observeWeeklySeriesFromSummary(metric, range)
            Bucket.Month -> observeMonthlySeriesFromSummary(metric, range)
        }
    }

    private fun observeDailySeriesFromSummary(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        return combine(
            summaryDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString()),
            prefsRepo.observeMetricDisplay(),
        ) { rows, prefs ->
            val rowMap = rows.associateBy { it.date }
            var d = range.start
            val points = mutableListOf<SeriesPoint>()
            while (d <= range.endInclusive) {
                val row = rowMap[d.toString()]
                points += SeriesPoint(
                    bucketStart = Instant.fromEpochMilliseconds(d.atStartOfDayMillis()),
                    value = row?.effectiveTotal(metric, prefs) ?: 0.0,
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

    private fun observeWeeklySeriesFromSummary(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        return combine(
            summaryDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString()),
            prefsRepo.observeMetricDisplay(),
        ) { rows, prefs ->
            val rowMap = rows.associateBy { it.date }
            val dailyEntries = mutableListOf<Pair<LocalDate, SummaryDailyMetricEntity?>>()
            var d = range.start
            while (d <= range.endInclusive) {
                dailyEntries += d to rowMap[d.toString()]
                d = d.plus(kotlinx.datetime.DatePeriod(days = 1))
            }
            val grouped = dailyEntries.groupBy { (date, _) ->
                val dow = date.dayOfWeek.ordinal
                date.minus(kotlinx.datetime.DatePeriod(days = dow))
            }
            val points = grouped.toSortedMap().map { (monday, entries) ->
                val sum = entries.sumOf { (_, row) -> row?.effectiveTotal(metric, prefs) ?: 0.0 }
                val goal = entries.firstNotNullOfOrNull { (_, row) -> row?.goal } ?: defaultGoal(metric)
                SeriesPoint(
                    bucketStart = Instant.fromEpochMilliseconds(monday.atStartOfDayMillis()),
                    value = sum,
                    goal = goal,
                )
            }
            MetricSeries(metric = metric, range = range, aggregation = Aggregation.Sum, points = points)
        }
    }

    private fun observeMonthlySeriesFromSummary(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        return combine(
            summaryDao.observeRange(metric.name, range.start.toString(), range.endInclusive.toString()),
            prefsRepo.observeMetricDisplay(),
        ) { rows, prefs ->
            val rowMap = rows.associateBy { it.date }
            val dailyEntries = mutableListOf<Pair<LocalDate, SummaryDailyMetricEntity?>>()
            var d = range.start
            while (d <= range.endInclusive) {
                dailyEntries += d to rowMap[d.toString()]
                d = d.plus(kotlinx.datetime.DatePeriod(days = 1))
            }
            val grouped = dailyEntries.groupBy { (date, _) ->
                LocalDate(date.year, date.monthNumber, 1)
            }
            val points = grouped.toSortedMap().map { (firstOfMonth, entries) ->
                val sum = entries.sumOf { (_, row) -> row?.effectiveTotal(metric, prefs) ?: 0.0 }
                val goal = entries.firstNotNullOfOrNull { (_, row) -> row?.goal } ?: defaultGoal(metric)
                SeriesPoint(
                    bucketStart = Instant.fromEpochMilliseconds(firstOfMonth.atStartOfDayMillis()),
                    value = sum,
                    goal = goal,
                )
            }
            MetricSeries(metric = metric, range = range, aggregation = Aggregation.Sum, points = points)
        }
    }

    private fun observeHourlySeries(
        metric: MetricType,
        range: DateRange,
    ): Flow<MetricSeries> {
        val fromMs = range.start.atStartOfDayMillis()
        val toMs = fromMs + 24 * 60 * 60 * 1000L
        val zone = TimeZone.currentSystemDefault()
        val dateStr = range.start.toString()

        // For ZoneMinutes, combine exercise sessions with the daily summary total
        // so non-exercise zone minutes (from all-day HR monitoring) are included.
        if (metric == MetricType.ZoneMinutes) {
            return combine(
                exerciseDao.observeRange(fromMs, toMs),
                summaryDao.observe(dateStr, MetricType.ZoneMinutes.name),
            ) { sessions, summaryEntity ->
                val hourly = DoubleArray(24)
                for (s in sessions) {
                    val hour = Instant.fromEpochMilliseconds(s.startUtcMs)
                        .toLocalDateTime(zone).hour
                    hourly[hour] += (s.zoneMinutes ?: 0).toDouble()
                }
                val exerciseTotal = hourly.sum()
                val summaryTotal = summaryEntity?.total ?: exerciseTotal
                val nonExerciseZm = (summaryTotal - exerciseTotal).coerceAtLeast(0.0)
                // Distribute non-exercise zone minutes across hours that had exercise,
                // or if none, add to the latest hour with data from summary
                if (nonExerciseZm > 0) {
                    val activeHours = hourly.indices.filter { hourly[it] > 0 }
                    if (activeHours.isNotEmpty()) {
                        val share = nonExerciseZm / activeHours.size
                        for (h in activeHours) hourly[h] += share
                    } else {
                        // No exercise sessions — put all zone minutes at noon as a placeholder
                        hourly[12] += nonExerciseZm
                    }
                }
                val points = hourly.mapIndexed { h, value ->
                    SeriesPoint(
                        bucketStart = Instant.fromEpochMilliseconds(fromMs + h * 3_600_000L),
                        value = value,
                        goal = null,
                    )
                }
                MetricSeries(metric = metric, range = range, aggregation = Aggregation.Sum, points = points)
            }
        }

        return exerciseDao.observeRange(fromMs, toMs).map { sessions ->
            val hourly = DoubleArray(24)
            for (s in sessions) {
                val hour = Instant.fromEpochMilliseconds(s.startUtcMs)
                    .toLocalDateTime(zone).hour
                hourly[hour] += when (metric) {
                    MetricType.Distance -> (s.distanceMeters ?: 0.0) / METERS_PER_MILE
                    MetricType.Calories, MetricType.ActiveCalories -> s.calories ?: 0.0
                    MetricType.Steps -> s.distanceMeters?.let { it / 0.762 } ?: 0.0
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

        var routeEntities = routePointDao.forSession(sessionId)
        var routeConsentRequired = false
        if (routeEntities.isEmpty() && hc.isAvailable()) {
            val rtStart = java.time.Instant.ofEpochMilli(entity.startUtcMs)
            val rtEnd = java.time.Instant.ofEpochMilli(entity.endUtcMs)
            when (val routeResult = hc.readExerciseRoute(rtStart, rtEnd, sessionId)) {
                is com.pulse.data.health.HealthConnectDataSource.RouteResult.Success -> {
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
                    routeConsentRequired = true
                }
                is com.pulse.data.health.HealthConnectDataSource.RouteResult.NoData -> {}
            }
        }

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

    override suspend fun updateExerciseMetrics(
        sessionId: String,
        calories: Double,
        distanceMeters: Double?,
        steps: Int?,
    ) {
        exerciseDao.updateMetrics(sessionId, calories, distanceMeters, steps)

        // Recompute daily summaries for the exercise's date
        val entity = exerciseDao.findById(sessionId) ?: return
        val dateStr = Instant.fromEpochMilliseconds(entity.startUtcMs)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        val nowMs = clock.now().toEpochMilliseconds()
        val dirtyMetrics = listOf(
            MetricType.Calories, MetricType.ActiveCalories,
            MetricType.Distance,
        )
        computeQueueDao.enqueue(dirtyMetrics.map { m ->
            ComputeQueueEntity(date = dateStr, metric = m.name, enqueuedAtMs = nowMs)
        })
        computeEngine.processQueue()
    }

    override fun observeSleep(date: LocalDate): Flow<SleepSummary?> {
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

    override fun observeSleepRange(range: com.pulse.domain.model.DateRange): Flow<List<SleepSummary>> {
        val fromMs = range.start.atStartOfDayMillis() - 12 * 60 * 60 * 1000L
        val toMs = range.endInclusive.atStartOfDayMillis() + 12 * 60 * 60 * 1000L
        return sleepDao.observeRange(fromMs, toMs).map { entities ->
            entities.map { e ->
                SleepSummary(
                    start = Instant.fromEpochMilliseconds(e.startUtcMs),
                    end = Instant.fromEpochMilliseconds(e.endUtcMs),
                    totalMinutes = e.totalMinutes,
                    deepMinutes = e.deepMinutes,
                    remMinutes = e.remMinutes,
                    lightMinutes = e.lightMinutes,
                    awakeMinutes = e.awakeMinutes,
                )
            }
        }
    }

    override fun observeIntradayHr(date: LocalDate): Flow<List<HrSample>> {
        val startMs = date.atStartOfDayMillis()
        val endMs = startMs + 24 * 60 * 60 * 1000L
        return rawSampleDao.observeRange("HeartRate", startMs, endMs).map { entities ->
            entities.map { HrSample(it.startUtcMs, it.value.toInt()) }
        }
    }

    override fun observeHrDailyRanges(range: com.pulse.domain.model.DateRange): Flow<List<DailyHrRange>> {
        val startMs = range.start.atStartOfDayMillis()
        val endMs = range.endInclusive.atStartOfDayMillis() + 24 * 60 * 60 * 1000L
        val tz = TimeZone.currentSystemDefault()
        return rawSampleDao.observeRange("HeartRate", startMs, endMs).map { entities ->
            entities.groupBy { e ->
                Instant.fromEpochMilliseconds(e.startUtcMs).toLocalDateTime(tz).date
            }.mapNotNull { (date, samples) ->
                if (samples.isEmpty()) return@mapNotNull null
                val bpmValues = samples.map { it.value.toInt() }
                DailyHrRange(
                    date = date,
                    minBpm = bpmValues.min(),
                    maxBpm = bpmValues.max(),
                    avgBpm = bpmValues.average().toInt(),
                    restingBpm = null, // filled below if available
                )
            }.sortedBy { it.date }
        }
    }

    override suspend fun recomputeAggregates(days: Int) {
        computeEngine.recomputeAll(days)
    }

    override suspend fun refreshFromHealthConnect(range: DateRange): Result<Unit> {
        val days = (range.start.daysUntil(range.endInclusive) + 1).coerceAtMost(30)
        return syncManager.syncRecent(days = days, forceFullFetch = true)
    }

    private fun kotlinx.datetime.LocalDate.daysUntil(other: kotlinx.datetime.LocalDate): Int {
        val startEpoch = this.toEpochDays()
        val endEpoch = other.toEpochDays()
        return (endEpoch - startEpoch).toInt()
    }

    private fun kotlinx.datetime.LocalDate.toEpochDays(): Long {
        val jd = java.time.LocalDate.of(year, monthNumber, dayOfMonth)
        return jd.toEpochDay()
    }

    override suspend fun refreshFromCloudApi(range: DateRange): Result<Unit> = runCatching {
        if (!cloudApi.isAvailable) return@runCatching

        val nowMs = clock.now().toEpochMilliseconds()
        val rawRows = mutableListOf<RawDailyMetricEntity>()
        val dirtyEntries = mutableListOf<ComputeQueueEntity>()

        fun addRaw(date: LocalDate, metric: MetricType, value: Double, unit: String) {
            val dateStr = date.toString()
            rawRows += RawDailyMetricEntity(
                date = dateStr, metric = metric.name, source = "GoogleHealth",
                value = value, unit = unit,
                externalId = "gh-${metric.name.lowercase()}-$dateStr", ingestedAtMs = nowMs,
            )
            dirtyEntries += ComputeQueueEntity(date = dateStr, metric = metric.name, enqueuedAtMs = nowMs)
        }

        cloudApi.reconcileSteps(range).forEach { (date, steps) ->
            addRaw(date, MetricType.Steps, steps.toDouble(), "count")
        }
        cloudApi.reconcileDistance(range).forEach { (date, mm) ->
            val miles = mm / 1000.0 / METERS_PER_MILE
            addRaw(date, MetricType.Distance, miles, "miles")
        }
        cloudApi.reconcileCalories(range).forEach { (date, cals) ->
            addRaw(date, MetricType.ActiveCalories, cals, "kcal")
        }
        cloudApi.reconcileZoneMinutes(range).forEach { (date, mins) ->
            addRaw(date, MetricType.ZoneMinutes, mins.toDouble(), "minutes")
        }
        cloudApi.reconcileWeight(range).forEach { (date, kg) ->
            if (kg > 0) addRaw(date, MetricType.Weight, kg * 2.20462, "lbs")
        }
        cloudApi.reconcileHrv(range).forEach { (date, ms) ->
            if (ms > 0) addRaw(date, MetricType.HRV, ms, "ms")
        }
        cloudApi.reconcileSpO2(range).forEach { (date, pct) ->
            if (pct > 0) addRaw(date, MetricType.SpO2, pct, "percent")
        }
        cloudApi.reconcileRestingHr(range).forEach { (date, bpm) ->
            if (bpm > 0) addRaw(date, MetricType.RestingHeartRate, bpm, "bpm")
        }

        if (rawRows.isNotEmpty()) {
            rawDailyDao.insertAll(rawRows)
            computeQueueDao.enqueue(dirtyEntries)
            computeEngine.processQueue()
        }
    }

    override suspend fun dumpRawRecords(date: LocalDate): List<HealthMetric> {
        // Read from raw_daily_metrics for the given date
        val dateStr = date.toString()
        val rawMetrics = rawDailyDao.getForDateAndMetric(dateStr, "%") // This won't work with LIKE
        // Fallback: read all metrics for the date by iterating known types
        val allRaw = mutableListOf<HealthMetric>()
        for (type in MetricType.entries) {
            val rows = rawDailyDao.getForDateAndMetric(dateStr, type.name)
            for (row in rows) {
                allRaw += HealthMetric(
                    id = row.externalId ?: "${row.date}-${row.metric}-${row.source}",
                    type = type,
                    value = row.value,
                    unit = unitOf(row.unit),
                    start = Instant.fromEpochMilliseconds(date.atStartOfDayMillis()),
                    end = Instant.fromEpochMilliseconds(date.atStartOfDayMillis() + 24 * 60 * 60 * 1000L),
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

    private fun defaultGoal(metric: MetricType): Double? = when (metric) {
        MetricType.Steps -> DEFAULT_STEP_GOAL
        MetricType.Distance -> DEFAULT_DISTANCE_GOAL_MI
        MetricType.ActiveCalories, MetricType.Calories -> DEFAULT_CALORIE_GOAL
        MetricType.ZoneMinutes -> DEFAULT_ZONE_MIN_GOAL
        else -> null
    }

    private fun buildInt(
        total: Double,
        defaultGoal: Double,
        unitLabel: String,
    ): MetricValue<Int> {
        val progress = if (defaultGoal > 0) (total / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = total.roundToInt(),
            goal = defaultGoal.roundToInt(),
            progress = progress,
            unitLabel = unitLabel,
        )
    }

    private fun buildMiles(
        total: Double,
        defaultGoal: Double,
    ): MetricValue<Double> {
        val progress = if (defaultGoal > 0) (total / defaultGoal).toFloat().coerceIn(0f, 1.25f) else 0f
        return MetricValue(
            current = total,
            goal = defaultGoal,
            progress = progress,
            unitLabel = "mi",
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
