package com.pulse.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.work.ImmediateSyncWorker
import com.pulse.domain.model.SyncOutcome
import com.pulse.domain.model.SyncPhase
import com.pulse.domain.model.SyncStatus
import com.pulse.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val aggregateDao: DailyAggregateDao,
    private val workManager: WorkManager,
) : SyncRepository {

    override fun observeStatus(): Flow<SyncStatus> = flow {
        val latestMs = aggregateDao.latestComputedAtMs()
        val lastSyncedAt = latestMs?.let { Instant.fromEpochMilliseconds(it) }
        emit(
            SyncStatus(
                lastSyncedAt = lastSyncedAt,
                state = SyncPhase.Idle,
                pendingItems = aggregateDao.dirtyCount(),
                lastError = null,
            )
        )
    }

    override suspend fun pushPending(): SyncOutcome {
        val dirty = aggregateDao.dirty(limit = 100)
        if (dirty.isEmpty()) return SyncOutcome.NoOp
        return SyncOutcome.Ok(dirty.size)
    }

    override suspend fun pullReconciled(since: Instant): SyncOutcome = SyncOutcome.NoOp

    override suspend fun forceSyncNow(): SyncOutcome {
        val req = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(ImmediateSyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork("health-sync-now", ExistingWorkPolicy.REPLACE, req)
        return SyncOutcome.Ok(0)
    }
}
