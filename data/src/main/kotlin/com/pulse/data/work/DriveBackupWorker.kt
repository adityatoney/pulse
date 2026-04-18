package com.pulse.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.data.cloud.DriveAuthManager
import com.pulse.data.cloud.backup.DriveBackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that backs up the Room database to Google Drive
 * every 24 hours. Silently skips if not authenticated.
 */
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveBackupManager: DriveBackupManager,
    private val driveAuth: DriveAuthManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!driveAuth.isAuthenticated) {
            Log.d(TAG, "Not authenticated, skipping backup")
            return Result.success()
        }

        Log.d(TAG, "Starting periodic backup")
        val result = driveBackupManager.backup()
        return if (result.isSuccess) {
            Log.d(TAG, "Backup succeeded")
            Result.success()
        } else {
            Log.w(TAG, "Backup failed: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "drive-backup"
        const val TAG = "DriveBackup"
    }
}
