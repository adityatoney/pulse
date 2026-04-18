package com.pulse.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "summary_daily_metrics",
    primaryKeys = ["date", "metric"],
)
data class SummaryDailyMetricEntity(
    val date: String,
    val metric: String,
    val total: Double,
    val goal: Double?,
    val sampleCount: Int,
    val computedAtMs: Long,
    val computationVersion: Int = 1,
    val sourceUsed: String? = null,
    val dirty: Boolean = true,
    val remoteVersion: Long? = null,
)
