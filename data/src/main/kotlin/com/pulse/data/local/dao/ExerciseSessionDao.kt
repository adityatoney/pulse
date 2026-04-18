package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSessionDao {

    @Upsert
    suspend fun upsert(sessions: List<ExerciseSessionEntity>)

    @Query("SELECT * FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs DESC")
    fun observeRange(fromMs: Long, toMs: Long): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    suspend fun getRange(fromMs: Long, toMs: Long): List<ExerciseSessionEntity>

    @Query("SELECT * FROM exercise_sessions WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<ExerciseSessionEntity>

    @Query("UPDATE exercise_sessions SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT COALESCE(SUM(calories), 0.0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumCalories(fromMs: Long, toMs: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(distanceMeters), 0.0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumDistanceMeters(fromMs: Long, toMs: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(zoneMinutes), 0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumZoneMinutes(fromMs: Long, toMs: Long): Flow<Int>

    @Query("SELECT * FROM exercise_sessions WHERE id = :id")
    suspend fun findById(id: String): ExerciseSessionEntity?

    @Query("UPDATE exercise_sessions SET zoneMinutes = :zoneMinutes WHERE id = :id")
    suspend fun updateZoneMinutes(id: String, zoneMinutes: Int)

    @Query("SELECT COUNT(DISTINCT date(startUtcMs/1000, 'unixepoch', 'localtime')) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    suspend fun exerciseDayCount(fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM exercise_sessions")
    suspend fun totalCount(): Int

    @Query("DELETE FROM exercise_sessions")
    suspend fun clear()

    @Query("SELECT * FROM exercise_sessions")
    suspend fun getAll(): List<ExerciseSessionEntity>
}
