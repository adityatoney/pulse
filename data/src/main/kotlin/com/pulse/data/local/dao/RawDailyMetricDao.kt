package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.RawDailyMetricEntity

@Dao
interface RawDailyMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<RawDailyMetricEntity>)

    @Query("SELECT * FROM raw_daily_metrics WHERE date = :date AND metric = :metric")
    suspend fun getForDateAndMetric(date: String, metric: String): List<RawDailyMetricEntity>

    @Query("SELECT * FROM raw_daily_metrics WHERE date BETWEEN :start AND :end AND metric = :metric ORDER BY date ASC")
    suspend fun getRange(metric: String, start: String, end: String): List<RawDailyMetricEntity>

    @Query("SELECT COUNT(*) FROM raw_daily_metrics WHERE externalId = :externalId")
    suspend fun existsByExternalId(externalId: String): Int

    @Query("SELECT * FROM raw_daily_metrics")
    suspend fun getAll(): List<RawDailyMetricEntity>

    @Query("DELETE FROM raw_daily_metrics")
    suspend fun clear()
}
