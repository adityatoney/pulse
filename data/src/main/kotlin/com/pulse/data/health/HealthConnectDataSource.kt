package com.pulse.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant as JavaInstant
import java.time.LocalDate as JavaLocalDate
import java.time.Period as JavaPeriod
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Wraps every call into [HealthConnectClient]. The ONLY class that imports
 * androidx.health.connect.* — swap implementations here if Google releases
 * a breaking SDK change.
 */
@Singleton
class HealthConnectDataSource @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val available: Boolean by lazy {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    private val client: HealthConnectClient? by lazy {
        if (available) HealthConnectClient.getOrCreate(context) else null
    }

    fun isAvailable(): Boolean = available

    /** Every permission the app needs, read + write. */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
    )

    suspend fun hasAllPermissions(): Boolean {
        val c = client ?: return false
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    fun permissionsContract() =
        PermissionController.createRequestPermissionResultContract()

    // --- Daily aggregates -------------------------------------------------

    suspend fun aggregateSteps(day: JavaLocalDate, zone: ZoneId): Long {
        val c = client ?: return 0L
        val range = dayRange(day, zone)
        val res: AggregationResult = c.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range)
        )
        return res[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun aggregateDistanceMeters(day: JavaLocalDate, zone: ZoneId): Double {
        val c = client ?: return 0.0
        val range = dayRange(day, zone)
        val res = c.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range))
        return res[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    }

    suspend fun aggregateActiveCalories(day: JavaLocalDate, zone: ZoneId): Double {
        val c = client ?: return 0.0
        val range = dayRange(day, zone)
        val res = c.aggregate(
            AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range)
        )
        return res[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
    }

    suspend fun aggregateTotalCalories(day: JavaLocalDate, zone: ZoneId): Double {
        val c = client ?: return 0.0
        val range = dayRange(day, zone)
        val res = c.aggregate(
            AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), range)
        )
        return res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
    }

    suspend fun aggregateFloors(day: JavaLocalDate, zone: ZoneId): Double {
        val c = client ?: return 0.0
        val range = dayRange(day, zone)
        val res = c.aggregate(
            AggregateRequest(setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), range)
        )
        return res[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0
    }

    suspend fun restingHeartRate(day: JavaLocalDate, zone: ZoneId): Int? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(
            ReadRecordsRequest(RestingHeartRateRecord::class, range)
        ).records
        return records.lastOrNull()?.beatsPerMinute?.toInt()
    }

    suspend fun readHeartRateSamples(
        day: JavaLocalDate,
        zone: ZoneId,
    ): List<Pair<JavaInstant, Int>> {
        val c = client ?: return emptyList()
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records
        return records.flatMap { r ->
            r.samples.map { it.time to it.beatsPerMinute.toInt() }
        }
    }

    suspend fun readSleep(day: JavaLocalDate, zone: ZoneId): List<SleepSessionRecord> {
        val c = client ?: return emptyList()
        val range = dayRange(day, zone)
        return c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
    }

    suspend fun readExerciseSessions(
        start: JavaLocalDate,
        end: JavaLocalDate,
        zone: ZoneId,
    ): List<ExerciseSessionRecord> {
        val c = client ?: return emptyList()
        val range = TimeRangeFilter.between(
            start.atStartOfDay(zone).toInstant(),
            end.plusDays(1).atStartOfDay(zone).toInstant()
        )
        return c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
    }

    // --- Range series (used by Metric Detail charts) ----------------------

    suspend fun stepsByDay(start: JavaLocalDate, end: JavaLocalDate, zone: ZoneId): Map<JavaLocalDate, Long> {
        val c = client ?: return emptyMap()
        val req = AggregateGroupByPeriodRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                start.atStartOfDay(zone).toLocalDateTime(),
                end.plusDays(1).atStartOfDay(zone).toLocalDateTime()
            ),
            timeRangeSlicer = JavaPeriod.ofDays(1)
        )
        val buckets = c.aggregateGroupByPeriod(req)
        return buckets.associate { bucket ->
            bucket.startTime.toLocalDate() to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
        }
    }

    suspend fun distanceByDay(start: JavaLocalDate, end: JavaLocalDate, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val req = AggregateGroupByPeriodRequest(
            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                start.atStartOfDay(zone).toLocalDateTime(),
                end.plusDays(1).atStartOfDay(zone).toLocalDateTime()
            ),
            timeRangeSlicer = JavaPeriod.ofDays(1)
        )
        return c.aggregateGroupByPeriod(req).associate { bucket ->
            bucket.startTime.toLocalDate() to (bucket.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0)
        }
    }

    suspend fun activeCaloriesByDay(start: JavaLocalDate, end: JavaLocalDate, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val req = AggregateGroupByPeriodRequest(
            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                start.atStartOfDay(zone).toLocalDateTime(),
                end.plusDays(1).atStartOfDay(zone).toLocalDateTime()
            ),
            timeRangeSlicer = JavaPeriod.ofDays(1)
        )
        return c.aggregateGroupByPeriod(req).associate { bucket ->
            bucket.startTime.toLocalDate() to (bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0)
        }
    }

    suspend fun totalCaloriesByDay(start: JavaLocalDate, end: JavaLocalDate, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val req = AggregateGroupByPeriodRequest(
            metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(
                start.atStartOfDay(zone).toLocalDateTime(),
                end.plusDays(1).atStartOfDay(zone).toLocalDateTime()
            ),
            timeRangeSlicer = JavaPeriod.ofDays(1)
        )
        return c.aggregateGroupByPeriod(req).associate { bucket ->
            bucket.startTime.toLocalDate() to (bucket.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0)
        }
    }

    // --- Change token for incremental sync --------------------------------

    suspend fun requestChangesToken(): String? {
        val c = client ?: return null
        return c.getChangesToken(
            ChangesTokenRequest(
                recordTypes = setOf(
                    StepsRecord::class,
                    DistanceRecord::class,
                    ActiveCaloriesBurnedRecord::class,
                    TotalCaloriesBurnedRecord::class,
                    HeartRateRecord::class,
                    RestingHeartRateRecord::class,
                    ExerciseSessionRecord::class,
                    SleepSessionRecord::class,
                    FloorsClimbedRecord::class,
                    WeightRecord::class,
                    BodyFatRecord::class,
                    OxygenSaturationRecord::class,
                    HeartRateVariabilityRmssdRecord::class,
                    Vo2MaxRecord::class,
                    SkinTemperatureRecord::class,
                )
            )
        )
    }

    // --- Changes API for incremental sync ------------------------------------

    sealed interface ChangesResult {
        data class Success(
            val upsertedRecordTypes: Set<KClass<out androidx.health.connect.client.records.Record>>,
            val deletionCount: Int,
            val nextToken: String,
        ) : ChangesResult
        data object TokenExpired : ChangesResult
    }

    suspend fun getChanges(token: String): ChangesResult {
        val c = client ?: return ChangesResult.TokenExpired
        val upsertedTypes = mutableSetOf<KClass<out androidx.health.connect.client.records.Record>>()
        var deletions = 0
        var currentToken = token
        do {
            val response = c.getChanges(currentToken)
            if (response.changesTokenExpired) return ChangesResult.TokenExpired
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> upsertedTypes.add(change.record::class)
                    is DeletionChange -> deletions++
                }
            }
            currentToken = response.nextChangesToken
        } while (response.hasMore)
        return ChangesResult.Success(
            upsertedRecordTypes = upsertedTypes,
            deletionCount = deletions,
            nextToken = currentToken,
        )
    }

    // --- Bulk range reads (type-first sync) --------------------------------

    suspend fun readWeightRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().weight.inKilograms }
    }

    suspend fun readBodyFatRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(BodyFatRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().percentage.value }
    }

    suspend fun readSpO2Range(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().percentage.value }
    }

    suspend fun readHrvRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().heartRateVariabilityMillis }
    }

    suspend fun readVo2MaxRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().vo2MillilitersPerMinuteKilogram }
    }

    suspend fun readSkinTemperatureRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Double> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(SkinTemperatureRecord::class, range)).records
        return records.groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().baseline?.inCelsius ?: 0.0 }
    }

    suspend fun readRestingHeartRateRange(start: JavaInstant, end: JavaInstant, zone: ZoneId): Map<JavaLocalDate, Int> {
        val c = client ?: return emptyMap()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range)).records
        return records.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, recs) -> recs.last().beatsPerMinute.toInt() }
    }

    /** Paginated read of all HR samples in a range (uses pageToken for >1000 records). */
    suspend fun readHeartRateSamplesRange(start: JavaInstant, end: JavaInstant): List<Pair<JavaInstant, Int>> {
        val c = client ?: return emptyList()
        val range = TimeRangeFilter.between(start, end)
        val all = mutableListOf<Pair<JavaInstant, Int>>()
        var request = ReadRecordsRequest(HeartRateRecord::class, range)
        do {
            val response = c.readRecords(request)
            response.records.flatMapTo(all) { r ->
                r.samples.map { it.time to it.beatsPerMinute.toInt() }
            }
            val pageToken = response.pageToken
            if (pageToken != null) {
                request = ReadRecordsRequest(HeartRateRecord::class, range, pageToken = pageToken)
            }
        } while (pageToken != null)
        return all
    }

    /** Bulk read all sleep sessions in a range (single API call). */
    suspend fun readSleepRange(start: JavaInstant, end: JavaInstant): List<SleepSessionRecord> {
        val c = client ?: return emptyList()
        val range = TimeRangeFilter.between(start, end)
        return c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
    }

    // --- Body & vitals (used by expanded dashboard) --------------------------

    suspend fun readWeight(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records
        return records.lastOrNull()?.weight?.inKilograms
    }

    suspend fun readBodyFat(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(BodyFatRecord::class, range)).records
        return records.lastOrNull()?.percentage?.value
    }

    suspend fun readSpO2(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range)).records
        return records.lastOrNull()?.percentage?.value
    }

    suspend fun readHrv(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records
        return records.lastOrNull()?.heartRateVariabilityMillis
    }

    suspend fun readVo2Max(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, range)).records
        return records.lastOrNull()?.vo2MillilitersPerMinuteKilogram
    }

    suspend fun readSkinTemperature(day: JavaLocalDate, zone: ZoneId): Double? {
        val c = client ?: return null
        val range = dayRange(day, zone)
        val records = c.readRecords(ReadRecordsRequest(SkinTemperatureRecord::class, range)).records
        return records.lastOrNull()?.baseline?.inCelsius
    }

    // --- Debug-menu writes ------------------------------------------------

    suspend fun insertSteps(records: List<StepsRecord>) {
        client?.insertRecords(records)
    }

    suspend fun insertHeartRate(records: List<HeartRateRecord>) {
        client?.insertRecords(records)
    }

    suspend fun insertDistance(records: List<DistanceRecord>) {
        client?.insertRecords(records)
    }

    suspend fun insertActiveCalories(records: List<ActiveCaloriesBurnedRecord>) {
        client?.insertRecords(records)
    }

    suspend fun insertExerciseSessions(records: List<ExerciseSessionRecord>) {
        client?.insertRecords(records)
    }

    suspend fun insertSleep(records: List<SleepSessionRecord>) {
        client?.insertRecords(records)
    }

    /** Aggregate distance+calories+steps within a specific time window (used per exercise session). */
    suspend fun aggregateForTimeRange(
        start: JavaInstant,
        end: JavaInstant,
    ): ExerciseAggregates {
        val c = client ?: return ExerciseAggregates()
        val range = TimeRangeFilter.between(start, end)
        val res = c.aggregate(
            AggregateRequest(
                setOf(
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    StepsRecord.COUNT_TOTAL,
                ),
                range,
            )
        )
        val meters = res[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        val activeCal = res[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        val totalCal = res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        // Prefer active calories; fall back to total if active is 0
        val kcal = if (activeCal > 0) activeCal else totalCal
        val steps = res[StepsRecord.COUNT_TOTAL] ?: 0L
        return ExerciseAggregates(meters = meters, kcal = kcal, steps = steps)
    }

    /** Read heart rate samples within a specific time range (for per-session detail). */
    suspend fun readHeartRateSamplesForRange(
        start: JavaInstant,
        end: JavaInstant,
    ): List<Pair<JavaInstant, Int>> {
        val c = client ?: return emptyList()
        val range = TimeRangeFilter.between(start, end)
        val records = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records
        return records.flatMap { r -> r.samples.map { it.time to it.beatsPerMinute.toInt() } }
    }

    /**
     * Read route points from an exercise session.
     * Returns lat/lng/altitude/time tuples, empty if no route or permission not granted.
     */
    /**
     * Result wrapper that distinguishes "no route data" from "consent needed".
     */
    sealed interface RouteResult {
        data class Success(val locations: List<RouteLocation>) : RouteResult
        data object ConsentRequired : RouteResult
        data object NoData : RouteResult
    }

    suspend fun readExerciseRoute(
        sessionStart: JavaInstant,
        sessionEnd: JavaInstant,
        sessionId: String,
    ): RouteResult {
        val c = client ?: return RouteResult.NoData
        return try {
            val range = TimeRangeFilter.between(
                sessionStart.minusSeconds(60),
                sessionEnd.plusSeconds(60),
            )
            val allRecords = c.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range)
            ).records
            android.util.Log.d("HealthConnect", "Route: found ${allRecords.size} sessions in range, looking for id=$sessionId")
            allRecords.forEach { r ->
                android.util.Log.d("HealthConnect", "Route: session id=${r.metadata.id}, type=${r.exerciseType}, routeResult=${r.exerciseRouteResult?.javaClass?.simpleName}")
            }
            val record = allRecords.find { it.metadata.id == sessionId }
            val routeResult = record?.exerciseRouteResult
            android.util.Log.d("HealthConnect", "Route: matched record=${record != null}, routeResult=${routeResult?.javaClass?.simpleName}")
            when (routeResult) {
                is androidx.health.connect.client.records.ExerciseRouteResult.Data ->
                    RouteResult.Success(routeResult.exerciseRoute.route.map { loc ->
                        RouteLocation(
                            timestampMs = loc.time.toEpochMilli(),
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude?.inMeters,
                        )
                    })
                is androidx.health.connect.client.records.ExerciseRouteResult.ConsentRequired ->
                    RouteResult.ConsentRequired
                else -> RouteResult.NoData
            }
        } catch (e: Exception) {
            android.util.Log.w("HealthConnect", "Failed to read exercise route: ${e.message}")
            RouteResult.NoData
        }
    }

    data class RouteLocation(
        val timestampMs: Long,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
    )

    data class ExerciseAggregates(
        val meters: Double = 0.0,
        val kcal: Double = 0.0,
        val steps: Long = 0,
    )

    suspend fun deleteAllForOrigin(origin: DataOrigin) {
        // Debug helper: remove only records created by our package
        // (real impl iterates per record type; stub for now)
    }

    private fun dayRange(day: JavaLocalDate, zone: ZoneId): TimeRangeFilter {
        val startOfDay = day.atStartOfDay(zone).toInstant()
        val startOfNext = day.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(startOfDay, startOfNext)
    }
}
