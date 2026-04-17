package com.pulse.domain.usecase

import com.pulse.domain.model.Goal
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.GoalsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGoalsUseCase @Inject constructor(private val repo: GoalsRepository) {
    operator fun invoke(): Flow<Map<MetricType, Goal>> = repo.observeGoals()
}

class SetGoalUseCase @Inject constructor(private val repo: GoalsRepository) {
    suspend operator fun invoke(goal: Goal) = repo.setGoal(goal)
}
