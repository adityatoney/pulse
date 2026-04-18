package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.data.local.entity.RawSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawSampleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<RawSampleEntity>)

    @Query("SELECT * FROM raw_samples WHERE type = :type AND startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    fun observeRange(type: String, fromMs: Long, toMs: Long): Flow<List<RawSampleEntity>>

    @Query("SELECT * FROM raw_samples WHERE type = :type AND startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    suspend fun getRange(type: String, fromMs: Long, toMs: Long): List<RawSampleEntity>

    @Query("SELECT * FROM raw_samples")
    suspend fun getAll(): List<RawSampleEntity>

    @Query("DELETE FROM raw_samples")
    suspend fun clear()
}
