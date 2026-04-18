package com.pulse.domain.repository

import com.pulse.domain.model.DateRange
import com.pulse.domain.model.HealthMetric
import kotlinx.datetime.LocalDate

/**
 * Privileged access granted only to the [:feature:debug] module.
 * Impl lives in :data and calls Room's clearAllTables, WorkManager introspection,
 * Health Connect writes, etc. No production screen should depend on this.
 */
interface DebugRepository {
    /** Deterministic seed produces identical data for the same (days, seed). */
    suspend fun seedFakeData(days: Int, seed: Long = 42L): Int

    /** Pre-canned 7-day block matching the Fitbit screenshots. */
    suspend fun seedRealisticWeek(): Int

    /** Wipes Room cache. `hard` also clears HC change tokens + feature flags. */
    suspend fun clearLocalCache(hard: Boolean)

    /** Returns an absolute file path for the generated CSV.
     *  The :feature:debug module wraps this in a FileProvider Uri for sharing. */
    suspend fun exportAsCsv(range: DateRange): String

    /** Dumps raw Health Connect records for debugging aggregation. */
    suspend fun dumpRecords(date: LocalDate): List<HealthMetric>

    /** Clears the Health Connect change token so next sync does a full read. */
    suspend fun resetChangeToken()

    /** Fires the HealthConnectSyncWorker as expedited. */
    suspend fun forceSyncNow()

    /** Toggles the global FaultInjectingInterceptor for the given window. */
    suspend fun simulateNetworkFailure(durationSeconds: Int)

    /** Pending dirty rows across all tables. */
    suspend fun pendingQueueSize(): Int

    /** Build / runtime info shown at the bottom of the debug sheet. */
    suspend fun debugBuildInfo(): DebugBuildInfo

    /** Stats about what data is currently stored locally. */
    suspend fun dataStats(): DataStats

    /** Returns the Fitbit sync cursor date (last synced date), or null if never synced. */
    suspend fun fitbitSyncCursor(): String?
}

data class DataStats(
    val minStepDate: String?,
    val maxStepDate: String?,
    val totalStepDays: Int,
    val totalExerciseSessions: Int,
    val totalSleepSessions: Int = 0,
    val metricCounts: Map<String, Int> = emptyMap(),
    val backfillCursor: String? = null,
    val backfillComplete: Boolean = false,
)

data class DebugBuildInfo(
    val appVersion: String,
    val gitSha: String,
    val deviceId: String,
    val healthConnectSdkVersion: String,
)
