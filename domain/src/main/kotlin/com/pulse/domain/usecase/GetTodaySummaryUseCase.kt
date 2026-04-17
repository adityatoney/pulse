package com.pulse.domain.usecase

import com.pulse.domain.model.TodaySummary
import com.pulse.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class GetTodaySummaryUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(date: LocalDate): Flow<TodaySummary> =
        health.observeTodaySummary(date)
}
