package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val totalMinutes: Long,
    val deepMinutes: Long?,
    val remMinutes: Long?,
    val lightMinutes: Long?,
    val awakeMinutes: Long?,
    val sourceJson: String?,
    val dirty: Boolean = true,
)
