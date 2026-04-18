package com.pulse.domain.model

/** Every metric the app ingests, derives, or displays. */
enum class MetricType {
    Steps,
    Distance,
    Calories,
    ActiveCalories,
    ZoneMinutes,
    HeartRate,
    RestingHeartRate,
    Sleep,
    Floors,
    Speed,
    Exercise,
    Weight,
    BodyFat,
    SpO2,
    SkinTemperature,
    HRV,
    VO2Max,

    /** @deprecated No longer used — Distance summary now handles activity-only via compute engine. */
    @Deprecated("Use Distance with activityOnlyDistance preference instead")
    ExerciseDistance,
    /** @deprecated No longer used — ActiveCalories summary now handles activity-only via compute engine. */
    @Deprecated("Use ActiveCalories with activityOnlyCalories preference instead")
    ExerciseCalories,
}
