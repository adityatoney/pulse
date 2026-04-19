package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {

    @Upsert
    suspend fun upsert(sessions: List<SleepSessionEntity>)

    @Query("SELECT * FROM sleep_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs DESC LIMIT 1")
    fun observeLatestForDate(fromMs: Long, toMs: Long): Flow<SleepSessionEntity?>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun totalCount(): Int

    @Query("DELETE FROM sleep_sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clear()

    @Query("SELECT * FROM sleep_sessions")
    suspend fun getAll(): List<SleepSessionEntity>
}
