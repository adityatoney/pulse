package com.pulse.data.repository

import android.util.Log
import com.pulse.data.cloud.fitbit.FitbitAuthManager
import com.pulse.data.cloud.fitbit.FitbitRestClient
import com.pulse.domain.model.DeviceStatus
import com.pulse.domain.model.UserChrome
import com.pulse.domain.repository.DeviceStatusRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceStatus"
private const val POLL_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

@Singleton
class DeviceStatusRepositoryImpl @Inject constructor(
    private val fitbitClient: FitbitRestClient,
    private val authManager: FitbitAuthManager,
) : DeviceStatusRepository {

    override fun observeDevice(): Flow<DeviceStatus> = flow {
        while (true) {
            val status = fetchWatchStatus()
            emit(status)
            // Retry sooner on failure, otherwise poll every 15 min
            delay(if (status.connected) POLL_INTERVAL_MS else 30_000L)
        }
    }

    private suspend fun fetchWatchStatus(): DeviceStatus {
        // Ensure tokens are loaded from storage
        authManager.tryRestoreTokens()
        return try {
            val devices = fitbitClient.fetchDevices()
            Log.d(TAG, "Fetched ${devices.size} devices")
            // Find the tracker (watch), not scales
            val tracker = devices.firstOrNull { it.type == "TRACKER" }
            if (tracker != null) {
                val pct = tracker.batteryLevel ?: batteryLabelToPct(tracker.battery)
                Log.d(TAG, "Tracker: ${tracker.deviceVersion}, battery=$pct% (${tracker.battery})")
                DeviceStatus(
                    batteryPct = pct,
                    model = tracker.deviceVersion,
                    connected = true,
                )
            } else {
                Log.d(TAG, "No TRACKER device found in: ${devices.map { "${it.deviceVersion}(${it.type})" }}")
                DeviceStatus(batteryPct = -1, model = "No tracker", connected = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch device status: ${e.message}")
            DeviceStatus(batteryPct = -1, model = "Unknown", connected = false)
        }
    }

    private fun batteryLabelToPct(label: String): Int = when (label) {
        "High" -> 80
        "Medium" -> 50
        "Low" -> 20
        "Empty" -> 5
        else -> -1
    }

    override fun observeUser(): Flow<UserChrome> = flow {
        emit(UserChrome(displayName = "You", avatarUrl = null))
    }
}
