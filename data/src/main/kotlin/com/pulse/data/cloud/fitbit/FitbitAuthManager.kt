package com.pulse.data.cloud.fitbit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.pulse.data.BuildConfig
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.SyncStateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FitbitAuth"

/**
 * Manages Fitbit OAuth 2.0 tokens using Authorization Code Grant with PKCE.
 *
 * Flow:
 * 1. App opens browser to Fitbit authorization page
 * 2. User authorizes → Fitbit redirects to pulse://fitbit/callback?code=...
 * 3. App exchanges code for access + refresh tokens
 * 4. Tokens are persisted in SyncStateDao for background worker access
 */
@Singleton
class FitbitAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val syncStateDao: SyncStateDao,
) {
    private val mutex = Mutex()
    private var cachedToken: TokenInfo? = null
    private var pendingCodeVerifier: String? = null

    val isAuthenticated: Boolean get() = cachedToken != null

    /**
     * Build an Intent that launches the Fitbit authorization page in the browser.
     * The caller should startActivity(intent) and wait for the redirect.
     */
    fun buildAuthIntent(): Intent {
        val codeVerifier = generateCodeVerifier()
        pendingCodeVerifier = codeVerifier
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.FITBIT_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("expires_in", "31536000") // 1 year refresh token
            .build()

        Log.d(TAG, "Auth URL built for Fitbit sign-in")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Handle the OAuth redirect callback. Exchange auth code for tokens.
     */
    suspend fun handleRedirect(code: String): Result<String> = runCatching {
        val verifier = pendingCodeVerifier
            ?: throw IllegalStateException("No pending auth flow — call buildAuthIntent() first")
        pendingCodeVerifier = null

        Log.d(TAG, "Exchanging authorization code for tokens")
        val httpResponse = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_challenge_method", "S256")
                append("redirect_uri", REDIRECT_URI)
                append("client_id", BuildConfig.FITBIT_CLIENT_ID)
                append("code_verifier", verifier)
            },
        ) {
            header("Authorization", basicAuthHeader())
        }
        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Log.e(TAG, "Token exchange failed (${httpResponse.status}): $errorBody")
            throw IllegalStateException("Token exchange failed (${httpResponse.status}): $errorBody")
        }
        val response: FitbitTokenResponse = httpResponse.body()

        val tokenInfo = TokenInfo(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.userId,
            expiresAtMs = System.currentTimeMillis() + (response.expiresIn * 1000L) - 60_000L,
        )
        mutex.withLock { cachedToken = tokenInfo }
        persistTokens(tokenInfo)
        Log.d(TAG, "Fitbit auth complete, userId=${response.userId}")
        tokenInfo.accessToken
    }

    /**
     * Try to restore tokens from persistent storage (for background workers / app restart).
     */
    suspend fun tryRestoreTokens(): Boolean {
        if (cachedToken != null) return true
        val accessEntity = syncStateDao.get(KEY_ACCESS_TOKEN) ?: return false
        val refreshEntity = syncStateDao.get(KEY_REFRESH_TOKEN) ?: return false
        val expiresEntity = syncStateDao.get(KEY_EXPIRES_AT)
        val userIdEntity = syncStateDao.get(KEY_USER_ID)

        val tokenInfo = TokenInfo(
            accessToken = accessEntity.value,
            refreshToken = refreshEntity.value,
            userId = userIdEntity?.value,
            expiresAtMs = expiresEntity?.value?.toLongOrNull() ?: 0L,
        )
        mutex.withLock { cachedToken = tokenInfo }
        Log.d(TAG, "Restored tokens from storage, userId=${tokenInfo.userId}")
        return true
    }

    /**
     * Get a valid access token, refreshing if expired.
     */
    suspend fun getAccessToken(): String? = mutex.withLock {
        val token = cachedToken ?: return null
        if (token.isExpired()) {
            Log.d(TAG, "Token expired, refreshing")
            val refreshed = refreshAccessToken(token.refreshToken)
            cachedToken = refreshed
            persistTokens(refreshed)
            refreshed.accessToken
        } else {
            token.accessToken
        }
    }

    suspend fun signOut() {
        Log.d(TAG, "Signing out")
        mutex.withLock { cachedToken = null }
        syncStateDao.remove(KEY_ACCESS_TOKEN)
        syncStateDao.remove(KEY_REFRESH_TOKEN)
        syncStateDao.remove(KEY_EXPIRES_AT)
        syncStateDao.remove(KEY_USER_ID)
    }

    private suspend fun refreshAccessToken(refreshToken: String): TokenInfo {
        val response: FitbitTokenResponse = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", BuildConfig.FITBIT_CLIENT_ID)
            },
        ) {
            header("Authorization", basicAuthHeader())
        }.body()

        return TokenInfo(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.userId ?: cachedToken?.userId,
            expiresAtMs = System.currentTimeMillis() + (response.expiresIn * 1000L) - 60_000L,
        )
    }

    private suspend fun persistTokens(token: TokenInfo) {
        val now = System.currentTimeMillis()
        syncStateDao.upsert(SyncStateEntity(KEY_ACCESS_TOKEN, token.accessToken, now))
        syncStateDao.upsert(SyncStateEntity(KEY_REFRESH_TOKEN, token.refreshToken, now))
        syncStateDao.upsert(SyncStateEntity(KEY_EXPIRES_AT, token.expiresAtMs.toString(), now))
        token.userId?.let {
            syncStateDao.upsert(SyncStateEntity(KEY_USER_ID, it, now))
        }
    }

    private fun basicAuthHeader(): String {
        val credentials = "${BuildConfig.FITBIT_CLIENT_ID}:${BuildConfig.FITBIT_CLIENT_SECRET}"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private data class TokenInfo(
        val accessToken: String,
        val refreshToken: String,
        val userId: String?,
        val expiresAtMs: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs
    }

    @Serializable
    data class FitbitTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long = 28800,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("user_id") val userId: String? = null,
        @SerialName("scope") val scope: String? = null,
    )

    companion object {
        const val REDIRECT_URI = "pulse://fitbit/callback"
        private const val AUTH_URL = "https://www.fitbit.com/oauth2/authorize"
        private const val TOKEN_URL = "https://api.fitbit.com/oauth2/token"
        private const val SCOPES = "activity heartrate sleep weight profile"
        private const val KEY_ACCESS_TOKEN = "fitbit_access_token"
        private const val KEY_REFRESH_TOKEN = "fitbit_refresh_token"
        private const val KEY_EXPIRES_AT = "fitbit_expires_at"
        private const val KEY_USER_ID = "fitbit_user_id"
    }
}
