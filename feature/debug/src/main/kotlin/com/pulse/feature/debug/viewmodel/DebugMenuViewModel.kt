package com.pulse.feature.debug.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.domain.repository.DebugRepository
import com.pulse.domain.util.Clock
import com.pulse.feature.debug.state.ConfirmAction
import com.pulse.feature.debug.state.DebugMenuEffect
import com.pulse.feature.debug.state.DebugMenuIntent
import com.pulse.feature.debug.state.DebugMenuState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
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
        onIntent(DebugMenuIntent.Load)
    }

    fun onIntent(intent: DebugMenuIntent) {
        when (intent) {
            DebugMenuIntent.Load -> viewModelScope.launch {
                val info = debug.debugBuildInfo()
                val queue = debug.pendingQueueSize()
                val stats = debug.dataStats()
                _state.update {
                    it.copy(
                        buildInfo = info,
                        pendingQueueSize = queue,
                        dataRangeStart = stats.minStepDate,
                        dataRangeEnd = stats.maxStepDate,
                        totalStepDays = stats.totalStepDays,
                        totalExerciseSessions = stats.totalExerciseSessions,
                        totalSleepSessions = stats.totalSleepSessions,
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
            DebugMenuIntent.ExportBackup -> viewModelScope.launch {
                _state.update { it.copy(inFlight = true, lastAction = "Exporting backup...") }
                runCatching {
                    debug.exportDriveBackup()
                }.onSuccess { path ->
                    _state.update { it.copy(inFlight = false, lastAction = "Backup exported to: $path") }
                    _effects.trySend(DebugMenuEffect.ShareCsv(path))
                }.onFailure { e ->
                    _state.update { it.copy(inFlight = false, lastAction = "Export failed: ${e.message}") }
                }
            }
            is DebugMenuIntent.ToggleFlag -> viewModelScope.launch {
                featureFlags.setFlag(intent.key, intent.value)
            }
            DebugMenuIntent.Dismiss -> _effects.trySend(DebugMenuEffect.NavigateBack)
        }
    }
}
