package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.data.sync.EnhancedHealthSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Tier 2 worker: backfills the remaining history (up to 365 days)
 * in 30-day chunks with 5-second delays between chunks.
 * Runs with battery-not-low constraint and exponential backoff.
 */
@HiltWorker
class HistoryBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: EnhancedHealthSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting history backfill")
        val result = syncManager.backfillHistory(totalDays = 365, chunkDays = 30)
        return if (result.isSuccess) {
            Log.d(TAG, "History backfill succeeded")
            Result.success()
        } else {
            Log.w(TAG, "History backfill failed: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pulse-history-backfill"
        const val TAG = "HistoryBackfill"
    }
}
