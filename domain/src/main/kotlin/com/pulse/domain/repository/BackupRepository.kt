package com.pulse.domain.repository

interface BackupRepository {
    val isBackupAvailable: Boolean
    suspend fun backup(): Result<BackupInfo>
    suspend fun findBackup(): BackupInfo?
    suspend fun restore(): Result<Int>
    suspend fun isDatabaseEmpty(): Boolean
}

data class BackupInfo(
    val modifiedTime: String,
    val sizeBytes: Long,
)
