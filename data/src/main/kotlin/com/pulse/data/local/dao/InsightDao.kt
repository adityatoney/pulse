package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.InsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

    @Upsert
    suspend fun upsert(insights: List<InsightEntity>)

    @Query("SELECT * FROM insights WHERE date = :date AND context LIKE '%' || :context || '%' ORDER BY score DESC LIMIT :limit")
    fun observeByContext(date: String, context: String, limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE date = :date AND context LIKE '%' || :context || '%' AND metric = :metric ORDER BY score DESC LIMIT :limit")
    fun observeByContextAndMetric(date: String, context: String, metric: String, limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE category = :category AND date BETWEEN :start AND :end ORDER BY date DESC, score DESC")
    fun observeByCategory(category: String, start: String, end: String): Flow<List<InsightEntity>>

    @Query("DELETE FROM insights WHERE date < :cutoff")
    suspend fun pruneOlderThan(cutoff: String)

    @Query("DELETE FROM insights")
    suspend fun clear()
}
