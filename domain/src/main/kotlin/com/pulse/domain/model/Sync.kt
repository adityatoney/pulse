package com.pulse.domain.model

import kotlinx.datetime.Instant

enum class SyncEntity { DailyAggregate, ExerciseSession, HealthSample }

sealed interface SyncOutcome {
    data class Ok(val pushed: Int) : SyncOutcome
    data class Failure(val message: String) : SyncOutcome
    data object NoOp : SyncOutcome
}

enum class SyncPhase { Idle, Syncing, Stale, Failed }

data class SyncStatus(
    val lastSyncedAt: Instant?,
    val state: SyncPhase,
    val pendingItems: Int,
    val lastError: String? = null,
)

data class SyncRecord(
    val id: String,
    val entity: SyncEntity,
    val localVersion: Long,
    val remoteVersion: Long?,
    val state: SyncPhase,
    val lastAttempt: Instant?,
    val attempts: Int,
)

data class DeviceStatus(
    val batteryPct: Int,
    val model: String,
    val connected: Boolean,
)

data class UserChrome(
    val displayName: String,
    val avatarUrl: String?,
)
