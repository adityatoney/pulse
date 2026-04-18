package com.pulse.data.cloud.backup

import androidx.datastore.core.DataStore
import com.pulse.data.proto.Preferences
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictDecision {
    RESTORE,
    SKIP_RESTORE,
    PROMPT_USER,
}

@Singleton
class BackupConflictResolver @Inject constructor(
    private val preferencesStore: DataStore<Preferences>,
    private val driveBackupManager: DriveBackupManager,
) {
    suspend fun resolve(backupMetadata: BackupMetadata): ConflictDecision {
        val prefs = preferencesStore.data.first()
        val localLastBackupMs = prefs.lastBackupAtMs
        val isEmpty = driveBackupManager.isDatabaseEmpty()

        val cloudModifiedMs = try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(backupMetadata.modifiedTime))
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }

        return when {
            isEmpty -> ConflictDecision.RESTORE
            localLastBackupMs == 0L -> ConflictDecision.PROMPT_USER
            localLastBackupMs > cloudModifiedMs -> ConflictDecision.SKIP_RESTORE
            cloudModifiedMs > localLastBackupMs -> ConflictDecision.PROMPT_USER
            else -> ConflictDecision.SKIP_RESTORE
        }
    }
}
