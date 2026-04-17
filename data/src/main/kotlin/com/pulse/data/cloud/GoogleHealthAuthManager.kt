package com.pulse.data.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pulse.data.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google OAuth 2.0 tokens for the Google Health REST API.
 *
 * Flow:
 * 1. User signs in via Credential Manager → we get a Google ID token
 * 2. Exchange the ID token for an access token + refresh token via Google's token endpoint
 * 3. Cache the access token; refresh it when it expires
 */
@Singleton
class GoogleHealthAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
) {
    private val mutex = Mutex()
    private var cachedToken: TokenInfo? = null

    val isAuthenticated: Boolean get() = cachedToken != null

    /**
     * Launch the Google Sign-In flow via Credential Manager.
     * Must be called from an Activity context.
     */
    suspend fun signIn(activityContext: android.app.Activity): Result<String> = runCatching {
        val credentialManager = CredentialManager.create(activityContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_HEALTH_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleId = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleId.idToken

            // Exchange ID token for access token via authorization code flow
            val tokenResponse = exchangeIdTokenForAccessToken(idToken)
            mutex.withLock {
                cachedToken = tokenResponse
            }
            tokenResponse.accessToken
        } else {
            throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     * Returns null if not authenticated.
     */
    suspend fun getAccessToken(): String? = mutex.withLock {
        val token = cachedToken ?: return null
        if (token.isExpired()) {
            val refreshed = refreshAccessToken(token.refreshToken ?: return null)
            cachedToken = refreshed
            refreshed.accessToken
        } else {
            token.accessToken
        }
    }

    suspend fun signOut() {
        mutex.withLock { cachedToken = null }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private suspend fun exchangeIdTokenForAccessToken(idToken: String): TokenInfo {
        val response: TokenResponse = httpClient.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", idToken)
                append("client_id", BuildConfig.GOOGLE_HEALTH_WEB_CLIENT_ID)
                append("scope", SCOPES)
            },
        ).body()

        return TokenInfo(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMs = System.currentTimeMillis() + (response.expiresIn * 1000L) - 60_000L,
        )
    }

    private suspend fun refreshAccessToken(refreshToken: String): TokenInfo {
        val response: TokenResponse = httpClient.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", BuildConfig.GOOGLE_HEALTH_WEB_CLIENT_ID)
            },
        ).body()

        return TokenInfo(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken ?: refreshToken,
            expiresAtMs = System.currentTimeMillis() + (response.expiresIn * 1000L) - 60_000L,
        )
    }

    private data class TokenInfo(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("token_type") val tokenType: String = "Bearer",
    )

    companion object {
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val SCOPES =
            "https://www.googleapis.com/auth/health.activity_and_fitness.readonly " +
            "https://www.googleapis.com/auth/health.health_metrics_and_measurements.readonly " +
            "https://www.googleapis.com/auth/health.sleep.readonly"
    }
}
