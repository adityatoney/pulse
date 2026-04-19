package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "insights",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["context", "date"]),
        Index(value = ["category", "date"]),
    ],
)
data class InsightEntity(
    val id: String,                 // "{date}:{type}:{metric}" deterministic key
    val date: String,               // anchor date (yyyy-MM-dd)
    val type: String,               // CircadianDelta, SupportLevel, BasalTrend, Streak, PersonalRecord, etc.
    val metric: String,             // Steps, RestingHeartRate, HRV, etc.
    val category: String,           // Daily, Weekly, Longitudinal
    val context: String,            // Comma-separated: InsightsTab, Dashboard, DetailDay, DetailWeek, etc.
    val headline: String,           // Short signal text
    val body: String,               // Longer explanation
    val sentiment: String,          // Positive, Neutral, Negative, Celebratory
    val score: Float,               // 0..1 ranking priority
    val signalValue: Double?,       // The computed delta/percentage
    val metadata: String?,          // JSON blob for extra data
    val computedAtMs: Long,
)
