package com.pulse.data.cloud.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Google Health REST API v4 reconcile responses.
 * Endpoint: GET /v4/users/me/dataTypes/{type}/dataPoints:reconcile
 */

@Serializable
data class ReconcileResponse(
    @SerialName("dataPoints") val dataPoints: List<ReconcileDataPoint> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

@Serializable
data class ReconcileDataPoint(
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
    @SerialName("values") val values: List<ReconcileValue> = emptyList(),
    @SerialName("dataSource") val dataSource: ReconcileDataSource? = null,
)

@Serializable
data class ReconcileValue(
    @SerialName("intVal") val intVal: Long? = null,
    @SerialName("fpVal") val fpVal: Double? = null,
    @SerialName("stringVal") val stringVal: String? = null,
) {
    fun asDouble(): Double = fpVal ?: intVal?.toDouble() ?: 0.0
    fun asLong(): Long = intVal ?: fpVal?.toLong() ?: 0L
}

@Serializable
data class ReconcileDataSource(
    @SerialName("dataSourceId") val dataSourceId: String? = null,
    @SerialName("device") val device: ReconcileDevice? = null,
)

@Serializable
data class ReconcileDevice(
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("type") val type: String? = null,
)

@Serializable
data class SleepSessionResponse(
    @SerialName("sessions") val sessions: List<SleepSession> = emptyList(),
)

@Serializable
data class SleepSession(
    @SerialName("id") val id: String,
    @SerialName("startTimeMillis") val startTimeMillis: Long,
    @SerialName("endTimeMillis") val endTimeMillis: Long,
    @SerialName("stages") val stages: List<SleepStage> = emptyList(),
)

@Serializable
data class SleepStage(
    @SerialName("startTimeMillis") val startTimeMillis: Long,
    @SerialName("endTimeMillis") val endTimeMillis: Long,
    @SerialName("stage") val stage: Int, // 1=Awake, 2=Sleep, 3=Out-of-bed, 4=Light, 5=Deep, 6=REM
)
