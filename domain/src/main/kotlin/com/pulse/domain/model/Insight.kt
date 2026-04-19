package com.pulse.domain.model

data class Insight(
    val id: String,
    val date: String,
    val type: InsightType,
    val metric: String,
    val category: InsightCategory,
    val headline: String,
    val body: String,
    val sentiment: InsightSentiment,
    val score: Float,
    val signalValue: Double?,
)

enum class InsightType {
    CircadianDelta, SupportLevel, BasalTrend,
    Streak, PersonalRecord, GoalConsistency,
    Anomaly, PaceTrajectory, WoW, MoM,
}

enum class InsightCategory { Daily, Weekly, Monthly, Longitudinal }

enum class InsightSentiment { Positive, Neutral, Negative, Celebratory }
