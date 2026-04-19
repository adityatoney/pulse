package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.SummaryDailyMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDailyMetricDao {

    @Upsert
    suspend fun upsert(summaries: List<SummaryDailyMetricEntity>)

    @Upsert
    suspend fun upsertOne(summary: SummaryDailyMetricEntity)

    @Query("SELECT * FROM summary_daily_metrics WHERE date = :date AND metric = :metric")
    fun observe(date: String, metric: String): Flow<SummaryDailyMetricEntity?>

    @Query("SELECT * FROM summary_daily_metrics WHERE date = :date AND metric = :metric")
    suspend fun get(date: String, metric: String): SummaryDailyMetricEntity?

    @Query("SELECT * FROM summary_daily_metrics WHERE date = :date")
    fun observeForDate(date: String): Flow<List<SummaryDailyMetricEntity>>

    @Query("SELECT * FROM summary_daily_metrics WHERE metric = :metric AND date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeRange(metric: String, start: String, end: String): Flow<List<SummaryDailyMetricEntity>>

    @Query("SELECT * FROM summary_daily_metrics WHERE dirty = 1 ORDER BY date ASC LIMIT :limit")
    suspend fun dirty(limit: Int): List<SummaryDailyMetricEntity>

    @Query("UPDATE summary_daily_metrics SET dirty = 0, remoteVersion = :version WHERE date = :date AND metric = :metric")
    suspend fun markSynced(date: String, metric: String, version: Long)

    @Query("SELECT COUNT(*) FROM summary_daily_metrics WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT MAX(computedAtMs) FROM summary_daily_metrics")
    suspend fun latestComputedAtMs(): Long?

    @Query("SELECT MIN(date) FROM summary_daily_metrics WHERE metric = 'Steps'")
    suspend fun minStepDate(): String?

    @Query("SELECT MAX(date) FROM summary_daily_metrics WHERE metric = 'Steps'")
    suspend fun maxStepDate(): String?

    @Query("SELECT COUNT(*) FROM summary_daily_metrics WHERE metric = 'Steps'")
    suspend fun stepDayCount(): Int

    @Query("SELECT * FROM summary_daily_metrics")
    suspend fun getAll(): List<SummaryDailyMetricEntity>

    @Query("SELECT * FROM summary_daily_metrics WHERE metric = :metric AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getRange(metric: String, start: String, end: String): List<SummaryDailyMetricEntity>

    @Query("SELECT * FROM summary_daily_metrics WHERE metric = :metric AND total > 0 ORDER BY total DESC LIMIT 1")
    suspend fun bestEver(metric: String): SummaryDailyMetricEntity?

    @Query("SELECT MIN(total) FROM summary_daily_metrics WHERE metric = :metric AND date BETWEEN :start AND :end AND total > 0")
    suspend fun minInRange(metric: String, start: String, end: String): Double?

    @Query("SELECT AVG(total) FROM summary_daily_metrics WHERE metric = :metric AND date BETWEEN :start AND :end AND total > 0")
    suspend fun avgInRange(metric: String, start: String, end: String): Double?

    @Query("SELECT COUNT(*) FROM summary_daily_metrics WHERE metric = :metric AND date BETWEEN :start AND :end AND total > 0")
    suspend fun countInRange(metric: String, start: String, end: String): Int

    @Query("DELETE FROM summary_daily_metrics")
    suspend fun clear()
}
