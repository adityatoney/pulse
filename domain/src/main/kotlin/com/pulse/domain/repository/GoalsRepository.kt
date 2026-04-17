package com.pulse.domain.repository

import com.pulse.domain.model.Goal
import com.pulse.domain.model.MetricType
import kotlinx.coroutines.flow.Flow

interface GoalsRepository {
    fun observeGoals(): Flow<Map<MetricType, Goal>>
    suspend fun setGoal(goal: Goal)
    suspend fun getGoal(metric: MetricType): Goal?
}
