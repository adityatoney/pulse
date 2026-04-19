package com.pulse.domain.usecase

import com.pulse.domain.model.Insight
import com.pulse.domain.repository.InsightsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInsightsUseCase @Inject constructor(
    private val repo: InsightsRepository,
) {
    operator fun invoke(date: String, context: String, limit: Int = 2): Flow<List<Insight>> =
        repo.observeByContext(date, context, limit)

    fun forMetric(date: String, context: String, metric: String, limit: Int = 2): Flow<List<Insight>> =
        repo.observeByContextAndMetric(date, context, metric, limit)

    fun byCategory(category: String, start: String, end: String): Flow<List<Insight>> =
        repo.observeByCategory(category, start, end)
}
