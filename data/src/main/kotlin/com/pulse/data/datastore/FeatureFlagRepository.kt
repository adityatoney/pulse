package com.pulse.data.datastore

import androidx.datastore.core.DataStore
import com.pulse.data.proto.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing snapshot of [FeatureFlags], shielded from the generated proto type. */
data class FeatureFlagSnapshot(
    val sharedElementTransitions: Boolean,
    val vicoGradientBars: Boolean,
    val wowMomOnDashboard: Boolean,
    val googleHealthReconcile: Boolean,
    val forceDarkMode: Boolean,
    val faultInjectionActive: Boolean,
    val faultInjectionExpiresAtMs: Long,
    val useDynamicColor: Boolean,
) {
    companion object {
        val Default = FeatureFlagSnapshot(
            sharedElementTransitions = true,
            vicoGradientBars = true,
            wowMomOnDashboard = true,
            googleHealthReconcile = false,
            forceDarkMode = false,
            faultInjectionActive = false,
            faultInjectionExpiresAtMs = 0L,
            useDynamicColor = false,
        )
    }
}

enum class FeatureFlagKey {
    SharedElementTransitions,
    VicoGradientBars,
    WowMomOnDashboard,
    GoogleHealthReconcile,
    ForceDarkMode,
    UseDynamicColor,
}

@Singleton
class FeatureFlagRepository @Inject constructor(
    private val store: DataStore<FeatureFlags>,
) {
    fun observe(): Flow<FeatureFlagSnapshot> =
        store.data.map { it.toSnapshot() }

    suspend fun setFlag(key: FeatureFlagKey, value: Boolean) {
        store.updateData { flags ->
            flags.toBuilder().apply {
                when (key) {
                    FeatureFlagKey.SharedElementTransitions -> sharedElementTransitions = value
                    FeatureFlagKey.VicoGradientBars -> vicoGradientBars = value
                    FeatureFlagKey.WowMomOnDashboard -> wowMomOnDashboard = value
                    FeatureFlagKey.GoogleHealthReconcile -> googleHealthReconcile = value
                    FeatureFlagKey.ForceDarkMode -> forceDarkMode = value
                    FeatureFlagKey.UseDynamicColor -> useDynamicColor = value
                }
            }.build()
        }
    }

    suspend fun setFaultInjection(activeForMs: Long) {
        store.updateData { flags ->
            flags.toBuilder()
                .setFaultInjectionActive(activeForMs > 0L)
                .setFaultInjectionExpiresAtMs(
                    if (activeForMs > 0L) System.currentTimeMillis() + activeForMs else 0L
                )
                .build()
        }
    }

    private fun FeatureFlags.toSnapshot() = FeatureFlagSnapshot(
        sharedElementTransitions = sharedElementTransitions,
        vicoGradientBars = vicoGradientBars,
        wowMomOnDashboard = wowMomOnDashboard,
        googleHealthReconcile = googleHealthReconcile,
        forceDarkMode = forceDarkMode,
        faultInjectionActive = faultInjectionActive,
        faultInjectionExpiresAtMs = faultInjectionExpiresAtMs,
        useDynamicColor = useDynamicColor,
    )
}
