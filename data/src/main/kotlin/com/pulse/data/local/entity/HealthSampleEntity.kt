package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "health_samples")
data class HealthSampleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val value: Double,
    val unit: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val source: String,
    val dirty: Boolean = true,
)
