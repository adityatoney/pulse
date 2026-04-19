package com.pulse.domain.repository

import com.pulse.domain.model.Insight
import kotlinx.coroutines.flow.Flow

interface InsightsRepository {
    fun observeByContext(date: String, context: String, limit: Int = 2): Flow<List<Insight>>
    fun observeByContextAndMetric(date: String, context: String, metric: String, limit: Int = 2): Flow<List<Insight>>
    fun observeByCategory(category: String, start: String, end: String): Flow<List<Insight>>
}
