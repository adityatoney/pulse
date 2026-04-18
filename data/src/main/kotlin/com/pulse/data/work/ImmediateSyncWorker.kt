package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pulse.data.sync.EnhancedHealthSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Tier 1 worker: fetches the last 7 days of all data types using
 * the type-first bulk strategy. Also used for periodic 15-min
 * incremental sync via the Changes API.
 */
@HiltWorker
class ImmediateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: EnhancedHealthSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceFullFetch = inputData.getBoolean(KEY_FORCE_FULL_FETCH, false)
        Log.d(TAG, "Starting immediate sync (forceFullFetch=$forceFullFetch)")
        val result = syncManager.syncRecent(days = 7, forceFullFetch = forceFullFetch)
        return if (result.isSuccess) {
            Log.d(TAG, "Immediate sync succeeded")
            Result.success()
        } else {
            Log.w(TAG, "Immediate sync failed: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pulse-immediate-sync"
        const val TAG = "ImmediateSync"
        const val KEY_FORCE_FULL_FETCH = "force_full_fetch"

        fun forceFullFetchData() = workDataOf(KEY_FORCE_FULL_FETCH to true)
    }
}
