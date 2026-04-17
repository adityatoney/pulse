package com.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_laps",
    foreignKeys = [ForeignKey(
        entity = ExerciseSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class ExerciseLapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationMs: Long,
    val paceSecondsPerMile: Int?,
)
