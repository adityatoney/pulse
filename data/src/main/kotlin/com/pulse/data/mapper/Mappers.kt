package com.pulse.data.mapper

import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.SummaryDailyMetricEntity
import com.pulse.domain.model.DailyAggregate
import com.pulse.domain.model.DataSource
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.model.MetricType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

fun SummaryDailyMetricEntity.toDomain(): DailyAggregate = DailyAggregate(
    date = LocalDate.parse(date),
    metric = MetricType.valueOf(metric),
    total = total,
    goal = goal,
    sampleCount = sampleCount,
    computedAt = Instant.fromEpochMilliseconds(computedAtMs),
)

fun ExerciseSessionEntity.toDomain(): ExerciseSession = ExerciseSession(
    id = id,
    type = type,
    start = Instant.fromEpochMilliseconds(startUtcMs),
    end = Instant.fromEpochMilliseconds(endUtcMs),
    distanceMeters = distanceMeters,
    calories = calories,
    avgHr = avgHr,
    maxHr = maxHr,
    zoneMinutes = zoneMinutes,
    source = DataSource.HealthConnect,
)

fun ExerciseSession.toEntity(sourceJson: String? = null, dirty: Boolean = true): ExerciseSessionEntity =
    ExerciseSessionEntity(
        id = id,
        type = type,
        startUtcMs = start.toEpochMilliseconds(),
        endUtcMs = end.toEpochMilliseconds(),
        distanceMeters = distanceMeters,
        calories = calories,
        avgHr = avgHr,
        maxHr = maxHr,
        sourceJson = sourceJson,
        dirty = dirty,
    )
