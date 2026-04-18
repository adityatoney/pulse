package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.DailyAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAggregateDao {

    @Upsert
    suspend fun upsert(aggregates: List<DailyAggregateEntity>)

    @Upsert
    suspend fun upsertOne(aggregate: DailyAggregateEntity)

    @Query("SELECT * FROM daily_aggregates WHERE date = :date AND metric = :metric")
    fun observe(date: String, metric: String): Flow<DailyAggregateEntity?>

    @Query("SELECT * FROM daily_aggregates WHERE date = :date AND metric = :metric")
    suspend fun get(date: String, metric: String): DailyAggregateEntity?

    @Query("SELECT * FROM daily_aggregates WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DailyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregates WHERE metric = :metric AND date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeRange(metric: String, start: String, end: String): Flow<List<DailyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregates WHERE dirty = 1 ORDER BY date ASC LIMIT :limit")
    suspend fun dirty(limit: Int): List<DailyAggregateEntity>

    @Query("UPDATE daily_aggregates SET dirty = 0, remoteVersion = :version WHERE date = :date AND metric = :metric")
    suspend fun markSynced(date: String, metric: String, version: Long)

    @Query("DELETE FROM daily_aggregates")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM daily_aggregates WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT MIN(date) FROM daily_aggregates WHERE metric = 'Steps'")
    suspend fun minStepDate(): String?

    @Query("SELECT MAX(date) FROM daily_aggregates WHERE metric = 'Steps'")
    suspend fun maxStepDate(): String?

    @Query("SELECT COUNT(*) FROM daily_aggregates WHERE metric = 'Steps'")
    suspend fun stepDayCount(): Int

    @Query("SELECT MAX(computedAtMs) FROM daily_aggregates")
    suspend fun latestComputedAtMs(): Long?

    @Query("SELECT * FROM daily_aggregates")
    suspend fun getAll(): List<DailyAggregateEntity>
}
