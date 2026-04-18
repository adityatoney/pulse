package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "raw_daily_metrics",
    primaryKeys = ["date", "metric", "source"],
    indices = [Index(value = ["date", "metric"])],
)
data class RawDailyMetricEntity(
    val date: String,           // ISO yyyy-MM-dd
    val metric: String,         // MetricType.name
    val source: String,         // "HealthConnect", "Fitbit", "GoogleHealth", "legacy"
    val value: Double,
    val unit: String,           // "count", "miles", "kcal", etc.
    val externalId: String?,    // Dedup key: "hc-steps-2026-04-17"
    val ingestedAtMs: Long,
)
