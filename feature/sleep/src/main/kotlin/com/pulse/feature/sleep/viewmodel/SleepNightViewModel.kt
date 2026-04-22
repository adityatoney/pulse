package com.pulse.feature.sleep.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.domain.model.HrSample
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.SleepSummary
import com.pulse.domain.repository.HealthRepository
import com.pulse.feature.sleep.state.SleepNightState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class SleepNightViewModel @Inject constructor(
    private val healthRepo: HealthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dateStr: String = savedStateHandle.get<String>("dateStr") ?: ""
    private val date: LocalDate = runCatching { LocalDate.parse(dateStr) }.getOrElse { LocalDate(2024, 1, 1) }

    private val _state = MutableStateFlow(SleepNightState(date = dateStr))
    val state: StateFlow<SleepNightState> = _state.asStateFlow()

    init {
        combine(
            healthRepo.observeSleep(date),
            healthRepo.observeIntradayHr(date),
        ) { sleep, hrSamples ->
            Pair(sleep, hrSamples)
        }.onEach { (sleep, hrSamples) ->
            if (sleep != null) {
                val tz = TimeZone.currentSystemDefault()
                val startLocal = sleep.start.toLocalDateTime(tz)
                val endLocal = sleep.end.toLocalDateTime(tz)

                val bedtimeHour = startLocal.hour + startLocal.minute / 60f
                val wakeHour = endLocal.hour + endLocal.minute / 60f

                val inBedMinutes = ((sleep.end - sleep.start).inWholeMinutes)
                val awakeMin = sleep.awakeMinutes ?: 0L
                val efficiency = if (inBedMinutes > 0) {
                    ((inBedMinutes - awakeMin).toFloat() / inBedMinutes * 100f)
                } else 0f

                // Filter HR samples to sleep window
                val sleepHr = hrSamples.filter {
                    it.timestampMs in sleep.start.toEpochMilliseconds()..sleep.end.toEpochMilliseconds()
                }
                val avgHr = if (sleepHr.isNotEmpty()) sleepHr.map { it.bpm }.average().toInt() else null

                // Sleep score: 40% duration vs 8h, 20% efficiency, 20% deep ratio, 20% low awake
                val sleepScore = computeSleepScore(sleep, efficiency)

                _state.update {
                    it.copy(
                        sleep = sleep,
                        durationLabel = formatDuration(sleep.totalMinutes),
                        inBedLabel = formatDuration(inBedMinutes),
                        efficiency = efficiency,
                        bedtimeLabel = formatTime(startLocal.hour, startLocal.minute),
                        wakeTimeLabel = formatTime(endLocal.hour, endLocal.minute),
                        bedtimeHour = bedtimeHour,
                        wakeHour = wakeHour,
                        hrSamples = sleepHr,
                        avgHrDuringSleep = avgHr,
                        sleepScore = sleepScore,
                        isLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }.launchIn(viewModelScope)

        // SpO2
        healthRepo.observeDailyAggregate(date, MetricType.SpO2).onEach { agg ->
            val v = agg.total.takeIf { it > 0 }
            _state.update { it.copy(spo2 = v) }
        }.launchIn(viewModelScope)
    }

    private fun computeSleepScore(sleep: SleepSummary, efficiency: Float): Int {
        // Duration score: 100 if >= 480 min (8h), proportional below
        val durationScore = ((sleep.totalMinutes.toFloat() / 480f) * 100f).coerceIn(0f, 100f)

        // Efficiency score
        val efficiencyScore = efficiency.coerceIn(0f, 100f)

        // Deep sleep ratio: ideal ~20% of total
        val deepMin = sleep.deepMinutes
        val deepRatio = if (sleep.totalMinutes > 0 && deepMin != null) {
            (deepMin.toFloat() / sleep.totalMinutes * 100f)
        } else 0f
        val deepScore = (deepRatio / 20f * 100f).coerceIn(0f, 100f)

        // Low awake: fewer awake minutes = better
        val awakeMin = sleep.awakeMinutes
        val awakeRatio = if (sleep.totalMinutes > 0 && awakeMin != null) {
            (awakeMin.toFloat() / sleep.totalMinutes * 100f)
        } else 0f
        val awakeScore = ((1f - awakeRatio / 30f) * 100f).coerceIn(0f, 100f)

        return (durationScore * 0.4f + efficiencyScore * 0.2f + deepScore * 0.2f + awakeScore * 0.2f).toInt()
    }

    companion object {
        private fun formatDuration(minutes: Long): String {
            val h = minutes / 60
            val m = minutes % 60
            return "${h}h ${m}m"
        }

        private fun formatTime(hour: Int, minute: Int): String {
            val amPm = if (hour < 12) "AM" else "PM"
            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            return "$h12:${"%02d".format(minute)} $amPm"
        }
    }
}
