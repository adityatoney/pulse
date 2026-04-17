package com.pulse.domain.repository

import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.ExerciseDetail
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.model.RoutePoint
import com.pulse.domain.model.HealthMetric
import com.pulse.domain.model.MetricSeries
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.SleepSummary
import com.pulse.domain.model.TodaySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

enum class Bucket { Hour, Day, Week, Month }

interface HealthRepository {
    /** The combined metrics rendered on the Dashboard for a given date. */
    fun observeTodaySummary(date: LocalDate): Flow<TodaySummary>

    fun observeDailyAggregate(date: LocalDate, metric: MetricType): Flow<DailyAggregate>

    fun observeSeries(metric: MetricType, range: DateRange, bucket: Bucket): Flow<MetricSeries>

    fun observeExerciseSessions(range: DateRange): Flow<List<ExerciseSession>>

    fun observeSleep(date: LocalDate): Flow<SleepSummary?>

    /** Pull fresh readings from Health Connect and write them to the local cache. */
    suspend fun refreshFromHealthConnect(range: DateRange): Result<Unit>

    /** Pull reconciled data from the Google Health REST API (yesterday+ only). */
    suspend fun refreshFromCloudApi(range: DateRange): Result<Unit>

    /** Load full exercise detail (session + HR samples + laps). */
    suspend fun getExerciseDetail(sessionId: String): ExerciseDetail?

    /** Save route points obtained from the route-consent UI flow. */
    suspend fun saveRoutePoints(sessionId: String, points: List<RoutePoint>)

    /** Raw records for the Debug Menu record-dump screen. */
    suspend fun dumpRawRecords(date: LocalDate): List<HealthMetric>
}
