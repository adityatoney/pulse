package com.pulse.data.cloud.backup

import android.util.Log
import com.pulse.data.cloud.DriveAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupRestore"

/**
 * Checks on app launch whether we should auto-restore from a cloud backup.
 * Only restores if the user is authenticated with Drive AND the local DB is empty.
 */
@Singleton
class BackupRestoreInitializer @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val driveAuth: DriveAuthManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkAndRestore() {
        if (!driveAuth.isAuthenticated) return

        scope.launch {
            try {
                if (!driveBackupManager.isDatabaseEmpty()) {
                    Log.d(TAG, "Database not empty, skipping restore")
                    return@launch
                }

                val backup = driveBackupManager.findBackup()
                if (backup == null) {
                    Log.d(TAG, "No backup found in Drive")
                    return@launch
                }

                Log.d(TAG, "Empty DB detected, restoring from cloud backup")
                val result = driveBackupManager.restore()
                result.onSuccess { count ->
                    Log.d(TAG, "Restore succeeded: $count records")
                }.onFailure { e ->
                    Log.e(TAG, "Restore failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-restore check failed", e)
            }
        }
    }
}
