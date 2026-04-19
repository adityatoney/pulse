package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.ExerciseHrSampleEntity

@Dao
interface ExerciseHrSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<ExerciseHrSampleEntity>)

    @Query("SELECT * FROM exercise_hr_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun forSession(sessionId: String): List<ExerciseHrSampleEntity>

    @Query("DELETE FROM exercise_hr_samples WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM exercise_hr_samples WHERE sessionId IN (:sessionIds)")
    suspend fun deleteForSessions(sessionIds: List<String>)

    @Query("SELECT * FROM exercise_hr_samples")
    suspend fun getAll(): List<ExerciseHrSampleEntity>
}
