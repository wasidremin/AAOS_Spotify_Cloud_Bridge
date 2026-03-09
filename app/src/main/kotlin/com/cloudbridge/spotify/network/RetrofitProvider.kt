package com.cloudbridge.spotify.network

import android.util.Log
import com.cloudbridge.spotify.BuildConfig
import com.cloudbridge.spotify.auth.AuthInterceptor
import com.cloudbridge.spotify.auth.GlobalRateLimitException
import com.cloudbridge.spotify.auth.TokenManager
import com.cloudbridge.spotify.auth.TokenRefreshAuthenticator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Provides configured Retrofit instances for both Spotify APIs.
 *
 * Two separate Retrofit instances are needed because:
 * 1. SpotifyApiService → api.spotify.com (uses Bearer auth)
 * 2. SpotifyAuthService → accounts.spotify.com (no Bearer auth, uses form POST)
 *
 * The auth service MUST use a separate OkHttpClient without the AuthInterceptor
 * and TokenRefreshAuthenticator to avoid circular dependencies during token refresh.
 */
class RetrofitProvider(private val tokenManager: TokenManager) {

    companion object {
        private const val CLOUD_RELAY_BASE_URL = "https://aaosspotiftycloudbridge-default-rtdb.firebaseio.com/"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ── Auth Service (no Bearer token needed) ────────────────────────

    private val authClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .build()

    val spotifyAuth: SpotifyAuthService = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/")
        .client(authClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyAuthService::class.java)

    val cloudRelay: CloudRelayService = Retrofit.Builder()
        .baseUrl(CLOUD_RELAY_BASE_URL)
        .client(authClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(CloudRelayService::class.java)

    // ── API Service (with auth + retry) ──────────────────────────────

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenManager))
        .addInterceptor(RateLimitRetryInterceptor(tokenManager))
        .authenticator(TokenRefreshAuthenticator(tokenManager) { spotifyAuth })
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .build()

    val spotifyApi: SpotifyApiService = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(apiClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyApiService::class.java)
}

/**
 * Interceptor that handles Spotify's rate limiting (HTTP 429 Too Many Requests).
 *
 * When Spotify returns 429, this interceptor:
 * 1. Reads the `Retry-After` header (value in **seconds**).
 * 2. If the requested delay is within [MAX_RETRY_DELAY_SECONDS] (10 s),
 *    sleeps on the current OkHttp dispatcher thread and retries.
 * 3. If the delay exceeds the cap, the 429 response is **immediately
 *    returned** to the caller so the UI can display a graceful error
 *    instead of blocking all OkHttp threads for minutes or hours.
 *
 * Maximum [MAX_RETRIES] (3) attempts to prevent infinite loops.
 *
 * Why `Thread.sleep` is acceptable:
 * OkHttp interceptors execute on OkHttp's own thread pool, not the
 * Android main thread. Sleeping here does not risk ANR.
 *
 * @see <a href="https://developer.spotify.com/documentation/web-api/concepts/rate-limits">
 *   Spotify Rate Limits</a>
 */
private class RateLimitRetryInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        private const val TAG = "RateLimitRetry"
        /** Maximum number of transparent retries before surfacing the 429. */
        private const val MAX_RETRIES = 3
        /**
         * Safety cap (seconds). If Spotify demands a backoff longer than this,
         * the 429 is immediately surfaced rather than sleeping the thread.
         * Prevents the scenario where a single `retry-after: 4664` header
         * blocks all OkHttp threads for over an hour.
         */
        private const val MAX_RETRY_DELAY_SECONDS = 10L
    }

    /**
     * Intercepts responses and transparently retries on 429.
     *
     * @param chain The OkHttp interceptor chain.
     * @return The final [Response] — either a successful retry or the 429 itself.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var retryCount = 0

        while (response.code == 429 && retryCount < MAX_RETRIES) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L

            // Cap check: if the server demands a long backoff, bail out
            // immediately so callers can show a user-facing error.
            if (retryAfter > MAX_RETRY_DELAY_SECONDS) {
                runBlocking { tokenManager.saveRateLimitLockout(retryAfter) }
                Log.w(TAG, "Rate limited (429). Retry-After ${retryAfter}s exceeds cap — surfacing 429 to caller.")
                return response
            }

            Log.w(TAG, "Rate limited (429). Retry-After: ${retryAfter}s. Attempt ${retryCount + 1}/$MAX_RETRIES")
            response.close()

            try {
                Thread.sleep(retryAfter * 1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Rate limit retry interrupted", e)
            }

            response = chain.proceed(request)
            retryCount++
        }

        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: MAX_RETRY_DELAY_SECONDS
            runBlocking { tokenManager.saveRateLimitLockout(retryAfter) }
            throw GlobalRateLimitException(
                lockedUntilEpochMs = runBlocking { tokenManager.getRateLimitUntilEpochMs() },
                retryAfterSeconds = retryAfter
            )
        }

        return response
    }
}
