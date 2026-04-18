package com.pulse.data.datastore

import androidx.datastore.core.DataStore
import com.pulse.data.proto.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class MetricDisplayPrefs(
    val activityOnlyDistance: Boolean = false,
    val activityOnlyCalories: Boolean = false,
)

@Singleton
class PreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    fun observeMetricDisplay(): Flow<MetricDisplayPrefs> =
        store.data.map {
            MetricDisplayPrefs(
                activityOnlyDistance = it.activityOnlyDistance,
                activityOnlyCalories = it.activityOnlyCalories,
            )
        }

    suspend fun getMetricDisplay(): MetricDisplayPrefs =
        observeMetricDisplay().first()

    suspend fun setActivityOnlyDistance(value: Boolean) {
        store.updateData { it.toBuilder().setActivityOnlyDistance(value).build() }
    }

    suspend fun setActivityOnlyCalories(value: Boolean) {
        store.updateData { it.toBuilder().setActivityOnlyCalories(value).build() }
    }
}
