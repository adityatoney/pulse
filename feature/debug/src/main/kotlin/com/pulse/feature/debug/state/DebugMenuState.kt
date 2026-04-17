package com.pulse.feature.debug.state

import com.pulse.data.datastore.FeatureFlagKey
import com.pulse.data.datastore.FeatureFlagSnapshot
import com.pulse.domain.repository.DebugBuildInfo

enum class SyncWorkerState { Idle, Enqueued, Running, Succeeded, Failed }

data class DebugMenuState(
    val featureFlags: FeatureFlagSnapshot = FeatureFlagSnapshot.Default,
    val buildInfo: DebugBuildInfo? = null,
    val pendingQueueSize: Int = 0,
    val inFlight: Boolean = false,
    val lastAction: String? = null,
    val confirm: ConfirmAction? = null,
    val dataRangeStart: String? = null,
    val dataRangeEnd: String? = null,
    val totalStepDays: Int = 0,
    val totalExerciseSessions: Int = 0,
    val syncWindowStart: String? = null,
    val syncWindowEnd: String? = null,
    val syncWorkerState: SyncWorkerState = SyncWorkerState.Idle,
    val googleHealthSignedIn: Boolean = false,
)

enum class ConfirmAction { ClearCache, HardReset }

sealed interface DebugMenuIntent {
    data object Load : DebugMenuIntent
    data object SeedFakeData : DebugMenuIntent
    data object SeedRealisticWeek : DebugMenuIntent
    data object RequestClearCache : DebugMenuIntent
    data object RequestHardReset : DebugMenuIntent
    data class ConfirmDestructive(val action: ConfirmAction) : DebugMenuIntent
    data object CancelConfirm : DebugMenuIntent
    data object ExportCsv : DebugMenuIntent
    data object ForceSyncNow : DebugMenuIntent
    data object SimulateNetworkFailure : DebugMenuIntent
    data object OpenHealthConnect : DebugMenuIntent
    data object ResetChangeToken : DebugMenuIntent
    data object DumpRecords : DebugMenuIntent
    data class ToggleFlag(val key: FeatureFlagKey, val value: Boolean) : DebugMenuIntent
    data object GoogleHealthSignIn : DebugMenuIntent
    data object GoogleHealthSignOut : DebugMenuIntent
    data object Dismiss : DebugMenuIntent
}

sealed interface DebugMenuEffect {
    /** Absolute file path on disk; the screen wraps it in a FileProvider Uri. */
    data class ShareCsv(val filePath: String) : DebugMenuEffect
    data class Snackbar(val message: String) : DebugMenuEffect
    data object OpenHealthConnectApp : DebugMenuEffect
    data object NavigateBack : DebugMenuEffect
    data class NavigateToRecordDump(val records: String) : DebugMenuEffect
    data object LaunchGoogleSignIn : DebugMenuEffect
}
