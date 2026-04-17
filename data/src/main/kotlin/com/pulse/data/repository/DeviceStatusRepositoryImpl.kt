package com.pulse.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.pulse.domain.model.DeviceStatus
import com.pulse.domain.model.UserChrome
import com.pulse.domain.repository.DeviceStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceStatusRepository {

    override fun observeDevice(): Flow<DeviceStatus> = flow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        emit(DeviceStatus(batteryPct = pct, model = android.os.Build.MODEL, connected = true))
    }

    override fun observeUser(): Flow<UserChrome> = flow {
        emit(UserChrome(displayName = "You", avatarUrl = null))
    }
}
