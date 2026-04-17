package com.pulse.domain.repository

import com.pulse.domain.model.DeviceStatus
import com.pulse.domain.model.UserChrome
import kotlinx.coroutines.flow.Flow

interface DeviceStatusRepository {
    fun observeDevice(): Flow<DeviceStatus>
    fun observeUser(): Flow<UserChrome>
}
