package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.domain.model.DateRange
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.util.Clock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Periodic 15-min delta sync using a hybrid model:
 * - Health Connect SDK for today's data (fast, real-time)
 * - Google Health REST API reconcile for yesterday+ (accurate, deduplicated)
 *
 * HC has API call quotas, so we chunk the date range into 30-day windows
 * and process most recent data first.
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val health: HealthRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val earliest = today.minus(DatePeriod(days = SYNC_WINDOW_DAYS))

        // Phase 1: Pull from Health Connect in 30-day chunks (newest first)
        val chunks = buildChunks(earliest, today, CHUNK_DAYS)
        Log.d(TAG, "Starting HC sync: ${chunks.size} chunks, $earliest to $today")

        for ((i, chunk) in chunks.withIndex()) {
            Log.d(TAG, "Chunk ${i + 1}/${chunks.size}: ${chunk.start} to ${chunk.endInclusive}")
            val result = health.refreshFromHealthConnect(chunk)
            if (result.isFailure) {
                Log.w(TAG, "Chunk ${i + 1} failed: ${result.exceptionOrNull()?.message}")
                // Delay and retry once for rate-limit recovery
                delay(QUOTA_RECOVERY_MS)
                val retry = health.refreshFromHealthConnect(chunk)
                if (retry.isFailure) {
                    Log.e(TAG, "Chunk ${i + 1} retry failed, will retry worker later")
                    return Result.retry()
                }
            }
            // Small delay between chunks to let HC quota recover
            if (i < chunks.lastIndex) delay(CHUNK_DELAY_MS)
        }
        Log.d(TAG, "HC sync complete")

        // Phase 2: Reconcile yesterday+ from Cloud API
        val yesterday = today.minus(DatePeriod(days = 1))
        val cloudRange = DateRange(start = earliest, endInclusive = yesterday)
        runCatching { health.refreshFromCloudApi(cloudRange) }

        return Result.success()
    }

    private fun buildChunks(start: LocalDate, end: LocalDate, chunkDays: Int): List<DateRange> {
        val chunks = mutableListOf<DateRange>()
        var chunkEnd = end
        while (chunkEnd >= start) {
            val chunkStart = maxOf(start, chunkEnd.minus(DatePeriod(days = chunkDays - 1)))
            chunks += DateRange(chunkStart, chunkEnd)
            chunkEnd = chunkStart.minus(DatePeriod(days = 1))
        }
        return chunks // newest-first order
    }

    private fun maxOf(a: LocalDate, b: LocalDate): LocalDate = if (a > b) a else b

    companion object {
        const val UNIQUE_NAME = "health-connect-sync"
        const val TAG = "HealthConnectSync"
        const val SYNC_WINDOW_DAYS = 365
        private const val CHUNK_DAYS = 30
        private const val CHUNK_DELAY_MS = 3_000L
        private const val QUOTA_RECOVERY_MS = 10_000L
    }
}
