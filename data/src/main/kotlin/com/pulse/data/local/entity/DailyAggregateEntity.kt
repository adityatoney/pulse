package com.pulse.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "daily_aggregates",
    primaryKeys = ["date", "metric"],
)
data class DailyAggregateEntity(
    val date: String,          // ISO yyyy-MM-dd in user's current timezone
    val metric: String,        // MetricType.name
    val total: Double,
    val goal: Double?,
    val sampleCount: Int,
    val computedAtMs: Long,
    val dirty: Boolean = true,
    val remoteVersion: Long? = null,
)
