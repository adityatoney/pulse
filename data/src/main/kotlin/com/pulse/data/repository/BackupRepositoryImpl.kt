package com.pulse.data.repository

import com.pulse.data.cloud.DriveAuthManager
import com.pulse.data.cloud.backup.DriveBackupManager
import com.pulse.domain.repository.BackupInfo
import com.pulse.domain.repository.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val driveAuth: DriveAuthManager,
) : BackupRepository {

    override val isBackupAvailable: Boolean
        get() = driveAuth.isAuthenticated

    override suspend fun backup(): Result<BackupInfo> {
        return driveBackupManager.backup().map { meta ->
            BackupInfo(
                modifiedTime = meta.modifiedTime,
                sizeBytes = meta.sizeBytes,
            )
        }
    }

    override suspend fun findBackup(): BackupInfo? {
        val meta = driveBackupManager.findBackup() ?: return null
        return BackupInfo(
            modifiedTime = meta.modifiedTime,
            sizeBytes = meta.sizeBytes,
        )
    }

    override suspend fun restore(): Result<Int> = driveBackupManager.restore()

    override suspend fun isDatabaseEmpty(): Boolean = driveBackupManager.isDatabaseEmpty()
}
