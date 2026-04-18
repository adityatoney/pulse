package com.pulse.feature.dashboard.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.usecase.CalculateMoMUseCase
import com.pulse.domain.usecase.CalculateWoWUseCase
import com.pulse.domain.usecase.GetTodaySummaryUseCase
import com.pulse.domain.usecase.ObserveDeviceStatusUseCase
import com.pulse.domain.usecase.ObserveSyncStatusUseCase
import com.pulse.domain.usecase.ObserveUserChromeUseCase
import com.pulse.domain.usecase.ForceSyncUseCase
import com.pulse.domain.util.Clock
import com.pulse.feature.dashboard.state.DashboardEffect
import com.pulse.feature.dashboard.state.DashboardIntent
import com.pulse.feature.dashboard.state.DashboardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getToday: GetTodaySummaryUseCase,
    private val calcWoW: CalculateWoWUseCase,
    private val calcMoM: CalculateMoMUseCase,
    private val observeSync: ObserveSyncStatusUseCase,
    private val observeDevice: ObserveDeviceStatusUseCase,
    private val observeUser: ObserveUserChromeUseCase,
    private val forceSync: ForceSyncUseCase,
    private val health: HealthRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialDate: LocalDate =
        savedStateHandle.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: clock.today()

    private val initialTimeframe: Timeframe =
        savedStateHandle.get<String>(KEY_TF)?.let(Timeframe::valueOf) ?: Timeframe.Day

    private val _state = MutableStateFlow(
        DashboardState(selectedDate = initialDate, today = clock.today(), timeframe = initialTimeframe)
    )
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _effects = Channel<DashboardEffect>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<DashboardEffect> = _effects.receiveAsFlow()

    private val intents =
        MutableSharedFlow<DashboardIntent>(extraBufferCapacity = 16)

    init {
        intents.onEach(::reduce).launchIn(viewModelScope)

        wireStreams()
        onIntent(DashboardIntent.Load)
    }

    fun onIntent(intent: DashboardIntent) { intents.tryEmit(intent) }

    private fun wireStreams() {
        val date = _state.value.selectedDate
        combine(
            getToday(date),
            calcWoW(MetricType.Steps, date),
            calcMoM(MetricType.Steps, date),
            observeSync(),
            observeDevice(),
        ) { today, wow, mom, sync, device ->
            Quint(today, wow, mom, sync, device)
        }.onEach { (today, wow, mom, sync, device) ->
            _state.update {
                it.copy(
                    isLoading = false,
                    metrics = today.today,
                    recovery = today.recovery,
                    wow = wow,
                    mom = mom,
                    sync = sync,
                    device = device,
                )
            }
        }.catch { t ->
            _state.update { it.copy(isLoading = false, error = com.pulse.feature.dashboard.state.DashboardError.Unknown(t.message ?: "Error")) }
        }.launchIn(viewModelScope)

        observeUser().onEach { chrome ->
            _state.update { it.copy(user = chrome) }
        }.launchIn(viewModelScope)

        health.observeExerciseSessions(DateRange(date, date)).onEach { sessions ->
            _state.update { it.copy(recentExercises = sessions) }
        }.launchIn(viewModelScope)

        combine(
            health.observeDailyAggregate(date, MetricType.RestingHeartRate),
            health.observeDailyAggregate(date, MetricType.Weight),
            health.observeDailyAggregate(date, MetricType.SpO2),
            health.observeDailyAggregate(date, MetricType.HRV),
        ) { rhr, weight, spo2, hrv ->
            _state.update {
                it.copy(
                    restingHr = rhr.total.takeIf { v -> v > 0 },
                    weight = weight.total.takeIf { v -> v > 0 },
                    spo2 = spo2.total.takeIf { v -> v > 0 },
                    hrv = hrv.total.takeIf { v -> v > 0 },
                )
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun reduce(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Load -> _state.update { it.copy(isLoading = true) }
            DashboardIntent.PullToRefresh -> {
                _state.update { it.copy(isRefreshing = true) }
                viewModelScope.launch {
                    val date = _state.value.selectedDate
                    val range = DateRange(date.minus(DatePeriod(days = 90)), date)
                    runCatching { health.refreshFromHealthConnect(range) }
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
            is DashboardIntent.ChangeDate -> {
                _state.update { it.copy(selectedDate = intent.date) }
                wireStreams()
            }
            is DashboardIntent.ChangeTimeframe -> {
                _state.update { it.copy(timeframe = intent.tf) }
            }
            is DashboardIntent.SelectMetric -> _effects.trySend(DashboardEffect.NavigateToMetricDetail(intent.metric))
            DashboardIntent.OpenExerciseLog -> _effects.trySend(DashboardEffect.NavigateToExerciseLog)
            is DashboardIntent.OpenExerciseDetail -> _effects.trySend(DashboardEffect.NavigateToExerciseDetail(intent.sessionId))
            DashboardIntent.RetrySync, DashboardIntent.ForceSyncNow -> {
                val date = _state.value.selectedDate
                val range = DateRange(date.minus(DatePeriod(days = 90)), date)
                runCatching { health.refreshFromHealthConnect(range) }
            }
            DashboardIntent.RequestPermissions -> _effects.trySend(DashboardEffect.RequestHealthConnectPermissions)
            is DashboardIntent.PermissionsResult -> _state.update { it.copy(permissionsGranted = intent.granted) }
            DashboardIntent.OpenChat -> _effects.trySend(DashboardEffect.NavigateToChat)
            DashboardIntent.OpenProfile -> _effects.trySend(DashboardEffect.NavigateToProfile)
            DashboardIntent.OpenDebugMenu -> _effects.trySend(DashboardEffect.NavigateToDebugMenu)
        }
    }

    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    companion object {
        private const val KEY_DATE = "selected_date"
        private const val KEY_TF = "timeframe"
    }
}
