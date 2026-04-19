package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "raw_hourly_metrics",
    primaryKeys = ["date", "hour", "metric", "source"],
    indices = [Index(value = ["metric", "date"])],
)
data class RawHourlyMetricEntity(
    val date: String,           // ISO yyyy-MM-dd
    val hour: Int,              // 0-23
    val metric: String,         // Steps, Distance, ActiveCalories
    val value: Double,
    val source: String,         // HealthConnect, Fitbit
    val ingestedAtMs: Long,
)
