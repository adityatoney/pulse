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
 * When rate-limited, schedules a follow-up via [SyncScheduler].
 */
@HiltWorker
class FitbitSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: FitbitSyncManager,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Fitbit sync worker (attempt $runAttemptCount)")

        val result = syncManager.sync(maxHistoryYears = 5)

        return when {
            result.isFailure -> {
                val error = result.exceptionOrNull()
                Log.w(TAG, "Fitbit sync failed: ${error?.message}", error)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            result.getOrNull() == FitbitSyncManager.SyncStatus.RateLimited -> {
                val resetSeconds = syncManager.getRateLimitResetSeconds()
                val delayMinutes = (resetSeconds / 60) + 1  // round up + 1 min buffer
                Log.d(TAG, "Rate-limited — scheduling retry in ${delayMinutes}m")
                syncScheduler.scheduleFitbitSyncDelayed(delayMinutes.toLong())
                Result.success() // this work is done; the delayed work continues
            }
            else -> {
                Log.d(TAG, "Fitbit sync completed fully")
                Result.success()
            }
        }
    }

    companion object {
        const val TAG = "FitbitSyncWorker"
        const val UNIQUE_NAME = "fitbit-history-sync"
    }
}
