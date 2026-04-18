package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "raw_samples",
    indices = [
        Index(value = ["type", "startUtcMs", "endUtcMs"]),
        Index(value = ["externalId"], unique = true),
    ],
)
data class RawSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // "HeartRate", "Weight", "SpO2", etc.
    val value: Double,
    val unit: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val source: String,         // "HealthConnect", "Fitbit"
    val externalId: String?,    // For dedup: HC metadata.id or fitbit-{logId}
    val ingestedAtMs: Long,
)
