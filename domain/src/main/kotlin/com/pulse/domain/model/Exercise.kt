package com.pulse.domain.model

import kotlinx.datetime.Instant

data class ExerciseSession(
    val id: String,
    val type: String,
    val start: Instant,
    val end: Instant,
    val distanceMeters: Double?,
    val calories: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val zoneMinutes: Int? = null,
    val source: DataSource,
) {
    val durationMinutes: Long
        get() = (end.toEpochMilliseconds() - start.toEpochMilliseconds()) / 60_000L
}

data class ExerciseDetail(
    val session: ExerciseSession,
    val steps: Int?,
    val zoneMinutes: Int?,
    val avgPaceSecondsPerMile: Int?,
    val elevationGainMeters: Double?,
    val hrSamples: List<HrSample>,
    val laps: List<ExerciseLap>,
    val route: List<RoutePoint> = emptyList(),
    val routeConsentRequired: Boolean = false,
)

data class HrSample(val timestampMs: Long, val bpm: Int)

data class ExerciseLap(
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationMs: Long,
    val paceSecondsPerMile: Int?,
)

data class RoutePoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
)

data class SleepSummary(
    val start: Instant,
    val end: Instant,
    val totalMinutes: Long,
    val deepMinutes: Long?,
    val remMinutes: Long?,
    val lightMinutes: Long?,
    val awakeMinutes: Long?,
)
