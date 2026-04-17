package com.pulse.domain.repository

import com.pulse.domain.model.SyncOutcome
import com.pulse.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface SyncRepository {
    fun observeStatus(): Flow<SyncStatus>
    suspend fun pushPending(): SyncOutcome
    suspend fun pullReconciled(since: Instant): SyncOutcome
    suspend fun forceSyncNow(): SyncOutcome
}
