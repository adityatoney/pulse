package com.pulse.data.repository

import com.pulse.data.local.dao.InsightDao
import com.pulse.data.local.entity.InsightEntity
import com.pulse.domain.model.Insight
import com.pulse.domain.model.InsightCategory
import com.pulse.domain.model.InsightSentiment
import com.pulse.domain.model.InsightType
import com.pulse.domain.repository.InsightsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsRepositoryImpl @Inject constructor(
    private val insightDao: InsightDao,
) : InsightsRepository {

    override fun observeByContext(date: String, context: String, limit: Int): Flow<List<Insight>> =
        insightDao.observeByContext(date, context, limit).map { it.map(::toDomain) }

    override fun observeByContextAndMetric(date: String, context: String, metric: String, limit: Int): Flow<List<Insight>> =
        insightDao.observeByContextAndMetric(date, context, metric, limit).map { it.map(::toDomain) }

    override fun observeByCategory(category: String, start: String, end: String): Flow<List<Insight>> =
        insightDao.observeByCategory(category, start, end).map { it.map(::toDomain) }

    private fun toDomain(entity: InsightEntity): Insight = Insight(
        id = entity.id,
        date = entity.date,
        type = runCatching { InsightType.valueOf(entity.type) }.getOrDefault(InsightType.Anomaly),
        metric = entity.metric,
        category = runCatching { InsightCategory.valueOf(entity.category) }.getOrDefault(InsightCategory.Daily),
        headline = entity.headline,
        body = entity.body,
        sentiment = runCatching { InsightSentiment.valueOf(entity.sentiment) }.getOrDefault(InsightSentiment.Neutral),
        score = entity.score,
        signalValue = entity.signalValue,
    )
}
