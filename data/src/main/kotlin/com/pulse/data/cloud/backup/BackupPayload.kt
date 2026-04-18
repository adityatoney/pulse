package com.pulse.data.cloud.backup

import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseHrSampleEntity
import com.pulse.data.local.entity.ExerciseLapEntity
import com.pulse.data.local.entity.ExerciseRoutePointEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.GoalEntity
import com.pulse.data.local.entity.HealthSampleEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val dbVersion: Int,
    val appVersion: String,
    val createdAtMs: Long,
    val dailyAggregates: List<DailyAggregateEntity>,
    val exerciseSessions: List<ExerciseSessionEntity>,
    val exerciseHrSamples: List<ExerciseHrSampleEntity>,
    val exerciseLaps: List<ExerciseLapEntity>,
    val exerciseRoutePoints: List<ExerciseRoutePointEntity>,
    val healthSamples: List<HealthSampleEntity>,
    val sleepSessions: List<SleepSessionEntity>,
    val syncState: List<SyncStateEntity>,
    val goals: List<GoalEntity>,
)

@Serializable
data class BackupMetadata(
    val fileId: String,
    val modifiedTime: String,
    val sizeBytes: Long,
)
