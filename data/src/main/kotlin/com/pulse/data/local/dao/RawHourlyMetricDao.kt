package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.RawHourlyMetricEntity

@Dao
interface RawHourlyMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<RawHourlyMetricEntity>)

    @Query("SELECT SUM(value) FROM raw_hourly_metrics WHERE date = :date AND metric = :metric AND hour <= :hour")
    suspend fun cumulativeUpToHour(date: String, metric: String, hour: Int): Double?

    @Query("""
        SELECT * FROM raw_hourly_metrics
        WHERE date IN (:dates) AND metric = :metric AND hour <= :hour
        ORDER BY date, hour
    """)
    suspend fun getForDatesUpToHour(dates: List<String>, metric: String, hour: Int): List<RawHourlyMetricEntity>

    @Query("SELECT * FROM raw_hourly_metrics WHERE date = :date AND metric = :metric ORDER BY hour ASC")
    suspend fun getForDate(date: String, metric: String): List<RawHourlyMetricEntity>

    @Query("DELETE FROM raw_hourly_metrics")
    suspend fun clear()
}
