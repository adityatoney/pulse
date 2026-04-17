package com.pulse.data.cloud

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.SleepSummary
import kotlinx.datetime.LocalDate

/**
 * Abstraction over the Google Health REST API v4 reconcile endpoints.
 * The reconcile endpoint returns server-side conflict-resolved data,
 * filtering noisy phone data and keeping clean watch data.
 */
interface GoogleHealthRemoteDataSource {

    /** Whether the user is authenticated and this source can fetch data. */
    val isAvailable: Boolean

    suspend fun reconcileSteps(range: DateRange): Map<LocalDate, Long>
    suspend fun reconcileDistance(range: DateRange): Map<LocalDate, Double> // millimeters
    suspend fun reconcileCalories(range: DateRange): Map<LocalDate, Double>
    suspend fun reconcileZoneMinutes(range: DateRange): Map<LocalDate, Int>
    suspend fun reconcileSleep(range: DateRange): List<SleepSummary>
    suspend fun reconcileWeight(range: DateRange): Map<LocalDate, Double> // kg
    suspend fun reconcileHrv(range: DateRange): Map<LocalDate, Double> // ms
    suspend fun reconcileSpO2(range: DateRange): Map<LocalDate, Double> // %
    suspend fun reconcileRestingHr(range: DateRange): Map<LocalDate, Double> // bpm
}

/** No-op fallback used when the user hasn't signed in or the feature flag is off. */
class NoopGoogleHealthRemoteDataSource : GoogleHealthRemoteDataSource {
    override val isAvailable: Boolean = false
    override suspend fun reconcileSteps(range: DateRange) = emptyMap<LocalDate, Long>()
    override suspend fun reconcileDistance(range: DateRange) = emptyMap<LocalDate, Double>()
    override suspend fun reconcileCalories(range: DateRange) = emptyMap<LocalDate, Double>()
    override suspend fun reconcileZoneMinutes(range: DateRange) = emptyMap<LocalDate, Int>()
    override suspend fun reconcileSleep(range: DateRange) = emptyList<SleepSummary>()
    override suspend fun reconcileWeight(range: DateRange) = emptyMap<LocalDate, Double>()
    override suspend fun reconcileHrv(range: DateRange) = emptyMap<LocalDate, Double>()
    override suspend fun reconcileSpO2(range: DateRange) = emptyMap<LocalDate, Double>()
    override suspend fun reconcileRestingHr(range: DateRange) = emptyMap<LocalDate, Double>()
}
