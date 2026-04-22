package com.pulse.feature.sleep.state

import com.pulse.domain.model.HrSample
import com.pulse.domain.model.SleepSummary

data class SleepNightState(
    val date: String = "",
    val sleep: SleepSummary? = null,
    val durationLabel: String = "",
    val inBedLabel: String = "",
    val efficiency: Float = 0f,
    val bedtimeLabel: String = "",
    val wakeTimeLabel: String = "",
    val bedtimeHour: Float = 0f,
    val wakeHour: Float = 0f,
    val hrSamples: List<HrSample> = emptyList(),
    val avgHrDuringSleep: Int? = null,
    val sleepScore: Int? = null,
    val spo2: Double? = null,
    val isLoading: Boolean = true,
)
