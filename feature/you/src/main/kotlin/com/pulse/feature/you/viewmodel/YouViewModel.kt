package com.pulse.feature.you.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.data.cloud.DriveAuthManager
import com.pulse.data.cloud.GoogleHealthAuthManager
import com.pulse.data.cloud.fitbit.FitbitAuthManager
import com.pulse.data.cloud.fitbit.FitbitSyncManager
import com.pulse.data.compute.SummaryComputeEngine
import com.pulse.data.datastore.FeatureFlagKey
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.datastore.PreferencesRepository
import com.pulse.domain.model.Cadence
import com.pulse.domain.model.Goal
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.BackupRepository
import com.pulse.domain.repository.DebugRepository
import com.pulse.domain.repository.GoalsRepository
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.domain.util.Clock
import com.pulse.feature.you.state.GoalSetting
import com.pulse.feature.you.state.YouEffect
import com.pulse.feature.you.state.YouIntent
import com.pulse.feature.you.state.YouState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouViewModel @Inject constructor(
    private val goalsRepo: GoalsRepository,
    private val healthRepo: HealthRepository,
    private val backupRepo: BackupRepository,
    private val debugRepo: DebugRepository,
    private val featureFlags: FeatureFlagRepository,
    private val prefsRepo: PreferencesRepository,
    val driveAuthManager: DriveAuthManager,
    val fitbitAuthManager: FitbitAuthManager,
    val googleHealthAuthManager: GoogleHealthAuthManager,
    private val fitbitSyncManager: FitbitSyncManager,
    private val computeEngine: SummaryComputeEngine,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(YouState())
    val state: StateFlow<YouState> = _state.asStateFlow()

    private val _effects = Channel<YouEffect>(Channel.BUFFERED)
    val effects: Flow<YouEffect> = _effects.receiveAsFlow()

    init {
        loadGoals()
        loadWeeklyStats()
        observeFeatureFlags()
        observeDisplayPrefs()
        loadBackupStatus()
        loadFitbitStatus()
        loadGoogleHealthStatus()
    }

    fun onIntent(intent: YouIntent) {
        when (intent) {
            is YouIntent.UpdateAge -> {
                val maxHr = 220 - intent.age
                _state.update {
                    it.copy(
                        age = intent.age,
                        maxHr = maxHr,
                        thresholds = ZoneMinuteCalculator.thresholds(it.restingHr, intent.age),
                    )
                }
            }
            is YouIntent.UpdateRestingHr -> {
                _state.update {
                    it.copy(
                        restingHr = intent.hr,
                        thresholds = ZoneMinuteCalculator.thresholds(intent.hr, it.age),
                    )
                }
            }
            is YouIntent.UpdateMaxHr -> {
                _state.update {
                    it.copy(
                        maxHr = intent.hr,
                        thresholds = ZoneMinuteCalculator.thresholds(it.restingHr, it.age, maxHrOverride = intent.hr),
                    )
                }
            }
            is YouIntent.EditGoal -> {
                val goal = _state.value.goals.find { it.metric == intent.metric }
                _state.update { it.copy(editingGoal = goal) }
            }
            YouIntent.DismissGoalEditor -> {
                _state.update { it.copy(editingGoal = null) }
            }
            is YouIntent.SaveDailyGoal -> {
                viewModelScope.launch {
                    val today = clock.today()
                    goalsRepo.setGoal(
                        Goal(
                            metric = intent.metric,
                            target = intent.target.toDouble(),
                            effectiveFrom = today,
                            cadence = Cadence.Daily,
                        )
                    )
                    _state.update { state ->
                        state.copy(
                            goals = state.goals.map { g ->
                                if (g.metric == intent.metric) g.copy(
                                    dailyTarget = intent.target,
                                    weeklyTarget = intent.target * 7,
                                ) else g
                            },
                            editingGoal = null,
                        )
                    }
                }
            }
            YouIntent.Back -> _effects.trySend(YouEffect.NavigateBack)

            // ── Data Providers ──
            YouIntent.FitbitSignIn -> _effects.trySend(YouEffect.LaunchFitbitSignIn)
            YouIntent.FitbitSignOut -> {
                viewModelScope.launch {
                    fitbitAuthManager.signOut()
                    _state.update { it.copy(fitbitConnected = false, fitbitSyncCursor = null) }
                }
            }
            YouIntent.GoogleHealthSignIn -> _effects.trySend(YouEffect.LaunchGoogleHealthAuth)
            YouIntent.GoogleHealthSignOut -> {
                viewModelScope.launch {
                    googleHealthAuthManager.signOut()
                    _state.update { it.copy(googleHealthSignedIn = false) }
                }
            }
            YouIntent.SyncNow -> {
                viewModelScope.launch {
                    _state.update { it.copy(syncing = true, syncMessage = null) }
                    try {
                        debugRepo.forceSyncNow()
                        var fitbitRateLimited = false
                        if (_state.value.fitbitConnected) {
                            _state.update { it.copy(syncMessage = "Syncing Fitbit history...") }
                            val result = fitbitSyncManager.sync()
                            if (result.isFailure) {
                                Log.w("YouVM", "Fitbit sync error", result.exceptionOrNull())
                            }
                            fitbitRateLimited = result.getOrNull() == FitbitSyncManager.SyncStatus.RateLimited
                        }
                        _state.update { it.copy(syncMessage = "Recomputing summaries...") }
                        computeEngine.recomputeAll(days = 1900)
                        val msg = if (fitbitRateLimited) {
                            "Sync paused — Fitbit rate limit reached. Will resume automatically."
                        } else {
                            "Sync complete"
                        }
                        _state.update { it.copy(syncing = false, syncMessage = msg) }
                    } catch (e: Exception) {
                        _state.update { it.copy(syncing = false, syncMessage = "Sync failed: ${e.message}") }
                    }
                }
            }

            // ── Backup ──
            YouIntent.DriveSignIn -> _effects.trySend(YouEffect.LaunchDriveSignIn)
            YouIntent.DriveSignOut -> {
                viewModelScope.launch {
                    driveAuthManager.signOut()
                    _state.update { it.copy(driveSignedIn = false, lastBackupTime = null, lastBackupSize = null) }
                }
            }
            YouIntent.BackupNow -> {
                viewModelScope.launch {
                    _state.update { it.copy(backupInProgress = true, backupMessage = null) }
                    backupRepo.backup()
                        .onSuccess { info ->
                            _state.update {
                                it.copy(
                                    backupInProgress = false,
                                    lastBackupTime = formatBackupTime(info.modifiedTime),
                                    lastBackupSize = formatBytes(info.sizeBytes),
                                    backupMessage = "Backup complete",
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.update {
                                it.copy(
                                    backupInProgress = false,
                                    backupMessage = "Backup failed: ${e.message}",
                                )
                            }
                        }
                }
            }
            YouIntent.RestoreNow -> {
                viewModelScope.launch {
                    _state.update { it.copy(restoreInProgress = true, backupMessage = null) }
                    backupRepo.restore()
                        .onSuccess { count ->
                            _state.update {
                                it.copy(
                                    restoreInProgress = false,
                                    backupMessage = "Restored $count records",
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.update {
                                it.copy(
                                    restoreInProgress = false,
                                    backupMessage = "Restore failed: ${e.message}",
                                )
                            }
                        }
                }
            }
            YouIntent.DismissBackupMessage -> {
                _state.update { it.copy(backupMessage = null) }
            }

            // ── Display Preferences ──
            is YouIntent.SetActivityOnlySteps -> {
                viewModelScope.launch {
                    prefsRepo.setActivityOnlySteps(intent.enabled)
                }
            }
            is YouIntent.SetActivityOnlyDistance -> {
                viewModelScope.launch {
                    prefsRepo.setActivityOnlyDistance(intent.enabled)
                }
            }
            is YouIntent.SetActivityOnlyCalories -> {
                viewModelScope.launch {
                    prefsRepo.setActivityOnlyCalories(intent.enabled)
                }
            }

            // ── Appearance ──
            is YouIntent.SetDarkMode -> {
                viewModelScope.launch {
                    featureFlags.setFlag(FeatureFlagKey.ForceDarkMode, intent.enabled)
                }
            }
            is YouIntent.SetDynamicColor -> {
                viewModelScope.launch {
                    featureFlags.setFlag(FeatureFlagKey.UseDynamicColor, intent.enabled)
                }
            }
        }
    }

    fun onSignInResult(success: Boolean, message: String?) {
        _state.update {
            it.copy(
                driveSignedIn = success,
                backupMessage = if (!success) "Sign-in failed: $message" else null,
            )
        }
        if (success) loadBackupStatus()
    }

    fun onGoogleSignInResult(success: Boolean, message: String?) {
        _state.update {
            it.copy(
                googleHealthSignedIn = success,
                syncMessage = if (!success) "Google sign-in failed: $message" else null,
            )
        }
    }

    private fun loadGoals() {
        viewModelScope.launch {
            goalsRepo.observeGoals().collect { goalMap ->
                _state.update { state ->
                    state.copy(
                        goals = state.goals.map { g ->
                            val saved = goalMap[g.metric]
                            if (saved != null) g.copy(
                                dailyTarget = saved.target.toInt(),
                                weeklyTarget = (saved.target * 7).toInt(),
                            ) else g
                        }
                    )
                }
            }
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch {
            val today = clock.today()
            healthRepo.observeTodaySummary(today).collect { summary ->
                _state.update {
                    it.copy(
                        todaySteps = summary.today.steps.current,
                        weekZoneMin = summary.today.zoneMinutes.current,
                        weekCalories = summary.today.calories.current,
                    )
                }
            }
        }
    }

    private fun observeDisplayPrefs() {
        viewModelScope.launch {
            prefsRepo.observeMetricDisplay().collect { prefs ->
                _state.update {
                    it.copy(
                        activityOnlySteps = prefs.activityOnlySteps,
                        activityOnlyDistance = prefs.activityOnlyDistance,
                        activityOnlyCalories = prefs.activityOnlyCalories,
                    )
                }
            }
        }
    }

    private fun observeFeatureFlags() {
        viewModelScope.launch {
            featureFlags.observe().collect { flags ->
                _state.update {
                    it.copy(
                        forceDarkMode = flags.forceDarkMode,
                        useDynamicColor = flags.useDynamicColor,
                    )
                }
            }
        }
    }

    private fun loadBackupStatus() {
        viewModelScope.launch {
            val signedIn = backupRepo.isBackupAvailable
            _state.update { it.copy(driveSignedIn = signedIn) }
            if (signedIn) {
                val info = backupRepo.findBackup()
                if (info != null) {
                    _state.update {
                        it.copy(
                            lastBackupTime = formatBackupTime(info.modifiedTime),
                            lastBackupSize = formatBytes(info.sizeBytes),
                        )
                    }
                }
            }
        }
    }

    private fun loadFitbitStatus() {
        viewModelScope.launch {
            val connected = fitbitAuthManager.tryRestoreTokens()
            _state.update { it.copy(fitbitConnected = connected) }
        }
    }

    private fun loadGoogleHealthStatus() {
        viewModelScope.launch {
            val connected = googleHealthAuthManager.isConnected()
            _state.update { it.copy(googleHealthSignedIn = connected) }
        }
    }

    fun onFitbitConnected() {
        _state.update { it.copy(fitbitConnected = true) }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun formatBackupTime(isoUtc: String): String = try {
        val instant = java.time.Instant.parse(isoUtc)
        val local = instant.atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").format(local)
    } catch (_: Exception) {
        isoUtc
    }
}
