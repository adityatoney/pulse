package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.ExerciseLapEntity

@Dao
interface ExerciseLapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(laps: List<ExerciseLapEntity>)

    @Query("SELECT * FROM exercise_laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun forSession(sessionId: String): List<ExerciseLapEntity>

    @Query("SELECT * FROM exercise_laps")
    suspend fun getAll(): List<ExerciseLapEntity>
}
