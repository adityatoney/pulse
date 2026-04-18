package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.data.cloud.fitbit.FitbitSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker for syncing data from the Fitbit Web API.
 *
 * On first run, backfills up to 5 years of historical data.
 * On subsequent runs, only fetches data since last sync.
 *
 * Rate limit aware: Fitbit allows 150 requests/hour.
 */
@HiltWorker
class FitbitSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: FitbitSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Fitbit sync worker")

        val result = syncManager.sync(maxHistoryYears = 5)

        return if (result.isSuccess) {
            Log.d(TAG, "Fitbit sync completed successfully")
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            Log.w(TAG, "Fitbit sync failed: ${error?.message}", error)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "FitbitSyncWorker"
        const val UNIQUE_NAME = "fitbit-history-sync"
    }
}
