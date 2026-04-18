package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.ExerciseRoutePointEntity

@Dao
interface ExerciseRoutePointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<ExerciseRoutePointEntity>)

    @Query("SELECT * FROM exercise_route_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun forSession(sessionId: String): List<ExerciseRoutePointEntity>

    @Query("DELETE FROM exercise_route_points WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("SELECT COUNT(*) > 0 FROM exercise_route_points WHERE sessionId = :sessionId LIMIT 1")
    suspend fun hasRoute(sessionId: String): Boolean

    @Query("SELECT * FROM exercise_route_points")
    suspend fun getAll(): List<ExerciseRoutePointEntity>
}
