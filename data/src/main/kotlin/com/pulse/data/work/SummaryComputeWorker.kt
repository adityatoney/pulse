package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.data.compute.SummaryComputeEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the compute queue and recomputes affected daily summaries.
 * Enqueued as a one-shot expedited worker after each ingestion batch.
 */
@HiltWorker
class SummaryComputeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val computeEngine: SummaryComputeEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting summary computation")
        computeEngine.processQueue()
        Log.d(TAG, "Summary computation complete")
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "pulse-summary-compute"
        const val TAG = "SummaryCompute"
    }
}
