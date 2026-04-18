package com.pulse.data.cloud.fitbit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Activity Log List (/1/user/-/activities/list.json) ----

@Serializable
data class FitbitActivityListResponse(
    val activities: List<FitbitActivity>,
    val pagination: FitbitPagination? = null,
)

@Serializable
data class FitbitActivity(
    val logId: Long,
    val activityName: String,
    val activityTypeId: Int? = null,
    val startTime: String,   // HH:mm:ss
    val startDate: String,   // yyyy-MM-dd
    val duration: Long,      // ms
    val distance: Double? = null,
    val distanceUnit: String? = null, // "Mile" or "Kilometer"
    val calories: Int = 0,
    val steps: Int? = null,
    val averageHeartRate: Int? = null,
    val heartRateZones: List<FitbitHeartRateZone>? = null,
    val activeDuration: Long? = null, // ms
    val source: FitbitSource? = null,
    val originalDuration: Long? = null,
    val originalStartTime: String? = null,
    val elevationGain: Double? = null,
    val pace: Double? = null,       // minutes per unit distance
    val speed: Double? = null,      // km/h or mph
)

@Serializable
data class FitbitHeartRateZone(
    val name: String,         // "Out of Range", "Fat Burn", "Cardio", "Peak"
    val min: Int,
    val max: Int,
    val minutes: Int = 0,
    val caloriesOut: Double = 0.0,
)

@Serializable
data class FitbitSource(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val url: String? = null,
    val trackerFeatures: List<String>? = null,
)

@Serializable
data class FitbitPagination(
    val afterDate: String? = null,
    val limit: Int = 20,
    val next: String? = null,
    val offset: Int = 0,
    val previous: String? = null,
    val sort: String? = null,
)

// ---- Time Series (/1/user/-/activities/{resource}/date/{start}/{end}.json) ----

@Serializable
data class FitbitTimeSeriesResponse(
    @SerialName("activities-steps")
    val steps: List<FitbitTimeSeriesEntry>? = null,
    @SerialName("activities-distance")
    val distance: List<FitbitTimeSeriesEntry>? = null,
    @SerialName("activities-calories")
    val calories: List<FitbitTimeSeriesEntry>? = null,
    @SerialName("activities-activityCalories")
    val activityCalories: List<FitbitTimeSeriesEntry>? = null,
    @SerialName("activities-minutesFairlyActive")
    val fairlyActive: List<FitbitTimeSeriesEntry>? = null,
    @SerialName("activities-minutesVeryActive")
    val veryActive: List<FitbitTimeSeriesEntry>? = null,
)

@Serializable
data class FitbitTimeSeriesEntry(
    val dateTime: String,  // yyyy-MM-dd
    val value: String,     // number as string
)

// ---- Sleep (/1.2/user/-/sleep/date/{start}/{end}.json) ----

@Serializable
data class FitbitSleepResponse(
    val sleep: List<FitbitSleepLog> = emptyList(),
)

@Serializable
data class FitbitSleepLog(
    val logId: Long,
    val dateOfSleep: String,        // yyyy-MM-dd
    val startTime: String,          // ISO datetime
    val endTime: String,            // ISO datetime
    val duration: Long,             // ms
    val minutesAsleep: Int = 0,
    val minutesAwake: Int = 0,
    val type: String? = null,       // "stages" or "classic"
    val levels: FitbitSleepLevels? = null,
    val isMainSleep: Boolean = true,
)

@Serializable
data class FitbitSleepLevels(
    val summary: FitbitSleepStageSummary? = null,
)

@Serializable
data class FitbitSleepStageSummary(
    val deep: FitbitSleepStageDetail? = null,
    val light: FitbitSleepStageDetail? = null,
    val rem: FitbitSleepStageDetail? = null,
    val wake: FitbitSleepStageDetail? = null,
    // Classic sleep tracking (non-stage devices)
    val asleep: FitbitSleepStageDetail? = null,
    val restless: FitbitSleepStageDetail? = null,
    val awake: FitbitSleepStageDetail? = null,
)

@Serializable
data class FitbitSleepStageDetail(
    val count: Int = 0,
    val minutes: Int = 0,
    val thirtyDayAvgMinutes: Int? = null,
)

// ---- Heart Rate (/1/user/-/activities/heart/date/{date}/1d.json) ----

@Serializable
data class FitbitHeartRateResponse(
    @SerialName("activities-heart")
    val activitiesHeart: List<FitbitDailyHeartRate> = emptyList(),
)

@Serializable
data class FitbitDailyHeartRate(
    val dateTime: String,
    val value: FitbitHeartRateValue,
)

@Serializable
data class FitbitHeartRateValue(
    val restingHeartRate: Int? = null,
    val heartRateZones: List<FitbitHeartRateZone> = emptyList(),
)

// ---- Weight (/1/user/-/body/log/weight/date/{start}/{end}.json) ----

@Serializable
data class FitbitWeightResponse(
    val weight: List<FitbitWeightLog> = emptyList(),
)

@Serializable
data class FitbitWeightLog(
    val logId: Long,
    val date: String,        // yyyy-MM-dd
    val time: String,        // HH:mm:ss
    val weight: Double,      // kg
    val bmi: Double? = null,
    val fat: Double? = null, // body fat %
)

// ---- SpO2 (/1/user/-/spo2/date/{date}.json) ----

@Serializable
data class FitbitSpO2Response(
    val value: FitbitSpO2Value? = null,
    val dateTime: String? = null,
)

@Serializable
data class FitbitSpO2Value(
    val avg: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
)
