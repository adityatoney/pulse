package com.pulse.domain.model

import kotlinx.datetime.LocalDate

data class DailyHrRange(
    val date: LocalDate,
    val minBpm: Int,
    val maxBpm: Int,
    val avgBpm: Int,
    val restingBpm: Int?,
)
