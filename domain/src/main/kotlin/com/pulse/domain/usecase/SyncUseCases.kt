package com.pulse.domain.usecase

import com.pulse.domain.model.SyncOutcome
import com.pulse.domain.model.SyncStatus
import com.pulse.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSyncStatusUseCase @Inject constructor(private val repo: SyncRepository) {
    operator fun invoke(): Flow<SyncStatus> = repo.observeStatus()
}

class ForceSyncUseCase @Inject constructor(private val repo: SyncRepository) {
    suspend operator fun invoke(): SyncOutcome = repo.forceSyncNow()
}
