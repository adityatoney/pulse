package com.pulse.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "goals", primaryKeys = ["metric"])
data class GoalEntity(
    val metric: String,
    val target: Double,
    val effectiveFromMs: Long,
    val cadence: String, // Daily | Weekly
)
