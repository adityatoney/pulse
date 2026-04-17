package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_sessions")
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val distanceMeters: Double?,
    val calories: Double?,
    val steps: Int? = null,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgPaceSecondsPerMile: Int? = null,
    val elevationGainMeters: Double? = null,
    val zoneMinutes: Int? = null,
    val sourceJson: String?,
    val dirty: Boolean = true,
)
