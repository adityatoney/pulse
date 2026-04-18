package com.pulse.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "compute_queue",
    primaryKeys = ["date", "metric"],
)
data class ComputeQueueEntity(
    val date: String,
    val metric: String,
    val enqueuedAtMs: Long,
)
