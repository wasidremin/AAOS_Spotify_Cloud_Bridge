package com.cloudbridge.spotify.auth

import android.util.Log
import com.cloudbridge.spotify.network.SpotifyAuthService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Authenticator that automatically refreshes the Spotify access token
 * when a 401 Unauthorized response is received.
 *
 * Architecture notes:
 * - Uses a [Mutex] to prevent concurrent refresh storms when multiple
 *   requests hit 401 simultaneously (common during browse tree loading).
 * - After the first request successfully refreshes, subsequent waiters
 *   will find a valid token and skip the refresh call.
 * - Limits retries to 1 to avoid infinite loops if the refresh token is revoked.
 *
 * Why an Authenticator instead of an Interceptor for refresh:
 * - OkHttp's Authenticator is purpose-built for 401 handling.
 * - It automatically retries the original request with the new credentials.
 * - It's only invoked on 401, not on every request (unlike an Interceptor).
 */
class TokenRefreshAuthenticator(
    private val tokenManager: TokenManager,
    private val authServiceProvider: () -> SpotifyAuthService
) : Authenticator {

    private val refreshMutex = Mutex()

    /**
     * Called by OkHttp when a 401 Unauthorized response is received.
     *
     * Acquires the [refreshMutex] to serialize concurrent refresh attempts,
     * then performs a double-check-locking pattern: if the token was already
     * refreshed by another thread while we waited, we reuse it immediately.
     *
     * @param route  The target route (may be `null` for CONNECT tunnels).
     * @param response The 401 response that triggered authentication.
     * @return A new [Request] with the refreshed Bearer token, or `null`
     *         to give up (stops OkHttp from retrying).
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite retry loops — give up after 1 attempt.
        // responseCount() walks the prior-response chain to count retries.
        if (responseCount(response) >= 2) {
            Log.w(TAG, "Token refresh failed after retry. Giving up.")
            return null
        }

        // runBlocking is safe here — OkHttp's Authenticator runs on its own
        // I/O thread pool, not the Android main thread.
        return runBlocking {
            // ── Mutex-guarded refresh ─────────────────────────────────
            // Only ONE thread performs the actual HTTP refresh; all others
            // wait and then reuse the freshly stored token.
            refreshMutex.withLock {
                // Double-check: another thread may have already refreshed
                // the token while we were blocked on the mutex.
                if (tokenManager.isAccessTokenValid()) {
                    val freshToken = tokenManager.getAccessToken()
                    Log.d(TAG, "Token already refreshed by another thread.")
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                }

                // Perform the actual refresh via Spotify Accounts API.
                val clientId = tokenManager.getClientId()
                val clientSecret = tokenManager.getClientSecret()
                val refreshToken = tokenManager.getRefreshToken()

                if (clientId.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                    Log.e(TAG, "No credentials stored. Cannot refresh token.")
                    return@runBlocking null
                }

                try {
                    // Build HTTP Basic auth header per OAuth 2.0 spec:
                    // Base64(client_id:client_secret)
                    val authHeader = "Basic " + android.util.Base64.encodeToString(
                        "$clientId:${clientSecret ?: ""}".toByteArray(),
                        android.util.Base64.NO_WRAP
                    )

                    val tokenResponse = authServiceProvider().refreshToken(
                        authHeader = authHeader,
                        grantType = "refresh_token",
                        refreshToken = refreshToken
                    )

                    // Persist the new token + expiry so other threads & future
                    // requests pick it up without another refresh.
                    tokenManager.saveAccessToken(
                        accessToken = tokenResponse.accessToken,
                        expiresInSeconds = tokenResponse.expiresIn
                    )

                    Log.i(TAG, "Token refreshed successfully. Expires in ${tokenResponse.expiresIn}s")

                    // Retry the original request with the new Bearer token.
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                        .build()
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh failed: ${e.message}", e)
                    null // Returning null tells OkHttp to stop retrying.
                }
            }
        }
    }

    /**
     * Counts how many times this response chain has been through authentication.
     * Prevents infinite retry loops.
     */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenRefreshAuth"
    }
}
