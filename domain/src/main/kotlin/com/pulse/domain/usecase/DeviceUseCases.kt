package com.pulse.domain.usecase

import com.pulse.domain.model.DeviceStatus
import com.pulse.domain.model.UserChrome
import com.pulse.domain.repository.DeviceStatusRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDeviceStatusUseCase @Inject constructor(private val repo: DeviceStatusRepository) {
    operator fun invoke(): Flow<DeviceStatus> = repo.observeDevice()
}

class ObserveUserChromeUseCase @Inject constructor(private val repo: DeviceStatusRepository) {
    operator fun invoke(): Flow<UserChrome> = repo.observeUser()
}
