package com.pulse.data.cloud

import com.pulse.data.cloud.dto.ReconcileResponse
import com.pulse.data.cloud.dto.ReconcileValue
import com.pulse.domain.model.DateRange
import com.pulse.domain.model.SleepSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [GoogleHealthRemoteDataSource] using the
 * Google Health REST API v4 `reconcile` endpoint.
 *
 * The `reconcile` endpoint applies server-side conflict resolution,
 * deduplicating data from phone + watch and returning authoritative values.
 * We filter by `dataSourceFamily=users/me/dataSourceFamilies/google-wearables`
 * to prefer clean watch data.
 */
@Singleton
class GoogleHealthRestClient @Inject constructor(
    private val httpClient: HttpClient,
    private val authManager: GoogleHealthAuthManager,
) : GoogleHealthRemoteDataSource {

    override val isAvailable: Boolean get() = authManager.isAuthenticated

    override suspend fun reconcileSteps(range: DateRange): Map<LocalDate, Long> =
        fetchReconciled("com.google.step_count.delta", range).mapValues { (_, points) ->
            points.sumOf { it.firstOrNull()?.asLong() ?: 0L }
        }

    override suspend fun reconcileDistance(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.distance.delta", range).mapValues { (_, points) ->
            // REST API returns meters; we store millimeters for precision
            points.sumOf { it.firstOrNull()?.asDouble() ?: 0.0 } * 1000.0
        }

    override suspend fun reconcileCalories(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.calories.expended", range).mapValues { (_, points) ->
            points.sumOf { it.firstOrNull()?.asDouble() ?: 0.0 }
        }

    override suspend fun reconcileZoneMinutes(range: DateRange): Map<LocalDate, Int> =
        fetchReconciled("com.google.active_minutes", range).mapValues { (_, points) ->
            points.sumOf { it.firstOrNull()?.asLong() ?: 0L }.toInt()
        }

    override suspend fun reconcileWeight(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.weight", range).mapValues { (_, points) ->
            // Take last measurement of the day
            points.lastOrNull()?.firstOrNull()?.asDouble() ?: 0.0
        }

    override suspend fun reconcileHrv(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.heart_rate_variability", range).mapValues { (_, points) ->
            points.lastOrNull()?.firstOrNull()?.asDouble() ?: 0.0
        }

    override suspend fun reconcileSpO2(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.oxygen_saturation", range).mapValues { (_, points) ->
            points.lastOrNull()?.firstOrNull()?.asDouble() ?: 0.0
        }

    override suspend fun reconcileRestingHr(range: DateRange): Map<LocalDate, Double> =
        fetchReconciled("com.google.heart_rate.resting", range).mapValues { (_, points) ->
            points.lastOrNull()?.firstOrNull()?.asDouble() ?: 0.0
        }

    override suspend fun reconcileSleep(range: DateRange): List<SleepSummary> {
        // Sleep uses sessions endpoint, not reconcile
        // For now, we rely on HC SDK for sleep; this is a placeholder
        return emptyList()
    }

    /**
     * Calls the reconcile endpoint for a given data type and groups results by date.
     * Returns a map of date → list of value arrays (one per data point).
     */
    private suspend fun fetchReconciled(
        dataType: String,
        range: DateRange,
    ): Map<LocalDate, List<List<ReconcileValue>>> {
        val token = authManager.getAccessToken() ?: return emptyMap()
        val zone = TimeZone.currentSystemDefault()
        val startMs = range.start.atStartOfDayIn(zone).toEpochMilliseconds()
        val endMs = range.endInclusive.atStartOfDayIn(zone).toEpochMilliseconds() + 86_400_000L

        val result = mutableMapOf<LocalDate, MutableList<List<ReconcileValue>>>()
        var pageToken: String? = null

        do {
            val response: ReconcileResponse = httpClient.get(
                "$BASE_URL/users/me/dataTypes/$dataType/dataPoints:reconcile"
            ) {
                header("Authorization", "Bearer $token")
                parameter("startTimeMillis", startMs)
                parameter("endTimeMillis", endMs)
                parameter("dataSourceFamily", DATA_SOURCE_FAMILY)
                if (pageToken != null) parameter("pageToken", pageToken)
            }.body()

            for (dp in response.dataPoints) {
                val instant = Instant.parse(dp.startTime)
                val date = instant.toLocalDateTime(zone).date
                result.getOrPut(date) { mutableListOf() }.add(dp.values)
            }

            pageToken = response.nextPageToken
        } while (pageToken != null)

        return result
    }

    companion object {
        private const val BASE_URL = "https://health.googleapis.com/v4"
        private const val DATA_SOURCE_FAMILY = "users/me/dataSourceFamilies/google-wearables"
    }
}
