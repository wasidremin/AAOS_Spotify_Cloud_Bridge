package com.cloudbridge.spotify.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp [Interceptor] that injects the `Authorization: Bearer <token>` header
 * into every outgoing request destined for the Spotify Web API.
 *
 * Design decisions:
 * - Reads the current access token from [TokenManager] using [runBlocking].
 *   This is **safe** because OkHttp interceptors execute on OkHttp's own
 *   I/O dispatcher threads, never on the Android main thread.
 * - Only targets requests to `api.spotify.com`; requests to
 *   `accounts.spotify.com` (token refresh) pass through unmodified to
 *   prevent a circular-dependency loop with [TokenRefreshAuthenticator].
 * - If no token is stored yet, the request proceeds unauthenticated.
 *   The resulting 401 will be caught by [TokenRefreshAuthenticator],
 *   which performs the refresh and retries the request transparently.
 *
 * @param tokenManager The singleton [TokenManager] that stores OAuth tokens.
 * @see TokenRefreshAuthenticator
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    /**
     * Intercepts the outgoing HTTP request and attaches a Bearer token.
     *
     * @param chain The OkHttp interceptor chain.
     * @return The [Response] from the next interceptor or the network.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Guard: skip auth header for non-API hosts (e.g. accounts.spotify.com)
        // to avoid circular dependency during token refresh.
        if (originalRequest.url.host != "api.spotify.com") {
            return chain.proceed(originalRequest)
        }

        runBlocking {
            if (tokenManager.isRateLimitLockoutActive()) {
                throw GlobalRateLimitException(
                    lockedUntilEpochMs = tokenManager.getRateLimitUntilEpochMs(),
                    retryAfterSeconds = tokenManager.getRateLimitRetryAfterSeconds()
                )
            }
        }

        // runBlocking is intentional — OkHttp interceptors run on their own
        // thread pool, so blocking here does not risk an ANR.
        val accessToken = runBlocking { tokenManager.getAccessToken() }

        val authenticatedRequest = if (!accessToken.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            // No token yet — proceed unauthenticated; the Authenticator will handle 401.
            originalRequest
        }

        return chain.proceed(authenticatedRequest)
    }
}
