package com.pulse.data.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Result of [GoogleHealthAuthManager.requestAuth]. */
sealed class GoogleHealthAuthOutcome {
    data class Authorized(val token: String) : GoogleHealthAuthOutcome()
    data class ConsentRequired(val pendingIntent: PendingIntent) : GoogleHealthAuthOutcome()
}

/**
 * Manages Google OAuth 2.0 tokens for the Google Health REST API.
 * Uses AuthorizationClient from Google Identity Services to request
 * health-related scopes and obtain access tokens.
 */
@Singleton
class GoogleHealthAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    val isAuthenticated: Boolean get() = cachedToken != null

    /**
     * Request authorization for Google Health scopes.
     * Returns [GoogleHealthAuthOutcome.Authorized] if already granted,
     * or [GoogleHealthAuthOutcome.ConsentRequired] with a PendingIntent to launch.
     */
    suspend fun requestAuth(activity: Activity): GoogleHealthAuthOutcome {
        if (isAuthenticated && !isExpired()) {
            val token = getAccessToken()
            if (token != null) return GoogleHealthAuthOutcome.Authorized(token)
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES.map { Scope(it) })
            .build()

        val result = Identity.getAuthorizationClient(activity)
            .authorize(request)
            .await()

        return if (result.hasResolution()) {
            val pi = result.pendingIntent
                ?: throw IllegalStateException("hasResolution() but no PendingIntent")
            GoogleHealthAuthOutcome.ConsentRequired(pi)
        } else {
            val token = result.accessToken
                ?: throw IllegalStateException("No access token returned")
            cacheToken(token)
            GoogleHealthAuthOutcome.Authorized(token)
        }
    }

    /**
     * Extract the access token from the consent result intent
     * (returned by the PendingIntent launched from [GoogleHealthAuthOutcome.ConsentRequired]).
     */
    suspend fun handleConsentResult(activity: Activity, data: Intent?): Result<String> = runCatching {
        val authResult = Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(data)
        val token = authResult.accessToken
            ?: throw IllegalStateException("No access token in consent result")
        cacheToken(token)
        token
    }

    suspend fun getAccessToken(): String? = mutex.withLock {
        val token = cachedToken ?: return null
        if (isExpired()) {
            cachedToken = null
            null
        } else {
            token
        }
    }

    suspend fun signOut() {
        mutex.withLock {
            cachedToken = null
            tokenExpiresAtMs = 0L
        }
    }

    private suspend fun cacheToken(token: String) {
        mutex.withLock {
            cachedToken = token
            tokenExpiresAtMs = System.currentTimeMillis() + 55 * 60 * 1000L
        }
    }

    private fun isExpired(): Boolean = System.currentTimeMillis() >= tokenExpiresAtMs

    companion object {
        private val SCOPES = listOf(
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
        )
    }
}
