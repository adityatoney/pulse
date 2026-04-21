package com.pulse.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /** Tier 1: one-shot expedited sync for the last 7 days (app launch). */
    fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(ImmediateSyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            ImmediateSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Tier 2: background history backfill for remaining 358 days. */
    fun scheduleHistoryBackfill() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<HistoryBackfillWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(HistoryBackfillWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            HistoryBackfillWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Ongoing: periodic 15-min incremental sync using Changes API. */
    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<ImmediateSyncWorker>(
            15, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(ImmediateSyncWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "pulse-periodic-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Periodic backup to Google Drive (every 24 hours). */
    fun schedulePeriodicBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            24, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(DriveBackupWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            DriveBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** One-shot Fitbit historical sync. Backfills up to 5 years on first run. */
    fun scheduleFitbitSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<FitbitSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(FitbitSyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            FitbitSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedule a delayed Fitbit sync to continue after rate limit resets.
     * Uses REPLACE so the new delay overwrites any pending retry.
     */
    fun scheduleFitbitSyncDelayed(delayMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FitbitSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(FitbitSyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            FitbitSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancel the legacy HealthConnectSyncWorker from before the refactor. */
    fun cancelLegacyWorker() {
        workManager.cancelUniqueWork(HealthConnectSyncWorker.UNIQUE_NAME)
    }
}
