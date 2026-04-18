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

/** Result of [DriveAuthManager.requestAuth]. */
sealed class DriveAuthOutcome {
    data class Authorized(val token: String) : DriveAuthOutcome()
    data class ConsentRequired(val pendingIntent: PendingIntent) : DriveAuthOutcome()
}

/**
 * Manages Google OAuth 2.0 tokens for the Google Drive backup API.
 * Uses AuthorizationClient from Google Identity Services to request
 * the `drive.appdata` scope and obtain access tokens.
 */
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    val isAuthenticated: Boolean get() = cachedToken != null

    /**
     * Request authorization for Drive appdata scope.
     * Returns [DriveAuthOutcome.Authorized] if already granted,
     * or [DriveAuthOutcome.ConsentRequired] with a PendingIntent to launch.
     */
    suspend fun requestAuth(activity: Activity): DriveAuthOutcome {
        if (isAuthenticated && !isExpired()) {
            val token = getAccessToken()
            if (token != null) return DriveAuthOutcome.Authorized(token)
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
            .build()

        val result = Identity.getAuthorizationClient(activity)
            .authorize(request)
            .await()

        return if (result.hasResolution()) {
            val pi = result.pendingIntent
                ?: throw IllegalStateException("hasResolution() but no PendingIntent")
            DriveAuthOutcome.ConsentRequired(pi)
        } else {
            val token = result.accessToken
                ?: throw IllegalStateException("No access token returned")
            cacheToken(token)
            DriveAuthOutcome.Authorized(token)
        }
    }

    /**
     * Extract the access token from the consent result intent
     * (returned by the PendingIntent launched from [DriveAuthOutcome.ConsentRequired]).
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
        private const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}
