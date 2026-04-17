package com.pulse.domain.model

/** Holds a value + goal + derived progress for a single metric ring. */
data class MetricValue<T : Number>(
    val current: T,
    val goal: T,
    val progress: Float,
    val unitLabel: String,
)

data class TodayMetrics(
    val steps: MetricValue<Int>,
    val zoneMinutes: MetricValue<Int>,
    val distanceMiles: MetricValue<Double>,
    val calories: MetricValue<Int>,
    val wow: DeltaPercent? = null,
    val mom: DeltaPercent? = null,
)

data class RecoveryBlock(val sleep: SleepSummary?)

data class TodaySummary(
    val today: TodayMetrics,
    val recovery: RecoveryBlock,
)
