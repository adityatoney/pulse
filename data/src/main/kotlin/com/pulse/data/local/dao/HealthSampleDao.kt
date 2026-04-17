package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.HealthSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSampleDao {

    @Upsert
    suspend fun upsert(samples: List<HealthSampleEntity>)

    @Query("SELECT * FROM health_samples WHERE type = :type AND startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    fun observeRange(type: String, fromMs: Long, toMs: Long): Flow<List<HealthSampleEntity>>

    @Query("SELECT * FROM health_samples WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    suspend fun dump(fromMs: Long, toMs: Long): List<HealthSampleEntity>

    @Query("DELETE FROM health_samples")
    suspend fun clear()
}
