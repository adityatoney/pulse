package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.ComputeQueueEntity

@Dao
interface ComputeQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entries: List<ComputeQueueEntity>)

    @Query("SELECT * FROM compute_queue ORDER BY date ASC LIMIT :limit")
    suspend fun dequeue(limit: Int = 500): List<ComputeQueueEntity>

    @Query("DELETE FROM compute_queue WHERE date = :date AND metric = :metric")
    suspend fun remove(date: String, metric: String)

    @Query("DELETE FROM compute_queue WHERE date IN (:dates) AND metric = :metric")
    suspend fun removeAll(dates: List<String>, metric: String)

    @Query("SELECT COUNT(*) FROM compute_queue")
    suspend fun count(): Int

    @Query("DELETE FROM compute_queue")
    suspend fun clear()
}
