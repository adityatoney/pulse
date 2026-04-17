package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun upsert(entity: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE key = :key")
    suspend fun get(key: String): SyncStateEntity?

    @Query("DELETE FROM sync_state WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
