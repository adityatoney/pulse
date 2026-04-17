package com.pulse.data.repository

import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.entity.GoalEntity
import com.pulse.domain.model.Cadence
import com.pulse.domain.model.Goal
import com.pulse.domain.model.MetricType
import com.pulse.domain.repository.GoalsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalsRepositoryImpl @Inject constructor(
    private val dao: GoalDao,
) : GoalsRepository {

    override fun observeGoals(): Flow<Map<MetricType, Goal>> =
        dao.observeAll().map { rows ->
            rows.associate { r ->
                val metric = MetricType.valueOf(r.metric)
                metric to r.toDomain()
            }
        }

    override suspend fun setGoal(goal: Goal) {
        dao.upsert(
            GoalEntity(
                metric = goal.metric.name,
                target = goal.target,
                effectiveFromMs = goal.effectiveFrom.atStartOfDayMs(),
                cadence = goal.cadence.name,
            )
        )
    }

    override suspend fun getGoal(metric: MetricType): Goal? =
        dao.get(metric.name)?.toDomain()

    private fun GoalEntity.toDomain(): Goal {
        val ldt = Instant.fromEpochMilliseconds(effectiveFromMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return Goal(
            metric = MetricType.valueOf(metric),
            target = target,
            effectiveFrom = ldt.date,
            cadence = Cadence.valueOf(cadence),
        )
    }

    private fun kotlinx.datetime.LocalDate.atStartOfDayMs(): Long {
        val zone = TimeZone.currentSystemDefault()
        return kotlinx.datetime.LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(zone).toEpochMilliseconds()
    }
}
