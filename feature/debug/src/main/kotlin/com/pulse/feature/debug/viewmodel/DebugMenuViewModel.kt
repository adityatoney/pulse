package com.pulse.feature.debug.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pulse.data.cloud.GoogleHealthAuthManager
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.work.HealthConnectSyncWorker
import com.pulse.domain.repository.DebugRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.debug.state.ConfirmAction
import com.pulse.feature.debug.state.DebugMenuEffect
import com.pulse.feature.debug.state.DebugMenuIntent
import com.pulse.feature.debug.state.DebugMenuState
import com.pulse.feature.debug.state.SyncWorkerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val debug: DebugRepository,
    private val featureFlags: FeatureFlagRepository,
    val authManager: GoogleHealthAuthManager,
    private val workManager: WorkManager,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugMenuState())
    val state: StateFlow<DebugMenuState> = _state.asStateFlow()

    private val _effects = Channel<DebugMenuEffect>(Channel.BUFFERED)
    val effects: Flow<DebugMenuEffect> = _effects.receiveAsFlow()

    init {
        featureFlags.observe().onEach { flags ->
            _state.update { it.copy(featureFlags = flags) }
        }.launchIn(viewModelScope)
        _state.update { it.copy(googleHealthSignedIn = authManager.isAuthenticated) }
        observeSyncWorker()
        onIntent(DebugMenuIntent.Load)
    }

    private var userTriggeredSync = false

    private fun observeSyncWorker() {
        workManager.getWorkInfosForUniqueWorkFlow("health-sync-now")
            .map { infos ->
                val info = infos.firstOrNull()
                when (info?.state) {
                    WorkInfo.State.RUNNING -> SyncWorkerState.Running
                    WorkInfo.State.ENQUEUED -> {
                        if (userTriggeredSync) SyncWorkerState.Enqueued else SyncWorkerState.Idle
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (userTriggeredSync) SyncWorkerState.Succeeded else SyncWorkerState.Idle
                    }
                    WorkInfo.State.FAILED -> {
                        if (userTriggeredSync) SyncWorkerState.Failed else SyncWorkerState.Idle
                    }
                    WorkInfo.State.CANCELLED, WorkInfo.State.BLOCKED, null -> SyncWorkerState.Idle
                }
            }
            .onEach { workerState ->
                _state.update { it.copy(syncWorkerState = workerState) }
                if (workerState == SyncWorkerState.Succeeded || workerState == SyncWorkerState.Failed) {
                    refreshDataStats()
                    userTriggeredSync = false
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshDataStats() {
        val stats = debug.dataStats()
        val queue = debug.pendingQueueSize()
        _state.update {
            it.copy(
                pendingQueueSize = queue,
                dataRangeStart = stats.minStepDate,
                dataRangeEnd = stats.maxStepDate,
                totalStepDays = stats.totalStepDays,
                totalExerciseSessions = stats.totalExerciseSessions,
            )
        }
    }

    fun onSignInResult(success: Boolean, message: String?) {
        _state.update {
            it.copy(
                googleHealthSignedIn = success,
                lastAction = if (success) "Google Health signed in" else "Sign-in failed: $message",
            )
        }
    }

    fun onIntent(intent: DebugMenuIntent) {
        when (intent) {
            DebugMenuIntent.Load -> viewModelScope.launch {
                val info = debug.debugBuildInfo()
                val queue = debug.pendingQueueSize()
                val stats = debug.dataStats()
                val tz = TimeZone.currentSystemDefault()
                val today = clock.now().toLocalDateTime(tz).date
                val syncStart = today.minus(DatePeriod(days = HealthConnectSyncWorker.SYNC_WINDOW_DAYS))
                _state.update {
                    it.copy(
                        buildInfo = info,
                        pendingQueueSize = queue,
                        dataRangeStart = stats.minStepDate,
                        dataRangeEnd = stats.maxStepDate,
                        totalStepDays = stats.totalStepDays,
                        totalExerciseSessions = stats.totalExerciseSessions,
                        syncWindowStart = syncStart.toString(),
                        syncWindowEnd = today.toString(),
                    )
                }
            }
            DebugMenuIntent.SeedFakeData -> run {
                _state.update { it.copy(inFlight = true) }
                viewModelScope.launch {
                    val rows = debug.seedFakeData(days = 90)
                    _state.update { it.copy(inFlight = false, lastAction = "Seeded $rows rows") }
                    _effects.trySend(DebugMenuEffect.Snackbar("Seeded 90 days of data"))
                }
            }
            DebugMenuIntent.SeedRealisticWeek -> viewModelScope.launch {
                val rows = debug.seedRealisticWeek()
                _effects.trySend(DebugMenuEffect.Snackbar("Seeded week ($rows rows)"))
            }
            DebugMenuIntent.RequestClearCache -> _state.update { it.copy(confirm = ConfirmAction.ClearCache) }
            DebugMenuIntent.RequestHardReset -> _state.update { it.copy(confirm = ConfirmAction.HardReset) }
            is DebugMenuIntent.ConfirmDestructive -> viewModelScope.launch {
                when (intent.action) {
                    ConfirmAction.ClearCache -> debug.clearLocalCache(hard = false)
                    ConfirmAction.HardReset -> debug.clearLocalCache(hard = true)
                }
                _state.update { it.copy(confirm = null, lastAction = "Cleared (${intent.action.name})") }
                _effects.trySend(DebugMenuEffect.Snackbar("Local cache cleared"))
            }
            DebugMenuIntent.CancelConfirm -> _state.update { it.copy(confirm = null) }
            DebugMenuIntent.ExportCsv -> viewModelScope.launch {
                val tz = TimeZone.currentSystemDefault()
                val today = clock.now().toLocalDateTime(tz).date
                val start = today.minus(DatePeriod(days = 90))
                val path = debug.exportAsCsv(com.pulse.domain.model.DateRange(start, today))
                _effects.trySend(DebugMenuEffect.ShareCsv(path))
            }
            DebugMenuIntent.ForceSyncNow -> viewModelScope.launch {
                userTriggeredSync = true
                _state.update { it.copy(syncWorkerState = SyncWorkerState.Enqueued) }
                debug.forceSyncNow()
            }
            DebugMenuIntent.SimulateNetworkFailure -> viewModelScope.launch {
                debug.simulateNetworkFailure(60)
                _effects.trySend(DebugMenuEffect.Snackbar("Fault injection active for 60s"))
            }
            DebugMenuIntent.OpenHealthConnect -> _effects.trySend(DebugMenuEffect.OpenHealthConnectApp)
            DebugMenuIntent.ResetChangeToken -> viewModelScope.launch {
                debug.resetChangeToken()
                _effects.trySend(DebugMenuEffect.Snackbar("Change token reset"))
            }
            DebugMenuIntent.DumpRecords -> viewModelScope.launch {
                val today = clock.today()
                val records = debug.dumpRecords(today)
                _effects.trySend(DebugMenuEffect.NavigateToRecordDump(records.joinToString("\n") { it.toString() }))
            }
            is DebugMenuIntent.ToggleFlag -> viewModelScope.launch {
                featureFlags.setFlag(intent.key, intent.value)
            }
            DebugMenuIntent.GoogleHealthSignIn -> {
                _effects.trySend(DebugMenuEffect.LaunchGoogleSignIn)
            }
            DebugMenuIntent.GoogleHealthSignOut -> viewModelScope.launch {
                authManager.signOut()
                _state.update { it.copy(googleHealthSignedIn = false, lastAction = "Google Health signed out") }
            }
            DebugMenuIntent.Dismiss -> _effects.trySend(DebugMenuEffect.NavigateBack)
        }
    }
}
