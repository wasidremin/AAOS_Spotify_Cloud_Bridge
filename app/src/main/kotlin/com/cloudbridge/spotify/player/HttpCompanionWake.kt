package com.cloudbridge.spotify.player

import com.cloudbridge.spotify.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * POSTs a JSON body to a Home Assistant (or similar) webhook URL to ask the
 * phone to open Spotify. Fire-and-forget: success means "request sent", not
 * "Spotify is ready".
 *
 * [urlProvider] is called each wake so the URL can be changed in Settings
 * without restarting the process. Empty/null URL → no-op (returns false).
 */
class HttpCompanionWake(
    private val urlProvider: () -> String?,
    private val client: OkHttpClient = defaultClient()
) : CompanionWakeStrategy {

    companion object {
        private const val TAG = "HttpCompanionWake"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build()
    }

    override suspend fun requestWake(): Boolean = withContext(Dispatchers.IO) {
        val url = urlProvider()?.trim().orEmpty()
        if (url.isBlank()) {
            AppLogger.d(TAG, "No companion wake URL configured")
            return@withContext false
        }

        return@withContext try {
            val body = """{"source":"cloud-bridge","action":"wake_spotify"}"""
                .toRequestBody(JSON)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                AppLogger.i(TAG, "Webhook ${response.code} → $url (ok=$ok)")
                ok
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Webhook failed: ${e.message}")
            false
        }
    }
}
