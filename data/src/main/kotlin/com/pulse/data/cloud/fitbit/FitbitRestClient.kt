package com.pulse.data.cloud.fitbit

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FitbitAPI"
private const val BASE_URL = "https://api.fitbit.com"

/**
 * Low-level Fitbit Web API v1 client.
 * All methods require a valid access token (managed by [FitbitAuthManager]).
 */
@Singleton
class FitbitRestClient @Inject constructor(
    private val httpClient: HttpClient,
    private val authManager: FitbitAuthManager,
) {
    private var rateLimitRemaining: Int = 150

    /**
     * Fetch activity logs (exercise sessions) after a given date.
     * Automatically paginates to fetch all available activities.
     */
    suspend fun fetchActivityLogs(
        afterDate: String,
        limit: Int = 100,
    ): List<FitbitActivity> {
        val all = mutableListOf<FitbitActivity>()
        var offset = 0
        var hasMore = true

        while (hasMore) {
            val response = authedGet<FitbitActivityListResponse>(
                "$BASE_URL/1/user/-/activities/list.json" +
                    "?afterDate=$afterDate&sort=asc&limit=$limit&offset=$offset"
            )
            if (response == null) break
            all.addAll(response.activities)

            hasMore = response.pagination?.next != null && response.activities.size == limit
            offset += limit
        }

        Log.d(TAG, "Fetched ${all.size} activity logs after $afterDate")
        return all
    }

    /**
     * Fetch daily step counts for a date range.
     * Max range: 1095 days (3 years).
     */
    suspend fun fetchStepsSeries(startDate: String, endDate: String): List<FitbitTimeSeriesEntry> {
        val response = authedGet<FitbitTimeSeriesResponse>(
            "$BASE_URL/1/user/-/activities/steps/date/$startDate/$endDate.json"
        )
        return response?.steps ?: emptyList()
    }

    /**
     * Fetch daily distance for a date range (miles).
     */
    suspend fun fetchDistanceSeries(startDate: String, endDate: String): List<FitbitTimeSeriesEntry> {
        val response = authedGet<FitbitTimeSeriesResponse>(
            "$BASE_URL/1/user/-/activities/distance/date/$startDate/$endDate.json"
        )
        return response?.distance ?: emptyList()
    }

    /**
     * Fetch daily **active** calories for a date range (excludes BMR).
     * Uses /activityCalories/ not /calories/ which includes BMR.
     */
    suspend fun fetchActiveCaloriesSeries(startDate: String, endDate: String): List<FitbitTimeSeriesEntry> {
        val response = authedGet<FitbitTimeSeriesResponse>(
            "$BASE_URL/1/user/-/activities/activityCalories/date/$startDate/$endDate.json"
        )
        return response?.activityCalories ?: emptyList()
    }

    /**
     * Fetch fairly active minutes for a date range.
     */
    suspend fun fetchFairlyActiveMinutes(startDate: String, endDate: String): List<FitbitTimeSeriesEntry> {
        val response = authedGet<FitbitTimeSeriesResponse>(
            "$BASE_URL/1/user/-/activities/minutesFairlyActive/date/$startDate/$endDate.json"
        )
        return response?.fairlyActive ?: emptyList()
    }

    /**
     * Fetch very active minutes for a date range.
     */
    suspend fun fetchVeryActiveMinutes(startDate: String, endDate: String): List<FitbitTimeSeriesEntry> {
        val response = authedGet<FitbitTimeSeriesResponse>(
            "$BASE_URL/1/user/-/activities/minutesVeryActive/date/$startDate/$endDate.json"
        )
        return response?.veryActive ?: emptyList()
    }

    /**
     * Fetch sleep logs for a date range (max 100 days per request).
     */
    suspend fun fetchSleep(startDate: String, endDate: String): List<FitbitSleepLog> {
        val response = authedGet<FitbitSleepResponse>(
            "$BASE_URL/1.2/user/-/sleep/date/$startDate/$endDate.json"
        )
        return response?.sleep ?: emptyList()
    }

    /**
     * Fetch resting heart rate for a date range (max 30 days per request).
     */
    suspend fun fetchHeartRate(startDate: String, endDate: String): List<FitbitDailyHeartRate> {
        val response = authedGet<FitbitHeartRateResponse>(
            "$BASE_URL/1/user/-/activities/heart/date/$startDate/$endDate.json"
        )
        return response?.activitiesHeart ?: emptyList()
    }

    /**
     * Fetch weight logs for a date range (max 31 days per request).
     */
    suspend fun fetchWeight(startDate: String, endDate: String): List<FitbitWeightLog> {
        val response = authedGet<FitbitWeightResponse>(
            "$BASE_URL/1/user/-/body/log/weight/date/$startDate/$endDate.json"
        )
        return response?.weight ?: emptyList()
    }

    /**
     * Fetch SpO2 for a single date.
     */
    suspend fun fetchSpO2(date: String): FitbitSpO2Response? {
        return authedGet("$BASE_URL/1/user/-/spo2/date/$date.json")
    }

    fun getRateLimitRemaining(): Int = rateLimitRemaining

    private suspend inline fun <reified T> authedGet(url: String): T? {
        val token = authManager.getAccessToken()
            ?: throw IllegalStateException("Not authenticated with Fitbit")

        val response: HttpResponse = httpClient.get(url) {
            header("Authorization", "Bearer $token")
            // Force imperial units so distance is always returned in miles,
            // regardless of the user's Fitbit profile locale setting.
            header("Accept-Language", "en_US")
            timeout {
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }

        // Track rate limit from response headers
        response.headers["Fitbit-Rate-Limit-Remaining"]?.toIntOrNull()?.let {
            rateLimitRemaining = it
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body<T>()
            HttpStatusCode.TooManyRequests -> {
                val resetSec = response.headers["Fitbit-Rate-Limit-Reset"]?.toIntOrNull() ?: 3600
                Log.w(TAG, "Rate limited. Resets in ${resetSec}s")
                rateLimitRemaining = 0
                null
            }
            HttpStatusCode.Unauthorized -> {
                Log.w(TAG, "Unauthorized — token may be invalid")
                null
            }
            else -> {
                Log.w(TAG, "Unexpected status ${response.status} for $url")
                null
            }
        }
    }
}
